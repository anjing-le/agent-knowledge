"""
RabbitMQ消息队列模块
用于实现任务队列的消费和结果队列的生产
"""

from kparser.rabbitmq_queue.connection import RabbitMQConnection
from kparser.rabbitmq_queue.producer import TaskResultProducer
from kparser.rabbitmq_queue.consumer import TaskQueueConsumer

__all__ = ['RabbitMQConnection', 'TaskResultProducer', 'TaskQueueConsumer']

