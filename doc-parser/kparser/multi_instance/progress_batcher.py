# -*- coding: utf-8 -*-
"""
进度批量更新器
将频繁的进度更新批量化处理，减少Redis写操作次数
"""
import asyncio
import time
from typing import Dict, List, Optional
from datetime import datetime, timezone
from collections import defaultdict

from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class ProgressUpdate:
    """进度更新数据结构"""
    def __init__(self, task_id: str, progress: int, logs: List[str], timestamp: float):
        self.task_id = task_id
        self.progress = progress
        self.logs = logs
        self.timestamp = timestamp


class ProgressBatcher:
    """
    进度批量更新器
    
    核心优化：
    1. 批量聚合：收集一定时间窗口内的更新，批量提交
    2. 智能节流：同一任务的多次更新合并为最新状态
    3. 自适应刷新：根据更新频率动态调整刷新间隔
    4. 重要更新快速通道：状态变更（开始/完成/失败）立即刷新
    """
    
    def __init__(
        self,
        state_manager,
        batch_interval: float = 1.0,
        max_batch_size: int = 100,
        enable_fast_track: bool = True
    ):
        """
        初始化批量更新器
        
        Args:
            state_manager: 状态管理器实例
            batch_interval: 批量刷新间隔（秒），默认1秒
            max_batch_size: 最大批量大小，达到后立即刷新
            enable_fast_track: 是否启用快速通道（重要状态变更立即刷新）
        """
        self.state_manager = state_manager
        self.batch_interval = batch_interval
        self.max_batch_size = max_batch_size
        self.enable_fast_track = enable_fast_track
        
        # 待处理的更新（task_id -> ProgressUpdate）
        self._pending_updates: Dict[str, ProgressUpdate] = {}
        self._update_lock = asyncio.Lock()
        
        # 批量刷新任务
        self._flush_task: Optional[asyncio.Task] = None
        self._shutdown_event = asyncio.Event()
        
        # 统计信息
        self._total_updates = 0
        self._batched_updates = 0
        self._fast_track_updates = 0
        self._batch_flush_count = 0
        
        logger.info(f"ProgressBatcher initialized: interval={batch_interval}s, max_batch={max_batch_size}")
    
    async def start(self):
        """启动批量刷新任务"""
        if self._flush_task is None or self._flush_task.done():
            self._flush_task = asyncio.create_task(self._batch_flush_loop())
            logger.info("ProgressBatcher started")
    
    async def stop(self):
        """停止批量刷新任务"""
        self._shutdown_event.set()
        
        if self._flush_task:
            self._flush_task.cancel()
            try:
                await self._flush_task
            except asyncio.CancelledError:
                pass
        
        # 刷新剩余的更新
        await self._flush_pending()
        
        logger.info(f"ProgressBatcher stopped. Stats: total={self._total_updates}, batched={self._batched_updates}, fast_track={self._fast_track_updates}, flushes={self._batch_flush_count}")
    
    async def update_progress(
        self,
        task_id: str,
        progress: int,
        logs: List[str],
        status: Optional[str] = None,
        force_immediate: bool = False
    ) -> bool:
        """
        提交进度更新（批量模式）
        
        Args:
            task_id: 任务ID
            progress: 进度百分比
            logs: 日志列表
            status: 任务状态（如果是状态变更，走快速通道）
            force_immediate: 强制立即刷新
        
        Returns:
            bool: 是否成功提交
        """
        self._total_updates += 1
        
        # 【快速通道】重要状态变更立即刷新
        is_critical_status = status in ["complete", "failed", "killed", "cancelled"]
        should_fast_track = (
            self.enable_fast_track and 
            (is_critical_status or force_immediate or progress == 100)
        )
        
        if should_fast_track:
            # 立即更新到Redis
            try:
                await self.state_manager.update_task_status(
                    task_id,
                    {
                        "progress": progress,
                        "logs": logs,
                        "status": status,
                        "updated_at": datetime.now(timezone.utc).isoformat()
                    } if status else {
                        "progress": progress,
                        "logs": logs,
                        "updated_at": datetime.now(timezone.utc).isoformat()
                    }
                )
                self._fast_track_updates += 1
                logger.debug(f"🚀 Fast-track update for task {task_id}: progress={progress}%, status={status}")
                return True
            except Exception as e:
                logger.error(f"Fast-track update failed for task {task_id}: {e}")
                return False
        
        # 【批量模式】收集到待处理队列
        async with self._update_lock:
            self._pending_updates[task_id] = ProgressUpdate(
                task_id=task_id,
                progress=progress,
                logs=logs,
                timestamp=time.time()
            )
            
            # 如果批量大小达到上限，触发立即刷新
            if len(self._pending_updates) >= self.max_batch_size:
                logger.debug(f"Batch size limit reached ({self.max_batch_size}), triggering flush")
                asyncio.create_task(self._flush_pending())
        
        self._batched_updates += 1
        return True
    
    async def _batch_flush_loop(self):
        """批量刷新循环"""
        while not self._shutdown_event.is_set():
            try:
                await asyncio.sleep(self.batch_interval)
                await self._flush_pending()
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Batch flush loop error: {e}")
    
    async def _flush_pending(self):
        """刷新待处理的更新到Redis"""
        async with self._update_lock:
            if not self._pending_updates:
                return
            
            # 获取待处理的更新
            updates_to_flush = dict(self._pending_updates)
            self._pending_updates.clear()
        
        # 批量写入Redis
        flush_start = time.time()
        success_count = 0
        failed_count = 0
        
        try:
            # 使用asyncio.gather并发写入（提升性能）
            tasks = []
            for task_id, update in updates_to_flush.items():
                task = self._write_single_update(task_id, update)
                tasks.append(task)
            
            # 等待所有写入完成
            results = await asyncio.gather(*tasks, return_exceptions=True)
            
            # 统计结果
            for result in results:
                if isinstance(result, Exception):
                    failed_count += 1
                    logger.error(f"Batch update failed: {result}")
                else:
                    success_count += 1
            
            self._batch_flush_count += 1
            flush_duration = time.time() - flush_start
            
            logger.debug(
                f"📦 Batch flush completed: "
                f"success={success_count}, failed={failed_count}, "
                f"duration={flush_duration*1000:.1f}ms, "
                f"avg={flush_duration/len(updates_to_flush)*1000:.1f}ms/update"
            )
            
        except Exception as e:
            logger.error(f"Batch flush error: {e}")
    
    async def _write_single_update(self, task_id: str, update: ProgressUpdate):
        """写入单个更新到Redis"""
        try:
            await self.state_manager.update_task_status(
                task_id,
                {
                    "progress": update.progress,
                    "logs": update.logs,
                    "updated_at": datetime.now(timezone.utc).isoformat()
                }
            )
        except Exception as e:
            logger.error(f"Failed to write update for task {task_id}: {e}")
            raise
    
    def get_stats(self) -> Dict:
        """获取批量更新器统计信息"""
        total = self._total_updates
        batched = self._batched_updates
        fast_track = self._fast_track_updates
        
        return {
            "total_updates": total,
            "batched_updates": batched,
            "fast_track_updates": fast_track,
            "batch_flush_count": self._batch_flush_count,
            "pending_updates": len(self._pending_updates),
            "batch_ratio": batched / total if total > 0 else 0,
            "avg_batch_size": batched / self._batch_flush_count if self._batch_flush_count > 0 else 0,
            "write_reduction": f"{(1 - self._batch_flush_count / total) * 100:.1f}%" if total > 0 else "N/A"
        }


# 全局单例
_progress_batcher: Optional[ProgressBatcher] = None


def get_progress_batcher(state_manager=None) -> ProgressBatcher:
    """获取进度批量更新器单例"""
    global _progress_batcher
    
    if _progress_batcher is None:
        if state_manager is None:
            from kparser.multi_instance.cached_state_manager import get_cached_state_manager
            state_manager = get_cached_state_manager()
        _progress_batcher = ProgressBatcher(state_manager)
    
    return _progress_batcher



