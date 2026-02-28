# -*- coding: utf-8 -*-
"""
负载均衡器
实现多实例间的任务分发策略
"""
import random
from typing import Optional, List, Dict
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class LoadBalancer:
    """负载均衡器 - 实现多种负载均衡策略"""
    
    def __init__(self):
        self.state_manager = None
        self._initialized = False
    
    async def initialize(self, state_manager):
        """初始化负载均衡器"""
        self.state_manager = state_manager
        self._initialized = True
        logger.info("LoadBalancer initialized")
    
    async def select_instance(self, strategy: str = "least_load") -> Optional[str]:
        """
        选择一个实例处理任务
        
        Args:
            strategy: 负载均衡策略
                - least_load: 最少负载优先
                - round_robin: 轮询
                - random: 随机
        
        Returns:
            实例ID，如果没有可用实例则返回 None
        """
        if not self._initialized:
            logger.error("LoadBalancer not initialized")
            return None
        
        instances = await self.state_manager.get_all_instances()
        
        if not instances:
            logger.warning("No available instances found")
            return None
        
        if strategy == "least_load":
            return await self._least_load_strategy(instances)
        elif strategy == "round_robin":
            return await self._round_robin_strategy(instances)
        elif strategy == "random":
            return await self._random_strategy(instances)
        else:
            logger.warning(f"Unknown strategy: {strategy}, using least_load")
            return await self._least_load_strategy(instances)
    
    async def _least_load_strategy(self, instances: List[Dict]) -> Optional[str]:
        """最少负载策略"""
        if not instances:
            return None
        
        # 获取每个实例的负载
        instance_loads = []
        for instance in instances:
            instance_id = instance["instance_id"]
            load = await self.state_manager.get_instance_load(instance_id)
            max_workers = instance.get("max_workers", 1)
            
            # 计算负载率
            load_ratio = load / max_workers if max_workers > 0 else 1.0
            
            instance_loads.append({
                "instance_id": instance_id,
                "load": load,
                "max_workers": max_workers,
                "load_ratio": load_ratio
            })
        
        # 选择负载最低的实例
        selected = min(instance_loads, key=lambda x: x["load_ratio"])
        
        logger.debug(f"Selected instance {selected['instance_id']} with load {selected['load']}/{selected['max_workers']}")
        
        return selected["instance_id"]
    
    async def _round_robin_strategy(self, instances: List[Dict]) -> Optional[str]:
        """轮询策略"""
        if not instances:
            return None
        
        # 简单实现：使用时间戳取模
        import time
        index = int(time.time()) % len(instances)
        selected = instances[index]["instance_id"]
        
        logger.debug(f"Selected instance {selected} (round robin)")
        
        return selected
    
    async def _random_strategy(self, instances: List[Dict]) -> Optional[str]:
        """随机策略"""
        if not instances:
            return None
        
        selected = random.choice(instances)["instance_id"]
        
        logger.debug(f"Selected instance {selected} (random)")
        
        return selected
    
    async def check_instance_availability(self, instance_id: str) -> bool:
        """检查实例是否可用"""
        instance_info = await self.state_manager.get_instance_info(instance_id)
        
        if not instance_info:
            return False
        
        # 检查实例状态
        if instance_info.get("status") != "running":
            return False
        
        # 检查实例负载
        load = await self.state_manager.get_instance_load(instance_id)
        max_workers = instance_info.get("max_workers", 1)
        
        return load < max_workers
    
    async def get_instance_metrics(self) -> Dict:
        """获取所有实例的负载指标"""
        instances = await self.state_manager.get_all_instances()
        
        metrics = {
            "total_instances": len(instances),
            "instances": []
        }
        
        for instance in instances:
            instance_id = instance["instance_id"]
            load = await self.state_manager.get_instance_load(instance_id)
            max_workers = instance.get("max_workers", 1)
            
            metrics["instances"].append({
                "instance_id": instance_id,
                "port": instance.get("port"),
                "load": load,
                "max_workers": max_workers,
                "load_percentage": f"{(load / max_workers * 100):.1f}%" if max_workers > 0 else "N/A",
                "status": instance.get("status"),
                "available_capacity": max(0, max_workers - load)
            })
        
        # 计算总体指标
        total_capacity = sum(inst.get("max_workers", 0) for inst in instances)
        total_load = sum(m["load"] for m in metrics["instances"])
        
        metrics["total_capacity"] = total_capacity
        metrics["total_load"] = total_load
        metrics["available_capacity"] = total_capacity - total_load
        metrics["overall_load_percentage"] = f"{(total_load / total_capacity * 100):.1f}%" if total_capacity > 0 else "N/A"
        
        return metrics


# 全局单例
_load_balancer: Optional[LoadBalancer] = None


def get_load_balancer() -> LoadBalancer:
    """获取负载均衡器单例"""
    global _load_balancer
    
    if _load_balancer is None:
        _load_balancer = LoadBalancer()
    
    return _load_balancer

