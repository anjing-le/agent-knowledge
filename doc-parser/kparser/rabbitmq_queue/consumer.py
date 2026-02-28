"""
RabbitMQ 消息消费者
用于从 dataParsingQueue 获取解析任务
"""
import json
from typing import Callable, Optional
from kparser.common.log_utils import get_logger
from kparser.rabbitmq_queue.connection import RabbitMQConnection
from kparser.common import config as settings

logger = get_logger(__name__)


class TaskQueueConsumer:
    """任务队列消费者 - 从 dataParsingQueue 获取任务"""
    
    # 支持的 taskAction 类型
    SUPPORTED_ACTIONS = {'documentParse', 'ocrRecognition'}
    
    def __init__(self):
        self.connection_manager = RabbitMQConnection()
        self.config = settings.RABBITMQ['task_queue']
        self.consumer_config = settings.RABBITMQ['consumer']
        self.callback: Optional[Callable] = None
        self._consuming = False
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
            
            # 设置 QoS（预取数量）
            self.connection_manager.channel.basic_qos(
                prefetch_count=int(self.consumer_config['prefetch_count'])
            )
    
    def _should_consume_task(self, task_action: str) -> bool:
        """
        判断是否应该消费该任务
        只消费 taskAction 为 documentParse 或 ocrRecognition 的任务
        """
        return task_action in self.SUPPORTED_ACTIONS
    
    def _on_message(self, channel, method, properties, body):
        """消息回调处理"""
        try:
            # 解析消息
            message = json.loads(body.decode('utf-8'))
            task_id = message.get('taskId')
            task_action = message.get('taskAction')
            
            logger.info(f"📥 Received task: taskId={task_id}, taskAction={task_action}")
            
            # 检查是否支持该任务类型
            if not self._should_consume_task(task_action):
                logger.info(f"⏭️  Skipping unsupported task: taskAction={task_action}, rejecting message")
                # 拒绝消息并重新入队（让其他消费者处理）
                channel.basic_reject(delivery_tag=method.delivery_tag, requeue=True)
                return
            
            # 调用回调处理任务
            if self.callback:
                success = self.callback(message)
                
                if success:
                    # 确认消息
                    channel.basic_ack(delivery_tag=method.delivery_tag)
                    logger.info(f"✅ Task accepted and ACKed: taskId={task_id}")
                else:
                    # 任务处理失败（如进程不足），拒绝消息不重新入队
                    channel.basic_reject(delivery_tag=method.delivery_tag, requeue=False)
                    logger.warning(f"⚠️  Task rejected (no available workers): taskId={task_id}")
            else:
                logger.error("No callback registered, rejecting message")
                channel.basic_reject(delivery_tag=method.delivery_tag, requeue=True)
                
        except json.JSONDecodeError as e:
            logger.error(f"❌ Failed to parse message: {e}, rejecting")
            channel.basic_reject(delivery_tag=method.delivery_tag, requeue=False)
        except Exception as e:
            logger.error(f"❌ Error processing message: {e}, rejecting with requeue")
            channel.basic_reject(delivery_tag=method.delivery_tag, requeue=True)
    
    def start_consuming(self, callback: Callable[[dict], bool]):
        """
        开始消费消息
        
        Args:
            callback: 消息处理回调函数，接收消息字典，返回 bool 表示是否成功接受任务
                     如果返回 False，表示无可用进程，消息会被拒绝不重新入队
        """
        self.callback = callback
        
        try:
            self._ensure_connection()
            
            # 开始消费
            self.connection_manager.channel.basic_consume(
                queue=self.config['queue'],
                on_message_callback=self._on_message,
                auto_ack=False  # 手动确认
            )
            
            logger.info(f"🎧 Started consuming from queue: {self.config['queue']}")
            logger.info(f"📋 Supported taskActions: {', '.join(self.SUPPORTED_ACTIONS)}")
            self._consuming = True
            
            # 阻塞式消费
            self.connection_manager.channel.start_consuming()
            
        except KeyboardInterrupt:
            logger.info("⚠️  Consumer interrupted by user")
            self.stop_consuming()
        except Exception as e:
            logger.error(f"❌ Consumer error: {e}")
            self._consuming = False
            raise
    
    def stop_consuming(self):
        """停止消费"""
        if self._consuming and self.connection_manager.is_connected():
            try:
                self.connection_manager.channel.stop_consuming()
                logger.info("Consumer stopped")
            except Exception as e:
                logger.error(f"Error stopping consumer: {e}")
            finally:
                self._consuming = False
    
    def get_single_message(self) -> Optional[dict]:
        """
        获取单个消息（非阻塞）
        用于在有空余进程时主动获取任务
        
        Returns:
            消息字典，如果没有消息则返回 None
        """
        try:
            self._ensure_connection()
            
            method_frame, properties, body = self.connection_manager.channel.basic_get(
                queue=self.config['queue'],
                auto_ack=False
            )
            
            if method_frame:
                # 解析消息
                message = json.loads(body.decode('utf-8'))
                task_action = message.get('taskAction')
                
                # 检查是否支持该任务类型
                if not self._should_consume_task(task_action):
                    logger.info(f"⏭️  Skipping unsupported task: taskAction={task_action}")
                    # 拒绝消息并重新入队
                    self.connection_manager.channel.basic_reject(
                        delivery_tag=method_frame.delivery_tag,
                        requeue=True
                    )
                    return None
                
                # 返回消息和确认信息
                return {
                    'message': message,
                    'delivery_tag': method_frame.delivery_tag
                }
            
            return None
            
        except Exception as e:
            logger.error(f"❌ Error getting message: {e}")
            return None
    
    def ack_message(self, delivery_tag):
        """确认消息"""
        try:
            self.connection_manager.channel.basic_ack(delivery_tag=delivery_tag)
            logger.debug(f"Message ACKed: delivery_tag={delivery_tag}")
        except Exception as e:
            logger.error(f"Error ACKing message: {e}")
    
    def reject_message(self, delivery_tag, requeue: bool = False):
        """拒绝消息"""
        try:
            self.connection_manager.channel.basic_reject(
                delivery_tag=delivery_tag,
                requeue=requeue
            )
            logger.debug(f"Message rejected: delivery_tag={delivery_tag}, requeue={requeue}")
        except Exception as e:
            logger.error(f"Error rejecting message: {e}")
    
    def close(self):
        """关闭连接"""
        self.stop_consuming()
        self.connection_manager.close()

