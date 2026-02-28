#!/bin/bash

# 设置端口号
port=7100
WORKERS=4

# 设置日志文件路径
log_file="logs/background_service.log"

# 确保日志目录存在
mkdir -p logs

# 停止旧的服务
echo "正在停止旧的服务..."
ps aux | grep $port | grep -v grep | awk '{print $2}' | xargs kill -9
sleep 2

# 启动新服务并重定向输出到日志文件
echo "正在启动新服务..."
# nohup /usr/bin/python3 -m uvicorn kparser.app:app --host 0.0.0.0 --port $port --workers $WORKERS > "$log_file" 2>&1 &
nohup /usr/bin/python3 -m gunicorn -k uvicorn.workers.UvicornWorker -w $WORKERS -b 0.0.0.0:$port kparser.app:app > "$log_file" 2>&1 &

# 获取进程ID
pid=$!

# 等待几秒检查服务是否成功启动
sleep 5
if ps -p $pid > /dev/null; then
    echo "服务已在后台启动成功！"
    echo "进程ID: $pid"
    echo "日志文件: $log_file"
    echo "使用 'tail -f $log_file' 查看日志"
else
    echo "服务启动失败，请检查日志文件: $log_file"
fi 