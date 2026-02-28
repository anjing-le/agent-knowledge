# -*- coding: utf-8 -*-
"""
多实例管理器
负责启动和管理多个解析服务实例
"""
import os
import sys
import time
import signal
import asyncio
import subprocess
from typing import List, Dict, Optional
from dataclasses import dataclass

from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


@dataclass
class InstanceConfig:
    """实例配置"""
    instance_id: str
    port: int
    max_concurrent: int
    worker_count: int


class MultiInstanceManager:
    """多实例管理器 - 启动和管理多个服务实例"""
    
    def __init__(self):
        self.instances: List[Dict] = []
        self.processes: List[subprocess.Popen] = []
        self._shutdown_event = asyncio.Event()
        
        # 从环境变量读取配置
        self.instance_count = int(os.environ.get("MULTI_INSTANCE_COUNT", "3"))
        self.base_port = int(os.environ.get("MULTI_INSTANCE_BASE_PORT", "7099"))
        self.max_concurrent = int(os.environ.get("SERVICE_MAX_JOB_NUMBER", "32"))
        self.worker_count = int(os.environ.get("MULTI_INSTANCE_WORKER_COUNT", "2"))
    
    def create_instance_config(self, index: int) -> InstanceConfig:
        """创建实例配置"""
        instance_id = f"instance-{index:03d}"
        port = self.base_port + index
        
        return InstanceConfig(
            instance_id=instance_id,
            port=port,
            max_concurrent=self.max_concurrent,
            worker_count=self.worker_count
        )
    
    def start_instances(self) -> List[InstanceConfig]:
        """启动所有实例"""
        logger.info(f"Starting {self.instance_count} instances...")
        
        instance_configs = []
        
        for i in range(self.instance_count):
            config = self.create_instance_config(i)
            instance_configs.append(config)
            
            # 构建环境变量
            env = os.environ.copy()
            env["INSTANCE_ID"] = config.instance_id
            env["INSTANCE_PORT"] = str(config.port)
            env["SERVICE_MAX_JOB_NUMBER"] = str(config.max_concurrent)
            
            # 使用 subprocess 启动独立的 uvicorn 进程
            cmd = [
                sys.executable, "-m", "uvicorn",
                "kparser.multi_instance_app:app",
                "--host", "0.0.0.0",
                "--port", str(config.port),
                "--log-level", "info"
            ]
            
            # 不重定向输出，让日志直接打印到终端
            process = subprocess.Popen(
                cmd,
                env=env
            )
            
            self.processes.append(process)
            self.instances.append({
                "config": config,
                "process": process,
                "pid": process.pid
            })
            
            logger.info(f"Started instance {config.instance_id} on port {config.port} (PID: {process.pid})")
            
            # 稍微延迟，避免端口冲突
            time.sleep(1)
        
        logger.info(f"All {self.instance_count} instances started successfully")
        return instance_configs
    
    
    async def initialize(self):
        """初始化管理器"""
        logger.info("Initializing MultiInstanceManager...")
        
        # 注册信号处理器
        signal.signal(signal.SIGTERM, self._signal_handler)
        signal.signal(signal.SIGINT, self._signal_handler)
        
        logger.info("MultiInstanceManager initialized")
    
    def _signal_handler(self, signum, frame):
        """信号处理器"""
        logger.info(f"Received signal {signum}, initiating shutdown...")
        # 创建一个新的事件循环来设置事件
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                loop.call_soon_threadsafe(self._shutdown_event.set)
            else:
                asyncio.run(self._set_shutdown_event())
        except:
            # 如果没有事件循环，直接终止进程
            self.shutdown_sync()
    
    async def _set_shutdown_event(self):
        """辅助方法：设置关闭事件"""
        self._shutdown_event.set()
    
    def shutdown_sync(self):
        """同步关闭所有实例"""
        logger.info("Shutting down all instances...")
        
        for instance in self.instances:
            process = instance["process"]
            config = instance["config"]
            
            if process.poll() is None:  # 进程还在运行
                logger.info(f"Terminating instance {config.instance_id} (PID: {process.pid})")
                process.terminate()
        
        # 等待进程结束
        for instance in self.instances:
            process = instance["process"]
            config = instance["config"]
            
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                logger.warning(f"Force killing instance {config.instance_id} (PID: {process.pid})")
                process.kill()
                process.wait()
        
        logger.info("All instances shut down")
    
    async def shutdown(self):
        """异步关闭所有实例"""
        self.shutdown_sync()
    
    async def monitor_instances(self):
        """监控实例状态"""
        while not self._shutdown_event.is_set():
            await asyncio.sleep(30)
            
            for instance in self.instances:
                process = instance["process"]
                config = instance["config"]
                
                if process.poll() is not None:  # 进程已退出
                    logger.error(f"Instance {config.instance_id} (PID: {process.pid}) has died")
                    
                    # 可选：自动重启实例
                    # self._restart_instance(instance)
        
        logger.info("Instance monitoring stopped")
    
    def _restart_instance(self, instance: Dict):
        """重启单个实例（可选功能）"""
        config = instance["config"]
        logger.info(f"Restarting instance {config.instance_id}...")
        
        # 构建环境变量
        env = os.environ.copy()
        env["INSTANCE_ID"] = config.instance_id
        env["INSTANCE_PORT"] = str(config.port)
        env["SERVICE_MAX_JOB_NUMBER"] = str(config.max_concurrent)
        
        # 启动新进程
        cmd = [
            sys.executable, "-m", "uvicorn",
            "kparser.multi_instance_app:app",
            "--host", "0.0.0.0",
            "--port", str(config.port),
            "--log-level", "info"
        ]
        
        process = subprocess.Popen(
            cmd,
            env=env
        )
        
        # 更新实例信息
        instance["process"] = process
        instance["pid"] = process.pid
        
        logger.info(f"Instance {config.instance_id} restarted (new PID: {process.pid})")
    
    def get_instance_status(self) -> List[Dict]:
        """获取所有实例的状态"""
        status = []
        
        for instance in self.instances:
            process = instance["process"]
            config = instance["config"]
            
            status.append({
                "instance_id": config.instance_id,
                "port": config.port,
                "pid": process.pid,
                "alive": process.poll() is None,
                "exitcode": process.poll()
            })
        
        return status


async def main():
    """主函数 - 启动多实例管理器"""
    manager = MultiInstanceManager()
    
    try:
        await manager.initialize()
        instance_configs = manager.start_instances()
        
        print("\n" + "="*60)
        print("🚀 Multi-Instance Service Started Successfully!")
        print("="*60)
        print(f"Total Instances: {len(instance_configs)}")
        print(f"Base Port: {manager.base_port}")
        print(f"Max Concurrent per Instance: {manager.max_concurrent}")
        print(f"Total System Capacity: {len(instance_configs) * manager.max_concurrent} tasks")
        print("\nInstance Endpoints:")
        
        for config in instance_configs:
            print(f"  • {config.instance_id}: http://localhost:{config.port}")
            print(f"    - API Docs: http://localhost:{config.port}/docs")
            print(f"    - Health: http://localhost:{config.port}/health")
        
        print("\n" + "="*60)
        print("Press Ctrl+C to stop all instances")
        print("="*60 + "\n")
        
        # 启动监控任务
        monitor_task = asyncio.create_task(manager.monitor_instances())
        
        # 等待关闭信号
        while not manager._shutdown_event.is_set():
            await asyncio.sleep(1)
        
        # 取消监控任务
        monitor_task.cancel()
        try:
            await monitor_task
        except asyncio.CancelledError:
            pass
        
    except KeyboardInterrupt:
        logger.info("Received keyboard interrupt")
    except Exception as e:
        logger.error(f"Manager error: {e}")
    finally:
        await manager.shutdown()


if __name__ == "__main__":
    # 运行主函数
    asyncio.run(main())

