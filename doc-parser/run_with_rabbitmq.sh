#!/bin/bash
# 启动 knowledge_center_parser 服务（启用 RabbitMQ 消费者）

echo "🚀 启动 Knowledge Center Parser (RabbitMQ 模式)"
echo "================================================"

# 设置环境变量
export SERVICE_MAX_JOB_NUMBER=32
export ENABLE_RABBITMQ_CONSUMER=true

# RabbitMQ 连接配置（请根据实际情况修改）
export RABBITMQ_HOST=${RABBITMQ_HOST:-localhost}
export RABBITMQ_PORT=${RABBITMQ_PORT:-5672}
export RABBITMQ_USERNAME=${RABBITMQ_USERNAME:-guest}
export RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD:-guest}

echo "📊 配置信息:"
echo "   最大并发任务数: $SERVICE_MAX_JOB_NUMBER"
echo "   RabbitMQ 地址: $RABBITMQ_HOST:$RABBITMQ_PORT"
echo "   RabbitMQ 用户: $RABBITMQ_USERNAME"
echo "   启用消费者: $ENABLE_RABBITMQ_CONSUMER"
echo ""

# 启动 HTTP 服务（后台）
echo "⚡ 启动 HTTP 服务..."
nohup python -m uvicorn kparser.app:app --host 0.0.0.0 --port 7099 > logs/http_service.log 2>&1 &
HTTP_PID=$!
echo "✅ HTTP 服务已启动 (PID: $HTTP_PID)"

# 等待 HTTP 服务启动
sleep 3

# 启动 RabbitMQ 消费者（后台）
if [ "$ENABLE_RABBITMQ_CONSUMER" = "true" ]; then
    echo "🐰 启动 RabbitMQ 消费者..."
    nohup python -m kparser.rabbitmq_queue.queue_processor > logs/rabbitmq_consumer.log 2>&1 &
    RABBITMQ_PID=$!
    echo "✅ RabbitMQ 消费者已启动 (PID: $RABBITMQ_PID)"
    
    # 等待消费者启动
    sleep 3
    
    # 检查进程是否存活
    if ps -p $RABBITMQ_PID > /dev/null; then
        echo "✅ 所有服务启动成功！"
    else
        echo "❌ RabbitMQ 消费者启动失败，请查看日志: logs/rabbitmq_consumer.log"
        exit 1
    fi
else
    echo "⏭️  RabbitMQ 消费者未启用"
fi

echo ""
echo "🌐 服务访问地址:"
echo "   API: http://localhost:7099"
echo "   文档: http://localhost:7099/docs"
echo "   健康检查: http://localhost:7099/health"
echo ""
echo "📝 日志文件:"
echo "   HTTP 服务: logs/http_service.log"
if [ "$ENABLE_RABBITMQ_CONSUMER" = "true" ]; then
    echo "   RabbitMQ 消费者: logs/rabbitmq_consumer.log"
fi
echo ""
echo "💡 查看日志:"
echo "   tail -f logs/http_service.log"
if [ "$ENABLE_RABBITMQ_CONSUMER" = "true" ]; then
    echo "   tail -f logs/rabbitmq_consumer.log"
fi
echo ""
echo "🛑 停止服务:"
echo "   kill $HTTP_PID"
if [ "$ENABLE_RABBITMQ_CONSUMER" = "true" ]; then
    echo "   kill $RABBITMQ_PID"
fi
echo ""

# 保存 PID 到文件
echo $HTTP_PID > logs/http_service.pid
if [ "$ENABLE_RABBITMQ_CONSUMER" = "true" ]; then
    echo $RABBITMQ_PID > logs/rabbitmq_consumer.pid
fi

echo "✅ 启动完成！"

