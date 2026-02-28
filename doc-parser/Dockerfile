#FROM your-registry.example.com/public/ubuntu-python:3.10
FROM your-registry.example.com/public/ubuntu-python-parser-base:2.0

# 复制项目文件
COPY kparser /app/kparser

# 复制脚本
COPY *.sh /app/
COPY static /app/static
COPY README.md /app/README.md

# 日志路径
RUN mkdir -p /app/logs
RUN touch /app/logs/kparser_detail.log

COPY ppocr_keys_v1.txt /usr/local/lib/python3.10/dist-packages/rapidocr/models/ppocr_keys_v1.txt

# 复制 nltk_data.tar.gz 到镜像
COPY nltk_data.tar.gz /tmp/nltk_data.tar.gz
RUN tar -xzf /tmp/nltk_data.tar.gz -C /usr/local/lib/ && \
    rm /tmp/nltk_data.tar.gz

RUN /usr/bin/python3 -m pip install pika httpx img2pdf trio a2wsgi fastapi aiohttp redis[hiredis]==5.0.1 elastic-apm==6.24.0 uvicorn[standard]==0.30.0 -i https://mirrors.aliyun.com/pypi/simple/

# 设置环境变量
ENV KPARSER_ENV=dev
ENV kPARSER_HOME=/app/deploy
ENV PYTHONPATH=/app:/usr/lib/python3/dist-packages
ENV DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1

# 多实例配置环境变量（可通过docker run时覆盖）
ENV MULTI_INSTANCE_COUNT=1
ENV MULTI_INSTANCE_BASE_PORT=7100
ENV SERVICE_MAX_JOB_NUMBER=32
ENV MULTI_INSTANCE_WORKER_COUNT=2
ENV STARTUP_MODE=auto
ENV ENABLE_RABBITMQ_CONSUMER=false

# 设置字体渲染环境变量（帮助 LibreOffice 使用安装的字体）
ENV FONTCONFIG_PATH=/etc/fonts
ENV LIBREOFFICE_ENABLE_GUI=0

# 创建日志目录
RUN mkdir -p /app/logs

# 创建启动脚本
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
COPY start_multi_instance_service.sh /app/start_multi_instance_service.sh
RUN chmod +x /app/docker-entrypoint.sh /app/start_multi_instance_service.sh

# 启动脚本：支持单实例和多实例模式（不使用消息队列）
CMD ["/app/docker-entrypoint.sh"]