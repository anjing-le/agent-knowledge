# -*- coding: utf-8 -*-
"""
多实例多进程文档解析服务
不依赖消息队列，通过 Redis 实现跨实例的任务分发和状态管理
"""
import asyncio
import json
import os
from contextlib import asynccontextmanager
from concurrent.futures.process import ProcessPoolExecutor
from datetime import datetime, timezone
from fastapi.openapi.docs import get_swagger_ui_html
from fastapi import FastAPI, Form, BackgroundTasks, File, UploadFile, Request
from typing import Optional
from fastapi.staticfiles import StaticFiles
from urllib.parse import unquote
import uuid

from kparser.core.job_manager import job_manager
from kparser.core.utils import validate_pages
from kparser.core.process_executor import start_deep_parse_task
from kparser.core.schemas import (
    success2callback, fail2callback,
    task_submit_success, task_processing, task_success, task_failed, task_not_found, task_canceled
)
from kparser.common import config as settings
from kparser.common.upload_oss import upload_oss_api
from kparser.common.log_utils import get_logger
from kparser.common.file_utils import parse_presigned_url
from kparser.common.config import TOS
from kparser.parserground.parser import ppt_doc_to_pdf
from kparser.apm_manager import init_apm, is_apm_enabled
from kparser.core.loader_dispatch import ocr_service, simple_ocr_service, text_extract

# 导入多实例相关模块（优化版本）
from kparser.multi_instance.cached_state_manager import get_cached_state_manager
from kparser.multi_instance.load_balancer import get_load_balancer
from kparser.multi_instance.progress_batcher import get_progress_batcher

# 导入进度和超时计算
from kparser.core.loader_dispatch import calculate_timeout

# 导入文档类型配置加载器
from kparser.config.doc_type_loader import validate_and_get_params, get_doc_type_loader

logger = get_logger(__name__)

# 获取实例标识
INSTANCE_ID = os.environ.get("INSTANCE_ID", f"instance-{uuid.uuid4().hex[:8]}")
INSTANCE_PORT = int(os.environ.get("INSTANCE_PORT", "7099"))


# ==================== 智能参数解析（兼容 SwaggerUI 和 JSON） ====================

def smart_parse_params(request: Request, form_params: dict, required_fields: list, optional_fields: list = None) -> tuple:
    """
    智能解析请求参数，同时支持 SwaggerUI、JSON 和表单请求
    
    工作原理：
    1. SwaggerUI / 表单请求：使用 form_params（FastAPI 已解析的表单参数）
    2. JSON 请求：检测 Content-Type，手动解析 JSON body，忽略 form_params
    3. URL 查询参数：从 request.query_params 中提取
    
    Args:
        request: FastAPI Request 对象
        form_params: FastAPI 自动解析的表单参数字典（给 SwaggerUI 用）
        required_fields: 必需字段列表
        optional_fields: 可选字段列表
        
    Returns:
        tuple: (params_dict, error_response)
        - 如果成功，返回 (params_dict, None)
        - 如果失败，返回 (None, error_response)
    """
    params = {}
    content_type = request.headers.get("content-type", "")
    optional_fields = optional_fields or []
    
    try:
        # 1. 首先解析 URL 查询参数
        query_params = dict(request.query_params)
        if query_params:
            params.update(query_params)
            logger.debug(f"✅ Parsed Query parameters: {list(query_params.keys())}")
        
        # 2. 然后解析请求体参数
        if "application/json" in content_type:
            # JSON 请求：忽略 Form 参数，手动解析 JSON body
            logger.debug("📝 Detected JSON request, parsing JSON body...")
            # 注意：这里不能使用 await request.json()，因为这个函数是同步的
            # 我们需要在调用处理这个
            params["__is_json__"] = True  # 标记为 JSON 请求
        else:
            # 表单请求：使用 FastAPI 自动解析的 Form 参数
            body_params = {k: v for k, v in form_params.items() if v is not None}
            params.update(body_params)
            logger.debug(f"✅ Using Form parameters: {list(body_params.keys())}")
        
        return params, None
        
    except Exception as e:
        logger.error(f"Error parsing request parameters: {e}")
        return None, {
            "code": "-1",
            "message": f"解析请求参数失败: {str(e)}",
            "data": None
        }


async def smart_parse_params_async(request: Request, form_params: dict, required_fields: list, optional_fields: list = None) -> tuple:
    """
    智能解析请求参数（异步版本），同时支持 SwaggerUI、JSON 和表单请求
    
    Args:
        request: FastAPI Request 对象
        form_params: FastAPI 自动解析的表单参数字典（给 SwaggerUI 用）
        required_fields: 必需字段列表
        optional_fields: 可选字段列表
        
    Returns:
        tuple: (params_dict, error_response)
        - 如果成功，返回 (params_dict, None)
        - 如果失败，返回 (None, error_response)
    """
    params = {}
    content_type = request.headers.get("content-type", "")
    optional_fields = optional_fields or []
    
    try:
        # 1. 首先解析 URL 查询参数
        query_params = dict(request.query_params)
        if query_params:
            params.update(query_params)
            logger.debug(f"✅ Parsed Query parameters: {list(query_params.keys())}")
        
        # 2. 然后解析请求体参数
        if "application/json" in content_type:
            # JSON 请求：忽略 Form 参数，手动解析 JSON body
            logger.debug("📝 Detected JSON request, parsing JSON body...")
            try:
                body_params = await request.json()
                params.update(body_params)
                logger.debug(f"✅ Parsed JSON parameters: {list(body_params.keys())}")
            except json.JSONDecodeError as e:
                logger.error(f"Failed to parse JSON: {e}")
                return None, {
                    "code": "-1",
                    "message": "JSON 格式错误",
                    "data": None
                }
        else:
            # 表单请求：使用 FastAPI 自动解析的 Form 参数
            body_params = {k: v for k, v in form_params.items() if v is not None}
            params.update(body_params)
            logger.debug(f"✅ Using Form parameters: {list(body_params.keys())}")
        
        # 3. 检查必需字段
        missing_fields = [field for field in required_fields if field not in params or not params[field]]
        if missing_fields:
            logger.warning(f"⚠️ Missing required fields: {missing_fields}")
            return None, {
                "code": "-1",
                "message": f"缺少必需参数: {', '.join(missing_fields)}",
                "data": None
            }
        
        logger.debug(f"✅ Final parsed parameters: {list(params.keys())}")
        return params, None
        
    except Exception as e:
        logger.error(f"❌ Error parsing request parameters: {e}")
        return None, {
            "code": "-1",
            "message": f"解析请求参数失败: {str(e)}",
            "data": None
        }


@asynccontextmanager
async def lifespan(app: FastAPI):
    """ 统一管理应用的生命周期 """
    # ---------- 应用启动逻辑 ----------
    logger.info(f">>>>> Starting instance {INSTANCE_ID} on port {INSTANCE_PORT} <<<<<<")
    
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
    logger.info(f">>>>> ProcessPoolExecutor initialized with {settings.SERVICE['max_job_number']} workers <<<<<<")

    # 🔥 初始化带缓存的状态管理器（优化轮询性能）
    app.state.state_manager = get_cached_state_manager()
    await app.state.state_manager.initialize()
    logger.info("✅ CachedStateManager initialized (with local TTL cache)")
    
    # 🔥 清理 Redis 中的虚假负载和未完成任务（重启时）
    try:
        logger.info(">>> 开始清理 Redis 中的虚假数据...")
        
        # 1. 清理未完成的任务（将 in_progress 和 pending 标记为 failed）
        cleaned_tasks = await app.state.state_manager.cleanup_stale_tasks(
            statuses=['in_progress', 'pending'],
            reason="服务重启"
        )
        if cleaned_tasks > 0:
            logger.warning(f"⚠️ 清理了 {cleaned_tasks} 个未完成的任务")
        
        # 2. 修复所有实例的虚假负载计数
        cleaned_loads = await app.state.state_manager.cleanup_instance_loads()
        if cleaned_loads > 0:
            logger.warning(f"⚠️ 修复了 {cleaned_loads} 个实例的负载计数")
        
        if cleaned_tasks == 0 and cleaned_loads == 0:
            logger.info("✅ 没有需要清理的数据")
        else:
            logger.info(f"✅ 清理完成: {cleaned_tasks} 任务, {cleaned_loads} 负载计数")
            
    except Exception as e:
        logger.error(f"❌ 清理失败: {e}", exc_info=True)
        # 不抛出异常，继续启动服务
    
    # 🔥 初始化进度批量更新器（减少Redis写操作）
    app.state.progress_batcher = get_progress_batcher(app.state.state_manager)
    await app.state.progress_batcher.start()
    logger.info("✅ ProgressBatcher started (batch interval=1s)")
    
    # 注册实例到集群
    await app.state.state_manager.register_instance(
        INSTANCE_ID,
        {
            "port": INSTANCE_PORT,
            "max_workers": int(settings.SERVICE["max_job_number"]),
            "status": "running"
        }
    )
    logger.info(f">>>>> Instance {INSTANCE_ID} registered to cluster <<<<<<")
    
    # 🔥 设置 job_manager 的 state_manager 引用（解决 K8s 多实例轮询错位问题）
    job_manager.set_state_manager(app.state.state_manager)
    logger.info("✅ JobManager configured with CachedStateManager for cross-pod progress sync")

    # 初始化负载均衡器
    app.state.load_balancer = get_load_balancer()
    await app.state.load_balancer.initialize(app.state.state_manager)
    
    # 启动定时清理任务
    cleanup_task = asyncio.create_task(job_manager.cleanup_jobs())
    
    # 启动实例心跳任务
    heartbeat_task = asyncio.create_task(send_heartbeat(app.state.state_manager))

    yield  # 应用运行阶段

    # ---------- 应用关闭逻辑 ----------
    logger.info(f">>>>> Shutting down instance {INSTANCE_ID} <<<<<<")
    
    # 停止心跳任务
    heartbeat_task.cancel()
    try:
        await heartbeat_task
    except asyncio.CancelledError:
        pass
    
    # 🔥 停止进度批量更新器（先刷新所有待处理的更新）
    if hasattr(app.state, 'progress_batcher'):
        await app.state.progress_batcher.stop()
        logger.info("✅ ProgressBatcher stopped and flushed")
    
    # 注销实例
    await app.state.state_manager.unregister_instance(INSTANCE_ID)
    
    # 关闭进程池
    app.state.executor.shutdown(wait=True)
    logger.info(">>>>> ProcessPoolExecutor shutdown <<<<<<")

    # 停止清理任务
    cleanup_task.cancel()
    try:
        await cleanup_task
    except asyncio.CancelledError:
        pass

    # 关闭共享状态管理器（包含缓存清理）
    await app.state.state_manager.close()
    logger.info("✅ CachedStateManager closed")

    # 记录最终任务状态
    for key, value in job_manager.jobs.items():
        logger.info(f"Final status: {key} - {value['status']}")


async def send_heartbeat(state_manager):
    """发送实例心跳"""
    while True:
        try:
            await asyncio.sleep(30)  # 每30秒发送一次心跳
            await state_manager.update_instance_heartbeat(INSTANCE_ID)
            logger.debug(f"Heartbeat sent for instance {INSTANCE_ID}")
        except asyncio.CancelledError:
            break
        except Exception as e:
            logger.error(f"Failed to send heartbeat: {e}")


# 创建 FastAPI 应用
app = FastAPI(
    title=f"Document Parser API - Instance {INSTANCE_ID}",
    description=f"Document Parser API - Multi-Instance Mode (Instance: {INSTANCE_ID})",
    lifespan=lifespan
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


# 查询支持的文档类型
@app.get("/loader/doc_types", tags=["loader"], summary="查询支持的文档类型列表")
async def list_doc_types():
    """查询当前支持的所有文档类型"""
    try:
        loader = get_doc_type_loader()
        doc_types = loader.list_doc_types()
        
        # 获取每个类型的详细信息
        type_info_list = []
        for doc_type in doc_types:
            info = loader.get_doc_type_info(doc_type)
            if info:
                type_info_list.append(info)
        
        return {
            "code": "0",
            "message": "查询成功",
            "data": {
                "total": len(type_info_list),
                "doc_types": type_info_list
            }
        }
    except Exception as e:
        logger.error(f"查询文档类型列表失败: {e}")
        return {
            "code": "-1",
            "message": f"查询失败: {str(e)}",
            "data": None
        }


# 异步解析接口（简化版，同时支持SwaggerUI、表单和JSON）
@app.post("/loader/deep_parse/async", tags=["loader"], summary="解析文件异步接口（支持SwaggerUI和JSON）")
async def deep_parse_async(
        request: Request,
        background_tasks: BackgroundTasks,
        request_id: Optional[str] = Form(None, description="请求ID（可选，不提供时自动生成UUID）"),
        original_url: Optional[str] = Form(None, description="文档存储地址（OSS路径）"),
        doc_type: Optional[str] = Form(None, description="文档类型，如：PLAIN_TEXT, DOCUMENT_BASIC等"),
        enable_vision_understand: Optional[bool] = Form(False, description="是否启用视觉理解，默认不启用")
):
    """
    解析文件异步接口
    
    支持三种请求方式:
    1. SwaggerUI / 表单提交：直接填写下方表单
    2. JSON 请求：Content-Type: application/json, Body: {"request_id": "xxx", "original_url": "yyy", "doc_type": "zzz"}
    3. URL查询参数：?request_id=xxx&original_url=yyy&doc_type=zzz
    
    注意：
    - request_id 为可选参数，如果不提供将自动生成UUID
    - 当 request_id 非空时，task_id 将设为 request_id 的值
    - 当 request_id 为空时，将生成新的 UUID 作为 task_id，并将 request_id 设为相同值
    """
    try:
        # 0. 智能解析请求参数（兼容 SwaggerUI、表单和 JSON）
        params, error = await smart_parse_params_async(
            request,
            {"request_id": request_id, "original_url": original_url, "doc_type": doc_type},
            required_fields=["original_url", "doc_type"],
            optional_fields=["request_id", "enable_vision_understand"]
        )
        if error:
            temp_task_id = str(uuid.uuid4())
            return task_failed(
                "unknown",
                error["message"],
                log=f"参数验证失败|{error['message']}",
                task_id=temp_task_id
            )
        
        request_id = params.get("request_id", "") or ""
        original_url = params["original_url"]
        doc_type = params["doc_type"]
        # 兼容Java端传递的"true"/"false"和Python端传递的True/False和字符串"True"/"False"
        val = params.get("enable_vision_understand", False)
        logger.info(f"enable_vision_understand={type(val)}, val={val}")
        if isinstance(val, str):
            enable_vision_understand = val.lower() == "true"
        else:
            enable_vision_understand = val
        # 1. 处理 request_id 和 task_id 逻辑
        if request_id:
            # request_id 非空时，验证其格式并使用其作为 task_id
            try:
                uuid.UUID(request_id)
                logger.debug(f"request_id validation passed: {request_id}")
                task_id = request_id  # 使用 request_id 作为 task_id
            except (ValueError, AttributeError) as e:
                logger.error(f"Invalid request_id format: {request_id}, error: {e}")
                temp_task_id = str(uuid.uuid4())
                return task_failed(
                    request_id,
                    "request_id 格式不正确，必须为标准 UUID 格式",
                    log="参数验证失败|request_id 格式错误",
                    task_id=temp_task_id
                )
        else:
            # request_id 为空时，生成新的 UUID 作为 task_id 和 request_id
            task_id = str(uuid.uuid4())
            request_id = task_id  # 保持一致
            logger.info(f"Generated new task_id and request_id: {task_id}")
        
        logger.info(f"Using task_id: {task_id}, request_id: {request_id}")
        
        # 3. 验证 doc_type 并加载配置
        is_valid, parse_params, error_msg = validate_and_get_params(doc_type)
        if not is_valid:
            logger.error(f"Invalid doc_type: {doc_type}, error: {error_msg}")
            return task_failed(
                request_id,
                error_msg,
                log=f"参数验证失败|{error_msg}",
                task_id=task_id
            )
        
        logger.info(f"使用文档类型配置: {doc_type}, 参数: {parse_params}")
        
        # 4. 生成临时文件名：task_id_timestamp.pdf
        # 格式：{task_id}_{timestamp}.pdf
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]  # 精确到毫秒
        filename = f"{task_id}_{timestamp}.pdf"
        logger.info(f"Generated temporary filename: {filename}")
        
        # 5. 提取解析参数
        upload_image = parse_params.get("upload_image", True)
        table_image = parse_params.get("table_image", True)
        parser_rule_data = parse_params.get("parser_rule", [{"rule_method":"3","feature_value":["ROW_HEADER"]}])
        to_pdf = parse_params.get("to_pdf", False)
        layout = parse_params.get("layout", True)
        ocr_content = parse_params.get("ocr_content", True)
        vision = parse_params.get("vision", False)
        table_vision = parse_params.get("table_vision", False)
        start_page = parse_params.get("start_page", 1)
        end_page = parse_params.get("end_page", "")
        
        # 处理 parser_rule（可能是列表或字符串）
        if isinstance(parser_rule_data, list):
            rule_config_dict = parser_rule_data
        elif isinstance(parser_rule_data, str):
            try:
                rule_config_dict = json.loads(parser_rule_data) if parser_rule_data else []
            except json.JSONDecodeError:
                rule_config_dict = [{"rule_method":"3","feature_value":["ROW_HEADER"]}]
        else:
            rule_config_dict = [{"rule_method":"3","feature_value":["ROW_HEADER"]}]
        
        # 6. 校验 page 设置
        start_page, end_page = validate_pages(start_page, end_page)
        
        # 7. 使用默认配置
        environment = "ONLINE"
        oss_type = "TOS"
        oss_config_dict = settings.TOS
        call_back_url = ""
        
        # 8. 检查实例负载
        state_manager = app.state.state_manager
        current_load = await state_manager.get_instance_load(INSTANCE_ID)
        max_workers = int(settings.SERVICE["max_job_number"])
        
        if current_load >= max_workers:
            logger.warning(f"Instance {INSTANCE_ID} is overloaded: {current_load}/{max_workers}")
            return task_failed(
                request_id,
                f"服务繁忙，请稍后重试 (当前负载: {current_load}/{max_workers})",
                log="任务提交失败|服务负载已满",
                task_id=task_id
            )
        
        try:
            # 9. 使用统一的 URL 解析函数，支持预签名 URL、标准 S3 URL 和 bucket/key 格式
            bucket_name, object_key = parse_presigned_url(original_url)
            
            # 获取系统配置的 bucket_name
            system_bucket = TOS.get('bucket', '')
            
            if bucket_name and object_key:
                # 只有当 bucket_name 等于系统配置的 bucket_name 时，才转换为 bucket/key 格式
                if bucket_name == system_bucket:
                    original_url = f"{bucket_name}/{object_key}"
                    logger.info(f"✅ Parsed URL (匹配系统bucket): bucket={bucket_name}, key={object_key}, 转换为: {original_url}")
                else:
                    # bucket_name 不匹配，保留原始 URL
                    logger.info(f"⚠️ Parsed URL (非系统bucket): bucket={bucket_name}, key={object_key}, 保留原始URL: {original_url}")
                
                # 从 object_key 中提取文件名（parse_presigned_url 已经解码且不含查询参数）
                original_filename = object_key.split("/")[-1]
            else:
                # 解析失败，尝试从原始 URL 提取文件名
                # 1. 先移除查询参数（? 后面的部分）
                # 2. 再进行 URL 解码
                # 3. 最后提取文件名
                logger.warning(f"⚠️ Failed to parse URL, extracting filename from original URL")
                url_without_query = original_url.split("?")[0]  # 移除查询参数
                original_filename = unquote(url_without_query).split("/")[-1]
            
            # 提取文件扩展名
            if "." in original_filename:
                file_ext = original_filename.split(".")[-1].lower()
            else:
                file_ext = "pdf"  # 默认
            
            logger.debug(f"Extracted filename: {original_filename}, extension: {file_ext}")
                
        except Exception as e:
            logger.warning(f"Failed to extract file extension: {e}")
            file_ext = "pdf"
        
        logger.debug(f"Extracted file extension from original_url: {file_ext}")
        
        # 根据真实文件类型估算处理时长
        file_size_estimate = {
            'pdf': 20, 'docx': 10, 'xlsx': 15, 'pptx': 25,
            'doc': 8, 'xls': 12, 'ppt': 20,
            'png': 5, 'jpg': 5, 'jpeg': 5,
            'txt': 1, 'json': 2, 'html': 3
        }.get(file_ext, 10)
        
        # 10. 创建任务（使用 task_id 作为任务的唯一标识）
        await job_manager.create_job(
            task_id,
            request_id=request_id,  # 保存 request_id 用于验证
            filename=filename,
            call_back_url=call_back_url,
            instance_id=INSTANCE_ID,
            file_size_mb=file_size_estimate,
            doc_type=doc_type,
            enable_vision_understand=str(enable_vision_understand)
        )
        
        # 11. 在共享状态中注册任务（使用 task_id 作为 key）
        await state_manager.register_task(
            task_id,
            {
                "request_id": request_id,
                "filename": filename,
                "instance_id": INSTANCE_ID,
                "status": "in_progress",
                "file_size_mb": file_size_estimate,
                "doc_type": doc_type,
                "enable_vision_understand": str(enable_vision_understand),
                "created_at": datetime.now(timezone.utc).isoformat()
            }
        )

        logger.info(f"Task {task_id} (request_id={request_id}, doc_type={doc_type}) created on instance {INSTANCE_ID}")
        
        # 12. 提交后台任务
        background_tasks.add_task(
            start_deep_parse_task_wrapper,
            task_id,                    # 位置参数：任务唯一标识
            state_manager,              # 位置参数：共享状态管理器
            request_id=request_id,      # 关键字参数：上游请求ID
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
            call_back_url=call_back_url,
            enable_vision_understand=enable_vision_understand
        )

        logger.info(f"Task {task_id} (request_id={request_id}) submitted successfully")
        return task_submit_success(request_id, task_id)
        
    except Exception as e:
        logger.error(f"request_id {request_id} async job raise error: {e}")
        # 如果 task_id 还未生成，使用临时值
        temp_task_id = locals().get('task_id', str(uuid.uuid4()))
        return task_failed(request_id, str(e), log="任务创建失败|系统异常", task_id=temp_task_id)


async def start_deep_parse_task_wrapper(task_id: str, state_manager, **kwargs):
    """
    包装解析任务，同步本地和共享状态
    
    🔥 关键功能：即使进程崩溃（如 SIGSEGV），也能将失败状态同步到 Redis
    🔥 添加超时保护，防止任务永久挂起导致服务无响应
    """
    try:
        # 🔥 获取任务超时时间（从配置或默认值）
        timeout = int(settings.SERVICE.get("time_out", 14400))  # 默认4小时
        request_id = kwargs.get("request_id", task_id)
        
        logger.info(f"Task {task_id} (request_id={request_id}) starting with timeout={timeout}s")
        
        # 🔥 添加总体超时保护（防止任务永久挂起）
        try:
            await asyncio.wait_for(
                start_deep_parse_task(task_id, **kwargs),
                timeout=timeout
            )
        except asyncio.TimeoutError:
            logger.error(
                f"❌ Task {task_id} (request_id={request_id}) timeout after {timeout} seconds. "
                f"This may indicate a hung process or infinite loop."
            )
            # 标记任务为超时失败
            await state_manager.update_task_status(
                task_id,
                {
                    "status": "failed",
                    "error": f"任务执行超时（{timeout}秒），可能由于进程挂起或资源不足",
                    "updated_at": datetime.now(timezone.utc).isoformat(),
                    "progress": job_manager.jobs.get(task_id, {}).get("progress", 0)
                }
            )
            # 同步到本地
            if task_id in job_manager.jobs:
                job_manager.jobs[task_id]["status"] = "failed"
                job_manager.jobs[task_id]["result"] = {"error": f"任务超时（{timeout}秒）"}
            return
        
        # 🔥 同步最终状态到 Redis（包括进程崩溃导致的失败状态）
        if task_id in job_manager.jobs:
            task_info = job_manager.jobs[task_id]
            
            # 构建更新数据
            update_data = {
                "status": task_info["status"],
                "updated_at": datetime.now(timezone.utc).isoformat()
            }
            
            # 根据状态添加额外信息
            if task_info["status"] == "complete":
                update_data["result"] = task_info.get("result")
                update_data["progress"] = 100
                # 🔥 同步 pdf_file 字段到 Redis
                if "pdf_file" in task_info:
                    update_data["pdf_file"] = task_info.get("pdf_file")
            elif task_info["status"] == "failed":
                update_data["error"] = task_info.get("result", {}).get("error", "Unknown error")
                update_data["progress"] = task_info.get("progress", 0)
            
            # 同步日志
            if task_info.get("logs"):
                update_data["logs"] = task_info["logs"]
            
            await state_manager.update_task_status(task_id, update_data)
            logger.info(f"✅ Task {task_id} final status synced to Redis: {task_info['status']}")
        else:
            logger.warning(f"⚠️ Task {task_id} not found in job_manager after completion")
            
    except Exception as e:
        logger.error(f"❌ Task {task_id} wrapper failed: {e}", exc_info=True)
        # 确保即使 wrapper 失败，也要更新 Redis 状态
        await state_manager.update_task_status(
            task_id,
            {
                "status": "failed",
                "error": f"任务包装器异常: {str(e)}",
                "updated_at": datetime.now(timezone.utc).isoformat()
            }
        )


@app.post("/loader/status", tags=["loader"], summary="通过请求ID查询文档解析结果（支持SwaggerUI和JSON）")
async def file_parse_status(
        request: Request,
        request_id: Optional[str] = Form(None, description="请求ID")
):
    """
    查询任务状态
    
    支持三种请求方式:
    1. SwaggerUI / 表单提交：直接填写下方表单
    2. JSON 请求：Content-Type: application/json, Body: {"request_id": "xxx"}
    3. URL查询参数：?request_id=xxx
    
    注意: 此接口不占用任务进程，不受负载限制影响
    """
    # 智能解析请求参数（兼容 SwaggerUI、表单和 JSON）
    params, error = await smart_parse_params_async(
        request,
        {"request_id": request_id},
        required_fields=["request_id"]
    )
    if error:
        return task_failed(
            "unknown",
            error["message"],
            log=f"参数验证失败|{error['message']}",
            task_id="unknown"
        )
    
    request_id = params["request_id"]
    task_id = request_id  # 现在 task_id 和 request_id 保持一致
    
    logger.debug("status job_manager.jobs={}".format(job_manager.jobs))
    
    # 🔥 优先从 Redis 读取最新数据（子进程更新的进度在 Redis 中）
    # 本地 job_manager.jobs 可能包含过时的数据
    state_manager = app.state.state_manager
    task_info = await state_manager.get_task_status(task_id)
    
    # 如果 Redis 中没有，尝试从本地获取
    if not task_info and task_id in job_manager.jobs:
        task_info = job_manager.jobs[task_id]
        logger.debug(f"Task {task_id} not found in Redis, using local data")
    
    # 如果都没有，返回未找到
    if not task_info:
        return task_not_found(request_id, task_id)
    
    # 验证 request_id 是否匹配（防止使用错误的 request_id 查询）
    stored_request_id = task_info.get("request_id", "")
    if stored_request_id and stored_request_id != request_id:
        logger.warning(f"request_id mismatch: provided={request_id}, stored={stored_request_id}")
        return task_failed(
            request_id,
            "request_id 与任务记录不匹配",
            log="参数验证失败|request_id 不匹配",
            task_id=task_id
        )
    
    # 获取进度和日志
    progress = task_info.get("progress", 0)
    logs = task_info.get("logs", [])
    log_str = "|".join(logs) if logs else job_manager.get_task_logs(task_id)
    
    # 计算文件大小（如果有）
    file_size_mb = 0
    try:
        # 尝试从任务信息中获取文件大小估算
        filename = task_info.get("filename", "")
        doc_type = task_info.get("doc_type", "")
        if doc_type == "DOCUMENT_ADVANCED":
            is_img_parse = True
        else:
            is_img_parse = False
        # 可以根据文件类型估算，或者从其他地方获取
        # 这里简化处理，实际应用中可以在任务创建时记录文件大小
        file_size_mb = task_info.get("file_size_mb", 10)  # 默认 10MB
    except:
        is_img_parse = False
        file_size_mb = 10
    
    # 根据状态返回不同响应
    if task_info["status"] == "in_progress":
        # 动态计算 timeout
        timeout = calculate_timeout(progress, file_size_mb, is_img_parse)
        
        return task_processing(
            request_id=request_id,
            task_id=task_id,
            progress=progress,
            log=log_str if log_str else "任务处理中",
            timeout=timeout
        )
    
    elif task_info["status"] == "complete":
        # 兼容新旧两种结果格式
        logger.debug(f"Task {task_id} complete result: {task_info}")
        result = task_info.get('result', '')
        pdf_file = None
        
        # 如果 result 是字典格式，提取 oss_url 和 pdf_file
        if isinstance(result, dict):
            oss_url = result.get('oss_url', '')
            pdf_file = result.get('pdf_file', None)
        else:
            # 旧格式：字符串
            oss_url = result
            # 尝试从 task_info 中获取 pdf_file（如果之前已经保存）
            pdf_file = task_info.get('pdf_file', None)
        
        return task_success(
            request_id=request_id,
            task_id=task_id,
            result=oss_url,
            pdf_file=pdf_file,
            progress=100,
            log=log_str if log_str else "任务处理完成"
        )
    
    elif task_info["status"] == "killed":
        return task_canceled(
            request_id=request_id,
            task_id=task_id,
            log=log_str if log_str else "任务已被取消"
        )
    
    else:
        # failed 或其他错误状态
        error_msg = task_info.get('result', task_info.get('error', 'Unknown error'))
        if isinstance(error_msg, dict):
            error_msg = error_msg.get('error', str(error_msg))
        return task_failed(
            request_id=request_id,
            task_id=task_id,
            error_msg=str(error_msg),
            progress=progress,
            log=log_str if log_str else "任务执行失败"
        )


@app.post("/loader/kill_task", tags=["loader"], summary="终止正在执行的解析任务（支持SwaggerUI和JSON）")
async def kill_parser_task(
        request: Request,
        request_id: Optional[str] = Form(None, description="请求ID")
):
    """
    终止正在执行的任务
    
    支持三种请求方式:
    1. SwaggerUI / 表单提交：直接填写下方表单
    2. JSON 请求：Content-Type: application/json, Body: {"request_id": "xxx"}
    3. URL查询参数：?request_id=xxx
    
    注意: 此接口不占用任务进程，不受负载限制影响
    """
    try:
        # 智能解析请求参数（兼容 SwaggerUI、表单和 JSON）
        params, error = await smart_parse_params_async(
            request,
            {"request_id": request_id},
            required_fields=["request_id"]
        )
        if error:
            return task_failed(
                "unknown",
                error["message"],
                log=f"参数验证失败|{error['message']}",
                task_id="unknown"
            )
        
        request_id = params["request_id"]
        task_id = request_id  # 现在 task_id 和 request_id 保持一致
        
        # 使用 task_id 查找任务
        if task_id not in job_manager.jobs:
            return task_not_found(request_id, task_id)

        task_info = job_manager.jobs[task_id]
        
        # 验证 request_id 是否匹配
        stored_request_id = task_info.get("request_id", "")
        if stored_request_id and stored_request_id != request_id:
            logger.warning(f"request_id mismatch: provided={request_id}, stored={stored_request_id}")
            return task_failed(
                request_id,
                "request_id 与任务记录不匹配",
                log="参数验证失败|request_id 不匹配",
                task_id=task_id
            )

        # 检查任务状态
        if task_info["status"] not in ["in_progress", "pending"]:
            return task_failed(
                request_id,
                f"任务未在执行中（当前状态：{task_info['status']}）",
                log=f"取消失败|任务状态为 {task_info['status']}",
                task_id=task_id
            )

        # 获取进程对象
        process = task_info.get("process")
        if not process:
            logger.warning(f"Task {task_id} has no process object, marking as killed")
            # 即使没有进程对象，也标记为已取消（可能还未启动进程）
        else:
            # 终止进程
            try:
                if hasattr(process, 'is_alive') and process.is_alive():
                    logger.info(f"Terminating process for task {task_id}, PID: {process.pid}")
                    process.terminate()  # 先尝试优雅终止
                    await asyncio.sleep(1)  # 等待进程终止
                    
                    if process.is_alive():
                        logger.warning(f"Process {process.pid} still alive, force killing...")
                        process.kill()  # 强制终止
                        await asyncio.sleep(0.5)
                    
                    logger.info(f"Task {task_id} (request_id={request_id}) process killed successfully")
                else:
                    logger.info(f"Process for task {task_id} is not alive")
            except Exception as e:
                logger.error(f"Error killing process for task {task_id}: {e}")
                # 继续执行，标记任务为已取消

        # 更新任务状态
        task_info["status"] = "killed"
        task_info["result"] = {"result": "用户主动终止"}
        task_info["timestamp"] = datetime.now(timezone.utc)
        
        # 添加取消日志
        logs = task_info.get("logs", [])
        logs.append("用户主动取消任务")
        task_info["logs"] = logs
        
        # 更新共享状态
        state_manager = app.state.state_manager
        await state_manager.update_task_status(
            task_id,
            {
                "status": "killed",
                "result": {"result": "用户主动终止"},
                "logs": logs,
                "updated_at": datetime.now(timezone.utc).isoformat()
            }
        )

        return task_canceled(request_id, task_id, log="|".join(logs))

    except ProcessLookupError:
        logger.error(f"进程不存在 (task_id={task_id})")
        return task_failed(request_id, "进程不存在", log="取消失败|进程不存在", task_id=task_id)

    except Exception as e:
        logger.error(f"终止任务失败 (task_id={task_id}): {str(e)}")
        return task_failed(request_id, f"终止失败: {str(e)}", log=f"取消失败|{str(e)}", task_id=task_id)


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


@app.post("/ocr/ocr_service", tags=["ocr"], summary="ocr服务（支持SwaggerUI和JSON）")
async def ocr_service_port(
        request: Request,
        request_id: Optional[str] = Form(None, description="请求ID（可选，不提供时自动生成UUID）"),
        original_url: Optional[str] = Form(None, description="原始文档地址"),
        zoomin: Optional[int] = Form(None, description="缩放比例")
):
    """
    OCR 服务
    
    支持三种请求方式:
    1. SwaggerUI / 表单提交：直接填写下方表单
    2. JSON 请求：Content-Type: application/json, Body: {"request_id": "xxx", "original_url": "yyy"}
    3. URL查询参数：?request_id=xxx&original_url=yyy
    
    注意：
    - request_id 为可选参数，如果不提供将自动生成UUID
    """
    # 0. 智能解析请求参数（兼容 SwaggerUI、表单和 JSON）
    params, error = await smart_parse_params_async(
        request,
        {"request_id": request_id, "original_url": original_url, "zoomin": zoomin},
        required_fields=["original_url"],
        optional_fields=["request_id","zoomin"]
    )
    if error:
        return task_failed(
            "unknown",
            error["message"],
            log=f"参数验证失败|{error['message']}",
            task_id=request_id
        )
    
    request_id = params.get("request_id", "") or ""
    original_url = params["original_url"]
    zoomin = params.get("zoomin", 3)
    # 1. 处理 request_id 逻辑
    if request_id:
        # request_id 非空时，验证其格式
        try:
            uuid.UUID(request_id)
            logger.debug(f"OCR service - request_id validation passed: {request_id}")
        except (ValueError, AttributeError) as e:
            logger.error(f"OCR service - Invalid request_id format: {request_id}, error: {e}")
            return task_failed(
                request_id,
                str("request_id 格式不正确，必须为标准 UUID 格式"),
                log=f"参数验证失败|request_id 格式不正确，必须为标准 UUID 格式",
                task_id=request_id
            )
    else:
        # request_id 为空时，生成新的 UUID
        request_id = str(uuid.uuid4())
        logger.info(f"OCR service - Generated new request_id: {request_id}")
    
    try:
        # 检查文件扩展名（支持预签名 URL，移除查询参数后检查）
        url_path = original_url.split('?')[0]  # 移除查询参数
        if not url_path.lower().endswith(".pdf") and not url_path.lower().endswith((".jpg", ".jpeg", ".png", ".bmp", ".tif", ".tiff", ".gif", ".webp", ".ico", ".icon")):
            return task_failed(
                request_id=request_id,
                task_id=request_id,
                error_msg="原始文档地址必须为pdf格式",
                progress=0,
                log="ocr服务异常|原始文档地址必须为pdf格式"
            )
        
        # 使用统一的 URL 解析函数，支持预签名 URL、标准 S3 URL 和 bucket/key 格式
        bucket_name, object_key = parse_presigned_url(original_url)
        
        # 获取系统配置的 bucket_name
        system_bucket = TOS.get('bucket', '')
        
        if bucket_name and object_key:
            # 只有当 bucket_name 等于系统配置的 bucket_name 时，才转换为 bucket/key 格式
            if bucket_name == system_bucket:
                original_url = f"{bucket_name}/{object_key}"
                logger.info(f"✅ OCR - Parsed URL (匹配系统bucket): bucket={bucket_name}, key={object_key}, 转换为: {original_url}")
            else:
                # bucket_name 不匹配，保留原始 URL
                logger.info(f"⚠️ OCR - Parsed URL (非系统bucket): bucket={bucket_name}, key={object_key}, 保留原始URL: {original_url}")
        else:
            # 解析失败，保持原 URL（可能是其他类型的 URL）
            logger.warning(f"⚠️ OCR - Failed to parse URL, using original: {original_url}")
        
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")[:-3]  # 精确到毫秒
        filename = f"{request_id}_{timestamp}.pdf"
        ocr_res = ocr_service(
            request_id=request_id,
            filename=filename,
            original_url=original_url,
            from_page=1,
            to_page="",
            zoomin=zoomin
        )
        # logger.info(f"ocr_res={ocr_res}")
        return task_success(
                request_id=request_id,
                task_id=request_id,
                result=str(ocr_res),
                progress=100,
                log="ocr服务完成"
            )
    except Exception as e:
        return task_failed(
            request_id=request_id,
            task_id=request_id,
            error_msg=str(e),
            progress=0,
            log=f"ocr服务异常|{str(e)}"
        )


@app.post("/ocr/text_extract", tags=["ocr"], summary="文本提取服务（支持SwaggerUI和JSON）")
async def text_extract_service(
        request: Request,
        request_id: Optional[str] = Form(None, description="请求ID（可选，不提供时自动生成UUID）"),
        original_url: Optional[str] = Form(None, description="原始文档地址"),
        from_page: Optional[int] = Form(None, description="开始页码"),
        to_page: Optional[int] = Form(None, description="结束页码"),
        oss_config: Optional[dict] = Form(None, description="OSS配置")
):
    try:
        params, error = await smart_parse_params_async(
            request,
            {"request_id": request_id, "original_url": original_url, "from_page": from_page, "to_page": to_page, "oss_config": oss_config},
            required_fields=["original_url"],
            optional_fields=["request_id","from_page","to_page","oss_config"]
            
        )
        if error:
            return task_failed(
                "unknown",
                error["message"],
                log=f"参数验证失败|{error['message']}",
                task_id="unknown"
            )
        request_id = params.get("request_id", "") or ""
        original_url = params["original_url"]
        from_page = params.get("from_page", 1)
        to_page = params.get("to_page", "")
        text_extract_url = text_extract(
            original_url=original_url,
            from_page=from_page,
            to_page=to_page,
        )
        return task_success(
            request_id=request_id,
            task_id=request_id,
            result=text_extract_url,
            progress=100,
            log="文本提取服务完成"
        )
    except Exception as e:
        return task_failed(
            request_id=request_id,
            task_id=request_id,
            error_msg=str(e),
            progress=0,
            log=f"文本提取服务异常|{str(e)}"
        )

@app.get("/health", tags=["system"], summary="健康检查")
async def health_check():
    """健康检查接口（增强版：包含缓存和批量更新统计）"""
    try:
        state_manager = app.state.state_manager
        redis_healthy = await state_manager.health_check()
        
        instance_info = await state_manager.get_instance_info(INSTANCE_ID)
        current_load = await state_manager.get_instance_load(INSTANCE_ID)
        
        # 🔥 获取缓存统计信息
        cache_info = state_manager.get_cache_info() if hasattr(state_manager, 'get_cache_info') else {}
        
        # 🔥 获取批量更新统计
        batcher_stats = app.state.progress_batcher.get_stats() if hasattr(app.state, 'progress_batcher') else {}
        
        return {
            "status": "success" if redis_healthy else "degraded",
            "message": "Service is running",
            "timestamp": datetime.now().isoformat(),
            "instance_id": INSTANCE_ID,
            "instance_port": INSTANCE_PORT,
            "current_load": current_load,
            "max_workers": int(settings.SERVICE["max_job_number"]),
            "load_percentage": f"{(current_load / int(settings.SERVICE['max_job_number']) * 100):.1f}%",
            "redis_healthy": redis_healthy,
            "apm_enabled": is_apm_enabled(),
            "instance_info": instance_info,
            # 🔥 性能优化统计
            "performance": {
                "cache": cache_info,
                "batch_updates": batcher_stats
            }
        }
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "error",
            "message": f"Health check failed: {str(e)}",
            "timestamp": datetime.now().isoformat(),
            "instance_id": INSTANCE_ID,
            "instance_port": INSTANCE_PORT
        }


@app.get("/cluster/status", tags=["system"], summary="集群状态")
async def cluster_status():
    """获取集群状态信息"""
    try:
        state_manager = app.state.state_manager
        
        # 获取所有实例信息
        instances = await state_manager.get_all_instances()
        
        # 获取任务统计
        task_stats = await state_manager.get_task_statistics()
        
        return {
            "status": "success",
            "timestamp": datetime.now().isoformat(),
            "cluster": {
                "total_instances": len(instances),
                "instances": instances
            },
            "tasks": task_stats
        }
    except Exception as e:
        logger.error(f"Failed to get cluster status: {e}")
        return {
            "status": "error",
            "message": str(e),
            "timestamp": datetime.now().isoformat()
        }

