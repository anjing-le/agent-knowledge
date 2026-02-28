from pydantic import BaseModel, Field
from typing import Any, Optional
from datetime import datetime


class TaskData(BaseModel):
    """任务详细信息"""
    requestId: str = Field(description="上游传入的请求ID")
    taskId: str = Field(description="任务ID，与requestId相同")
    status: str = Field(description="任务状态：PENDING、PROCESSING、SUCCESS、FAILED、CANCELED")
    progress: int = Field(description="执行进度百分比(0-100)", default=0)
    log: str = Field(description="步骤日志，使用'|'分隔", default="")
    errorMsg: Optional[str] = Field(description="错误信息，仅status为FAILED时非空", default=None)
    result: Optional[str] = Field(description="任务结果文件地址，仅status为SUCCESS时非空", default=None)
    pdfFile: Optional[str] = Field(description="转换后的PDF文件下载地址（带预签名），仅当to_pdf=true且转换成功时非空", default=None)
    timeout: int = Field(description="建议下次轮询延迟（秒）", default=5)


class UnifiedResponse(BaseModel):
    """统一响应格式"""
    code: str = Field(description="状态码：'0'成功，'1'进行中，'-1'失败")
    message: str = Field(description="响应消息")
    data: Optional[TaskData] = Field(description="任务数据", default=None)


# 旧版兼容（内部使用）
class Response(BaseModel):
    code: int = Field(description="返回码,200为正常返回", default=200)
    status: str = Field(description="返回消息", default="success")
    message: Any = Field(description="返回数据", default="core is running")


class ResponseCallback(BaseModel):
    code: int = Field(description="返回码,200为正常返回", default=200)
    status: str = Field(description="返回消息", default="success")
    message: Any = Field(description="返回数据", default="core is running")
    data: str = Field(description="返回结果", default="data content")


# 解析任务成功
def success2resp(res):
    resp = Response(code=200,
                    status="success",
                    message=str(res))
    return resp

# 解析任务异常
def exception2resp(ex):
    resp = Response(code=500,
                    status="server internal error",
                    message=str(ex))
    return resp


# 解析任务失败
def fail2resp(ex):
    resp = Response(code=200,
                    status="fail",
                    message=str(ex))

    return resp


# 任务成功
def success2callback(message, data=""):
    resp = ResponseCallback(code=200,
                            status="success",
                            message=message,
                            data=data)
    return resp

# 任务失败
def fail2callback(message, data=""):
    resp = ResponseCallback(code=500,
                            status="fail",
                            message=message,
                            data=data)
    return resp

# 任务异常
def exception2callback(message, data=""):
    resp = ResponseCallback(code=500,
                            status="error",
                            message=message,
                            data=data)
    return resp

# 任务运行中
def running2callback(message, data=""):
    resp = ResponseCallback(code=300,
                            status="in_progress",
                            message=message,
                            data=data)
    return resp

# 任务未执行
def notask2callback(message, data=""):
    resp = ResponseCallback(code=400,
                            status="error",
                            message=message,
                            data=data)
    return resp

# 任务不存在
def null2callback(message, data=""):
    resp = ResponseCallback(code=404,
                            status="error",
                            message=message,
                            data=data)
    return resp

# 终止失败
def terminate2callback(message, data=""):
    resp = ResponseCallback(code=501,
                            status="error",
                            message=message,
                            data=data)
    return resp


# ==================== 新版统一响应格式 ====================

def create_task_response(
    request_id: str,
    task_id: str = None,
    status: str = "PENDING",
    progress: int = 0,
    log: str = "",
    error_msg: Optional[str] = None,
    result: Optional[str] = None,
    pdf_file: Optional[str] = None,
    timeout: int = 5
) -> dict:
    """
    创建任务响应
    
    Args:
        request_id: 请求ID（上游提供）
        task_id: 任务ID（服务端生成），如果为 None 则与 request_id 相同
        status: 任务状态（PENDING、PROCESSING、SUCCESS、FAILED、CANCELED）
        progress: 当前进度百分比（0-100）
        log: 日志信息，使用'|'分隔
        error_msg: 错误信息
        result: 结果文件地址
        pdf_file: 转换后的PDF文件下载地址（带预签名）
        timeout: 建议轮询间隔（秒）
    
    Returns:
        统一格式的响应字典
    """
    # 如果没有提供 task_id，使用 request_id
    if task_id is None:
        task_id = request_id
    # 根据状态确定 code
    if status in ["FAILED"]: code = "-1"
    else:code = "0"
    
    # 根据状态确定 message
    status_messages = {
        "PENDING": "任务已创建，等待处理",
        "PROCESSING": "任务处理中",
        "SUCCESS": "任务执行成功",
        "FAILED": "任务执行失败",
        "CANCELED": "任务已取消"
    }
    message = status_messages.get(status, "任务状态未知")
    
    task_data = TaskData(
        requestId=request_id,
        taskId=task_id,
        status=status,
        progress=progress,
        log=log,
        errorMsg=error_msg,
        result=result,
        pdfFile=pdf_file,
        timeout=timeout
    )
    
    response = UnifiedResponse(
        code=code,
        message=message,
        data=task_data
    )
    
    return response.dict()


def task_submit_success(request_id: str, task_id: str = None) -> dict:
    """任务提交成功响应"""
    if task_id is None:
        task_id = request_id
    
    return create_task_response(
        request_id=request_id,
        task_id=task_id,
        status="PENDING",
        progress=0,
        log="任务已提交|等待处理",
        timeout=2
    )


def task_processing(request_id: str, progress: int = 0, log: str = "", timeout: int = 5, task_id: str = None) -> dict:
    """任务处理中响应"""
    if task_id is None:
        task_id = request_id
    
    return create_task_response(
        request_id=request_id,
        task_id=task_id,
        status="PROCESSING",
        progress=progress,
        log=log,
        timeout=timeout
    )


def task_success(request_id: str, result: str, progress: int = 100, log: str = "", pdf_file: str = None, task_id: str = None) -> dict:
    """任务成功响应"""
    if task_id is None:
        task_id = request_id
    
    return create_task_response(
        request_id=request_id,
        task_id=task_id,
        status="SUCCESS",
        progress=progress,
        log=log,
        result=result,
        pdf_file=pdf_file,
        timeout=0  # 成功后不需要再轮询
    )


def task_failed(request_id: str, error_msg: str, progress: int = 0, log: str = "", task_id: str = None) -> dict:
    """任务失败响应"""
    if task_id is None:
        task_id = request_id
    
    return create_task_response(
        request_id=request_id,
        task_id=task_id,
        status="FAILED",
        progress=progress,
        log=log,
        error_msg=error_msg,
        timeout=0  # 失败后不需要再轮询
    )


def task_not_found(request_id: str, task_id: str = None) -> dict:
    """任务不存在响应"""
    if task_id is None:
        task_id = request_id
    
    return {
        "code": "-1",
        "message": "任务不存在",
        "data": {
            "requestId": request_id,
            "taskId": task_id,
            "status": "FAILED",
            "progress": 0,
            "log": "",
            "errorMsg": f"任务ID为{task_id}的任务不存在",
            "result": None,
            "timeout": 0
        }
    }


def task_canceled(request_id: str, task_id: str = None, log: str = "") -> dict:
    """任务取消响应"""
    if task_id is None:
        task_id = request_id
    
    return create_task_response(
        request_id=request_id,
        task_id=task_id,
        status="CANCELED",
        log=log if log else "任务已被取消",
        error_msg="任务已被用户取消",
        timeout=0
    )