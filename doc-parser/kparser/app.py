import asyncio
import json
from typing import Any, Dict, Optional
from contextlib import asynccontextmanager
from concurrent.futures.process import ProcessPoolExecutor
from datetime import datetime, timezone
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi import FastAPI, Form, BackgroundTasks, File, UploadFile, Body, Request
from fastapi.staticfiles import StaticFiles
import uuid
import requests
import time

from kparser.core.job_manager import job_manager
from kparser.core.utils import validate_pages
from kparser.core.process_executor import start_deep_parse_task
from kparser.core.schemas import (exception2callback, success2callback, running2callback, fail2callback,
                                  null2callback, terminate2callback, notask2callback, success2resp, fail2resp)
from kparser.common import config as settings
from kparser.common.upload_oss import upload_oss_api
from kparser.common.log_utils import get_logger
from kparser.parserground.parser import ppt_doc_to_pdf
from kparser.apm_manager import init_apm, is_apm_enabled


logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """ 统一管理应用的生命周期 """
    # ---------- 应用启动逻辑 ----------
    # 🔧 确保多进程使用安全的启动方法（防止 SIGSEGV）
    import multiprocessing
    try:
        multiprocessing.set_start_method('spawn', force=True)
        logger.info("✅ Multiprocessing start method set to 'spawn' for ProcessPoolExecutor")
    except RuntimeError:
        logger.debug("Multiprocessing start method already set")
    
    # 初始化进程池
    app.state.executor = ProcessPoolExecutor(
        max_workers=int(settings.SERVICE["max_job_number"])
    )
    logger.info(">>>>> ProcessPoolExecutor initialized <<<<<<")

    # 启动定时清理任务
    cleanup_task = asyncio.create_task(job_manager.cleanup_jobs())

    yield  # 应用运行阶段

    # ---------- 应用关闭逻辑 ----------
    # 关闭进程池
    app.state.executor.shutdown(wait=True)
    logger.info(">>>>> ProcessPoolExecutor shutdown <<<<<<")

    # 停止清理任务
    cleanup_task.cancel()
    try:
        await cleanup_task
    except asyncio.CancelledError:
        pass

    # 记录最终任务状态
    for key, value in job_manager.jobs.items():
        logger.info(f"Final status: {key} - {value['status']}")


# 启动服务
# app = FastAPI(title="Delivery Parser API")
app = FastAPI(
    title="Document Parser API",
    description="Document Parser API",
    lifespan=lifespan  # 注册生命周期管理器
)
app.mount("/static", StaticFiles(directory="static"), name="static")

init_apm(app)


async def _request_payload(request: Request) -> Dict[str, Any]:
    content_type = request.headers.get("content-type", "")
    if "application/json" in content_type:
        body = await request.json()
        return body if isinstance(body, dict) else {}

    form = await request.form()
    return dict(form)


def _payload_text(payload: Dict[str, Any], key: str, default: str = "") -> str:
    value = payload.get(key)
    if value is None or _is_upload_file(value):
        return default
    return str(value)


def _is_upload_file(value: Any) -> bool:
    return hasattr(value, "filename") and hasattr(value, "read")


def _payload_bool(payload: Dict[str, Any], key: str, default: bool) -> bool:
    value = payload.get(key)
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "on"}


def _payload_int(payload: Dict[str, Any], key: str, default: int) -> int:
    value = payload.get(key)
    if value is None:
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _metadata(payload: Dict[str, Any]) -> Dict[str, Any]:
    value = payload.get("metadata")
    if isinstance(value, dict):
        return value
    if value is None or _is_upload_file(value):
        return {}
    try:
        parsed = json.loads(str(value))
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {}


def _resolve_task_id(payload: Dict[str, Any], metadata: Dict[str, Any]) -> str:
    for key in ("task_id", "taskId", "request_id", "requestId"):
        value = _payload_text(payload, key)
        if value:
            return value
    for key in ("requestId", "request_id", "taskId", "task_id"):
        value = metadata.get(key)
        if value:
            return str(value)
    return f"parser_task_{uuid.uuid4().hex}"


def _async_submit_success(task_id: str, message: str = "task accepted") -> Dict[str, Any]:
    return {
        "success": True,
        "task_id": task_id,
        "status": "PENDING",
        "message": message
    }


def _async_failure(task_id: Optional[str], message: str) -> Dict[str, Any]:
    return {
        "success": False,
        "task_id": task_id,
        "status": "FAILED",
        "progress": 0.0,
        "message": message,
        "error": message
    }


def _normalize_async_result(result: Any) -> Dict[str, Any]:
    if isinstance(result, dict):
        if "content" in result or "chunks" in result or "metadata" in result:
            normalized = dict(result)
            normalized.setdefault("success", True)
            return normalized
        if "oss_url" in result:
            return {
                "success": True,
                "content": "",
                "chunks": [],
                "metadata": {
                    "result_url": result.get("oss_url"),
                    "pdf_file": result.get("pdf_file", "")
                }
            }
    if isinstance(result, str):
        return {
            "success": True,
            "content": "",
            "chunks": [],
            "metadata": {
                "result_url": result
            }
        }
    return {
        "success": True,
        "content": "",
        "chunks": [],
        "metadata": {}
    }


def _async_status_response(task_id: str, task_info: Dict[str, Any]) -> Dict[str, Any]:
    raw_status = task_info.get("status")
    progress = max(0, min(100, int(task_info.get("progress") or 0))) / 100
    logs = "|".join(task_info.get("logs", []))

    if raw_status == "complete":
        return {
            "success": True,
            "task_id": task_id,
            "status": "SUCCEEDED",
            "progress": 1.0,
            "message": "task completed",
            "error": None,
            "result": _normalize_async_result(task_info.get("result"))
        }
    if raw_status == "failed":
        error = str(task_info.get("result") or "task failed")
        return {
            "success": True,
            "task_id": task_id,
            "status": "FAILED",
            "progress": progress,
            "message": error,
            "error": error,
            "result": None
        }
    if raw_status == "killed":
        return {
            "success": True,
            "task_id": task_id,
            "status": "CANCELED",
            "progress": progress,
            "message": "task canceled",
            "error": "task canceled",
            "result": None
        }

    return {
        "success": True,
        "task_id": task_id,
        "status": "RUNNING" if progress > 0 else "PENDING",
        "progress": progress,
        "message": logs or "task running",
        "error": None,
        "result": None
    }


def _parse_binary_for_rag(filename: str, binary: bytes, doc_type: str) -> Dict[str, Any]:
    from kparser.rag.templates import general, presentation, picture
    from kparser.common.types_utils import GENERAL_TYPE, PRESENTATION_TYPE, PICTURE_TYPE

    suffix = filename.rsplit(".", 1)[-1].lower() if "." in filename else "txt"

    if suffix in GENERAL_TYPE:
        parser_id = "general"
    elif suffix in PRESENTATION_TYPE:
        parser_id = "presentation"
    elif suffix in PICTURE_TYPE:
        parser_id = "picture"
    else:
        return {"success": False, "error": f"不支持的文件类型: {suffix}"}

    factory = {
        "general": general,
        "presentation": presentation,
        "picture": picture
    }
    chunker = factory[parser_id]

    parser_config = {
        "layout": False,
        "ocr_content": True,
        "rules": [{"rule_method": "3", "feature_value": ["ROW_HEADER"]}]
    }

    cks = chunker.chunk(filename, filename, binary=binary,
                        from_page=0, to_page=100000,
                        parser_config=parser_config)

    full_content = "\n".join(
        ck.get("content", "") for ck in cks if ck.get("content", "").strip()
    )

    chunks = []
    for i, ck in enumerate(cks):
        content = ck.get("content", "")
        if not content.strip():
            continue
        chunks.append({
            "content": content,
            "index": i,
            "length": len(content),
            "tokenCount": len(content) // 2,
            "metadata": {
                "page_idx": ck.get("page_idx", []),
                "content_type": ck.get("content_type", "TEXT")
            }
        })

    return {
        "success": True,
        "content": full_content,
        "chunks": chunks,
        "metadata": {
            "filename": filename,
            "doc_type": doc_type,
            "parser_id": parser_id,
            "total_chunks": len(chunks)
        }
    }


def _parse_url_for_rag(file_url: str, doc_type: str) -> Dict[str, Any]:
    if not file_url:
        return {"success": False, "error": "file_url 不能为空"}

    response = requests.get(file_url, timeout=120)
    response.raise_for_status()
    url_clean = file_url.split("?")[0]
    filename = url_clean.rsplit("/", 1)[-1] if "/" in url_clean else "unknown.txt"
    return _parse_binary_for_rag(filename, response.content, doc_type)


async def _run_uploaded_file_parse_task(task_id: str, filename: str, binary: bytes, doc_type: str):
    try:
        await job_manager.update_progress(task_id, 10, "读取上传文件")
        result = await asyncio.to_thread(_parse_binary_for_rag, filename, binary, doc_type)
        if result.get("success") is False:
            raise ValueError(result.get("error", "解析失败"))
        await job_manager.update_progress(task_id, 90, "解析完成，准备返回结果")
        job_manager.jobs[task_id]["result"] = result
        job_manager.jobs[task_id]["status"] = "complete"
        job_manager.jobs[task_id]["progress"] = 100
        job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)
    except Exception as exc:
        logger.error(f"Task {task_id} uploaded file parse failed: {exc}", exc_info=True)
        if task_id in job_manager.jobs:
            job_manager.jobs[task_id]["status"] = "failed"
            job_manager.jobs[task_id]["result"] = str(exc)
            job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)


async def _run_url_file_parse_task(task_id: str, file_url: str, doc_type: str):
    try:
        await job_manager.update_progress(task_id, 10, "下载远程文件")
        result = await asyncio.to_thread(_parse_url_for_rag, file_url, doc_type)
        if result.get("success") is False:
            raise ValueError(result.get("error", "解析失败"))
        await job_manager.update_progress(task_id, 90, "解析完成，准备返回结果")
        job_manager.jobs[task_id]["result"] = result
        job_manager.jobs[task_id]["status"] = "complete"
        job_manager.jobs[task_id]["progress"] = 100
        job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)
    except Exception as exc:
        logger.error(f"Task {task_id} URL parse failed: {exc}", exc_info=True)
        if task_id in job_manager.jobs:
            job_manager.jobs[task_id]["status"] = "failed"
            job_manager.jobs[task_id]["result"] = str(exc)
            job_manager.jobs[task_id]["timestamp"] = datetime.now(timezone.utc)


# Custom docs route
@app.get("/local_docs", include_in_schema=False)
async def custom_swagger_ui_html():
    return get_swagger_ui_html(
        openapi_url=app.openapi_url,
        title=app.title + " - Swagger UI",
        swagger_js_url="/static/swagger-ui-bundle.js",
        swagger_css_url="/static/swagger-ui.css",
        swagger_favicon_url="/static/favicon.png"
    )


# 文档上传oss接口
@app.post("/loader/upload", tags=["oss-io"], summary="上传本地文件到oss存储")
def upload_to_oss(
    file: UploadFile = File(
        description="上传的文件, 支持后缀名.txt/.md/.json/.jsonl/.docx/.doc/.pdf/.pptx/.ppt/.xlsx/.xls/.csv"
    )
):
    try:
        url = upload_oss_api(file)
        return success2callback(message="upload file to oss successfully",
                                data=url)
    except Exception as e:
        logger.error(f"upload file job raise error: {e}")
        return fail2callback(message=f"upload file to oss failed, error: {e}")


# 异步解析接口
@app.post("/loader/deep_parse/async", tags=["loader"], summary="解析文件异步接口")
async def deep_parse_async(
        request: Request,
        background_tasks: BackgroundTasks,
        ):

    try:
        payload = await _request_payload(request)
        metadata = _metadata(payload)
        request_id = _resolve_task_id(payload, metadata)
        doc_type = _payload_text(payload, "doc_type", "DOCUMENT_BASIC")
        file = payload.get("file")
        file_url = _payload_text(payload, "file_url")
        original_url = _payload_text(payload, "original_url")

        # 🔥 检查是否有空余进程
        can_accept, current_load, max_workers = await job_manager.can_accept_new_job()
        
        if not can_accept:
            error_msg = f"服务器繁忙，当前负载 {current_load}/{max_workers}，请稍后重试"
            logger.warning(f"⚠️  Rejected HTTP request: {error_msg}, request_id={request_id}")
            return _async_failure(request_id, error_msg)

        if _is_upload_file(file):
            filename = file.filename or _payload_text(payload, "filename", "uploaded-document")
            binary = await file.read()
            await job_manager.create_job(
                request_id,
                request_id=request_id,
                filename=filename,
                doc_id=metadata.get("docId") or metadata.get("doc_id") or _payload_text(payload, "doc_id"),
                doc_type=doc_type,
                metadata=metadata,
                source="multipart"
            )
            background_tasks.add_task(
                _run_uploaded_file_parse_task,
                request_id,
                filename,
                binary,
                doc_type
            )
            logger.info(f"Request {request_id} async uploaded file job created")
            return _async_submit_success(request_id)

        if file_url:
            filename = file_url.split("?")[0].rsplit("/", 1)[-1] if "/" in file_url else "remote-document"
            await job_manager.create_job(
                request_id,
                request_id=request_id,
                filename=filename,
                doc_id=metadata.get("docId") or metadata.get("doc_id") or _payload_text(payload, "doc_id"),
                doc_type=doc_type,
                metadata=metadata,
                source="file_url"
            )
            background_tasks.add_task(
                _run_url_file_parse_task,
                request_id,
                file_url,
                doc_type
            )
            logger.info(f"Request {request_id} async URL file job created")
            return _async_submit_success(request_id)
        
        # 校验page设置
        start_page = _payload_text(payload, "start_page", "1")
        end_page = _payload_text(payload, "end_page")
        start_page, end_page = validate_pages(start_page, end_page)

        # 解析 oss_config JSON 字符串
        oss_config = _payload_text(payload, "oss_config", json.dumps(settings.TOS))
        try:
            oss_config_dict = json.loads(oss_config) if oss_config else {}
        except json.JSONDecodeError:
            oss_config_dict = {}
        
        # 使用管理器创建任务
        filename = _payload_text(payload, "filename", "image_table.pdf")
        doc_id = _payload_int(payload, "doc_id", 123456)
        call_back_url = _payload_text(payload, "call_back_url")
        await job_manager.create_job(
            request_id,
            filename=filename,
            doc_id=doc_id,
            call_back_url=call_back_url,
            doc_type=doc_type,
            metadata=metadata,
            source="original_url"
        )

        logger.info(f"Request {request_id} async job created")
        parser_rule = _payload_text(payload, "parser_rule")
        parser_rule = '[{"rule_method":"3","feature_value":["ROW_HEADER"]}]' if parser_rule == "" else parser_rule
        rule_config_dict = json.loads(parser_rule)
        background_tasks.add_task(
            start_deep_parse_task,
            request_id,
            request_id=request_id,
            doc_id=doc_id,
            filename=filename,
            original_url=original_url,
            from_page=start_page - 1,
            to_page=end_page,
            upload_image=_payload_bool(payload, "upload_image", True),
            table_image=_payload_bool(payload, "table_image", True),
            rule_config=rule_config_dict,
            to_pdf=_payload_bool(payload, "to_pdf", False),
            layout=_payload_bool(payload, "layout", True),
            ocr_content=_payload_bool(payload, "ocr_content", True),
            vision=_payload_bool(payload, "vision", False),
            table_vision=_payload_bool(payload, "table_vision", False),
            environment=_payload_text(payload, "environment", "ONLINE"),
            oss_type=_payload_text(payload, "oss_type", "TOS"),
            oss_config=oss_config_dict,
            call_back_url=call_back_url
        )

        logger.info(f"Request {request_id} async job return status")
        return _async_submit_success(request_id)
    except Exception as e:
        failed_request_id = locals().get("request_id")
        logger.error(f"request_id {failed_request_id} async job raise error: {e}")
        return _async_failure(failed_request_id, str(e))


@app.post("/loader/status", tags=["loader"], summary="通过请求id查询文档解析结果存储地址")
async def file_parse_status(
    request: Request,
):
    payload = await _request_payload(request)
    request_id = (
        _payload_text(payload, "task_id")
        or _payload_text(payload, "taskId")
        or _payload_text(payload, "request_id")
        or _payload_text(payload, "requestId")
    )
    if not request_id:
        return _async_failure(None, "task_id 不能为空")

    logger.debug("status job_manager.jobs={}".format(job_manager.jobs))
    if request_id not in job_manager.jobs:
        return _async_failure(request_id, f"请求id为{request_id}任务未提交")

    task_info = job_manager.jobs[request_id]
    return _async_status_response(request_id, task_info)


@app.post("/loader/kill_task", tags=["loader"], summary="终止正在执行的解析任务")
async def kill_parser_task(
        request_id: str = Form(description="需要终止的任务请求ID", examples=["1635b85cc5f211efbe1d1e63462f5d8f"])
):
    try:
        # 检查任务是否存在
        if request_id not in job_manager.jobs:
            return null2callback(message="任务不存在")

        task_info = job_manager.jobs[request_id]

        # 检查任务状态
        if task_info["status"] != "in_progress":
            return notask2callback(message="任务未在执行中")

        # 获取进程PID
        pid = task_info.get("process")
        if not pid:
            return exception2callback(message="进程ID未找到")

        # 终止进程
        process = task_info.get("process")
        if process and process.is_alive():
            process.terminate()  # 先尝试优雅终止
            await asyncio.sleep(2)
            if process.is_alive():
                process.kill()  # 强制终止

        logger.info(f"request_id={request_id} process killed")

        # 更新任务状态
        task_info["status"] = "killed"
        task_info["result"] = {"result": "用户主动终止"}
        task_info["timestamp"] = datetime.now(timezone.utc)

        return success2callback(message="任务已终止")

    except ProcessLookupError:
        logger.error(f"进程不存在")
        return null2callback(message="进程不存在")

    except Exception as e:
        logger.error(f"终止任务失败: {str(e)}")
        return terminate2callback(message=f"终止失败: {str(e)}")


@app.post("/document/conversion", tags=["convert"], summary="ppt/doc/pptx/docx等文件转pdf格式")
async def convert_to_pdf(
        original_url: str = Form(
            default="http://cdn.bigmodel.cn/upload/20250307/b526cd8f4fe79b2cdc4ace6aaced1dde.ppt",
            description="原始文档地址"
        ),
):
    try:
        pdf_original_url = ppt_doc_to_pdf(original_url)
        return success2callback(message="转换pdf成功",
                                data=pdf_original_url)

    except Exception as e:
        return fail2callback(message=f"{original_url}任务转换pdf异常，错误为{e}")

@app.post("/parse", tags=["sync-parse"], summary="同步解析本地上传的文件（供Java知识库服务调用）")
async def sync_parse_file(
        file: UploadFile = File(description="上传的文件"),
        doc_type: str = Form(default="DOCUMENT_BASIC", description="文档类型")
):
    """
    同步接口：接收上传文件 → 解析 → 直接返回结构化内容。
    专为 Java DocParserClient 设计，无需 OSS 中转。
    """
    from kparser.rag.templates import general, presentation, picture
    from kparser.common.types_utils import GENERAL_TYPE, PRESENTATION_TYPE, PICTURE_TYPE

    try:
        filename = file.filename or "unknown.txt"
        binary = await file.read()

        suffix = filename.rsplit(".", 1)[-1].lower() if "." in filename else "txt"

        if suffix in GENERAL_TYPE:
            parser_id = "general"
        elif suffix in PRESENTATION_TYPE:
            parser_id = "presentation"
        elif suffix in PICTURE_TYPE:
            parser_id = "picture"
        else:
            return {"success": False, "error": f"不支持的文件类型: {suffix}"}

        factory = {
            "general": general,
            "presentation": presentation,
            "picture": picture
        }
        chunker = factory[parser_id]

        parser_config = {
            "layout": False,
            "ocr_content": True,
            "rules": [{"rule_method": "3", "feature_value": ["ROW_HEADER"]}]
        }

        cks = chunker.chunk(filename, filename, binary=binary,
                            from_page=0, to_page=100000,
                            parser_config=parser_config)

        full_content = "\n".join(
            ck.get("content", "") for ck in cks if ck.get("content", "").strip()
        )

        chunks = []
        for i, ck in enumerate(cks):
            c = ck.get("content", "")
            if not c.strip():
                continue
            chunks.append({
                "content": c,
                "index": i,
                "length": len(c),
                "tokenCount": len(c) // 2,
                "metadata": {
                    "page_idx": ck.get("page_idx", []),
                    "content_type": ck.get("content_type", "TEXT")
                }
            })

        return {
            "content": full_content,
            "chunks": chunks,
            "metadata": {
                "filename": filename,
                "doc_type": doc_type,
                "parser_id": parser_id,
                "total_chunks": len(chunks)
            }
        }

    except Exception as e:
        logger.error(f"同步解析失败: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@app.post("/parse_url", tags=["sync-parse"], summary="同步解析远程URL文件（供Java知识库服务调用）")
async def sync_parse_url(
        request_body: dict = Body(...)
):
    """
    同步接口：通过 URL 下载文件 → 解析 → 直接返回结构化内容。
    """
    from kparser.rag.templates import general, presentation, picture
    from kparser.common.types_utils import GENERAL_TYPE, PRESENTATION_TYPE, PICTURE_TYPE

    try:
        file_url = request_body.get("file_url", "")
        doc_type = request_body.get("doc_type", "DOCUMENT_BASIC")

        if not file_url:
            return {"success": False, "error": "file_url 不能为空"}

        resp = requests.get(file_url, timeout=120)
        resp.raise_for_status()
        binary = resp.content

        url_clean = file_url.split("?")[0]
        filename = url_clean.rsplit("/", 1)[-1] if "/" in url_clean else "unknown.txt"
        suffix = filename.rsplit(".", 1)[-1].lower() if "." in filename else "txt"

        if suffix in GENERAL_TYPE:
            parser_id = "general"
        elif suffix in PRESENTATION_TYPE:
            parser_id = "presentation"
        elif suffix in PICTURE_TYPE:
            parser_id = "picture"
        else:
            return {"success": False, "error": f"不支持的文件类型: {suffix}"}

        factory = {
            "general": general,
            "presentation": presentation,
            "picture": picture
        }
        chunker = factory[parser_id]

        parser_config = {
            "layout": False,
            "ocr_content": True,
            "rules": [{"rule_method": "3", "feature_value": ["ROW_HEADER"]}]
        }

        cks = chunker.chunk(filename, filename, binary=binary,
                            from_page=0, to_page=100000,
                            parser_config=parser_config)

        full_content = "\n".join(
            ck.get("content", "") for ck in cks if ck.get("content", "").strip()
        )

        chunks = []
        for i, ck in enumerate(cks):
            c = ck.get("content", "")
            if not c.strip():
                continue
            chunks.append({
                "content": c,
                "index": i,
                "length": len(c),
                "tokenCount": len(c) // 2,
                "metadata": {
                    "page_idx": ck.get("page_idx", []),
                    "content_type": ck.get("content_type", "TEXT")
                }
            })

        return {
            "content": full_content,
            "chunks": chunks,
            "metadata": {
                "filename": filename,
                "doc_type": doc_type,
                "parser_id": parser_id,
                "total_chunks": len(chunks)
            }
        }

    except requests.RequestException as e:
        logger.error(f"下载文件失败: {e}")
        return {"success": False, "error": f"下载文件失败: {e}"}
    except Exception as e:
        logger.error(f"URL同步解析失败: {e}", exc_info=True)
        return {"success": False, "error": str(e)}


@app.get("/health", tags=["system"], summary="健康检查")
async def health_check():
    return {
        "status": "success",
        "message": "Service is running",
        "timestamp": datetime.now().isoformat(),
        "apm_enabled": is_apm_enabled()
    }
