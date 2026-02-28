#!/bin/bash
# Docker容器启动脚本 - 支持单实例和多实例模式（不使用消息队列）
# 使用 Redis 实现跨实例协调

# 错误处理
set -e

echo "🚀 Knowledge Center Parser - Docker Startup Script (Multi-Instance)"
echo "===================================================================="

# 启动LibreOffice（后台服务）
echo "📝 Starting LibreOffice in headless mode..."
soffice --headless --accept="socket,host=localhost,port=2002;urp;" --nofirststartwizard &
LIBREOFFICE_PID=$!

# 等待LibreOffice启动
sleep 5

# 检查LibreOffice是否启动成功
if ps -p $LIBREOFFICE_PID > /dev/null; then
    echo "✅ LibreOffice started successfully (PID: $LIBREOFFICE_PID)"
else
    echo "❌ LibreOffice failed to start"
    exit 1
fi

# 读取环境变量
MULTI_INSTANCE_COUNT=${MULTI_INSTANCE_COUNT:-1}
MULTI_INSTANCE_BASE_PORT=${MULTI_INSTANCE_BASE_PORT:-7100}
SERVICE_MAX_JOB_NUMBER=${SERVICE_MAX_JOB_NUMBER:-32}
MULTI_INSTANCE_WORKER_COUNT=${MULTI_INSTANCE_WORKER_COUNT:-1}
STARTUP_MODE=${STARTUP_MODE:-auto}
ENABLE_RABBITMQ_CONSUMER=${ENABLE_RABBITMQ_CONSUMER:-false}

echo "📊 Configuration:"
echo "   Instance Count: $MULTI_INSTANCE_COUNT"
echo "   Base Port: $MULTI_INSTANCE_BASE_PORT"
echo "   Max Concurrent Jobs: $SERVICE_MAX_JOB_NUMBER"
echo "   Workers per Instance: $MULTI_INSTANCE_WORKER_COUNT"
echo "   Startup Mode: $STARTUP_MODE"
echo "   RabbitMQ Consumer: $ENABLE_RABBITMQ_CONSUMER"

# 信号处理函数
cleanup() {
    echo "🛑 Received shutdown signal, cleaning up..."
    
    # 终止LibreOffice
    if ps -p $LIBREOFFICE_PID > /dev/null; then
        kill $LIBREOFFICE_PID
        echo "✅ LibreOffice stopped"
    fi
    
    # 终止Python服务
    if [ ! -z "$SERVICE_PID" ]; then
        kill $SERVICE_PID
        wait $SERVICE_PID 2>/dev/null || true
        echo "✅ Python service stopped"
    fi
    
    echo "👋 Cleanup completed"
    exit 0
}

# 注册信号处理器
trap cleanup SIGTERM SIGINT

# 🔥 启动 RabbitMQ 消费者（如果启用）
if [ "$ENABLE_RABBITMQ_CONSUMER" = "true" ]; then
    echo "🐰 Starting RabbitMQ Consumer Process..."
    echo "   RabbitMQ Host: ${RABBITMQ_HOST:-rbtmq-202f5d298207.rabbitmq.ivolces.com}"
    echo "   RabbitMQ Port: ${RABBITMQ_PORT:-5672}"
    echo "   RabbitMQ User: ${RABBITMQ_USERNAME:-user_a}"
    echo "   Virtual Host: ${RABBITMQ_VHOST:-python-use}"
    echo "   Task Queue: ${RABBITMQ_TASK_QUEUE:-parsingTaskQueue}"
    echo "   Result Queue: ${RABBITMQ_RESULT_QUEUE:-parsingResultQueue}"
    echo ""
    
    # 启动消费者进程（日志同时输出到文件和标准输出）
    /usr/bin/python3 -m kparser.rabbitmq_queue.queue_processor 2>&1 | tee /app/logs/rabbitmq_consumer.log &
    RABBITMQ_PID=$!
    
    # 等待消费者启动
    sleep 5
    
    if ps -p $RABBITMQ_PID > /dev/null; then
        echo "✅ RabbitMQ consumer started successfully (PID: $RABBITMQ_PID)"
        echo "   Logs will appear in docker logs output"
        echo "   Also saved to: /app/logs/rabbitmq_consumer.log"
        echo ""
    else
        echo "❌ RabbitMQ consumer failed to start or crashed immediately"
        echo "   Showing last 50 lines of log:"
        echo "----------------------------------------"
        tail -n 50 /app/logs/rabbitmq_consumer.log 2>/dev/null || echo "No log file found"
        echo "----------------------------------------"
        # 不立即退出，显示更多信息后再决定
        echo ""
        echo "⚠️  Common issues:"
        echo "   1. RabbitMQ connection refused - check RABBITMQ_HOST and port"
        echo "   2. Authentication failed - check RABBITMQ_USERNAME and PASSWORD"
        echo "   3. Virtual host not found - check RABBITMQ_VHOST"
        echo "   4. Network connectivity issues"
        echo ""
        echo "🔍 Testing RabbitMQ connectivity..."
        python3 -c "import pika; print('pika library OK')" || echo "❌ pika library not installed"
        echo ""
        exit 1
    fi
else
    echo "⏭️  RabbitMQ consumer disabled"
fi


# 根据实例数量决定启动模式
if [ "$MULTI_INSTANCE_COUNT" -gt 1 ] || [ "$STARTUP_MODE" = "multi" ]; then
    echo "🔥 Starting Multi-Instance Mode (Redis-based coordination)"
    echo "   Total Instances: $MULTI_INSTANCE_COUNT"
    echo "   Expected Total Capacity: $(($MULTI_INSTANCE_COUNT * $SERVICE_MAX_JOB_NUMBER)) concurrent tasks"
    echo "   Port Range: $MULTI_INSTANCE_BASE_PORT - $(($MULTI_INSTANCE_BASE_PORT + $MULTI_INSTANCE_COUNT - 1))"
    echo "   Coordination: Redis (No Message Queue)"
    /usr/bin/python3 -m kparser.multi_instance.instance_manager &
    SERVICE_PID=$!
    
else
    echo "⚡ Starting Single Instance Mode"
    echo "   Port: $MULTI_INSTANCE_BASE_PORT"
    echo "   Max Concurrent Jobs: $SERVICE_MAX_JOB_NUMBER"
    echo "   Coordination: Redis (No Message Queue)"
    
    # 设置单实例环境变量
    export INSTANCE_ID="instance-single"
    export INSTANCE_PORT=$MULTI_INSTANCE_BASE_PORT
    
    # 启动单实例模式（使用新的 multi_instance_app）
    /usr/bin/python3 -m uvicorn kparser.multi_instance_app:app \
        --host 0.0.0.0 \
        --port $MULTI_INSTANCE_BASE_PORT \
        --log-level info &
    SERVICE_PID=$!
fi

# 检查服务是否启动成功
sleep 5
if ps -p $SERVICE_PID > /dev/null; then
    echo "✅ Parser service started successfully (PID: $SERVICE_PID)"
    
    # 显示访问信息
    if [ "$MULTI_INSTANCE_COUNT" -gt 1 ] || [ "$STARTUP_MODE" = "multi" ]; then
        echo ""
        echo "🌐 Multi-Instance Service Endpoints:"
        for ((i=0; i<$MULTI_INSTANCE_COUNT; i++)); do
            port=$(($MULTI_INSTANCE_BASE_PORT + $i))
            echo "   Instance $((i+1)): http://localhost:$port"
            echo "   Instance $((i+1)) Docs: http://localhost:$port/docs"
            echo "   Instance $((i+1)) Health: http://localhost:$port/health"
        done
        echo ""
        echo "📊 Total System Capacity: $(($MULTI_INSTANCE_COUNT * $SERVICE_MAX_JOB_NUMBER)) concurrent tasks"
        echo "🔄 Load Balancing: Client-side via Redis coordination"
        echo "🛡️  State Management: Shared via Redis"
    else
        echo ""
        echo "🌐 Single Instance Service:"
        echo "   API: http://localhost:$MULTI_INSTANCE_BASE_PORT"
        echo "   Docs: http://localhost:$MULTI_INSTANCE_BASE_PORT/docs"
        echo "   Health: http://localhost:$MULTI_INSTANCE_BASE_PORT/health"
        echo ""
        echo "📊 Capacity: $SERVICE_MAX_JOB_NUMBER concurrent tasks"
    fi
else
    echo "❌ Parser service failed to start"
    exit 1
fi

# 等待服务进程
wait $SERVICE_PID
