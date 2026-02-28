from openai import OpenAI, AsyncOpenAI
import asyncio
from functools import wraps

from kparser.common.log_utils import get_logger
from kparser.common.config import VISION

logger = get_logger(__name__)


def async_to_sync(async_func):
    """将异步函数转换为同步函数的装饰器，用于向后兼容"""
    @wraps(async_func)
    def sync_wrapper(*args, **kwargs):
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # 如果已经在事件循环中，创建一个新的线程来运行
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(asyncio.run, async_func(*args, **kwargs))
                    return future.result()
            else:
                return loop.run_until_complete(async_func(*args, **kwargs))
        except RuntimeError:
            # 如果没有事件循环，创建一个新的
            return asyncio.run(async_func(*args, **kwargs))
    return sync_wrapper


class API_GLM:
    """
    在线调用VLM - 支持异步并发调用
    """
    def __init__(self):
        logger.info(f"初始化API_GLM，所有参数直接从VISION配置读取")
        api_key = VISION.get("vlm_api_key", "")
        model_url = VISION.get("vlm_api_base", "https://open.bigmodel.cn/api/paas/v4")
        model_channel = "API_GLM"
        self.model_code = VISION.get("vlm_model", "glm-4v-plus-0111")
        logger.info(f"API_GLM api_key={api_key}, \
                    model_url={model_url}, \
                    model_channel={model_channel}, \
                    model_code={self.model_code}")
        
        # 同时创建同步和异步客户端
        self.client = OpenAI(api_key=api_key, base_url=model_url)
        self.async_client = AsyncOpenAI(api_key=api_key, base_url=model_url)
        
        # 从VISION配置中读取参数，确保类型正确
        self.temperature = float(VISION.get("temperature", 0.8))
        self.top_p = float(VISION.get("top_p", 0.6))
        self.max_tokens = int(VISION.get("max_tokens", 1024))

    async def generate_async(self, img_url, prompt, stream=False):
        """异步版本的 generate 方法，支持真正的并发"""
        model_name = str(self.model_code)
        response = await self.async_client.chat.completions.create(
                model=model_name,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "image_url",
                                "image_url": {
                                    "url": img_url
                                }
                            },
                            {
                                "type": "text",
                                "text": prompt
                            }
                        ]
                    }
                ],
                stream=stream,
                temperature=self.temperature,
                top_p=self.top_p,
                max_tokens=self.max_tokens
                )

        if stream:
            contents = []
            async for e in response:
                if e.choices[0].delta.content:
                    contents.append(e.choices[0].delta.content)
            return "".join(contents)
        else:
            return response.choices[0].message.content

    def generate(self, img_url, prompt, stream=False):
        """同步版本的 generate 方法，内部调用异步版本以支持并发"""
        return async_to_sync(self.generate_async)(img_url, prompt, stream)
