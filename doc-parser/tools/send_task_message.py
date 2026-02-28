#!/usr/bin/env python3
"""
发送任务消息到 RabbitMQ 队列
"""
import sys
import json
import time
import uuid
import pika
from kparser.common import config as settings

def send_task_to_queue(
    task_action='documentParse',
    original_url='knowledge-center-dev/shark/1-1 PKG 普.pdf',
    doc_type='DOCUMENT_BASIC',
    zoomin=3,
    task_name=None,
    scena_type='GSDS'
):
    """
    发送单个任务到队列
    
    Args:
        task_action: 任务类型 (documentParse 或 ocrRecognition)
        original_url: 文件存储地址
        doc_type: 文档类型
        zoomin: 缩放级别
        task_name: 任务名称
    
    Returns:
        task_id: 任务ID
    """
    # 生成任务ID
    task_id = f"task_{uuid.uuid4().hex[:16]}"
    
    # 如果没有提供任务名称，从 URL 中提取
    if not task_name:
        task_name = original_url.split('/')[-1]
    
    # 构建消息
    message = {
        'taskId': task_id,
        'taskType': 'dataParsing',
        'taskAction': task_action,
        'timestamp': int(time.time() * 1000),
        'params': {
            'userId': 'test_user',
            'taskName': task_name,
            'executionMode': 'async',
            'createdAt': time.strftime('%Y-%m-%dT%H:%M:%S.000Z', time.gmtime()),
            'originalUrl': original_url,
            'docType': doc_type,
            'zoomin': zoomin,
            'scenaType': scena_type
        }
    }
    
    try:
        # 连接 RabbitMQ
        rabbitmq_config = settings.RABBITMQ
        credentials = pika.PlainCredentials(
            username=rabbitmq_config['username'],
            password=rabbitmq_config['password']
        )
        
        parameters = pika.ConnectionParameters(
            host=rabbitmq_config['host'],
            port=int(rabbitmq_config['port']),
            virtual_host=rabbitmq_config['virtual_host'],
            credentials=credentials,
            heartbeat=int(rabbitmq_config['connection']['heartbeat'])
        )
        
        connection = pika.BlockingConnection(parameters)
        channel = connection.channel()
        
        # 声明交换机和队列
        task_queue_config = rabbitmq_config['task_queue']
        channel.exchange_declare(
            exchange=task_queue_config['exchange'],
            exchange_type='topic',
            durable=True
        )
        channel.queue_declare(
            queue=task_queue_config['queue'],
            durable=True
        )
        channel.queue_bind(
            queue=task_queue_config['queue'],
            exchange=task_queue_config['exchange'],
            routing_key=task_queue_config['routing_key']
        )
        
        # 发送消息
        channel.basic_publish(
            exchange=task_queue_config['exchange'],
            routing_key=task_queue_config['routing_key'],
            body=json.dumps(message, ensure_ascii=False),
            properties=pika.BasicProperties(
                delivery_mode=2,  # 持久化消息
                content_type='application/json'
            )
        )
        
        connection.close()
        
        print(f"✅ 任务已发送: {task_id}")
        print(f"   类型: {task_action}")
        print(f"   文件: {original_url}")
        print(f"   docType: {doc_type}")
        print(f"   队列: {task_queue_config['queue']}")
        print()
        
        return task_id
        
    except Exception as e:
        print(f"❌ 发送失败: {e}")
        import traceback
        traceback.print_exc()
        return None


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='发送任务消息到 RabbitMQ 队列')
    parser.add_argument(
        '--action',
        choices=['documentParse', 'ocrRecognition', 'cocaColaParse'],
        default='documentParse',
        help='任务类型'
    )
    parser.add_argument(
        '--url',
        default='knowledge-center-dev/shark/1-1 PKG 普.pdf',
        help='文件存储地址'
    )
    parser.add_argument(
        '--doc-type',
        default='DOCUMENT_BASIC',
        help='文档类型'
    )
    parser.add_argument(
        '--zoomin',
        type=int,
        default=3,
        help='缩放级别'
    )
    parser.add_argument(
        '--name',
        help='任务名称'
    )
    parser.add_argument(
        '--scena-type',
        default='GSDS',
        help='业务场景类型'
    )
    
    args = parser.parse_args()
    
    print("=" * 80)
    print("📤 发送任务到 RabbitMQ 队列")
    print("=" * 80)
    print()
    
    task_id = send_task_to_queue(
        task_action=args.action,
        original_url=args.url,
        doc_type=args.doc_type,
        zoomin=args.zoomin,
        task_name=args.name,
        scena_type=args.scena_type
    )
    
    if task_id:
        print("=" * 80)
        print("✅ 发送成功！")
        print(f"任务ID: {task_id}")
        print("=" * 80)
        return 0
    else:
        print("=" * 80)
        print("❌ 发送失败！")
        print("=" * 80)
        return 1


if __name__ == '__main__':
    sys.exit(main())

