import requests
import json
import time

from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


headers = {
    "Content-Type": "application/json"
}

def call_back_status(url, status, request_id, doc_id, oss_url="", message="", max_retries=3, retry_delay=5):
    retry_count = 0  # 重试计数器
    if not url:
        return True, "Callback URL is not set"
    while retry_count < max_retries:
        try:
            if status == "complete":
                result = True
            else:
                result = False

            data = {
                "type": "PARSING",
                "requestId": request_id,
                "fileId": doc_id,
                "ossUrl": oss_url,
                "result": result,
                "message": message
            }

            logger.info(data)

            response = requests.post(url, headers=headers, data=json.dumps(data))
            res_json = response.json()

            logger.debug("url={}, res_json={}".format(url, res_json))

            if res_json["code"] == 200:
                msg = res_json["data"]["message"]
                if res_json["data"]["status"]:
                    return True, msg
                else:
                    return False, msg
            else:
                # 如果返回的code不是200，记录错误并重试
                logger.error(f"CallBack API returned non-200 code: {res_json}")
                retry_count += 1
                time.sleep(retry_delay)
                continue
        except Exception as e:
            logger.error(f"Attention please! CallBack API does not work! Task {request_id} call back error: {e}")
            retry_count += 1
            time.sleep(retry_delay)
            continue

    # 如果重试次数用完仍未成功，返回失败
    logger.error(f"All retries failed for task {request_id}")
    return False, "Attention please! CallBack API does not work after multiple retries!"

if __name__ == "__main__":
    call_back_status("complete", "ewrwewqda21131", 12345, oss_url="ewqew", message="3e4")