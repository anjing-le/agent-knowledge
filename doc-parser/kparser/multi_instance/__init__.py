# -*- coding: utf-8 -*-
"""
多实例管理模块
提供跨实例的任务分发、状态管理和负载均衡
"""

from .shared_state_manager import get_shared_state_manager
from .load_balancer import get_load_balancer
from .instance_manager import MultiInstanceManager

__all__ = [
    "get_shared_state_manager",
    "get_load_balancer",
    "MultiInstanceManager"
]

