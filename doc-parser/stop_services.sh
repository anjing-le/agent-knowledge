#!/bin/bash
# 停止 knowledge_center_parser 服务

echo "🛑 停止 Knowledge Center Parser 服务..."

# 停止 HTTP 服务
if [ -f logs/http_service.pid ]; then
    HTTP_PID=$(cat logs/http_service.pid)
    if ps -p $HTTP_PID > /dev/null; then
        kill $HTTP_PID
        echo "✅ HTTP 服务已停止 (PID: $HTTP_PID)"
    else
        echo "⚠️  HTTP 服务进程不存在"
    fi
    rm -f logs/http_service.pid
fi

# 停止 RabbitMQ 消费者
if [ -f logs/rabbitmq_consumer.pid ]; then
    RABBITMQ_PID=$(cat logs/rabbitmq_consumer.pid)
    if ps -p $RABBITMQ_PID > /dev/null; then
        kill $RABBITMQ_PID
        echo "✅ RabbitMQ 消费者已停止 (PID: $RABBITMQ_PID)"
    else
        echo "⚠️  RabbitMQ 消费者进程不存在"
    fi
    rm -f logs/rabbitmq_consumer.pid
fi

echo "✅ 所有服务已停止"

