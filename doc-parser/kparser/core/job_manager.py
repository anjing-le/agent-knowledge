import asyncio
import multiprocessing
from typing import Dict, Tuple
from datetime import datetime, timedelta, timezone

from kparser.common.log_utils import get_logger
from kparser.common import config as settings
from kparser.core.utils import job_fail_wrap

logger = get_logger(__name__)


JOB_EXPIRATION_TIME = timedelta(minutes=10)  # 定义任务保留时间（例如，保留10分钟）
JOB_WAITING_TIME = timedelta(seconds=int(settings.SERVICE["time_out"])) # 定义任务排队时间（例如，等待4小时）


class JobManager:
    def __init__(self):
        self.jobs: Dict[str, dict] = {}
        self._lock = asyncio.Lock()  # 全局锁（用于jobs字典的结构性操作）
        self._task_locks: Dict[str, asyncio.Lock] = {}  # 🔥 细粒度锁：每个任务独立的锁
        self._task_locks_lock = asyncio.Lock()  # 保护_task_locks字典的锁
        self._executor = None
        self._state_manager = None  # 共享状态管理器（用于同步到 Redis）
        self._max_workers = int(settings.SERVICE.get("max_job_number", 32))  # 最大并发任务数

    async def init_executor(self, max_workers: int):
        """初始化进程池（保持原功能）"""
        self._executor = multiprocessing.get_context("spawn").Pool(max_workers)
        self._max_workers = max_workers
    
    def set_state_manager(self, state_manager):
        """设置共享状态管理器（用于同步进度到 Redis）"""
        self._state_manager = state_manager
        logger.info("JobManager state_manager configured for Redis sync")
    
    async def _get_task_lock(self, task_id: str) -> asyncio.Lock:
        """
        获取或创建任务专属的锁（细粒度锁机制）
        
        优势：不同任务的操作可以并发执行，消除锁竞争
        """
        async with self._task_locks_lock:
            if task_id not in self._task_locks:
                self._task_locks[task_id] = asyncio.Lock()
            return self._task_locks[task_id]
    
    async def get_running_jobs_count(self) -> int:
        """
        获取当前正在运行的任务数量（本地实例）
        
        Returns:
            int: 正在执行的任务数量
        """
        async with self._lock:
            running_count = sum(
                1 for job in self.jobs.values()
                if job.get("status") == "in_progress"
            )
            return running_count
    
    async def can_accept_new_job(self) -> Tuple[bool, int, int]:
        """
        检查是否可以接受新任务
        
        Returns:
            tuple: (是否可接受, 当前运行数, 最大并发数)
        """
        current_load = await self.get_running_jobs_count()
        can_accept = current_load < self._max_workers
        return can_accept, current_load, self._max_workers
    
    def get_max_workers(self) -> int:
        """获取最大并发任务数"""
        return self._max_workers

    async def create_job(self, task_id: str, **meta):
        """
        原子化任务创建
        
        Args:
            task_id: 任务唯一标识（服务端生成的 UUID）
            **meta: 其他任务元数据（如 request_id, filename, doc_type 等）
        """
        async with self._lock:
            self.jobs[task_id] = {
                **meta,
                "status": "in_progress",
                "created_at": datetime.now(timezone.utc),
                "process": None,
                "result": None,
                # 进度跟踪字段
                "progress": 0,  # 百分比 0-100
                "logs": []  # 日志列表
            }
            logger.info(f"Job created: task_id={task_id}, jobs={self.jobs}")
    
    async def update_progress(self, task_id: str, progress: int, log_message: str = "", sync_to_redis: bool = True):
        """
        更新任务进度和日志（优化：细粒度锁 + 批量更新）
        
        Args:
            task_id: 任务ID（任务的唯一标识）
            progress: 进度百分比（0-100）
            log_message: 日志消息
            sync_to_redis: 是否同步到 Redis（主进程中为 True，子进程中为 False）
        """
        # 🔥 使用任务专属的锁，而不是全局锁
        # 优势：不同任务的进度更新可以并发执行
        task_lock = await self._get_task_lock(task_id)
        
        async with task_lock:
            if task_id in self.jobs:
                # 确保进度在 0-100 范围内
                self.jobs[task_id]["progress"] = max(0, min(100, progress))
                if log_message:
                    self.jobs[task_id]["logs"].append(log_message)
                logger.info(f"Task {task_id} progress updated: {self.jobs[task_id]['progress']}%")
                
                # 🔥 仅在主进程中通过异步 state_manager 同步到 Redis
                # 子进程中通过 ProgressSyncManager 同步（在 update_progress_sync 中）
                if sync_to_redis and self._state_manager:
                    try:
                        # 主进程中使用异步 Redis 客户端
                        await self._state_manager.update_task_status(
                            task_id,
                            {
                                "progress": self.jobs[task_id]["progress"],
                                "logs": self.jobs[task_id]["logs"].copy(),
                                "updated_at": datetime.now(timezone.utc).isoformat()
                            }
                        )
                    except Exception as e:
                        # 同步失败不应阻塞任务执行
                        logger.warning(f"Failed to sync progress to Redis for task {task_id}: {e}")
    
    def get_task_logs(self, task_id: str) -> str:
        """获取任务日志（用'|'连接）"""
        if task_id in self.jobs:
            return "|".join(self.jobs[task_id].get("logs", []))
        return ""

    async def cleanup_jobs(self):
        """定时清理任务"""
        while True:
            await asyncio.sleep(300)  # 每 10 分钟清理一次
            now = datetime.now(timezone.utc)
            async with self._lock:
                running_jobs = 0
                keys_to_delete = []
                locks_to_delete = []  # 🔥 新增：需要清理的锁
                
                for key, value in list(self.jobs.items()):
                    if value["status"] in ["complete", "failed", "killed"]:
                        if "timestamp" in value and now - value["timestamp"] > JOB_EXPIRATION_TIME:
                            keys_to_delete.append(key)
                            locks_to_delete.append(key)  # 🔥 记录要清理的锁
                    elif value["status"] == "in_progress":
                        if "timestamp" in value and now - value["timestamp"] > JOB_WAITING_TIME:
                            keys_to_delete.append(key)
                            locks_to_delete.append(key)  # 🔥 记录要清理的锁
                            logger.info(f"任务 {key} 执行已超时")
                            job_fail_wrap(job_manager.jobs, key, f"任务 {key} 执行已超时")
                        else:
                            running_jobs += 1

                for key in keys_to_delete:
                    del self.jobs[key]
                    logger.info(f"任务 {key} 已清理")
                
                # 🔥 清理对应的锁（防止内存泄漏）
                async with self._task_locks_lock:
                    for key in locks_to_delete:
                        if key in self._task_locks:
                            del self._task_locks[key]
                            logger.debug(f"任务锁 {key} 已清理")
                    
                    # 🔥 记录锁字典大小（用于监控）
                    if len(self._task_locks) > 100:
                        logger.warning(f"⚠️ Task locks dictionary size: {len(self._task_locks)} (may indicate a leak)")

                logger.info("current time={}, total jobs count={}, running jobs count={}, task_locks count={}".format(
                    datetime.now(),
                    len(self.jobs),
                    running_jobs,
                    len(self._task_locks)
                ))

job_manager = JobManager()