"""
RabbitMQ 连接管理
"""
import pika
from typing import Optional
from kparser.common.log_utils import get_logger
from kparser.common import config as settings

logger = get_logger(__name__)


class RabbitMQConnection:
    """RabbitMQ 连接管理器"""
    
    def __init__(self):
        self.connection: Optional[pika.BlockingConnection] = None
        self.channel: Optional[pika.channel.Channel] = None
        self.config = settings.RABBITMQ
        
    def connect(self) -> bool:
        """建立 RabbitMQ 连接"""
        try:
            # 构建连接参数
            credentials = pika.PlainCredentials(
                username=self.config['username'],
                password=self.config['password']
            )
            
            parameters = pika.ConnectionParameters(
                host=self.config['host'],
                port=int(self.config['port']),
                virtual_host=self.config['virtual_host'],
                credentials=credentials,
                heartbeat=int(self.config['connection']['heartbeat']),
                blocked_connection_timeout=float(self.config['connection']['blocked_connection_timeout'])
            )
            
            # 建立连接
            self.connection = pika.BlockingConnection(parameters)
            self.channel = self.connection.channel()
            
            logger.info(f"✅ RabbitMQ connected: {self.config['host']}:{self.config['port']}")
            return True
            
        except Exception as e:
            logger.error(f"❌ Failed to connect to RabbitMQ: {e}")
            return False
    
    def close(self):
        """关闭连接"""
        try:
            if self.channel and not self.channel.is_closed:
                self.channel.close()
            if self.connection and not self.connection.is_closed:
                self.connection.close()
            logger.info("RabbitMQ connection closed")
        except Exception as e:
            logger.error(f"Error closing RabbitMQ connection: {e}")
    
    def is_connected(self) -> bool:
        """检查连接是否有效"""
        return (self.connection is not None 
                and not self.connection.is_closed 
                and self.channel is not None 
                and not self.channel.is_closed)
    
    def reconnect(self) -> bool:
        """重新连接"""
        logger.info("Attempting to reconnect to RabbitMQ...")
        self.close()
        return self.connect()
    
    def declare_exchange(self, exchange_name: str, exchange_type: str = 'topic', durable: bool = True):
        """声明交换机"""
        if not self.is_connected():
            raise RuntimeError("Not connected to RabbitMQ")
        
        self.channel.exchange_declare(
            exchange=exchange_name,
            exchange_type=exchange_type,
            durable=durable
        )
        logger.info(f"Exchange declared: {exchange_name} (type={exchange_type}, durable={durable})")
    
    def declare_queue(self, queue_name: str, durable: bool = True):
        """声明队列"""
        if not self.is_connected():
            raise RuntimeError("Not connected to RabbitMQ")
        
        result = self.channel.queue_declare(
            queue=queue_name,
            durable=durable
        )
        logger.info(f"Queue declared: {queue_name} (durable={durable})")
        return result
    
    def bind_queue(self, queue_name: str, exchange_name: str, routing_key: str):
        """绑定队列到交换机"""
        if not self.is_connected():
            raise RuntimeError("Not connected to RabbitMQ")
        
        self.channel.queue_bind(
            queue=queue_name,
            exchange=exchange_name,
            routing_key=routing_key
        )
        logger.info(f"Queue bound: {queue_name} -> {exchange_name} (routing_key={routing_key})")

