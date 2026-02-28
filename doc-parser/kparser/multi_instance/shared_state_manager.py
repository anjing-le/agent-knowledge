# -*- coding: utf-8 -*-
"""
共享状态管理器
使用 Redis 实现跨实例的任务状态管理和同步
"""
import json
import asyncio
from typing import Dict, Optional, List
from datetime import datetime, timedelta
import redis.asyncio as aioredis

from kparser.common import config as settings
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class SharedStateManager:
    """共享状态管理器 - 使用 Redis 实现跨实例状态同步"""
    
    def __init__(self):
        self.redis_client: Optional[aioredis.Redis] = None
        self._initialized = False
        
        # Redis 键前缀
        self.INSTANCE_PREFIX = "parser:instance:"
        self.TASK_PREFIX = "parser:task:"
        self.TASK_QUEUE_PREFIX = "parser:queue:"
        self.STATS_PREFIX = "parser:stats:"
        
        # 超时设置
        self.INSTANCE_TTL = 120  # 实例心跳超时时间（秒）
        self.TASK_TTL = 86400  # 任务信息保留时间（24小时）
    
    async def initialize(self):
        """初始化 Redis 连接（优化：增强连接池配置）"""
        if self._initialized:
            return
        
        try:
            redis_config = settings.REDIS
            
            # 🔥 优化连接池参数
            self.redis_client = await aioredis.from_url(
                f"redis://{redis_config.get('host', 'localhost')}:{redis_config.get('port', 6379)}",
                password=redis_config.get('password'),
                db=redis_config.get('db', 0),
                encoding="utf-8",
                decode_responses=True,
                
                # ⬆️ 连接池大小（根据实例worker数量调整）
                max_connections=redis_config.get('max_connections', 100),
                
                # ⬆️ 超时配置（避免频繁超时）
                socket_connect_timeout=redis_config.get('socket_connect_timeout', 10),
                socket_timeout=redis_config.get('socket_timeout', 10),
                socket_keepalive=True,
                
                # ✅ 自动重试
                retry_on_timeout=True,
                retry_on_error=[ConnectionError, TimeoutError],
                
                # ✅ 健康检查
                health_check_interval=30
            )
            
            # 测试连接
            await self.redis_client.ping()
            self._initialized = True
            logger.info(
                f"SharedStateManager initialized successfully: "
                f"host={redis_config.get('host')}, "
                f"max_connections={redis_config.get('max_connections', 100)}"
            )
            
        except Exception as e:
            logger.error(f"Failed to initialize Redis connection: {e}")
            raise
    
    async def close(self):
        """关闭 Redis 连接"""
        if self.redis_client:
            await self.redis_client.close()
            self._initialized = False
            logger.info("SharedStateManager closed")
    
    async def health_check(self) -> bool:
        """健康检查"""
        try:
            if not self.redis_client:
                return False
            await self.redis_client.ping()
            return True
        except Exception as e:
            logger.error(f"Health check failed: {e}")
            return False
    
    # ==================== 实例管理 ====================
    
    async def register_instance(self, instance_id: str, instance_info: Dict):
        """注册实例到集群"""
        try:
            key = f"{self.INSTANCE_PREFIX}{instance_id}"
            instance_data = {
                **instance_info,
                "instance_id": instance_id,
                "registered_at": datetime.now().isoformat(),
                "last_heartbeat": datetime.now().isoformat()
            }
            
            await self.redis_client.setex(
                key,
                self.INSTANCE_TTL,
                json.dumps(instance_data)
            )
            
            # 添加到实例集合
            await self.redis_client.sadd("parser:instances", instance_id)
            
            logger.info(f"Instance {instance_id} registered")
            
        except Exception as e:
            logger.error(f"Failed to register instance {instance_id}: {e}")
            raise
    
    async def unregister_instance(self, instance_id: str):
        """注销实例"""
        try:
            key = f"{self.INSTANCE_PREFIX}{instance_id}"
            await self.redis_client.delete(key)
            await self.redis_client.srem("parser:instances", instance_id)
            
            logger.info(f"Instance {instance_id} unregistered")
            
        except Exception as e:
            logger.error(f"Failed to unregister instance {instance_id}: {e}")
    
    async def update_instance_heartbeat(self, instance_id: str):
        """更新实例心跳"""
        try:
            key = f"{self.INSTANCE_PREFIX}{instance_id}"
            instance_data_str = await self.redis_client.get(key)
            
            if instance_data_str:
                instance_data = json.loads(instance_data_str)
                instance_data["last_heartbeat"] = datetime.now().isoformat()
                
                await self.redis_client.setex(
                    key,
                    self.INSTANCE_TTL,
                    json.dumps(instance_data)
                )
            
        except Exception as e:
            logger.error(f"Failed to update heartbeat for instance {instance_id}: {e}")
    
    async def get_instance_info(self, instance_id: str) -> Optional[Dict]:
        """获取实例信息"""
        try:
            key = f"{self.INSTANCE_PREFIX}{instance_id}"
            instance_data_str = await self.redis_client.get(key)
            
            if instance_data_str:
                return json.loads(instance_data_str)
            return None
            
        except Exception as e:
            logger.error(f"Failed to get instance info for {instance_id}: {e}")
            return None
    
    async def get_all_instances(self) -> List[Dict]:
        """获取所有活跃实例"""
        try:
            instance_ids = await self.redis_client.smembers("parser:instances")
            instances = []
            
            for instance_id in instance_ids:
                instance_info = await self.get_instance_info(instance_id)
                if instance_info:
                    instances.append(instance_info)
            
            return instances
            
        except Exception as e:
            logger.error(f"Failed to get all instances: {e}")
            return []
    
    async def get_instance_load(self, instance_id: str) -> int:
        """获取实例当前负载（正在处理的任务数）"""
        try:
            # 从 Redis 获取该实例的任务计数
            key = f"{self.STATS_PREFIX}{instance_id}:load"
            load = await self.redis_client.get(key)
            return int(load) if load else 0
            
        except Exception as e:
            logger.error(f"Failed to get instance load for {instance_id}: {e}")
            return 0
    
    async def increment_instance_load(self, instance_id: str):
        """增加实例负载"""
        try:
            key = f"{self.STATS_PREFIX}{instance_id}:load"
            await self.redis_client.incr(key)
            await self.redis_client.expire(key, 86400)  # 24小时过期
            
        except Exception as e:
            logger.error(f"Failed to increment instance load for {instance_id}: {e}")
    
    async def decrement_instance_load(self, instance_id: str):
        """减少实例负载"""
        try:
            key = f"{self.STATS_PREFIX}{instance_id}:load"
            current = await self.redis_client.get(key)
            
            if current and int(current) > 0:
                await self.redis_client.decr(key)
            
        except Exception as e:
            logger.error(f"Failed to decrement instance load for {instance_id}: {e}")
    
    # ==================== 任务管理 ====================
    
    async def register_task(self, task_id: str, task_info: Dict):
        """注册任务（优化：使用Hash结构）"""
        try:
            key = f"{self.TASK_PREFIX}{task_id}"
            task_data = {
                **task_info,
                "task_id": task_id,
                "created_at": task_info.get("created_at", datetime.now().isoformat()),
                "progress": str(task_info.get("progress", 0)),
                "logs": json.dumps(task_info.get("logs", []))  # 只有数组需要序列化
            }
            
            # 🔥 使用 Hash 结构替代 JSON 字符串
            # 优势：支持部分字段更新，减少序列化开销
            await self.redis_client.hset(key, mapping=task_data)
            await self.redis_client.expire(key, self.TASK_TTL)
            
            # 增加实例负载
            if "instance_id" in task_info:
                await self.increment_instance_load(task_info["instance_id"])
            
            # 更新统计
            await self._update_task_stats("total_tasks", 1)
            await self._update_task_stats(f"status:{task_info.get('status', 'unknown')}", 1)
            
            logger.debug(f"Task {task_id} registered (Hash structure)")
            
        except Exception as e:
            logger.error(f"Failed to register task {task_id}: {e}")
            raise
    
    async def update_task_status(self, task_id: str, update_data: Dict):
        """更新任务状态（优化：使用Hash部分字段更新）"""
        try:
            key = f"{self.TASK_PREFIX}{task_id}"
            
            # 🔥 使用 HGET 获取旧状态（只读取需要的字段，而不是整个对象）
            old_status = await self.redis_client.hget(key, "status")
            
            # 准备更新数据（处理数据类型）
            hash_update = {}
            for k, v in update_data.items():
                if k == "logs" and isinstance(v, list):
                    # 数组需要序列化
                    hash_update[k] = json.dumps(v)
                elif k == "progress":
                    # 进度转为字符串
                    hash_update[k] = str(v)
                elif isinstance(v, dict):
                    # 字典需要序列化
                    hash_update[k] = json.dumps(v)
                else:
                    hash_update[k] = str(v) if v is not None else ""
            
            hash_update["updated_at"] = datetime.now().isoformat()
            
            # 🔥 使用 HSET 部分更新（避免读取整个对象）
            # 优势：原子操作，减少网络IO和序列化开销
            if hash_update:
                await self.redis_client.hset(key, mapping=hash_update)
                # 刷新TTL
                await self.redis_client.expire(key, self.TASK_TTL)
            
            # 更新统计
            new_status = update_data.get("status")
            if new_status and new_status != old_status:
                if old_status:
                    await self._update_task_stats(f"status:{old_status}", -1)
                await self._update_task_stats(f"status:{new_status}", 1)
                
                # 如果任务完成或失败，减少实例负载
                if new_status in ["complete", "failed", "killed"]:
                    instance_id = await self.redis_client.hget(key, "instance_id")
                    if instance_id:
                        await self.decrement_instance_load(instance_id)
            
            logger.debug(f"Task {task_id} status updated to {new_status} (Hash partial update)")
            
        except Exception as e:
            logger.error(f"Failed to update task status for {task_id}: {e}")
    
    async def get_task_status(self, task_id: str) -> Optional[Dict]:
        """获取任务状态（优化：从Hash结构读取）"""
        try:
            key = f"{self.TASK_PREFIX}{task_id}"
            
            # 🔥 使用 HGETALL 获取Hash所有字段
            # 优势：一次性获取，比JSON反序列化快
            task_data = await self.redis_client.hgetall(key)
            
            if not task_data:
                return None
            
            # 转换数据类型（Redis Hash所有值都是字符串）
            result = {}
            for k, v in task_data.items():
                if k == "logs":
                    # 反序列化日志数组
                    try:
                        result[k] = json.loads(v) if v else []
                    except json.JSONDecodeError:
                        result[k] = []
                elif k == "progress":
                    # 转为整数
                    try:
                        result[k] = int(v)
                    except (ValueError, TypeError):
                        result[k] = 0
                elif k in ["result", "error"] and v:
                    # 尝试反序列化可能的JSON字段
                    try:
                        result[k] = json.loads(v)
                    except json.JSONDecodeError:
                        result[k] = v
                else:
                    result[k] = v
            
            return result
            
        except Exception as e:
            logger.error(f"Failed to get task status for {task_id}: {e}")
            return None
    
    async def get_task_statistics(self) -> Dict:
        """获取任务统计信息"""
        try:
            stats = {}
            
            # 获取所有统计键
            stat_keys = await self.redis_client.keys(f"{self.STATS_PREFIX}*")
            
            for key in stat_keys:
                value = await self.redis_client.get(key)
                stat_name = key.replace(self.STATS_PREFIX, "")
                stats[stat_name] = int(value) if value else 0
            
            # 获取实例信息
            instances = await self.get_all_instances()
            stats["active_instances"] = len(instances)
            
            return stats
            
        except Exception as e:
            logger.error(f"Failed to get task statistics: {e}")
            return {}
    
    async def _update_task_stats(self, stat_name: str, increment: int = 1):
        """更新任务统计"""
        try:
            key = f"{self.STATS_PREFIX}{stat_name}"
            if increment > 0:
                await self.redis_client.incrby(key, increment)
            elif increment < 0:
                current = await self.redis_client.get(key)
                if current and int(current) > 0:
                    await self.redis_client.decrby(key, abs(increment))
            
            await self.redis_client.expire(key, 86400)  # 24小时过期
            
        except Exception as e:
            logger.error(f"Failed to update task stats for {stat_name}: {e}")
    
    # ==================== 任务队列（可选） ====================
    
    async def enqueue_task(self, task_id: str, priority: int = 0):
        """将任务加入队列"""
        try:
            queue_key = f"{self.TASK_QUEUE_PREFIX}pending"
            await self.redis_client.zadd(queue_key, {task_id: priority})
            logger.debug(f"Task {task_id} enqueued with priority {priority}")
            
        except Exception as e:
            logger.error(f"Failed to enqueue task {task_id}: {e}")
    
    async def dequeue_task(self) -> Optional[str]:
        """从队列中获取任务"""
        try:
            queue_key = f"{self.TASK_QUEUE_PREFIX}pending"
            # 使用 ZPOPMIN 获取优先级最高的任务
            result = await self.redis_client.zpopmin(queue_key, 1)
            
            if result:
                task_id, _ = result[0]
                return task_id
            return None
            
        except Exception as e:
            logger.error(f"Failed to dequeue task: {e}")
            return None
    
    async def get_queue_size(self) -> int:
        """获取队列大小"""
        try:
            queue_key = f"{self.TASK_QUEUE_PREFIX}pending"
            return await self.redis_client.zcard(queue_key)
            
        except Exception as e:
            logger.error(f"Failed to get queue size: {e}")
            return 0
    
    # ==================== 实例负载清理（重启时使用） ====================
    
    async def reset_instance_load(self, instance_id: str) -> bool:
        """
        重置实例负载计数为 0
        
        Args:
            instance_id: 实例ID
            
        Returns:
            是否成功
        """
        try:
            key = f"{self.STATS_PREFIX}{instance_id}:load"
            await self.redis_client.set(key, 0)
            await self.redis_client.expire(key, 86400)  # 24小时过期
            logger.info(f"✅ Reset instance load for {instance_id} to 0")
            return True
        except Exception as e:
            logger.error(f"Failed to reset instance load for {instance_id}: {e}")
            return False
    
    async def cleanup_instance_loads(self) -> int:
        """
        清理所有实例的虚假负载计数
        遍历所有实例，将其负载重置为实际正在运行的任务数
        
        Returns:
            清理的实例数量
        """
        cleaned_count = 0
        
        try:
            # 获取所有实例的负载键
            load_keys = await self.redis_client.keys(f"{self.STATS_PREFIX}*:load")
            
            for key in load_keys:
                try:
                    # 提取实例ID
                    instance_id = key.replace(f"{self.STATS_PREFIX}", "").replace(":load", "")
                    
                    # 获取该实例实际的运行中任务数（从任务列表中统计）
                    actual_load = await self._count_instance_running_tasks(instance_id)
                    
                    # 获取当前记录的负载
                    current_load_str = await self.redis_client.get(key)
                    current_load = int(current_load_str) if current_load_str else 0
                    
                    if current_load != actual_load:
                        # 更新为实际负载
                        await self.redis_client.set(key, actual_load)
                        await self.redis_client.expire(key, 86400)
                        logger.info(f"✅ Fixed load for {instance_id}: {current_load} -> {actual_load}")
                        cleaned_count += 1
                    
                except Exception as e:
                    logger.error(f"Failed to cleanup load for key {key}: {e}")
                    continue
            
            if cleaned_count > 0:
                logger.warning(f"⚠️ Fixed {cleaned_count} instance load counters")
            else:
                logger.info(f"✅ All instance load counters are correct")
            
            return cleaned_count
            
        except Exception as e:
            logger.error(f"Failed to cleanup instance loads: {e}")
            return cleaned_count
    
    async def _count_instance_running_tasks(self, instance_id: str) -> int:
        """
        统计指定实例实际运行中的任务数（status=in_progress）
        
        Args:
            instance_id: 实例ID
            
        Returns:
            运行中的任务数
        """
        try:
            count = 0
            cursor = 0
            pattern = f"{self.TASK_PREFIX}*"
            
            while True:
                cursor, keys = await self.redis_client.scan(cursor, match=pattern, count=100)
                
                for key in keys:
                    try:
                        # 检查键类型
                        key_type = await self.redis_client.type(key)
                        
                        task_instance_id = None
                        task_status = None
                        
                        if key_type == "hash":
                            # 新格式：Hash 结构
                            task_instance_id = await self.redis_client.hget(key, "instance_id")
                            task_status = await self.redis_client.hget(key, "status")
                        elif key_type == "string":
                            # 旧格式：JSON 字符串
                            task_data_str = await self.redis_client.get(key)
                            if task_data_str:
                                try:
                                    task_data = json.loads(task_data_str)
                                    task_instance_id = task_data.get("instance_id")
                                    task_status = task_data.get("status")
                                except json.JSONDecodeError:
                                    continue
                        
                        # 统计该实例正在运行的任务
                        if task_instance_id == instance_id and task_status == "in_progress":
                            count += 1
                    
                    except Exception:
                        continue
                
                if cursor == 0:
                    break
            
            return count
            
        except Exception as e:
            logger.error(f"Failed to count running tasks for {instance_id}: {e}")
            return 0
    
    async def cleanup_stale_tasks(self, statuses: List[str] = None, reason: str = "服务重启") -> int:
        """
        清理指定状态的任务，将其标记为失败
        
        Args:
            statuses: 要清理的状态列表，默认为 ['in_progress', 'pending']
            reason: 失败原因
            
        Returns:
            清理的任务数量
        """
        if statuses is None:
            statuses = ['in_progress', 'pending']
        
        cleaned_count = 0
        
        try:
            cursor = 0
            pattern = f"{self.TASK_PREFIX}*"
            
            while True:
                cursor, keys = await self.redis_client.scan(cursor, match=pattern, count=100)
                
                for key in keys:
                    try:
                        # 检查键类型
                        key_type = await self.redis_client.type(key)
                        
                        task_status = None
                        task_id = key.replace(self.TASK_PREFIX, "")
                        
                        # 读取任务状态
                        if key_type == "hash":
                            task_status = await self.redis_client.hget(key, "status")
                        elif key_type == "string":
                            task_data_str = await self.redis_client.get(key)
                            if task_data_str:
                                try:
                                    task_data = json.loads(task_data_str)
                                    task_status = task_data.get("status")
                                except json.JSONDecodeError:
                                    continue
                        
                        # 如果状态匹配，则清理
                        if task_status in statuses:
                            if key_type == "hash":
                                # Hash 格式更新
                                await self.redis_client.hset(key, mapping={
                                    "status": "failed",
                                    "error": f"任务被清理: {reason}",
                                    "updated_at": datetime.now().isoformat()
                                })
                            elif key_type == "string":
                                # JSON 格式更新
                                task_data_str = await self.redis_client.get(key)
                                if task_data_str:
                                    task_data = json.loads(task_data_str)
                                    task_data["status"] = "failed"
                                    task_data["error"] = f"任务被清理: {reason}"
                                    task_data["updated_at"] = datetime.now().isoformat()
                                    await self.redis_client.setex(
                                        key,
                                        self.TASK_TTL,
                                        json.dumps(task_data)
                                    )
                            
                            cleaned_count += 1
                            logger.info(f"Cleaned stale task: {task_id} (was '{task_status}')")
                    
                    except Exception as e:
                        logger.error(f"Failed to clean task {key}: {e}")
                        continue
                
                if cursor == 0:
                    break
            
            if cleaned_count > 0:
                logger.warning(f"⚠️ Cleaned {cleaned_count} stale tasks (reason: {reason})")
            else:
                logger.info(f"✅ No stale tasks found")
            
            return cleaned_count
            
        except Exception as e:
            logger.error(f"Failed to cleanup stale tasks: {e}")
            return cleaned_count


# 全局单例
_shared_state_manager: Optional[SharedStateManager] = None


def get_shared_state_manager() -> SharedStateManager:
    """获取共享状态管理器单例"""
    global _shared_state_manager
    
    if _shared_state_manager is None:
        _shared_state_manager = SharedStateManager()
    
    return _shared_state_manager

