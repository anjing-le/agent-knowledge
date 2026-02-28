import re
import json
import base64
from copy import deepcopy
from io import BytesIO
from timeit import default_timer as timer
from rapidocr import RapidOCR
from kparser.rag.templates import presentation, general, picture
from kparser.rag.storage.storage_factory import STORAGE_IMPL
from kparser.parserground.parser import doc2docx, ppt2pptx, ppt_doc_to_pdf, PdfParser, PDFLoader, PDFLoader_enhanced
from kparser.common.types_utils import ParserType, GENERAL_TYPE, PRESENTATION_TYPE, PICTURE_TYPE, get_random_uuid
from kparser.common.file_utils import read_file_from_original_url, upload2cdn, upload2tos
from kparser.common.log_utils import get_logger
from kparser.common.config import SERVICE, VISION, MINIO, TOS
from kparser.model.model_api import get_vision_model
from kparser.common.callback_client import call_back_status
from kparser.core.progress_tracker import get_progress_tracker
import asyncio
import sys
import img2pdf

PROMPT = VISION["image_prompt"]

logger = get_logger(__name__)

"""
    根据文件类型，选择对应的解析器解析
"""

engine = RapidOCR(params={"Global.lang_det": "ch_mobile", "Global.lang_rec": "ch_mobile"})

FACTORY = {
    ParserType.GENERAL.value: general,
    ParserType.PRESENTATION.value: presentation,
    ParserType.PICTURE.value: picture
}

def get_parser_id_by_filename(filename):
    """
    根据文件名获取解析器ID
    支持预签名URL（会自动移除查询参数）
    
    Args:
        filename: 文件名或URL
    
    Returns:
        str: 解析器ID ("general", "presentation", "picture") 或 -1 (不支持的类型)
    """
    # 移除查询参数（支持预签名 URL）
    filename_without_query = filename.split("?")[0]
    
    # 提取文件扩展名
    suffix = filename_without_query.split(".")[-1].lower()
    
    if suffix in GENERAL_TYPE:  # general (PDF/DOCX/XLSX/TXT/MARKDOWN/HTML/JSON)
        return "general"
    elif suffix in PRESENTATION_TYPE:  # presentation (PPTX)
        return "presentation"
    elif suffix in PICTURE_TYPE:  # picture (JPEG等)
        return "picture"
    else:
        logger.exception(f"不支持{suffix}文件类型")
        return -1

def get_bucket_name(original_url: str):
    oss_info = original_url.split("/")
    bucket = oss_info[0]
    name = oss_info[1]
    return bucket, name

def get_storage_binary(bucket, name):
    return STORAGE_IMPL.get(bucket, name)

def get_parser_config(parser_id, layout, ocr_content, rule_config):
    if parser_id == "general":
        parser_config = {"layout": layout,
                         "ocr_content": ocr_content,
                         "rules": rule_config}
    else:
        key_mapping = {
            "presentation": {},
            "picture": {}}
        parser_config = key_mapping[parser_id]

    return parser_config

def file_to_pdf(to_pdf, original_url, filename):
    # 移除查询参数（支持预签名 URL）
    url_path = original_url.split('?')[0]
    
    # doc文档转换
    if url_path.endswith(".doc"):
        if to_pdf:
            logger.warning("DOC文件会转换为PDF进行处理")
            original_url = ppt_doc_to_pdf(original_url)
        else:
            logger.warning("DOC文件会转换为DOCX进行处理")
            original_url = doc2docx(original_url, filename)
    elif url_path.endswith(".ppt"):   # ppt文档转换
        if to_pdf:
            logger.warning("PPT文件会转换为PDF进行处理")
            original_url = ppt_doc_to_pdf(original_url)
        else:
            logger.warning("PPT文件会转换为PPTX进行处理")
            original_url = ppt2pptx(original_url, filename)
    elif url_path.endswith(".docx"):  # docx文档转换
        if to_pdf:
            logger.warning("DOCX等文件会转换为PDF进行处理")
            original_url = ppt_doc_to_pdf(original_url)
    elif url_path.endswith(".pptx"):  # pptx文档转换
        if to_pdf:
            logger.warning("PPTX等文件会转换为PDF进行处理")
            original_url = ppt_doc_to_pdf(original_url)
    else:
        logger.warning("非doc/docx/ppt/pptx文件，不进行转换")

    return original_url


def calculate_timeout(progress: int, file_size_mb: float = 0, is_img_parse: bool = False) -> int:
    """
    根据进度和文件大小动态计算建议的轮询超时时间
    
    Args:
        progress: 当前进度百分比 (0-100)
        file_size_mb: 文件大小（MB）
        is_img_parse: 是否是图片解析
    Returns:
        建议的轮询间隔（秒）
    """
    # 基础超时时间
    if is_img_parse: time_scale_factor = 10
    else: time_scale_factor = 1
    if progress == 0:
        # 刚提交，快速查询
        return 5 * time_scale_factor
    elif progress < 10:
        # 初始化阶段（下载）
        return max(3, int(file_size_mb / 10) * time_scale_factor)  # 大文件下载时间长
    elif progress < 20:
        # 格式转换阶段
        return 5 * time_scale_factor
    elif progress < 80:
        # 解析阶段（主要耗时）
        # 根据文件大小和页数估算
        base_timeout = 5
        if file_size_mb > 50:
            base_timeout = 10
        elif file_size_mb > 100:
            base_timeout = 15
        return base_timeout * time_scale_factor
    elif progress < 95:
        # 后处理阶段
        return 5 * time_scale_factor
    elif progress < 100:
        # 上传阶段
        return 3 * time_scale_factor
    else:
        # 完成，不需要再轮询
        return 0


def update_progress_sync(task_id: str, progress: int, log_message: str = ""):
    """
    同步方式更新进度（在子进程中使用，避免事件循环问题）
    
    Args:
        task_id: 任务ID
        progress: 进度百分比 (0-100)
        log_message: 日志消息
    """
    from kparser.core.job_manager import job_manager
    from kparser.multi_instance.progress_sync import ProgressSyncManager
    
    try:
        # 1. 更新本地状态
        # 检测是否已经在事件循环中
        try:
            loop = asyncio.get_running_loop()
            # 已经在事件循环中，不能创建新循环
            # 直接同步更新（不使用 asyncio，需要注意线程安全）
            import threading
            lock = threading.Lock()
            with lock:
                if task_id in job_manager.jobs:
                    job_manager.jobs[task_id]["progress"] = max(0, min(100, progress))
                    if log_message:
                        job_manager.jobs[task_id]["logs"].append(log_message)
                    logger.info(f"Task {task_id} progress updated: {progress}% (sync mode)")
        except RuntimeError:
            # 没有运行中的事件循环，可以创建新的（子进程场景）
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                loop.run_until_complete(
                    job_manager.update_progress(task_id, progress, log_message, sync_to_redis=False)
                    # ⚠️ sync_to_redis=False 避免在子进程的事件循环中访问主进程的 Redis 连接
                )
            finally:
                loop.close()
        
        # 2. 使用同步 Redis 客户端直接同步到 Redis
        # 🔥 这里不依赖事件循环，使用独立的 Redis 连接
        if task_id in job_manager.jobs:
            try:
                progress_sync = ProgressSyncManager.get_instance()
                success = progress_sync.update_progress(
                    task_id,
                    job_manager.jobs[task_id]["progress"],
                    job_manager.jobs[task_id]["logs"]
                )
                if not success:
                    logger.warning(f"Progress sync to Redis failed for task {task_id}")
            except Exception as e:
                # 同步失败不应阻塞任务执行
                logger.error(f"Exception during progress sync for task {task_id}: {e}")
                
    except Exception as e:
        logger.error(f"Failed to update progress for task {task_id}: {e}")


def parse_file_deepdoc(request_id: str,
                       filename: str,
                       original_url: str,
                       from_page: int,
                       to_page: int,
                       upload_image: bool=True,
                       table_image: bool=True,
                       rule: bool=False,
                       rule_config:dict=None,
                       to_pdf: bool=False,
                       layout: bool=True,
                       ocr_content: bool = True,
                       vision: bool = False,
                       table_vision: bool = False,
                       environment: str = None,
                       oss_type: str = None,
                       oss_config: dict = None,
                       call_back_url: str = "",
                       enable_vision_understand: bool = False,
                       task_id: str = None,  # 🔥 任务唯一标识（服务端生成）
                       doc_id: int = None  # 🔥 保留作为可选参数，仅用于兼容性
                       ):

    def dummy(prog=None, msg=""):
        pass

    # 解析函数入口
    logger.info(f"request_id={request_id}, task_id={task_id}, filename={filename}, original_url={original_url}, from_page={from_page}, "
                f"to_page={to_page}, upload_image={upload_image}, table_image={table_image}, rule={rule}, "
                f"rule_config={rule_config}, to_pdf={to_pdf}, layout={layout}, ocr_content={ocr_content}, "
                f"vision={vision}, table_vision={table_vision}, call_back_url={call_back_url}, enable_vision_understand={enable_vision_understand}")
    environment = environment or SERVICE["environment"]
    oss_type = oss_type or SERVICE["use_storage"]
    oss_config = oss_config or TOS
    
    # 使用 task_id 作为进度更新的标识（task_id 是真正的唯一标识）
    progress_id = task_id if task_id else request_id
    
    # 初始化进度：0%
    update_progress_sync(progress_id, 0, "任务开始")
    try:
        # 初始化处理页码
        if to_page is None or to_page <= 0:
            to_page = 1000000
        
        # 进度：5% - 初始化配置
        update_progress_sync(progress_id, 5, "初始化配置")
        
        # doc/ppt/docx/pptx文件转换处理
        logger.info("original file url={}".format(original_url))
        original_url_before_convert = original_url
        original_url = file_to_pdf(to_pdf, original_url, filename)
        logger.info("converted file url={}".format(original_url))
        
        # 进度：8% - 文件格式处理完成
        if to_pdf:
            update_progress_sync(progress_id, 8, "文件格式转换完成")
        
        # 处理后的文档名称（移除查询参数，支持预签名URL）
        url_without_query = original_url.split("?")[0]
        real_filename = url_without_query.split("/")[-1]
        # 根据文件后缀获取parser_id（函数内部已处理查询参数）
        parser_id = get_parser_id_by_filename(original_url)
        logger.info("parser_id={}".format(parser_id))
        if parser_id == -1:
            raise Exception("输入的文档类型不支持，请检查文档后缀名是否正确")
        # 得到解析器
        chunker = FACTORY[parser_id.lower()]
        # 根据parser_id获取parser的配置
        parser_config = get_parser_config(parser_id, layout, ocr_content, rule_config)
        parser_config["vision"] = vision
        # 初始化多模态模型
        vision_agent=None
        if vision:
            update_progress_sync(progress_id, 10, "初始化多模态模型")
            vision_agent = get_vision_model()
    except Exception as ex:
        logger.exception("request_id={}, Get bucket name or parser id failed: {}".format(request_id, ex))
        # callback
        job_fail_wrap(request_id, doc_id or 0, call_back_url, ex, environment)
        raise Exception("Get bucket name or parser id failed: {}".format(ex))

    # 从oss下载文件
    file_size_mb = 0  # 文件大小（MB）
    try:
        # 进度：10% - 开始下载文件
        update_progress_sync(progress_id, 10, "开始下载文件")
        
        st = timer()
        # 通过url读取资源
        binary = read_file_from_original_url(original_url)
        
        # 计算文件大小
        file_size_mb = len(binary) / (1024 * 1024)
        
        logger.info("request_id={} From minio({}) {}, {}, size={:.2f}MB".format(
            request_id, timer() - st, original_url, filename, file_size_mb))
        
        # 进度：15% - 文件下载完成
        update_progress_sync(progress_id, 15, f"文件下载完成 ({file_size_mb:.2f}MB)")
        
    except TimeoutError:
        logger.exception("request_id={}, Minio {}, {} got timeout: Fetch file from minio timeout.".format(request_id, original_url, filename))
        raise
    except Exception as ex:
        if re.search("(No such file|not found)", str(ex)):
            logger.error(-1, "request_id={}, Can not find file {} from minio. Could you try it again?".format(request_id, filename))
        else:
            logger.error(-1, "request_id={}, Get file from minio: {}".format(request_id, ex))
        # callback
        job_fail_wrap(request_id, doc_id or 0, call_back_url, ex, environment)
        raise Exception("request_id={}, {}/{} got minio exception".format(request_id, original_url, filename))

    try:
        # 进度：20% - 开始文档解析
        update_progress_sync(progress_id, 20, "开始解析文档")
        
        # 文档解析
        logger.info("filename={}, real_filename={}".format(filename, real_filename))
        cks = chunker.chunk(filename,
                            real_filename,
                            binary=binary,
                            from_page=from_page,
                            to_page=to_page,
                            callback=dummy,
                            parser_config=parser_config)
        
        logger.info("request_id={}, Parsing({}) {}, {} done".format(request_id, timer() - st, original_url, filename))
        update_progress_sync(progress_id, 60, f"文档解析完成")
        
    except Exception as ex:
        logger.exception("request_id={}, Parsing {}, {} got exception".format(request_id, original_url, filename))
        # callback
        job_fail_wrap(request_id, doc_id or 0, call_back_url, ex, environment)
        raise Exception("request_id={}, Parsing {}/{} got exception".format(request_id, original_url, filename))

    # 默认存储桶
    if environment == "ATOM":
        default_bucket = oss_config.get("default_bucket", SERVICE["default_bucket"])

    # 对图片进行处理（先保存到oss中）
    # 进度：65% - 开始处理图片和表格
    update_progress_sync(progress_id, 65, "开始处理图片和表格")
    
    parse_content_list = []
    total_chunks = len(cks)
    processed_chunks = 0
    for ck in cks:
        if ck["content_type"] == "IMAGE":
            try:
                # 图片处理
                logger.info("image processing")
                image_content = ck["extra_data"].get("image", None)
                if image_content is None:
                    continue
                output_buffer = BytesIO()
                if isinstance(image_content, bytes):
                    output_buffer = BytesIO(image_content)
                else:
                    image_content.save(output_buffer, format='PNG')
                    # 🔥 修复：重置BytesIO指针到开头，否则上传时会读取到空内容
                    output_buffer.seek(0)

                # 上传图片
                logger.info("upload image")
                if upload_image:
                    if environment == "ATOM":
                        STORAGE_IMPL.put(default_bucket, ck["id"] + ".png", output_buffer.getvalue())
                        url = oss_config['prefix'] + default_bucket + "/" + ck["id"] + ".png"
                    else:
                        if oss_type == "TOS":
                            object_name = ck["id"] + ".png"
                            tos_object_key = oss_config["temp_object_key_prefix"] + "/" + object_name
                            # 🔥 图片也生成预签名 URL（有效期1小时），方便直接访问
                            # 🔥 修复：传递bytes而不是BytesIO对象，避免上传后文件被关闭
                            url = upload2tos(
                                object_key=tos_object_key, 
                                file_bytes=output_buffer.getvalue(), 
                                bucket_name=oss_config.get("bucket", TOS['bucket']),
                                generate_presigned_url=True,  # 🔥 启用预签名URL
                                presigned_expires=3600  # 🔥 有效期1小时（3600秒）
                            )
                        else:
                            url = upload2cdn(ck["id"] + ".png", output_buffer.getvalue(), cdn_host=oss_config.get("cdn_host", SERVICE["cdn_host"]))
                    ck["extra_data"]["image_name"] = url
                    ck["extra_data"]["url"] = url

                # 丢弃原始PIL图片信息
                ck["extra_data"].pop("image", None)  # 删除原始的PIL图像对象

                # 图像信息处理 （1）多模态；（2）OCR
                # if vision:  # 多模态
                if enable_vision_understand:
                    logger.info("multimodal processing")
                    try:
                        output_buffer.seek(0)
                        base64_image = base64.b64encode(output_buffer.getvalue()).decode("utf-8")
                        # 🔥 修复：OpenAI兼容的API需要data URI格式
                        image_url = f"data:image/png;base64,{base64_image}"
                        image_vlm_prompt = PROMPT
                        vision_content = vision_agent.generate(image_url, image_vlm_prompt)
                        # logger.info(f"image_vision_content={vision_content}")
                        ck["content"] = vision_content
                    except Exception as ex:
                        logger.error("request_id={}, multimodal processing of chunk {} got exception {}".format(request_id, ck["id"], ex))
                        ck["content"] = ""
                else:
                    # ocr判别
                    if not ocr_content:
                        ck["content"] = ""
                    else:
                        if ck["content"] == "__no_ocr__":
                            logger.info("ocr processing...")
                            text = engine(output_buffer.getvalue())
                            logger.info("text.txts={}".format(text.txts))
                            if text.txts is not None:
                                ck["content"] = ",".join(text.txts)
                            else:
                                ck["content"] = ""

                # 处理后的图片信息
                parse_content_list.append(ck)
            except Exception as ex:
                logger.error("processing image of chunk {}, {} got exception {}".format(default_bucket, ck["id"], ex))
                # callback
                job_fail_wrap(request_id, doc_id or 0, call_back_url, ex, environment)
                raise Exception("Saving image of chunk got exception={}".format(ex))

        elif ck["content_type"] == "TABLE" and "image" in ck["extra_data"]:
            try:
                logger.info("image of table processing")
                image_content = ck["extra_data"]["image"]
                output_buffer = BytesIO()
                if isinstance(image_content, bytes):
                    output_buffer = BytesIO(image_content)
                else:
                    image_content.save(output_buffer, format='PNG')
                    # 🔥 修复：重置BytesIO指针到开头，否则上传时会读取到空内容
                    output_buffer.seek(0)

                # 需要上传图片的场景
                if table_image:
                    if environment == "ATOM":
                        STORAGE_IMPL.put(default_bucket, ck["id"] + ".png", output_buffer.getvalue())
                        url = oss_config['prefix'] + default_bucket + "/" + ck["id"] + ".png"
                    else:
                        if oss_type == "TOS":
                            object_name = ck["id"] + ".png"
                            tos_object_key = oss_config["temp_object_key_prefix"] + "/" + object_name
                            # 🔥 表格图片也生成预签名 URL（有效期1小时），方便直接访问
                            # 🔥 修复：传递bytes而不是BytesIO对象，避免上传后文件被关闭
                            url = upload2tos(
                                object_key=tos_object_key, 
                                file_bytes=output_buffer.getvalue(), 
                                bucket_name=oss_config.get("bucket", TOS['bucket']),
                                generate_presigned_url=True,  # 🔥 启用预签名URL
                                presigned_expires=3600  # 🔥 有效期1小时（3600秒）
                            )
                        else:
                            url = upload2cdn(ck["id"] + ".png", output_buffer.getvalue(), cdn_host=oss_config.get("cdn_host", SERVICE["cdn_host"]))
                    ck["extra_data"]["image_name"] = url

                ck["extra_data"].pop("image", None)  # 删除原始的PIL图像对象

                if table_vision and ('xlsx' in original_url_before_convert or 'xls' in original_url_before_convert or enable_vision_understand):  # 多模态表格识别
                    try:
                        output_buffer.seek(0)
                        base64_image = base64.b64encode(output_buffer.getvalue()).decode("utf-8")
                        # 🔥 修复：OpenAI兼容的API需要data URI格式
                        image_url = f"data:image/png;base64,{base64_image}"
                        table_vlm_prompt = VISION["table_prompt"]
                        # logger.info("table_vlm_prompt={}".format(table_vlm_prompt))
                        table_vision_content = vision_agent.generate(image_url, table_vlm_prompt)
                        # logger.info(f"table_vision_content={table_vision_content}")
                        ck["vlm_content"] = table_vision_content
                    except Exception as ex:
                        logger.error("request_id={}, multimodal processing of table {} got exception {}".format(request_id, ck["id"], ex))

                parse_content_list.append(ck)
            except Exception as ex:
                logger.error(
                    "Saving image of chunk {}, {} got exception".format(default_bucket, ck["id"]))
                # callback
                job_fail_wrap(request_id, doc_id or 0, call_back_url, ex, environment)
                raise Exception("Saving table image of chunk got exception={}".format(ex))
        else:
            parse_content_list.append(ck)
        
        # 更新处理进度 (65%-85%)
        processed_chunks += 1
        if total_chunks > 0 and processed_chunks % max(1, total_chunks // 10) == 0:
            progress = 65 + int((processed_chunks / total_chunks) * 20)
            update_progress_sync(progress_id, progress, f"处理中 ({processed_chunks}/{total_chunks})")

    # 构建存储id
    try:
        # 进度：85% - 图片处理完成
        update_progress_sync(progress_id, 85, "图片和表格处理完成")
        
        # 进度：90% - 开始上传结果
        update_progress_sync(progress_id, 90, "开始上传解析结果")
        
        json_bytes = json.dumps(parse_content_list).encode("utf-8")
        output_buffer = BytesIO(json_bytes)
        parse_content_id = get_random_uuid()
        
        result_size_mb = len(json_bytes) / (1024 * 1024)

        if environment == "ATOM":
            STORAGE_IMPL.put(default_bucket, parse_content_id + ".json", output_buffer.getvalue())
            oss_url = oss_config['prefix'] + default_bucket + "/" + parse_content_id + ".json"
        else:
            if oss_type == "TOS":
                object_name = parse_content_id + ".json"
                tos_object_key = oss_config["temp_object_key_prefix"] + "/" + object_name
                # 🔥 生成预签名 URL（有效期1小时），方便直接访问
                oss_url = upload2tos(
                    object_key=tos_object_key, 
                    file_bytes=output_buffer, 
                    bucket_name=oss_config.get("bucket", TOS['bucket']),
                    generate_presigned_url=True,  # 🔥 启用预签名URL
                    presigned_expires=3600  # 🔥 有效期1小时（3600秒）
                )
            else:
                oss_url = upload2cdn(parse_content_id + ".json", output_buffer.getvalue(), cdn_host=oss_config.get("cdn_host", SERVICE["cdn_host"]))
        
        # 进度：95% - 结果上传完成
        update_progress_sync(progress_id, 95, f"结果上传完成 ({result_size_mb:.2f}MB)")
        
        logger.info("@@@Successfully finished: filename={}, request_id={}, oss_url={}".format(filename, request_id, oss_url))

    except Exception as ex:
        logger.exception("request_id={}, Saving parse content of chunk {}, {}, {} got exception".format(request_id,
                                                                                                      original_url,
                                                                                                      filename,
                                                                                                      doc_id))
        # 回调报错信息
        job_fail_wrap(request_id, doc_id or 0, call_back_url, ex, environment)
        raise Exception("解析文档存储失败, error={}".format(ex))

    # 生成 pdf_file 预签名下载链接
    pdf_file = ""
    if to_pdf:
        # 说明进行了文件转换，生成转换后 PDF 的预签名下载链接
        try:
            from kparser.common.file_utils import parse_presigned_url
            from kparser.rag.storage.tos_conn import RAGTOS
            
            # 解析 bucket 和 object_key
            bucket_name, object_key = parse_presigned_url(original_url)
            
            if bucket_name and object_key:
                # 生成预签名 URL（有效期1小时）
                tos_client = RAGTOS()
                pdf_file = tos_client.get_presigned_url(bucket_name, object_key, 3600)
                logger.info(f"✅ Generated presigned URL for converted PDF: {pdf_file[:100] if pdf_file else 'None'}...")
            else:
                logger.warning(f"⚠️ Failed to parse bucket/key from converted URL: {original_url}")
        except Exception as ex:
            logger.error(f"❌ Failed to generate presigned URL for PDF: {ex}")
            # 不抛出异常，pdf_file 保持为空字符串
    
    # 检查 oss_url 是否有效（支持预签名 URL，需要检查路径部分而不是整个 URL）
    if oss_url is not None and "json" in oss_url:
        # 对于预签名 URL，移除查询参数后检查
        url_path = oss_url.split('?')[0]  # 移除查询参数
        if url_path.endswith("json") or url_path.endswith(".json"):
            # 进度：100% - 任务完成
            update_progress_sync(progress_id, 100, "任务完成")
            
            # 回调解析结果信息
            job_success_wrap(request_id, doc_id or 0, call_back_url, oss_url, environment)
        else:
            # 回调报错信息
            logger.warning(f"oss_url 格式不正确，不是 JSON 文件: {url_path}")
            job_fail_wrap(request_id, doc_id or 0, call_back_url, f"上传结果格式错误: {url_path}", environment)
    else:
        # 回调报错信息
        logger.error(f"oss_url 为空或无效: {oss_url}")
        job_fail_wrap(request_id, doc_id or 0, call_back_url, f"上传结果失败: {oss_url}", environment)

    logger.debug("oss_url={}, pdf_file={}".format(oss_url, pdf_file))
    
    # 返回字典格式，包含解析结果和PDF文件下载链接
    return {
        "oss_url": oss_url,
        "pdf_file": pdf_file
    }

def job_success_wrap(task_id, doc_id, call_back_url, result, environment=None):
    logger.info(f"Task {task_id} completed successfully, call_back_url={call_back_url}")
    environment = environment or SERVICE["environment"]
    if environment == "ONLINE":
        if call_back_url == "":
            call_back_url = SERVICE["call_back_url"]
            res_status, res_msg = call_back_status(call_back_url, "complete", task_id, doc_id, oss_url=result,
                                                   message="")
        elif call_back_url == "None":
            res_status = True
            res_msg = "no need to call back"
            logger.info(f"Task {task_id} call back url is None, no need to call back")
        else:
            res_status, res_msg = call_back_status(call_back_url, "complete", task_id, doc_id, oss_url=result,
                                                   message="")
        logger.info(f"Task {task_id} call back status={res_status}, res_msg={res_msg}")

def job_fail_wrap(task_id, doc_id, call_back_url, ex, environment=None):
    logger.error(f"Task {task_id} failed: {ex}, call_back_url={call_back_url}")
    if "no attribute" in str(ex) or "not iterable" in str(ex):
        ex = "待解析的文件可能已损坏，请先检查url对应的文档完整性和可用性！"
    environment = environment or SERVICE["environment"]
    if environment == "ONLINE":
        if call_back_url == "":
            call_back_url = SERVICE["call_back_url"]
            res_status, res_msg = call_back_status(call_back_url,
                                                   "failed",
                                                   task_id,
                                                   doc_id,
                                                   oss_url="",
                                                   message=str(ex))
        elif call_back_url == "None":
            res_status = True
            res_msg = "no need to call back"
            logger.info(f"Task {task_id} call back url is None, no need to call back")
        else:
            res_status, res_msg = call_back_status(call_back_url,
                                                   "failed",
                                                   task_id,
                                                   doc_id,
                                                   oss_url="",
                                                   message=str(ex))

        logger.error(f"Task {task_id} call back status={res_status}, res_msg={res_msg}")


def read_pdf_as_img_binary(original_url: str, filename: str):
    logger.info("original file url={}".format(original_url))
    # 移除查询参数（支持预签名 URL）
    url_path = original_url.split('?')[0]
    converted_url = file_to_pdf(False if url_path.endswith('.pdf') else True, original_url, filename)
    if original_url != converted_url:
        logger.info("converted file url={}".format(converted_url))
    return read_file_from_original_url(converted_url)


def ocr_service(request_id: str,
                filename: str,
                original_url: str,
                from_page: int,
                to_page: int,
                environment: str = None,
                oss_type: str = None,
                oss_config: dict = None,
                zoomin: int = 3,
    ):
    logger.info(f"request_id={request_id}, filename={filename}, original_url={original_url}, zoomin={zoomin}")
    if to_page is None or to_page == "" or to_page <= 0:
        to_page = 1000000
    environment = environment or SERVICE["environment"]
    oss_type = oss_type or SERVICE["use_storage"]
    oss_config = oss_config or TOS
    try:
        pdfparser = PdfParser()
        # 移除查询参数后判断文件类型（支持预签名URL）
        url_without_query = original_url.split('?')[0]
        if url_without_query.lower().endswith(".pdf"):
            binary = read_pdf_as_img_binary(original_url, filename)
        elif url_without_query.lower().endswith(('.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff', '.gif', '.webp', '.ico', '.icon')):
            binary = read_file_from_original_url(original_url)
            binary = img2pdf.convert(binary)
        else:
            raise Exception("非PDF和图片文件不支持OCR服务")
        pdfparser.__images__(binary, zoomin)
        pdfparser._layouts_rec(zoomin)
        pdfparser._table_transformer_job(zoomin)
        pdfparser._text_merge()
        tbls = pdfparser._extract_table_figure(True, zoomin, True, True)
        pdfparser._concat_downward()
        # logger.info(f"{tbls[-1]}")
        res = [(b["text"], pdfparser._line_tag(b, zoomin))
                for b in pdfparser.boxes], [(t[0][1], "@@{}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}##".format(t[1][0][0], t[1][0][1], t[1][0][2], t[1][0][3], t[1][0][4])) for t in tbls]
        
        # 将结果序列化为 JSON 字符串，然后转为 bytes
        res_json = json.dumps(res, ensure_ascii=False)
        res_bytes = res_json.encode('utf-8')
        buffer = BytesIO(res_bytes)
        
        parse_content_id = get_random_uuid()
        object_name = parse_content_id + ".json"  # 改为 .json 扩展名
        tos_object_key = oss_config["temp_object_key_prefix"] + "/" + object_name
        oss_url = upload2tos(
            object_key=tos_object_key, 
            file_bytes=buffer, 
            bucket_name=oss_config.get("bucket", TOS['bucket']),
            generate_presigned_url=True,  # 🔥 启用预签名URL
            presigned_expires=3600  # 🔥 有效期1小时（3600秒）
        )
        return oss_url
        # # res = pdfparser(binary)
        # return deepcopy(pdfparser.boxes)
        # # return res
    except Exception as e:
        logger.error(f"Error ocr service: {e}")
        raise Exception("OCR服务失败: {}".format(e))


def simple_ocr_service(request_id: str,
                doc_id: int,
                filename: str,
                original_url: str,
                from_page: int,
                to_page: int,
                environment: str = None,
                oss_type: str = None,
                oss_config: dict = None,
    ):
    ocr = RapidOCR(params={"Global.lang_det": "ch_mobile", "Global.lang_rec": "ch_mobile"})
    try:
        binary = read_pdf_as_img_binary(original_url, filename)
        res = ocr(binary)
        
        # 将结果序列化为 JSON 字符串，然后转为 bytes
        res_json = json.dumps(res, ensure_ascii=False)
        res_bytes = res_json.encode('utf-8')
        buffer = BytesIO(res_bytes)
        
        parse_content_id = get_random_uuid()
        object_name = parse_content_id + ".json"  # 改为 .json 扩展名
        tos_object_key = oss_config["temp_object_key_prefix"] + "/" + object_name
        oss_url = upload2tos(
            object_key=tos_object_key, 
            file_bytes=buffer, 
            bucket_name=oss_config.get("bucket", TOS['bucket']),
            generate_presigned_url=True,  # 🔥 启用预签名URL
            presigned_expires=3600  # 🔥 有效期1小时（3600秒）
        )
        return oss_url
    except Exception as e:
        logger.error(f"Error simple ocr service: {e}")
        raise Exception("Simple OCR服务失败: {}".format(e))


def img_crop():
    pass


def text_extract(
                original_url: str,
                from_page: int,
                to_page: int,
                oss_config: dict = None
                ):
    try:
        oss_config = oss_config or TOS
        pdf_loader = PDFLoader_enhanced()
        binary = read_file_from_original_url(original_url)
        chunks = pdf_loader(binary, from_page, to_page)
        
        res_json = json.dumps(chunks, ensure_ascii=False)
        res_bytes = res_json.encode('utf-8')
        buffer = BytesIO(res_bytes)
        
        parse_content_id = get_random_uuid()
        object_name = parse_content_id + ".json"  # 改为 .json 扩展名
        tos_object_key = oss_config["temp_object_key_prefix"] + "/" + object_name
        oss_url = upload2tos(
            object_key=tos_object_key, 
            file_bytes=buffer, 
            bucket_name=oss_config.get("bucket", TOS['bucket']),
            generate_presigned_url=True,  # 🔥 启用预签名URL
            presigned_expires=3600  # 🔥 有效期1小时（3600秒）
        )
        return oss_url
    except Exception as e:
        logger.error(f"Error text extract: {e}")
        raise Exception("Text extract失败: {}".format(e))