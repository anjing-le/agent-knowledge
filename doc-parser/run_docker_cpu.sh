#!/bin/bash
set -e
cd $(dirname $0)

TAG=$1
HOST_PORT=$2

# 服务配置
SERVICE_NAME=general-doc-parser-dev
IMAGE_NAME=your-registry.example.com/general-doc-parser-dev
CONTAINER_PORT=7100
# 将部署目录设置在项目文件夹内，方便管理
DEPLOY_HOME=$(pwd)/data
LOG_HOME=$DEPLOY_HOME/logs
ENV_FILE=$(pwd)/.env

# 检查参数
if [ -z "$TAG" ] || [ -z "$HOST_PORT" ]; then
    echo "用法: $0 <镜像版本> <主机端口>"
    echo "示例: $0 1.0.0 7099"
    exit 1
fi

# 自动创建数据和日志目录
mkdir -p $DEPLOY_HOME
mkdir -p $LOG_HOME

# 检查环境变量文件
if [ ! -f "$ENV_FILE" ]; then
    echo "环境变量文件 $ENV_FILE 不存在，正在创建..."
    if [ -f "env_example.txt" ]; then
        cp env_example.txt $ENV_FILE
        echo "已从 env_example.txt 复制生成新的环境变量文件: $ENV_FILE"
        echo "请根据您的环境修改此文件，然后重新运行脚本。"
    else
        echo "错误: 找不到 env_example.txt 文件"
        echo "请手动创建 .env 文件或确保 env_example.txt 存在"
    fi
    exit 1
fi

echo "正在停止并移除旧版服务..."
docker rm -f $SERVICE_NAME || true
sleep 2

echo "正在启动新版服务，映射端口 $HOST_PORT -> $CONTAINER_PORT ..."
docker run -itd --restart always --name $SERVICE_NAME \
-p ${HOST_PORT}:${CONTAINER_PORT} \
-v $LOG_HOME:/app/logs \
-v $ENV_FILE:/app/.env \
-e DPARSER_ENV=dev \
-e PYTHONIOENCODING=utf-8 \
$IMAGE_NAME:$TAG

echo "服务启动完成"
echo "现在可以通过 http://<服务器IP>:${HOST_PORT} 访问服务"
echo "请使用 'docker logs -f $SERVICE_NAME' 查看日志"
