# -*- coding: utf-8 -*-
"""
带缓存的状态管理器
通过本地缓存大幅减少Redis访问，提升轮询性能
"""
import asyncio
from typing import Dict, Optional, List
from datetime import datetime
from cachetools import TTLCache
# 🔥 移除 threading 导入，改用 asyncio.Lock

from kparser.multi_instance.shared_state_manager import SharedStateManager
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class CachedStateManager:
    """
    带缓存的状态管理器 - 减少Redis访问频率
    
    核心优化：
    1. 本地TTL缓存：减少80-90%的Redis查询
    2. 智能缓存失效：任务状态变化时主动失效缓存
    3. 分层查询：本地缓存 -> Redis -> 降级
    4. 缓存预热：任务创建时预填充缓存
    """
    
    def __init__(self, underlying_manager: SharedStateManager, cache_ttl: int = 2, cache_maxsize: int = 10000):
        """
        初始化缓存管理器
        
        Args:
            underlying_manager: 底层的SharedStateManager
            cache_ttl: 缓存TTL（秒），默认2秒
            cache_maxsize: 缓存最大条目数，默认10000
        """
        self.underlying = underlying_manager
        
        # 任务状态缓存 - 使用TTL缓存自动过期
        self._task_cache = TTLCache(maxsize=cache_maxsize, ttl=cache_ttl)
        # 🔥 修复：使用 asyncio.Lock 替代 threading.Lock（避免阻塞事件循环）
        self._cache_lock = asyncio.Lock()
        
        # 实例信息缓存 - 更长的TTL（30秒）
        self._instance_cache = TTLCache(maxsize=1000, ttl=30)
        
        # 统计信息
        self._cache_hits = 0
        self._cache_misses = 0
        self._cache_invalidations = 0
        
        logger.info(f"CachedStateManager initialized: cache_ttl={cache_ttl}s, maxsize={cache_maxsize}")
    
    async def initialize(self):
        """初始化底层管理器"""
        await self.underlying.initialize()
        logger.info("CachedStateManager underlying manager initialized")
    
    async def close(self):
        """关闭底层管理器"""
        await self.underlying.close()
        # 清空缓存
        async with self._cache_lock:
            self._task_cache.clear()
            self._instance_cache.clear()
        logger.info(f"CachedStateManager closed. Cache stats: hits={self._cache_hits}, misses={self._cache_misses}, invalidations={self._cache_invalidations}")
    
    async def health_check(self) -> bool:
        """健康检查"""
        return await self.underlying.health_check()
    
    # ==================== 实例管理（直接透传） ====================
    
    async def register_instance(self, instance_id: str, instance_info: Dict):
        """注册实例"""
        result = await self.underlying.register_instance(instance_id, instance_info)
        # 预热实例缓存
        self._instance_cache[instance_id] = {**instance_info, "instance_id": instance_id}
        return result
    
    async def unregister_instance(self, instance_id: str):
        """注销实例"""
        result = await self.underlying.unregister_instance(instance_id)
        # 清除缓存
        async with self._cache_lock:
            self._instance_cache.pop(instance_id, None)
        return result
    
    async def update_instance_heartbeat(self, instance_id: str):
        """更新实例心跳"""
        return await self.underlying.update_instance_heartbeat(instance_id)
    
    async def get_instance_info(self, instance_id: str) -> Optional[Dict]:
        """获取实例信息（带缓存）"""
        # 1. 查本地缓存
        if instance_id in self._instance_cache:
            return self._instance_cache[instance_id]
        
        # 2. 查Redis
        info = await self.underlying.get_instance_info(instance_id)
        
        # 3. 写入缓存
        if info:
            self._instance_cache[instance_id] = info
        
        return info
    
    async def get_all_instances(self) -> List[Dict]:
        """获取所有活跃实例"""
        return await self.underlying.get_all_instances()
    
    async def get_instance_load(self, instance_id: str) -> int:
        """获取实例负载（不缓存，需要实时数据）"""
        return await self.underlying.get_instance_load(instance_id)
    
    async def increment_instance_load(self, instance_id: str):
        """增加实例负载"""
        return await self.underlying.increment_instance_load(instance_id)
    
    async def decrement_instance_load(self, instance_id: str):
        """减少实例负载"""
        return await self.underlying.decrement_instance_load(instance_id)
    
    # ==================== 任务管理（核心优化） ====================
    
    async def register_task(self, task_id: str, task_info: Dict):
        """
        注册任务（预热缓存）
        """
        # 1. 写入Redis
        await self.underlying.register_task(task_id, task_info)
        
        # 2. 预热缓存（减少首次查询的延迟）
        async with self._cache_lock:
            self._task_cache[task_id] = {
                **task_info,
                "task_id": task_id,
                "created_at": task_info.get("created_at", datetime.now().isoformat()),
                "progress": task_info.get("progress", 0),
                "logs": task_info.get("logs", [])
            }
        
        logger.debug(f"Task {task_id} registered and cached")
    
    async def update_task_status(self, task_id: str, update_data: Dict):
        """
        更新任务状态（主动失效缓存）
        """
        # 1. 更新Redis
        await self.underlying.update_task_status(task_id, update_data)
        
        # 2. 主动失效缓存（确保下次读取最新数据）
        async with self._cache_lock:
            if task_id in self._task_cache:
                # 更新缓存而不是删除，减少缓存未命中
                cached_data = self._task_cache[task_id]
                cached_data.update(update_data)
                cached_data["updated_at"] = datetime.now().isoformat()
                self._cache_invalidations += 1
                logger.debug(f"Task {task_id} cache updated after status change")
            else:
                # 如果缓存中没有，尝试填充（可能是其他Pod创建的任务）
                task_info = await self.underlying.get_task_status(task_id)
                if task_info:
                    self._task_cache[task_id] = task_info
    
    async def get_task_status(self, task_id: str) -> Optional[Dict]:
        """
        获取任务状态（核心优化：分层缓存）
        
        查询顺序：
        1. 本地缓存（TTL=2秒）
        2. Redis
        3. 返回None
        """
        # 【第一层】本地缓存查询
        async with self._cache_lock:
            if task_id in self._task_cache:
                self._cache_hits += 1
                cached_data = self._task_cache[task_id]
                logger.debug(f"✅ Cache HIT for task {task_id} (hits={self._cache_hits}, misses={self._cache_misses}, hit_rate={self._get_hit_rate():.1%})")
                return cached_data
        
        # 【第二层】Redis查询
        self._cache_misses += 1
        logger.debug(f"❌ Cache MISS for task {task_id} (hits={self._cache_hits}, misses={self._cache_misses}, hit_rate={self._get_hit_rate():.1%})")
        
        task_info = await self.underlying.get_task_status(task_id)
        
        # 【回填缓存】
        if task_info:
            async with self._cache_lock:
                self._task_cache[task_id] = task_info
                logger.debug(f"Task {task_id} cached from Redis")
        
        return task_info
    
    async def get_task_statistics(self) -> Dict:
        """获取任务统计信息（不缓存）"""
        stats = await self.underlying.get_task_statistics()
        
        # 添加缓存统计
        stats["cache_hits"] = self._cache_hits
        stats["cache_misses"] = self._cache_misses
        stats["cache_hit_rate"] = self._get_hit_rate()
        stats["cache_size"] = len(self._task_cache)
        stats["cache_invalidations"] = self._cache_invalidations
        
        return stats
    
    def _get_hit_rate(self) -> float:
        """计算缓存命中率"""
        total = self._cache_hits + self._cache_misses
        if total == 0:
            return 0.0
        return self._cache_hits / total
    
    # ==================== 任务队列（透传） ====================
    
    async def enqueue_task(self, task_id: str, priority: int = 0):
        """将任务加入队列"""
        return await self.underlying.enqueue_task(task_id, priority)
    
    async def dequeue_task(self) -> Optional[str]:
        """从队列中获取任务"""
        return await self.underlying.dequeue_task()
    
    async def get_queue_size(self) -> int:
        """获取队列大小"""
        return await self.underlying.get_queue_size()
    
    # ==================== 实例负载清理（重启时使用） ====================
    
    async def reset_instance_load(self, instance_id: str) -> bool:
        """重置实例负载计数（透传）"""
        return await self.underlying.reset_instance_load(instance_id)
    
    async def cleanup_instance_loads(self) -> int:
        """清理所有实例的虚假负载计数（透传）"""
        cleaned_count = await self.underlying.cleanup_instance_loads()
        if cleaned_count > 0:
            logger.info(f"✅ Fixed {cleaned_count} instance load counters")
        return cleaned_count
    
    async def cleanup_stale_tasks(self, statuses: list = None, reason: str = "服务重启") -> int:
        """清理指定状态的任务（透传）"""
        cleaned_count = await self.underlying.cleanup_stale_tasks(statuses, reason)
        # 清理完成后，清空本地缓存以确保数据一致性
        if cleaned_count > 0:
            await self.clear_cache()
            logger.info(f"Local cache cleared after cleaning {cleaned_count} stale tasks")
        return cleaned_count
    
    # ==================== 缓存管理 ====================
    
    async def clear_cache(self):
        """手动清空缓存（调试用）"""
        async with self._cache_lock:
            task_count = len(self._task_cache)
            instance_count = len(self._instance_cache)
            self._task_cache.clear()
            self._instance_cache.clear()
        logger.info(f"Cache cleared: {task_count} tasks, {instance_count} instances")
    
    async def invalidate_task_cache(self, task_id: str):
        """手动失效特定任务的缓存"""
        async with self._cache_lock:
            removed = self._task_cache.pop(task_id, None)
            if removed:
                self._cache_invalidations += 1
                logger.debug(f"Task {task_id} cache invalidated")
    
    async def get_cache_info(self) -> Dict:
        """获取缓存详细信息"""
        async with self._cache_lock:
            return {
                "task_cache_size": len(self._task_cache),
                "instance_cache_size": len(self._instance_cache),
                "cache_hits": self._cache_hits,
                "cache_misses": self._cache_misses,
                "cache_hit_rate": self._get_hit_rate(),
                "cache_invalidations": self._cache_invalidations,
                "task_cache_maxsize": self._task_cache.maxsize,
                "task_cache_ttl": self._task_cache.ttl
            }


# 全局单例（带缓存）
_cached_state_manager: Optional[CachedStateManager] = None


def get_cached_state_manager() -> CachedStateManager:
    """
    获取带缓存的状态管理器单例
    
    注意：这将替代原有的 get_shared_state_manager()
    """
    global _cached_state_manager
    
    if _cached_state_manager is None:
        from kparser.multi_instance.shared_state_manager import get_shared_state_manager
        underlying = get_shared_state_manager()
        _cached_state_manager = CachedStateManager(underlying)
    
    return _cached_state_manager



