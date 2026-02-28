# -*- coding: utf-8 -*-
"""
进度同步管理器（同步版本）
用于在子进程中安全地同步进度到 Redis，避免事件循环问题
"""
import redis
import json
from datetime import datetime, timezone
from typing import List
from kparser.common import config as settings
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class ProgressSyncManager:
    """
    进度同步管理器（同步版本）
    
    设计目标：
    1. 在子进程中安全使用（不依赖事件循环）
    2. 使用同步 Redis 客户端（redis 而非 redis.asyncio）
    3. 延迟初始化 Redis 连接（每个进程独立连接）
    4. 自动重试和错误恢复
    5. 使用连接池管理连接（避免连接耗尽）
    """
    
    _instance = None
    _lock = None  # 类级别的锁，用于保护单例创建
    
    def __init__(self):
        """初始化（延迟连接，实际连接在首次使用时建立）"""
        self._redis_client = None
        self._connection_pool = None  # 🔥 新增：连接池
        self._initialized = False
        self.TASK_PREFIX = "parser:task:"
        self._last_sync_time = {}  # task_id -> timestamp
    
    @classmethod
    def get_instance(cls):
        """获取单例（每个进程独立的单例，线程安全）"""
        if cls._instance is None:
            # 🔥 双重检查锁定模式（线程安全的单例）
            import threading
            if cls._lock is None:
                cls._lock = threading.Lock()
            
            with cls._lock:
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance
    
    def _ensure_connection(self):
        """确保 Redis 连接存在且有效（使用连接池）"""
        if not self._initialized or self._redis_client is None:
            try:
                redis_config = settings.REDIS
                
                # 🔥 创建连接池（每个进程独立，但在进程内复用连接）
                if self._connection_pool is None:
                    self._connection_pool = redis.ConnectionPool(
                        host=redis_config.get('host', 'localhost'),
                        port=redis_config.get('port', 6379),
                        password=redis_config.get('password'),
                        db=redis_config.get('db', 0),
                        decode_responses=True,
                        socket_connect_timeout=redis_config.get('socket_connect_timeout', 5),
                        socket_timeout=redis_config.get('socket_timeout', 5),
                        socket_keepalive=True,
                        max_connections=10,  # 🔥 每个进程最多10个连接
                        health_check_interval=30
                    )
                
                # 🔥 使用连接池创建Redis客户端
                self._redis_client = redis.Redis(connection_pool=self._connection_pool)
                
                # 测试连接
                self._redis_client.ping()
                self._initialized = True
                logger.info(
                    f"ProgressSyncManager Redis connected with connection pool: "
                    f"{redis_config.get('host')}:{redis_config.get('port')} "
                    f"(PID={os.getpid()}, max_connections=10)"
                )
            except Exception as e:
                logger.error(f"Failed to connect to Redis: {e}")
                self._initialized = False
                self._redis_client = None
                raise
    
    def update_progress(self, task_id: str, progress: int, logs: List[str], max_retries: int = 3) -> bool:
        """
        同步更新任务进度到 Redis
        
        Args:
            task_id: 任务ID
            progress: 进度百分比 (0-100)
            logs: 日志列表
            max_retries: 最大重试次数
        
        Returns:
            bool: 是否成功同步
        """
        for attempt in range(max_retries):
            try:
                # 确保连接
                self._ensure_connection()
                
                key = f"{self.TASK_PREFIX}{task_id}"
                
                # 获取现有任务数据
                task_data_str = self._redis_client.get(key)
                if not task_data_str:
                    logger.warning(f"Task {task_id} not found in Redis")
                    return False
                
                task_data = json.loads(task_data_str)
                
                # 更新进度和日志
                task_data["progress"] = progress
                task_data["logs"] = logs
                task_data["updated_at"] = datetime.now(timezone.utc).isoformat()
                
                # 获取 TTL 并写回 Redis
                ttl = self._redis_client.ttl(key)
                if ttl > 0:
                    self._redis_client.setex(
                        key,
                        ttl,
                        json.dumps(task_data)
                    )
                else:
                    # 使用默认 TTL（24 小时）
                    self._redis_client.setex(
                        key,
                        86400,
                        json.dumps(task_data)
                    )
                
                # 记录最后同步时间
                self._last_sync_time[task_id] = datetime.now()
                
                logger.debug(f"✅ Progress synced to Redis: task={task_id}, progress={progress}%")
                return True
                
            except redis.ConnectionError as e:
                # 连接错误，尝试重连
                logger.warning(f"Redis connection error on attempt {attempt + 1}/{max_retries}: {e}")
                self._redis_client = None
                self._initialized = False
                
                if attempt < max_retries - 1:
                    import time
                    time.sleep(0.5 * (attempt + 1))  # 递增延迟
                else:
                    logger.error(f"❌ Failed to sync progress after {max_retries} retries")
                    return False
                    
            except json.JSONDecodeError as e:
                logger.error(f"Invalid JSON in Redis for task {task_id}: {e}")
                return False
                
            except Exception as e:
                logger.error(f"Failed to sync progress to Redis for task {task_id}: {e}")
                return False
        
        return False
    
    def close(self):
        """关闭 Redis 连接和连接池"""
        if self._redis_client:
            try:
                self._redis_client.close()
                logger.debug(f"ProgressSyncManager Redis client closed (PID={os.getpid()})")
            except Exception as e:
                logger.error(f"Error closing Redis client: {e}")
            finally:
                self._redis_client = None
        
        # 🔥 关闭连接池
        if self._connection_pool:
            try:
                self._connection_pool.disconnect()
                logger.debug(f"ProgressSyncManager Redis connection pool closed (PID={os.getpid()})")
            except Exception as e:
                logger.error(f"Error closing Redis connection pool: {e}")
            finally:
                self._connection_pool = None
        
        self._initialized = False
    
    def __del__(self):
        """析构时关闭连接"""
        self.close()


import os  # 用于获取进程 ID

