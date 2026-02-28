#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Redis 清理工具

用于手动清理 Redis 中的虚假数据：
1. 清理未完成的任务
2. 修复实例负载计数
3. 重置指定实例的负载

使用示例:
    # 清理所有虚假数据
    python tools/cleanup_redis.py --all
    
    # 只清理任务
    python tools/cleanup_redis.py --tasks
    
    # 只修复负载计数
    python tools/cleanup_redis.py --loads
    
    # 重置指定实例的负载为0
    python tools/cleanup_redis.py --reset-load instance-single
    
    # 查看当前状态
    python tools/cleanup_redis.py --status
"""
import asyncio
import argparse
import sys
from pathlib import Path

# 添加项目根目录到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from kparser.multi_instance.cached_state_manager import get_cached_state_manager
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


async def show_status(state_manager):
    """显示当前 Redis 状态"""
    logger.info("=" * 80)
    logger.info("Redis 状态信息")
    logger.info("=" * 80)
    
    try:
        # 获取所有实例
        instances = await state_manager.get_all_instances()
        logger.info(f"\n活跃实例数: {len(instances)}")
        
        for instance in instances:
            instance_id = instance.get("instance_id", "unknown")
            load = await state_manager.get_instance_load(instance_id)
            max_workers = instance.get("max_workers", 0)
            logger.info(f"  - {instance_id}: {load}/{max_workers} (负载 {load/max_workers*100:.1f}%)")
        
        # 获取任务统计
        stats = await state_manager.get_task_statistics()
        logger.info(f"\n任务统计:")
        logger.info(f"  - 总任务数: {stats.get('total_tasks', 0)}")
        logger.info(f"  - in_progress: {stats.get('status:in_progress', 0)}")
        logger.info(f"  - pending: {stats.get('status:pending', 0)}")
        logger.info(f"  - complete: {stats.get('status:complete', 0)}")
        logger.info(f"  - failed: {stats.get('status:failed', 0)}")
        
        # 缓存统计
        if hasattr(state_manager, 'get_cache_info'):
            cache_info = state_manager.get_cache_info()
            logger.info(f"\n缓存统计:")
            logger.info(f"  - 缓存大小: {cache_info.get('task_cache_size', 0)}")
            logger.info(f"  - 命中率: {cache_info.get('cache_hit_rate', 0):.1%}")
        
        logger.info("=" * 80)
        
    except Exception as e:
        logger.error(f"获取状态失败: {e}", exc_info=True)


async def cleanup_tasks(state_manager):
    """清理未完成的任务"""
    logger.info("\n>>> 开始清理未完成的任务...")
    try:
        cleaned = await state_manager.cleanup_stale_tasks(
            statuses=['in_progress', 'pending'],
            reason="手动清理"
        )
        if cleaned > 0:
            logger.info(f"✅ 成功清理 {cleaned} 个任务")
        else:
            logger.info("✅ 没有需要清理的任务")
        return cleaned
    except Exception as e:
        logger.error(f"❌ 清理任务失败: {e}", exc_info=True)
        return 0


async def cleanup_loads(state_manager):
    """修复实例负载计数"""
    logger.info("\n>>> 开始修复实例负载计数...")
    try:
        cleaned = await state_manager.cleanup_instance_loads()
        if cleaned > 0:
            logger.info(f"✅ 成功修复 {cleaned} 个实例的负载计数")
        else:
            logger.info("✅ 所有实例的负载计数都是正确的")
        return cleaned
    except Exception as e:
        logger.error(f"❌ 修复负载计数失败: {e}", exc_info=True)
        return 0


async def reset_load(state_manager, instance_id: str):
    """重置指定实例的负载为 0"""
    logger.info(f"\n>>> 重置实例 {instance_id} 的负载为 0...")
    try:
        success = await state_manager.reset_instance_load(instance_id)
        if success:
            logger.info(f"✅ 成功重置实例 {instance_id} 的负载")
        else:
            logger.error(f"❌ 重置失败")
        return success
    except Exception as e:
        logger.error(f"❌ 重置负载失败: {e}", exc_info=True)
        return False


async def main():
    parser = argparse.ArgumentParser(
        description="Redis 清理工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
使用示例:
  %(prog)s --all                        清理所有虚假数据
  %(prog)s --tasks                      只清理未完成的任务
  %(prog)s --loads                      只修复负载计数
  %(prog)s --reset-load instance-single 重置指定实例的负载为0
  %(prog)s --status                     查看当前状态
        """
    )
    
    parser.add_argument('--all', action='store_true', help='清理所有虚假数据（任务+负载）')
    parser.add_argument('--tasks', action='store_true', help='只清理未完成的任务')
    parser.add_argument('--loads', action='store_true', help='只修复负载计数')
    parser.add_argument('--reset-load', metavar='INSTANCE_ID', help='重置指定实例的负载为0')
    parser.add_argument('--status', action='store_true', help='显示当前 Redis 状态')
    
    args = parser.parse_args()
    
    # 如果没有指定任何参数，显示帮助
    if not any([args.all, args.tasks, args.loads, args.reset_load, args.status]):
        parser.print_help()
        return 1
    
    # 初始化状态管理器
    state_manager = get_cached_state_manager()
    await state_manager.initialize()
    logger.info("✅ 已连接到 Redis")
    
    try:
        # 显示状态
        if args.status:
            await show_status(state_manager)
        
        # 清理所有
        if args.all:
            logger.info("=" * 80)
            logger.info("开始清理所有虚假数据")
            logger.info("=" * 80)
            
            cleaned_tasks = await cleanup_tasks(state_manager)
            cleaned_loads = await cleanup_loads(state_manager)
            
            logger.info("\n" + "=" * 80)
            logger.info("清理完成")
            logger.info("=" * 80)
            logger.info(f"清理任务数: {cleaned_tasks}")
            logger.info(f"修复负载数: {cleaned_loads}")
            logger.info("=" * 80)
            
            # 显示清理后的状态
            await show_status(state_manager)
        
        # 只清理任务
        elif args.tasks:
            cleaned = await cleanup_tasks(state_manager)
            logger.info(f"\n清理完成: {cleaned} 个任务")
        
        # 只修复负载
        elif args.loads:
            cleaned = await cleanup_loads(state_manager)
            logger.info(f"\n修复完成: {cleaned} 个负载计数")
        
        # 重置指定实例负载
        elif args.reset_load:
            success = await reset_load(state_manager, args.reset_load)
            return 0 if success else 1
        
        return 0
        
    except Exception as e:
        logger.error(f"执行失败: {e}", exc_info=True)
        return 1
    
    finally:
        await state_manager.close()
        logger.info("✅ 已断开 Redis 连接")


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    sys.exit(exit_code)

