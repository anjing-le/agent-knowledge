# -*- coding: utf-8 -*-
"""
进度跟踪器
用于在解析任务过程中更新进度
"""
from typing import Optional, Callable
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class ProgressTracker:
    """进度跟踪器"""
    
    def __init__(self, task_id: str, total_steps: int = 10, callback: Optional[Callable] = None):
        """
        初始化进度跟踪器
        
        Args:
            task_id: 任务ID
            total_steps: 总步骤数（用于计算百分比）
            callback: 进度更新回调函数，接收 (task_id, progress, log_message) 参数
        """
        self.task_id = task_id
        self.total_steps = total_steps
        self.current_step = 0
        self.callback = callback
        
    def update(self, step: Optional[int] = None, log_message: str = ""):
        """
        更新进度
        
        Args:
            step: 当前步骤数（如果为 None，则自动递增）
            log_message: 日志消息
        """
        if step is not None:
            self.current_step = step
        else:
            self.current_step += 1
        
        # 计算百分比（0-100）
        progress = int((self.current_step / self.total_steps) * 100)
        progress = max(0, min(100, progress))  # 确保在 0-100 范围内
        
        logger.info(f"Task {self.task_id} progress: {progress}% (step {self.current_step}/{self.total_steps})")
        
        # 调用回调函数
        if self.callback:
            try:
                self.callback(self.task_id, progress, log_message)
            except Exception as e:
                logger.error(f"Failed to call progress callback: {e}")
    
    def set_progress(self, progress: int, log_message: str = ""):
        """
        直接设置进度百分比
        
        Args:
            progress: 进度百分比（0-100）
            log_message: 日志消息
        """
        progress = max(0, min(100, progress))
        logger.info(f"Task {self.task_id} progress set to: {progress}%")
        
        if self.callback:
            try:
                self.callback(self.task_id, progress, log_message)
            except Exception as e:
                logger.error(f"Failed to call progress callback: {e}")
    
    def complete(self, log_message: str = "任务完成"):
        """
        标记任务完成（设置进度为100%）
        
        Args:
            log_message: 日志消息
        """
        self.set_progress(100, log_message)


# 全局进度回调函数（可以被设置）
_global_progress_callback: Optional[Callable] = None


def set_global_progress_callback(callback: Callable):
    """
    设置全局进度回调函数
    
    Args:
        callback: 回调函数，接收 (task_id, progress, log_message) 参数
    """
    global _global_progress_callback
    _global_progress_callback = callback


def get_progress_tracker(task_id: str, total_steps: int = 10) -> ProgressTracker:
    """
    获取进度跟踪器
    
    Args:
        task_id: 任务ID
        total_steps: 总步骤数
    
    Returns:
        ProgressTracker 实例
    """
    return ProgressTracker(task_id, total_steps, _global_progress_callback)

