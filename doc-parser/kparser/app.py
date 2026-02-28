import asyncio
import json
from contextlib import asynccontextmanager
from concurrent.futures.process import ProcessPoolExecutor
from datetime import datetime, timezone
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi import FastAPI, Form, BackgroundTasks, File, UploadFile, Body
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
        background_tasks: BackgroundTasks,
        request_id: str = Form(default="1635b85cc5f211efbe1d1e63462f5d8f", description="请求id，生产环境必须保持id的唯一性"),
        doc_id: int = Form(default=123456, description="文档id"),
        filename: str = Form(default="image_table.pdf", description="文档名称，支持PDF/DOC/DOCX/PPT/PPTX/XLS/XLSX/PNG/JPEG/JPG/CSV/TXT/JSON/HTML"),
        original_url: str = Form(
            default="knowledge-center-dev/shark/多轮（100组-第4轮）模型跑批结果.xlsx",
            description="文档存储地址"
        ),
        start_page: str = Form(default=1, description="开始页码，整型数值，从1开始计数"),
        end_page: str = Form(default="", description="结束页码，整型数值，必须大于开始页码，空值表示最后一页"),
        upload_image: bool = Form(default=True, description="是否上传纯图片到OSS"),
        table_image: bool = Form(default=True, description="是否上传表格转换得到的图片到OSS"),
        parser_rule: str = Form(default="", description='解析规则配置，例如[{"rule_method":"3","feature_value":["ROW_HEADER"]}]'),
        to_pdf: bool = Form(default=False, description="是否将ppt、pptx、doc、docx自动转成pdf处理"),
        layout: bool = Form(default=True, description="是否执行版面分析"),
        ocr_content: bool = Form(default=True, description="是否ocr识别图片中的文字"),
        vision: bool = Form(default=False, description="是否执行多模态识别"),
        table_vision: bool = Form(default=False, description="是否执行针对表格图片的多模态识别"),
        environment: str = Form(default="ONLINE", description="环境，可选值为ONLINE/ATOM"),
        oss_type: str = Form(default="TOS", description="OSS类型，可选值为TOS/CDN"),
        oss_config: str = Form(default=json.dumps(settings.TOS), description="OSS配置JSON字符串"),
        call_back_url: str = Form(default="", description="解析任务回调地址，如果为空，则读取配置文件")
        ):

    try:
        # 🔥 检查是否有空余进程
        can_accept, current_load, max_workers = await job_manager.can_accept_new_job()
        
        if not can_accept:
            error_msg = f"服务器繁忙，当前负载 {current_load}/{max_workers}，请稍后重试"
            logger.warning(f"⚠️  Rejected HTTP request: {error_msg}, request_id={request_id}")
            return fail2resp(error_msg)
        
        # 校验page设置
        start_page, end_page = validate_pages(start_page, end_page)

        # 解析 oss_config JSON 字符串
        try:
            oss_config_dict = json.loads(oss_config) if oss_config else {}
        except json.JSONDecodeError:
            oss_config_dict = {}
        
        # 使用管理器创建任务
        await job_manager.create_job(
            request_id,
            filename=filename,
            doc_id=doc_id,
            call_back_url=call_back_url
        )

        logger.info(f"Request {request_id} async job created")
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
            upload_image=upload_image,
            table_image=table_image,
            rule_config=rule_config_dict,
            to_pdf=to_pdf,
            layout=layout,
            ocr_content=ocr_content,
            vision=vision,
            table_vision=table_vision,
            environment=environment,
            oss_type=oss_type,
            oss_config=oss_config_dict,
            call_back_url=call_back_url
        )

        logger.info(f"Request {request_id} async job return status")
        return success2resp("parser job created")
    except Exception as e:
        logger.error(f"request_id {request_id} async job raise error: {e}")
        return fail2resp(e)


@app.post("/loader/status", tags=["loader"], summary="通过请求id查询文档解析结果存储地址")
async def file_parse_status(
    request_id: str = Form(description="对应文档解析任务请求id", examples=["1635b85cc5f211efbe1d1e63462f5d8f"]),
):
    logger.debug("status job_manager.jobs={}".format(job_manager.jobs))
    if request_id not in job_manager.jobs:
        return null2callback(message=f"请求id为{request_id}任务未提交")

    else:
        task_info = job_manager.jobs[request_id]
        if task_info["status"] == "in_progress":
            return running2callback(message=f"请求id为{request_id}任务解析中")

        elif task_info["status"] == "complete":
            # 兼容新旧两种结果格式
            result = task_info.get('result', '')
            
            # 如果 result 是字典格式，提取 oss_url（保持向后兼容）
            if isinstance(result, dict):
                result_url = result.get('oss_url', '')
            else:
                result_url = result
            
            return success2callback(message=f"请求id为{request_id}任务解析成功",
                             data=result_url)

        else:
            return exception2callback(message=f"请求id为{request_id}任务解析异常，错误为{task_info['result']}")


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