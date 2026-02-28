from datetime import datetime, timezone

from kparser.common import config as settings
from kparser.common.log_utils import get_logger
from kparser.common.callback_client import call_back_status

logger = get_logger(__name__)


def validate_pages(start_page, end_page):
    """
    校验页码参数（支持字符串或整数类型）
    
    Args:
        start_page: 开始页码（int 或 str）
        end_page: 结束页码（int、str 或 空字符串）
    
    Returns:
        (start_page: int, end_page: int)
    """
    # 校验 start_page
    if start_page is None or start_page == "":
        raise Exception("start_page must be greater than or equal to 1.")
    
    # 转换为整数（如果是字符串）
    if isinstance(start_page, str):
        if not start_page.isdigit():
            raise Exception("start_page must be a valid integer.")
        start_page = int(start_page)
    elif not isinstance(start_page, int):
        raise Exception("start_page must be an integer.")
    
    if start_page < 1:
        raise Exception("start_page must be greater than or equal to 1.")
    
    # 校验 end_page
    if end_page is None or end_page == "":
        end_page = 1000000  # 默认到最后一页
    else:
        # 转换为整数（如果是字符串）
        if isinstance(end_page, str):
            try:
                end_page = int(end_page)
            except ValueError:
                raise Exception("end_page must be a valid integer or empty.")
        elif not isinstance(end_page, int):
            raise Exception("end_page must be an integer.")
        
        if end_page < 1:
            raise Exception("end_page must be a positive integer or empty.")
        
        if end_page <= start_page:
            raise Exception("end_page must be greater than start_page.")
    
    logger.info(f"Validated pages: start_page={start_page}, end_page={end_page}")
    return start_page, end_page


def job_fail_wrap(jobs, task_id, ex):
    jobs[task_id]["status"] = "failed"
    jobs[task_id]["result"] = {"error": str(ex)}
    jobs[task_id]["timestamp"] = datetime.now(timezone.utc)  # 记录失败时间
    doc_id = jobs[task_id].get("doc_id", 0)  # 兼容性：doc_id 可能不存在
    call_back_url = jobs[task_id].get("call_back_url", "")
    logger.error(f"Task {task_id}, {jobs[task_id]['filename']} failed: {ex}")
    if "no attribute" in str(ex) or "not iterable" in str(ex):
        ex = "待解析的文件可能已损坏，请先检查url对应的文档完整性和可用性！"
    
    # 仅在配置了回调 URL 时才回调（新版 API 不使用回调）
    if settings.SERVICE["environment"] == "ONLINE" and call_back_url and call_back_url != "None":
        if call_back_url == "":
            call_back_url = settings.SERVICE["call_back_url"]
        
        if call_back_url and call_back_url != "None":
            res_status, res_msg = call_back_status(call_back_url,
                                                   "failed",
                                                   task_id,
                                                   doc_id,
                                                   oss_url="",
                                                   message=str(ex))
            logger.error(f"Task {task_id} call back status={res_status}, res_msg={res_msg}")
        else:
            logger.info(f"Task {task_id} no callback configured")


