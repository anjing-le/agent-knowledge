import time
import os
import json
import logging

from logging import handlers
from functools import wraps
from typing import Callable, Any, List
from fastapi.params import Body
from multiprocessing import Lock

config_lock = Lock()


log_path = "./logs/dparser_detail.log"

def get_logger(logger_name, file_path=log_path):
    with config_lock:
        logger = logging.getLogger(logger_name)
        if not logger.hasHandlers():  # 检查 logger 是否已经配置过处理器
            logger.setLevel(level=logging.DEBUG)
            formatter = logging.Formatter('%(asctime)s - %(filename)s[%(lineno)d] - %(levelname)s: %(message)s')

            # 确保日志文件的目录存在
            os.makedirs(os.path.dirname(file_path), exist_ok=True)

            # 每天零点存一个文件，保存最近15天数据
            time_rotating_file_handler = handlers.TimedRotatingFileHandler(file_path, when="MIDNIGHT", interval=1, backupCount=15, encoding="utf8")
            time_rotating_file_handler.setLevel(logging.INFO)
            time_rotating_file_handler.setFormatter(formatter)

            # 添加 StreamHandler 以便日志输出到控制台 (stdout)
            stream_handler = logging.StreamHandler()
            stream_handler.setLevel(logging.DEBUG)
            stream_handler.setFormatter(formatter)

            logger.addHandler(time_rotating_file_handler)
            logger.addHandler(stream_handler)

    return logger


log = get_logger(__name__)


def parse_body(func: Callable):
    @wraps(func)
    def wrapped(**kwargs):
        print(f"{kwargs=}")
        for k, v in kwargs.items():
            if isinstance(v, Body):
                kwargs[k] = v.default
        return func(**kwargs)
    return wrapped


def safe_format(template: str, **kwargs):
    for k, v in kwargs.items():
        template = template.replace("{" + k +"}", v)
    return template


def json_load(path, max_retries=3, retry_delay=0.01):
    retries = 0
    while retries < max_retries:
        try:
            content = ''
            with config_lock:
                with open(path, 'r', encoding='utf-8') as file:
                    lines = file.readlines()
                    for line in lines:
                        if line.strip():  # 只有在非空行才添加
                            content += line
                    log.debug(f"[Attempt {retries + 1}] JSON file content: \n{content}")
            
            if content:
                return json.loads(content)
            else:
                raise ValueError("File content is empty after reading")
        
        except Exception as e:
            log.error(f"Failed to decode JSON from {path} on attempt {retries + 1}: {e}")
            retries += 1
            time.sleep(retry_delay)
    
    raise Exception(f"Failed to decode JSON from {path} after {max_retries} retries")


def jsonl_load(path, max_retries=3, retry_delay=0.01):
    retries = 0
    while retries < max_retries:
        try:
            jsonl_result = []
            with config_lock:
                with open(path, 'r', encoding='utf-8') as file:
                    lines = file.readlines()
                    for line in lines:
                        if line.strip():  # 只有在非空行才添加
                            jsonl_result.append(json.loads(line))
                    log.debug(f"[Attempt {retries + 1}] JSON file content: \n{json.dumps(jsonl_result, ensure_ascii=False)}")
            
            return jsonl_result        
        except Exception as e:
            log.error(f"Failed to decode JSON from {path} on attempt {retries + 1}: {e}")
            retries += 1
            time.sleep(retry_delay)
    
    raise Exception(f"Failed to decode JSON from {path} after {max_retries} retries")


def json_dumps(data):
    if not data:
        raise ValueError(f"json_dumps data is null.")
    
    try:
        data = json.dumps(data, ensure_ascii=False)
        return data
    except Exception as e:
        log.error(f"json_dumps suffer error: {e}")
        return None

def get_storage_size(s, encoding='utf-8'):
    """
    获取字符串的实际存储大小（以字节为单位）。
    :param s: 字符串
    :param encoding: 编码方式，默认为 'utf-8'
    :return: 字符串的存储大小（以字节计算）
    """
    return len(s.encode(encoding))

def truncate_string(s, max_size, encoding='utf-8'):
    """
    截断字符串以确保其存储大小不超过最大长度限制。
    :param s: 原始字符串
    :param max_size: 最大存储大小（以字节为单位）
    :param encoding: 编码方式，默认为 'utf-8'
    :return: 截断后的字符串
    """
    encoded_str = s.encode(encoding)
    if len(encoded_str) <= max_size:
        return s

    # 找到最大允许的字符串长度
    truncated_str = s
    while len(truncated_str.encode(encoding)) > max_size:
        truncated_str = truncated_str[:-1]

    return truncated_str

def process_strings(strings, max_size, encoding='utf-8'):
    """
    处理字符串列表，统计其存储大小并在需要时进行截断。
    :param strings: 字符串列表
    :param max_size: 单个字符串的最大存储大小（以字节为单位）
    :param encoding: 编码方式，默认为 'utf-8'
    :return: 处理后的字符串列表和原始大小信息
    """
    original_sizes = [get_storage_size(s, encoding) for s in strings]
    truncated_strings = [truncate_string(s, max_size, encoding) for s in strings]

    return truncated_strings, original_sizes


def all_subclasses(cls: Any) -> List[Any]:
    """Returns all known (imported) subclasses of a class."""

    return cls.__subclasses__() + [g for s in cls.__subclasses__()
                                   for g in all_subclasses(s)]


def get_trace(logger_name):
    """获取跟踪日志记录器，用于调试和性能分析"""
    return get_logger(f"{logger_name}_trace", "./logs/dparser_trace.log")



