"""
RabbitMQ 消息生产者
用于发送任务执行结果到 taskActionQueue
"""
import json
import time
from typing import Dict, Any
import pika
from kparser.common.log_utils import get_logger
from kparser.rabbitmq_queue.connection import RabbitMQConnection
from kparser.common import config as settings

logger = get_logger(__name__)


class TaskResultProducer:
    """任务结果生产者 - 发送任务执行结果到 taskActionQueue"""
    
    def __init__(self):
        self.connection_manager = RabbitMQConnection()
        self.config = settings.RABBITMQ['result_queue']
        self._ensure_connection()
    
    def _ensure_connection(self):
        """确保连接可用"""
        if not self.connection_manager.is_connected():
            if not self.connection_manager.connect():
                raise RuntimeError("Failed to connect to RabbitMQ")
            
            # 声明交换机和队列
            self.connection_manager.declare_exchange(
                self.config['exchange'],
                exchange_type='topic',
                durable=True
            )
            self.connection_manager.declare_queue(
                self.config['queue'],
                durable=True
            )
            self.connection_manager.bind_queue(
                self.config['queue'],
                self.config['exchange'],
                self.config['routing_key']
            )
    
    def send_task_update(self, message: Dict[str, Any]) -> bool:
        """
        发送任务状态更新消息
        
        消息格式参考 MESSAGE_QUEUE_FORMATS.md 中的 TaskActionMessage
        
        Args:
            message: 包含以下字段的字典
                - taskId: 任务ID
                - status: 任务状态 (PENDING/PROCESSING/SUCCESS/FAILED/CANCELED)
                - timestamp: 时间戳（毫秒）
                - progress: 进度 (0-100)
                - result: 任务结果（成功时）
                - error: 错误信息（失败时）
                - metadata: 附加元数据
        
        Returns:
            bool: 是否发送成功
        """
        try:
            # 确保连接可用
            self._ensure_connection()
            
            # 添加时间戳（如果没有）
            if 'timestamp' not in message:
                message['timestamp'] = int(time.time() * 1000)
            
            # 发送消息
            self.connection_manager.channel.basic_publish(
                exchange=self.config['exchange'],
                routing_key=self.config['routing_key'],
                body=json.dumps(message, ensure_ascii=False),
                properties=pika.BasicProperties(
                    delivery_mode=2,  # 持久化消息
                    content_type='application/json'
                )
            )
            
            logger.info(f"📤 Task update sent: taskId={message.get('taskId')}, status={message.get('status')}, progress={message.get('progress', 0)}")
            return True
            
        except Exception as e:
            logger.error(f"❌ Failed to send task update: {e}")
            # 尝试重连
            try:
                self.connection_manager.reconnect()
            except Exception as reconnect_error:
                logger.error(f"Failed to reconnect: {reconnect_error}")
            return False
    
    def send_task_start(self, task_id: str, message: str = "任务开始处理") -> bool:
        """发送任务开始消息（progress = 0）"""
        return self.send_task_update({
            'taskId': task_id,
            'status': 'PROCESSING',
            'progress': 0,
            'metadata': {
                'message': message
            }
        })
    
    def send_task_progress(self, task_id: str, progress: int, message: str = "") -> bool:
        """发送任务进度更新"""
        update_msg = {
            'taskId': task_id,
            'status': 'PROCESSING',
            'progress': progress
        }
        if message:
            update_msg['metadata'] = {'message': message}
        return self.send_task_update(update_msg)
    
    def send_task_success(self, task_id: str, result: Any, message: str = "任务执行成功") -> bool:
        """发送任务成功消息"""
        return self.send_task_update({
            'taskId': task_id,
            'status': 'SUCCESS',
            'progress': 100,
            'result': result,
            'metadata': {
                'message': message
            }
        })
    
    def send_task_failure(self, task_id: str, error: str, message: str = "任务执行失败") -> bool:
        """发送任务失败消息"""
        return self.send_task_update({
            'taskId': task_id,
            'status': 'FAILED',
            'error': error,
            'metadata': {
                'message': message
            }
        })
    
    def close(self):
        """关闭连接"""
        self.connection_manager.close()

