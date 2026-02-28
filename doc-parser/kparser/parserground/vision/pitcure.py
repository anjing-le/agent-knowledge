#
#  Copyright 2025 The InfiniFlow Authors. All Rights Reserved.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

import io
import re
import asyncio
from typing import Dict, Any, Optional
import base64
import numpy as np
from PIL import Image
from kparser.parserground.vision import OCR
from kparser.rag.nlp import tokenize, clean_markdown_block
from kparser.rag.nlp import rag_tokenizer
from kparser.common.log_utils import get_logger

# 使用 httpx 替代 requests 以支持异步
try:
    import httpx
except ImportError:
    import requests
    httpx = None

logger = get_logger(__name__)

ocr = OCR()

class LLMBundle:
    """
    真实的LLMBundle实现，用于调用外部视觉模型API - 支持异步并发
    请根据您的API接口修改此类
    """
    
    def __init__(self, llm_name: str, lang: str = "Chinese", **kwargs):
        self.llm_name = llm_name
        self.lang = lang
        
        # API配置 - 请根据您的实际API修改
        self.api_config = {
            "api_url": kwargs.get("api_url", ""),  # 您的API地址
            "api_key": kwargs.get("api_key", ""),  # API密钥
            "model_name": llm_name,  # 模型名称
            "timeout": kwargs.get("timeout", 30),  # 请求超时
        }
        
        # 创建异步 HTTP 客户端
        if httpx:
            self.async_client = httpx.AsyncClient(timeout=self.api_config["timeout"])
        else:
            self.async_client = None
            logger.warning("httpx 未安装，将使用同步 requests（无法并发）")
        
        logger.info(f"🔧 创建LLMBundle:")
        logger.info(f"   - 模型名称: {llm_name}")
        logger.info(f"   - 语言: {lang}")
        logger.info(f"   - API地址: {self.api_config['api_url']}")
        logger.info(f"   - 异步支持: {'是' if httpx else '否'}")

    def describe_with_prompt(self, image, prompt: str = None):
        """
        VisionParser需要的接口方法
        
        Args:
            image: 图像数据（PIL Image对象或文件路径）
            prompt: 提示词
            
        Returns:
            str: 视觉模型返回的文本描述
        """
        try:
            # 调试信息：打印image类型和属性
            logger.info(f"🔍 describe_with_prompt 收到图像类型: {type(image)}")
            
            # 处理不同类型的图像输入
            if isinstance(image, str):
                # 如果是文件路径，打开图像
                from PIL import Image as PILImage
                logger.info(f"   -> 处理文件路径: {image}")
                image_obj = PILImage.open(image)
            elif isinstance(image, bytes):
                # 如果是bytes数据，从内存中打开
                from PIL import Image as PILImage
                import io
                logger.info(f"   -> 处理bytes数据，大小: {len(image)} bytes")
                image_obj = PILImage.open(io.BytesIO(image))
            elif hasattr(image, 'read'):
                # 如果是文件对象，读取并打开
                from PIL import Image as PILImage
                logger.info(f"   -> 处理文件对象: {type(image)}")
                image_obj = PILImage.open(image)
            elif hasattr(image, 'mode'):
                # 假设已经是PIL Image对象
                logger.info(f"   -> 已是PIL Image对象，模式: {image.mode}")
                image_obj = image
            else:
                # 打印更多调试信息
                logger.info(f"   -> 未知图像类型: {type(image)}")
                logger.info(f"   -> 图像属性: {dir(image)[:10]}")  # 只显示前10个属性
                
                # 尝试直接使用
                image_obj = image
            
            logger.info(f"   -> 最终图像对象类型: {type(image_obj)}")
            if hasattr(image_obj, 'size'):
                logger.info(f"   -> 图像尺寸: {image_obj.size}")
            
            # 调用API
            text_result = self.call_vision_api(image_obj, prompt)
            
            # 注意：picture.py中的vision_llm_chunk期望只返回文本，不是元组
            return text_result
            
        except Exception as e:
            error_msg = f"describe_with_prompt 调用失败: {str(e)}"
            logger.info(f"❌ {error_msg}")
            logger.info(f"   图像参数类型: {type(image)}")
            logger.info(f"   图像参数内容（前100字符）: {str(image)[:100]}")
            import traceback
            traceback.print_exc()
            return error_msg

    async def call_vision_api_async(self, image_data, prompt: str = None) -> str:
        """
        异步调用您的视觉模型API - 支持真正的并发
        
        Args:
            image_data: 图像数据（PIL Image对象）
            prompt: 提示词
            
        Returns:
            str: API返回的文本结果
        """
        try:
            # 将PIL Image转换为base64编码
            image_base64 = self._image_to_base64(image_data)
            
            # 构建API请求 - 请根据您的API格式修改
            payload = self._build_api_payload(image_base64, prompt)
            headers = self._build_api_headers()
            
            logger.info(f"🌐 异步调用视觉模型API: {self.api_config['model_name']}")
            
            # 发送异步API请求
            if self.async_client:
                response = await self.async_client.post(
                    self.api_config["api_url"],
                    headers=headers,
                    json=payload
                )
                
                if response.status_code == 200:
                    result = self._parse_api_response(response.json())
                    logger.info(f"✅ 异步API调用成功，返回内容长度: {len(result)}")
                    return result
                else:
                    error_msg = f"API调用失败: {response.status_code} - {response.text}"
                    logger.info(f"❌ {error_msg}")
                    return f"API调用失败: {error_msg}"
            else:
                # 降级到同步调用
                return self.call_vision_api_sync(image_data, prompt)
                
        except Exception as e:
            error_msg = f"异步API调用异常: {str(e)}"
            logger.info(f"❌ {error_msg}")
            return error_msg

    def call_vision_api_sync(self, image_data, prompt: str = None) -> str:
        """
        同步调用视觉模型API（降级方案）
        
        Args:
            image_data: 图像数据（PIL Image对象）
            prompt: 提示词
            
        Returns:
            str: API返回的文本结果
        """
        try:
            import requests
            # 将PIL Image转换为base64编码
            image_base64 = self._image_to_base64(image_data)
            
            # 构建API请求
            payload = self._build_api_payload(image_base64, prompt)
            headers = self._build_api_headers()
            
            logger.info(f"🌐 同步调用视觉模型API: {self.api_config['model_name']}")
            
            # 发送同步API请求
            response = requests.post(
                self.api_config["api_url"],
                headers=headers,
                json=payload,
                timeout=self.api_config["timeout"]
            )
            
            if response.status_code == 200:
                result = self._parse_api_response(response.json())
                logger.info(f"✅ 同步API调用成功，返回内容长度: {len(result)}")
                return result
            else:
                error_msg = f"API调用失败: {response.status_code} - {response.text}"
                logger.info(f"❌ {error_msg}")
                return f"API调用失败: {error_msg}"
                
        except Exception as e:
            error_msg = f"同步API调用异常: {str(e)}"
            logger.info(f"❌ {error_msg}")
            return error_msg

    def call_vision_api(self, image_data, prompt: str = None) -> str:
        """
        同步接口，内部尝试使用异步以支持并发
        
        Args:
            image_data: 图像数据（PIL Image对象）
            prompt: 提示词
            
        Returns:
            str: API返回的文本结果
        """
        # 尝试在异步环境中运行
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # 如果已经在事件循环中，使用线程池
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(
                        asyncio.run, 
                        self.call_vision_api_async(image_data, prompt)
                    )
                    return future.result()
            else:
                return loop.run_until_complete(self.call_vision_api_async(image_data, prompt))
        except RuntimeError:
            # 如果没有事件循环，创建新的
            return asyncio.run(self.call_vision_api_async(image_data, prompt))

    def _image_to_base64(self, image: Image.Image) -> str:
        """将PIL Image转换为base64字符串"""
        buffered = io.BytesIO()
        # 转换为RGB格式（某些API可能不支持RGBA）
        if image.mode in ('RGBA', 'LA', 'P'):
            image = image.convert('RGB')
        image.save(buffered, format="JPEG", quality=85)
        img_str = base64.b64encode(buffered.getvalue()).decode()
        return img_str

    def _build_api_payload(self, image_base64: str, prompt: Optional[str] = None) -> Dict[str, Any]:
        """
        构建API请求payload
        请根据您的API接口格式修改此方法
        """
        # 示例payload结构 - 请根据您的API修改
        default_prompt = f"Please describe this document page in {self.lang}. Focus on the text content, layout, tables, and figures."
        
        payload = {
            "model": self.api_config["model_name"],
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "text",
                            "text": prompt or default_prompt
                        },
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/jpeg;base64,{image_base64}"
                            }
                        }
                    ]
                }
            ],
            "max_tokens": 2000,
            "temperature": 0.1
        }
        
        return payload

    def _build_api_headers(self) -> Dict[str, str]:
        """
        构建API请求头
        请根据您的API认证方式修改
        """
        headers = {
            "Content-Type": "application/json",
        }
        
        # API密钥认证 - 请根据您的API修改
        if self.api_config["api_key"]:
            headers["Authorization"] = f"Bearer {self.api_config['api_key']}"
        return headers

    def _parse_api_response(self, response_data: Dict[str, Any]) -> str:
        """
        解析API响应
        请根据您的API响应格式修改此方法
        """
        try:
            # OpenAI格式的响应解析 - 请根据您的API修改
            if "choices" in response_data:
                return response_data["choices"][0]["message"]["content"]
            
            # 如果不是预期格式，返回原始响应
            return str(response_data)
            
        except Exception as e:
            return f"解析API响应失败: {str(e)} - 原始响应: {response_data}"
    
    def __repr__(self):
        return self.__str__()


def chunk(filename, binary, lang, callback=None, **kwargs):
    img = Image.open(io.BytesIO(binary)).convert('RGB')
    doc = {
        "docnm_kwd": filename,
        "title_tks": rag_tokenizer.tokenize(re.sub(r"\.[a-zA-Z]+$", "", filename)),
        "image": img,
        "doc_type_kwd": "image"
    }
    bxs = ocr(np.array(img))
    txt = "\n".join([t[0] for _, t in bxs if t[0]])
    eng = lang.lower() == "english"
    callback(0.4, "Finish OCR: (%s ...)" % txt[:12])
    if (eng and len(txt.split()) > 32) or len(txt) > 32:
        tokenize(doc, txt, eng)
        callback(0.8, "OCR results is too long to use CV LLM.")
        return [doc]

    try:
        callback(0.4, "Use CV LLM to describe the picture.")
        cv_mdl = LLMBundle('image2text', lang=lang)
        img_binary = io.BytesIO()
        img.save(img_binary, format='JPEG')
        img_binary.seek(0)
        ans = cv_mdl.describe(img_binary.read())
        callback(0.8, "CV LLM respond: %s ..." % ans[:32])
        txt += "\n" + ans
        tokenize(doc, txt, eng)
        return [doc]
    except Exception as e:
        callback(prog=-1, msg=str(e))

    return []


async def vision_llm_chunk_async(binary, vision_model, prompt=None, callback=None):
    """
    异步版本：处理图像到markdown文本 - 支持真正的并发
    
    Args:
        binary: 图像二进制数据（PIL Image对象）
        vision_model: 视觉模型实例
        prompt: 提示词
        callback: 回调函数
        
    Returns:
        str: VLM生成的markdown文本
    """
    callback = callback or (lambda prog, msg: None)

    img = binary
    txt = ""

    try:
        with io.BytesIO() as img_binary:
            img.save(img_binary, format='JPEG')
            img_binary.seek(0)
            
            # 检查 vision_model 是否有异步方法
            if hasattr(vision_model, 'describe_with_prompt_async'):
                ans = await vision_model.describe_with_prompt_async(img_binary.read(), prompt)
            elif hasattr(vision_model, 'generate_async'):
                # 对于 API_GLM 类型的模型，需要先转换为 base64
                img_binary.seek(0)
                import base64
                base64_image = f"data:image/jpeg;base64,{base64.b64encode(img_binary.getvalue()).decode('utf-8')}"
                ans = await vision_model.generate_async(base64_image, prompt)
            else:
                # 降级到同步调用
                ans = vision_model.describe_with_prompt(img_binary.read(), prompt)
            
            ans = clean_markdown_block(ans)
            txt += "\n" + ans
            return txt

    except Exception as e:
        callback(-1, str(e))
        logger.exception(f"异步vision_llm_chunk失败: {e}")

    return ""


def vision_llm_chunk(binary, vision_model, prompt=None, callback=None):
    """
    同步版本：处理图像到markdown文本（向后兼容）
    
    内部尝试使用异步以支持并发
    
    Args:
        binary: 图像二进制数据（PIL Image对象）
        vision_model: 视觉模型实例
        prompt: 提示词
        callback: 回调函数
        
    Returns:
        str: VLM生成的markdown文本
    """
    try:
        loop = asyncio.get_event_loop()
        if loop.is_running():
            # 如果已经在事件循环中，使用线程池
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor() as executor:
                future = executor.submit(
                    asyncio.run,
                    vision_llm_chunk_async(binary, vision_model, prompt, callback)
                )
                return future.result()
        else:
            return loop.run_until_complete(
                vision_llm_chunk_async(binary, vision_model, prompt, callback)
            )
    except RuntimeError:
        # 如果没有事件循环，创建新的
        return asyncio.run(
            vision_llm_chunk_async(binary, vision_model, prompt, callback)
        )
