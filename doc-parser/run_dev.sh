port=9001
WORKERS=4

echo "stoping old service"
ps aux | grep $port | grep -v grep | awk '{print $2}' | xargs kill -9
sleep 2

# /usr/bin/python3 -m uvicorn kparser.app:app --host 0.0.0.0 --port $port --workers $WORKERS

python -m gunicorn -k uvicorn.workers.UvicornWorker -w $WORKERS -b 0.0.0.0:$port kparser.app:app
