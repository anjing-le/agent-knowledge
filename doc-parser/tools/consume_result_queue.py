#!/usr/bin/env python3
"""
从 RabbitMQ 结果队列消费消息
"""
import sys
import json
import pika
from datetime import datetime
from kparser.common import config as settings

class ResultConsumer:
    """结果队列消费者"""
    
    def __init__(self):
        self.connection = None
        self.channel = None
        self.rabbitmq_config = settings.RABBITMQ
        self.result_queue_config = self.rabbitmq_config['result_queue']
        self.task_results = {}
        self.message_count = 0
        
    def connect(self):
        """连接到 RabbitMQ"""
        try:
            credentials = pika.PlainCredentials(
                username=self.rabbitmq_config['username'],
                password=self.rabbitmq_config['password']
            )
            
            parameters = pika.ConnectionParameters(
                host=self.rabbitmq_config['host'],
                port=int(self.rabbitmq_config['port']),
                virtual_host=self.rabbitmq_config['virtual_host'],
                credentials=credentials,
                heartbeat=int(self.rabbitmq_config['connection']['heartbeat'])
            )
            
            self.connection = pika.BlockingConnection(parameters)
            self.channel = self.connection.channel()
            
            # 声明交换机和队列
            self.channel.exchange_declare(
                exchange=self.result_queue_config['exchange'],
                exchange_type='topic',
                durable=True
            )
            self.channel.queue_declare(
                queue=self.result_queue_config['queue'],
                durable=True
            )
            self.channel.queue_bind(
                queue=self.result_queue_config['queue'],
                exchange=self.result_queue_config['exchange'],
                routing_key=self.result_queue_config['routing_key']
            )
            
            print(f"✅ 已连接到 RabbitMQ")
            print(f"   队列: {self.result_queue_config['queue']}")
            print()
            return True
            
        except Exception as e:
            print(f"❌ 连接失败: {e}")
            return False
    
    def callback(self, ch, method, properties, body):
        """消息回调"""
        try:
            message = json.loads(body.decode('utf-8'))
            self.message_count += 1
            
            task_id = message.get('taskId')
            status = message.get('status')
            progress = message.get('progress', 0)
            timestamp = message.get('timestamp')
            
            # 记录任务状态
            if task_id not in self.task_results:
                self.task_results[task_id] = {
                    'first_seen': datetime.now(),
                    'messages': []
                }
            
            self.task_results[task_id]['messages'].append({
                'status': status,
                'progress': progress,
                'timestamp': timestamp,
                'time': datetime.now()
            })
            
            # 格式化时间
            time_str = datetime.now().strftime('%H:%M:%S')
            
            # 打印消息
            print(f"[{time_str}] 📨 消息 #{self.message_count}")
            print(f"   TaskID: {task_id}")
            print(f"   Status: {status}")
            print(f"   Progress: {progress}%")
            
            if 'result' in message:
                print(f"   Result: {message['result']}")
            
            if 'error' in message:
                print(f"   Error: {message['error']}")
            
            if 'metadata' in message:
                metadata = message['metadata']
                if 'message' in metadata:
                    print(f"   Message: {metadata['message']}")
            
            print()
            
            # 确认消息
            ch.basic_ack(delivery_tag=method.delivery_tag)
            
        except Exception as e:
            print(f"❌ 处理消息失败: {e}")
            ch.basic_ack(delivery_tag=method.delivery_tag)
    
    def start_consuming(self, auto_stop=False, max_messages=None):
        """开始消费"""
        try:
            print("=" * 80)
            print("🎧 开始消费结果队列")
            print("=" * 80)
            print()
            
            if auto_stop:
                print(f"⏱️  自动停止模式: 空闲 10 秒后停止")
                if max_messages:
                    print(f"📊 最大消息数: {max_messages}")
                print()
            else:
                print("💡 按 Ctrl+C 停止消费")
                print()
            
            # 设置 QoS
            self.channel.basic_qos(prefetch_count=1)
            
            # 开始消费
            self.channel.basic_consume(
                queue=self.result_queue_config['queue'],
                on_message_callback=self.callback,
                auto_ack=False
            )
            
            if auto_stop:
                # 自动停止模式：检查队列是否为空
                import time
                idle_count = 0
                max_idle = 10  # 10 次检查（10 秒）
                
                while True:
                    # 处理一秒钟的消息
                    self.connection.process_data_events(time_limit=1)
                    
                    # 检查是否达到最大消息数
                    if max_messages and self.message_count >= max_messages:
                        print(f"\n✅ 已达到最大消息数 ({max_messages})，停止消费")
                        break
                    
                    # 检查队列是否为空
                    queue_state = self.channel.queue_declare(
                        queue=self.result_queue_config['queue'],
                        durable=True,
                        passive=True
                    )
                    
                    if queue_state.method.message_count == 0:
                        idle_count += 1
                        if idle_count >= max_idle:
                            print(f"\n✅ 队列已空闲 {max_idle} 秒，停止消费")
                            break
                    else:
                        idle_count = 0
            else:
                # 持续消费模式
                self.channel.start_consuming()
                
        except KeyboardInterrupt:
            print("\n\n⚠️  用户中断，停止消费")
        except Exception as e:
            print(f"\n\n❌ 消费错误: {e}")
            import traceback
            traceback.print_exc()
    
    def show_summary(self):
        """显示统计摘要"""
        print()
        print("=" * 80)
        print("📊 消费统计")
        print("=" * 80)
        print(f"   总消息数: {self.message_count}")
        print(f"   任务数: {len(self.task_results)}")
        print()
        
        if self.task_results:
            print("📋 任务状态:")
            for task_id, info in self.task_results.items():
                messages = info['messages']
                last_msg = messages[-1]
                print(f"   {task_id[:16]}... - {last_msg['status']} ({last_msg['progress']}%)")
            print()
        
        print("=" * 80)
    
    def close(self):
        """关闭连接"""
        if self.channel:
            self.channel.close()
        if self.connection:
            self.connection.close()
        print("🔌 连接已关闭")


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(description='从 RabbitMQ 结果队列消费消息')
    parser.add_argument(
        '--auto-stop',
        action='store_true',
        help='队列空闲 10 秒后自动停止'
    )
    parser.add_argument(
        '--max-messages',
        type=int,
        help='最大消费消息数'
    )
    
    args = parser.parse_args()
    
    print()
    print("=" * 80)
    print("📥 RabbitMQ 结果队列消费者")
    print("=" * 80)
    print()
    
    consumer = ResultConsumer()
    
    if not consumer.connect():
        return 1
    
    try:
        consumer.start_consuming(
            auto_stop=args.auto_stop,
            max_messages=args.max_messages
        )
    finally:
        consumer.show_summary()
        consumer.close()
    
    return 0


if __name__ == '__main__':
    sys.exit(main())

