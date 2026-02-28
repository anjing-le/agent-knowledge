import base64
import os
import re
import time
import enum
import copy
import pdfplumber
import pandas as pd
import urllib.request
import requests
import tos
from urllib.parse import quote
from PIL import Image
from io import BytesIO
from numpy import ndarray
from pandas import DataFrame
from pydantic import BaseModel, Field, ConfigDict, validator
from typing import Optional, Iterable, List
from abc import abstractmethod
from kparser.common.config import SERVICE, TOS
from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger
from kparser.rag.storage.tos_conn import RAGTOS

logger = get_logger(__name__)

def thumbnail_img(filename, blob):
    """
    MySQL LongText max length is 65535
    """
    from pdf2image import convert_from_bytes
    
    filename = filename.lower()
    if re.match(r".*\.pdf$", filename):
        # 🔥 使用pdf2image生成缩略图，更稳定
        resolution = 32
        img = None
        for _ in range(10):
            try:
                # 转换第一页
                images = convert_from_bytes(blob, dpi=resolution, first_page=1, last_page=1, fmt='png')
                if images:
                    buffered = BytesIO()
                    images[0].save(buffered, format="png")
                    img = buffered.getvalue()
                    if len(img) >= 64000 and resolution >= 2:
                        resolution = resolution / 2
                    else:
                        break
                else:
                    break
            except Exception as e:
                logger.warning(f"生成PDF缩略图失败 (resolution={resolution}): {e}")
                break
        return img

    elif re.match(r".*\.(jpg|jpeg|png|tif|gif|icon|ico|webp)$", filename):
        image = Image.open(BytesIO(blob))
        image.thumbnail((30, 30))
        buffered = BytesIO()
        image.save(buffered, format="png")
        return buffered.getvalue()

    elif re.match(r".*\.(ppt|pptx)$", filename):
        import aspose.slides as slides
        import aspose.pydrawing as drawing
        try:
            with slides.Presentation(BytesIO(blob)) as presentation:
                buffered = BytesIO()
                scale = 0.03
                img = None
                for _ in range(10):
                    # https://reference.aspose.com/slides/python-net/aspose.slides/slide/get_thumbnail/#float-float
                    presentation.slides[0].get_thumbnail(scale, scale).save(
                        buffered, drawing.imaging.ImageFormat.png)
                    img = buffered.getvalue()
                    if len(img) >= 64000:
                        scale = scale / 2.0
                        buffered = BytesIO()
                    else:
                        break
                return img
        except Exception:
            pass
    return None


def thumbnail(filename, blob):
    img = thumbnail_img(filename, blob)
    if img is not None:
        return SERVICE["IMG_BASE64_PREFIX"] + \
            base64.b64encode(img).decode("utf-8")
    else:
        return ''


def traversal_files(base):
    for root, ds, fs in os.walk(base):
        for f in fs:
            fullname = os.path.join(root, f)
            yield fullname


def read_file_from_original_url(url_path, retries=1, delay=1):
    """
    读取指定 URL 指向的文本文件，并返回内容。如果读取失败，则会进行重试。
    优先尝试使用 requests.get 下载，失败后再使用 TOS 下载。

    :param url_path: 文本文件的 URL 路径
    :param retries: 最大重试次数
    :param delay: 每次重试的间隔时间（秒）
    :return: 文件内容字符串
    :raises Exception: 如果读取过程中出现问题
    """
    # 方法1：先尝试使用 requests.get 下载（适用于公网可访问的URL）
    attempt = 0
    while attempt <= retries:
        try:
            logger.info(f"尝试使用 requests.get 下载: {url_path}")
            response = requests.get(url_path, timeout=30)
            response.raise_for_status()  # 如果状态码不是 200，抛出异常
            logger.info(f"✅ 使用 requests.get 下载成功: {url_path}")
            return response.content
        except Exception as e:
            attempt += 1
            logger.warning(f"requests.get 下载失败 (尝试 {attempt}/{retries+1}): {e}")
            if attempt <= retries:
                time.sleep(delay)
    
    # 方法2：如果 requests.get 失败，尝试使用 TOS 下载（适用于 bucket/key 格式或预签名URL）
    if SERVICE["use_storage"] == "TOS":
        try:
            logger.info(f"尝试使用 TOS 下载: {url_path}")
            content = download_tos_object_to_memory(url_path)
            logger.info(f"✅ 使用 TOS 下载成功: {url_path}")
            return content
        except Exception as e:
            logger.error(f"TOS 下载失败: {e}")
    
    # 方法3：回退到 urllib（适用于其他协议）
    attempt = 0
    while attempt <= retries:
        try:
            logger.info(f"尝试使用 urllib 下载: {url_path}")
            encoded_url = quote(url_path, safe=':/')
            with urllib.request.urlopen(encoded_url) as response:
                content = response.read()
            logger.info(f"✅ 使用 urllib 下载成功: {url_path}")
            return content
        except Exception as e:
            attempt += 1
            logger.warning(f"urllib 下载失败 (尝试 {attempt}/{retries+1}): {e}")
            if attempt <= retries:
                time.sleep(delay)
    
    # 所有方法都失败
    raise Exception(f"❌ 所有下载方法都失败，无法从 {url_path} 读取文件")


def upload2cdn(file_name, object, cdn_host=None) -> str:
    CDN_HOST = cdn_host or SERVICE["cdn_host"]
    header = {
        "Referer": CDN_HOST+"/",
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
    url = f"{CDN_HOST}"
    files = {'file': (file_name, object)}
    logger.debug(f"uploading {file_name} to {CDN_HOST}")
    resp = requests.post(url, headers=header, files=files)
    resp.raise_for_status()
    data = resp.json()
    if not data.get("success"):
        raise Exception(f"upload2cdn failed: {data['msg']}")
    if "link" in data["data"].keys():
        return data["data"]["link"]
    else:
        raise Exception(f"upload2cdn failed: {data}")


def analyse_table(binary_content):
    xl = pd.ExcelFile(BytesIO(binary_content))
    for name in xl.sheet_names:
        df = pd.read_excel(xl, name)
        page_index = 1
        content = data2markdown(df)
        yield TableChunk(content=content, page_idx=page_index, data=df)
        page_index += 1


def data2markdown(data):
    if isinstance(data, ndarray):
        data = DataFrame(data)
    data.fillna("", inplace=True)
    markdown = data.to_markdown(index=False)
    return markdown


class ContentType(str, enum.Enum):
    TABLE = "TABLE"
    TITLE = "TITLE"
    TEXT = "TEXT"
    PARSED_TABLE = "PARSED_TABLE"
    IMAGE = "IMAGE"
    OUTLINE = "OUTLINE"
    DICT = "DICT"


class Chunk(BaseModel):
    id: str = Field(description="chunk的id", default_factory=lambda: get_random_uuid())
    content: str = Field(description="chunk的内容")
    content_type: ContentType = Field(description="chunk类型", default=ContentType.TEXT)
    page_idx: list[int] = Field(description="chunk在文档中的页码,从1开始", default=[1])
    extra_data: dict = Field(description="额外的信息", default_factory=dict)

    def to_json(self):
        return self.model_dump(exclude_none=True)

    @classmethod
    def from_dict(cls, data: dict) -> "Chunk":
        content_type = data["content_type"]
        cls: type[BaseModel] = _content_type2cls[content_type]
        return cls.model_validate(data)


class TextChunk(Chunk):
    content_type: ContentType = ContentType.TEXT


class TableChunk(Chunk):
    model_config = ConfigDict(arbitrary_types_allowed=True)
    content_type: ContentType = ContentType.TABLE
    data: Optional[ndarray | DataFrame] = Field(description="表格的数据, 二维数组或pandas的dataframe", default=None)
    content: str = Field(description="表格的文字描述, markdown格式", default=None)

    def to_json(self):
        rs = self.model_dump(exclude_none=True, exclude={"data"})
        return rs

    @validator('data', pre=True, always=True)
    def set_content_based_on_data(cls, v, values, **kwargs):
        if values.get("content") is None:
            content = data2markdown(v)
            values["content"] = content
            return v
        return v


_content_type2cls = {
    ContentType.TABLE: TableChunk
}


def merge_chunks(chunks: Iterable[Chunk], joiner="\n") -> Iterable[Chunk]:
    acc = ""
    cur_text_page_idx = [1]
    for chunk in chunks:
        if chunk.content_type == ContentType.TEXT and not chunk.content:
            continue
        # logger.debug(f"chunk: {chunk}")
        if chunk.content_type == ContentType.TEXT:
            if chunk.page_idx[0] == cur_text_page_idx[0]:
                acc = joiner.join([acc, chunk.content])
            else:
                if acc.strip():
                    yield TextChunk(content=acc, page_idx=cur_text_page_idx)
                acc = chunk.content
                cur_text_page_idx = chunk.page_idx

        else:
            if acc.strip():
                yield TextChunk(content=acc, page_idx=cur_text_page_idx)
            acc = ""
            yield chunk
    if acc.strip():
        yield TextChunk(content=acc, page_idx=cur_text_page_idx)


def get_batched_data(data: Iterable, batch_size: int) -> Iterable[list]:
    """将数据按照batch_size分组

    Args:
        data (Iterable): 待分组数据
        batch_size (int): 分组大小

    Yields:
        _type_: batch数据
    """
    batch = []
    for item in data:
        batch.append(item)
        if len(batch) == batch_size:
            yield batch
            batch = []
    if batch:
        yield batch


def merge_cut_texts(texts: Iterable[str], min_len: int, max_len: int) -> Iterable[str]:
    acc = ""
    for text in texts:
        acc += text
        if len(acc) < min_len:
            continue
        if len(acc) > max_len:
            for item in get_batched_data(acc, max_len):
                item = "".join(item)
                if len(item) >= min_len:
                    yield item
                    acc = ""
                else:
                    acc = item
        else:
            yield acc
            acc = ""
    if acc:
        yield acc


class AbstractSplitter:
    def __init__(self, invalid_chunks: List[str] = []):
        self.invalid_chunks = invalid_chunks

    @abstractmethod
    def split(self, text: str) -> List[str]:
        raise NotImplementedError

    def split_chunk(self, chunk: Chunk) -> Iterable[Chunk]:
        if chunk.content_type == ContentType.IMAGE or chunk.content_type == ContentType.OUTLINE:
            # 图片暂时不做切割
            yield chunk
        else:

            for content in self.split(chunk.content):
                # content = content.strip()
                if content in self.invalid_chunks:
                    continue
                if content.strip():
                    chunk_dict = copy.copy(chunk.to_json())
                    chunk_dict["content"] = content
                    del chunk_dict["id"]
                    yield Chunk.from_dict(chunk_dict)


class BaseSplitter(AbstractSplitter):

    def __init__(self,
                 separator="\n|。|？|\?|！|！|，",
                 parse_table=False,
                 max_len=200,
                 min_len=20,
                 **kwargs):
        super().__init__(**kwargs)
        self.parse_table = parse_table
        self.separator = separator
        self.max_len = max_len
        self.min_len = min_len

    def _parse_text(self, text: str) -> str:
        return text

    def split(self, text: str) -> Iterable[str]:
        texts = re.split(self.separator, text)
        texts = [self._parse_text(t) for t in texts]
        mcs = list(merge_cut_texts(texts, self.min_len, self.max_len))
        yield from mcs


def get_tos_client():
    logger.info("upload2tos: TOS={}".format(TOS))
    ak = TOS['ak']
    sk = TOS['sk']
    endpoint = TOS['endpoint']
    region = TOS['region']
    return tos.TosClientV2(ak, sk, endpoint, region)


def parse_presigned_url(url: str) -> tuple:
    """
    从预签名 URL 或普通 URL 中解析出 bucket 和 object_key
    
    支持的格式：
    1. 虚拟主机风格预签名 URL: https://bucket.tos-s3-cn-beijing.volces.com/path/to/file.json?X-Amz-Algorithm=...
    2. 虚拟主机风格 S3 URL: https://bucket.tos-s3-cn-beijing.volces.com/path/to/file.json
    3. 路径风格 S3 URL: https://tos-s3-cn-beijing.volces.com/bucket/path/to/file.json
    4. bucket/key 格式: bucket_name/path/to/file.json
    
    Args:
        url: 待解析的 URL
    
    Returns:
        tuple: (bucket_name, object_key) 或 (None, None) 如果解析失败
    """
    from urllib.parse import urlparse, unquote
    
    try:
        # 判断是否为完整的 URL
        if url.startswith("http://") or url.startswith("https://"):
            # 先解析 URL 结构（不解码），以正确处理特殊字符
            parsed = urlparse(url)
            
            # 判断是虚拟主机风格还是路径风格
            # 虚拟主机风格：bucket.tos-s3-cn-beijing.volces.com
            # 路径风格：tos-s3-cn-beijing.volces.com/bucket/...
            
            hostname_parts = parsed.hostname.split('.')
            first_part = hostname_parts[0]
            
            # 如果 hostname 的第一部分包含 'tos-s3-' 或 'tos-'，说明这是 endpoint，使用路径风格
            if 'tos-s3-' in first_part or first_part.startswith('tos-'):
                # 路径风格：bucket 在路径的第一部分
                # 先解码路径，再分割
                decoded_path = unquote(parsed.path.lstrip('/'))
                path_parts = decoded_path.split('/', 1)
                if len(path_parts) >= 2:
                    bucket_name = path_parts[0]
                    object_key = path_parts[1]
                elif len(path_parts) == 1:
                    # 只有 bucket，没有 key
                    bucket_name = path_parts[0]
                    object_key = ""
                else:
                    bucket_name = None
                    object_key = decoded_path
                
                logger.debug(f"Parsed path-style S3 URL: bucket={bucket_name}, key={object_key}")
            else:
                # 虚拟主机风格：bucket 在 hostname 的第一部分
                bucket_name = first_part
                # 解码路径部分
                object_key = unquote(parsed.path.lstrip('/'))
                logger.debug(f"Parsed virtual-hosted-style S3 URL: bucket={bucket_name}, key={object_key}")
            
            return bucket_name, object_key
        else:
            # bucket/key 格式，不解码（假设传入的就是 TOS 上的实际 key）
            parts = url.split("/", 1)
            if len(parts) == 2:
                bucket_name, object_key = parts
                logger.debug(f"Parsed bucket/key format (no decode): bucket={bucket_name}, key={object_key}")
                return bucket_name, object_key
            else:
                logger.warning(f"Failed to parse URL: {url}")
                return None, None
    except Exception as e:
        logger.error(f"Error parsing URL {url}: {e}")
        return None, None


def upload2tos(object_key, local_file_path=None, file_bytes=None, bucket_name=None, generate_presigned_url=False, presigned_expires=3600):
    """
    上传本地文件或内存二进制内容到TOS，返回TOS对象URL或预签名URL。
    
    Args:
        object_key: TOS 对象键（路径）
        local_file_path: 本地文件路径（可选）
        file_bytes: 文件字节内容（可选）
        bucket_name: 存储桶名称（可选，默认使用配置中的bucket）
        generate_presigned_url: 是否生成预签名URL（默认False，返回 bucket/key 格式）
        presigned_expires: 预签名URL有效期（秒），默认3600秒（1小时）
    
    Returns:
        str: 如果 generate_presigned_url=True，返回带签名的可直接访问URL
             如果 generate_presigned_url=False，返回 "bucket_name/object_key" 格式
    
    Note:
        ak、sk、endpoint、region只能从config配置中获取。
    """
    bucket_name = bucket_name or TOS['bucket']
    try:
        tos_client = RAGTOS()
        if file_bytes is not None:
            tos_client.put(bucket_name, object_key, file_bytes)
        elif local_file_path is not None:
            with open(local_file_path, 'rb') as f:
                tos_client.put(bucket_name, object_key, f.read())
        else:
            raise ValueError("必须提供local_file_path或file_bytes")
        
        # 根据参数决定返回格式
        if generate_presigned_url:
            # 生成预签名URL（有效期为 presigned_expires 秒）
            presigned_url = tos_client.get_presigned_url(bucket_name, object_key, presigned_expires)
            if presigned_url:
                logger.info(f"✅ Generated presigned URL for {bucket_name}/{object_key} (expires in {presigned_expires}s)")
                return presigned_url
            else:
                logger.warning(f"⚠️ Failed to generate presigned URL, falling back to bucket/key format")
                return f"{bucket_name}/{object_key}"
        else:
            # 返回传统的 bucket/key 格式
            return f"{bucket_name}/{object_key}"
    except Exception as e:
        raise RuntimeError(f"TOS上传失败: {e}")


def download_from_tos_to_local(tos_url, local_file_path):
    bucket_name, folder, filename = tos_url.split("/")
    object_key = f"{folder}/{filename}"
    bucket_name = bucket_name or TOS['bucket']
    try:
        tos_client = RAGTOS()
        data = tos_client.get(bucket_name, object_key)
        with open(local_file_path, 'wb') as f:
            f.write(data)
        return local_file_path
    except Exception as e:
        raise RuntimeError(f"TOS下载失败: {e}")


def download_tos_object_to_memory(tos_url):
    """
    从TOS下载对象到内存，返回bytes内容。
    
    支持的格式：
    1. 预签名 URL: https://bucket.tos-s3-cn-beijing.volces.com/path/to/file.json?X-Amz-Algorithm=...
    2. 标准 S3 URL: https://bucket.tos-s3-cn-beijing.volces.com/path/to/file.json
    3. bucket/key 格式: bucket_name/path/to/file.json
    """
    logger.info(f"Downloading from TOS: {tos_url}")
    
    # 1. 尝试使用新的 parse_presigned_url 解析（支持预签名URL、S3 URL和 bucket/key 格式）
    bucket_name, object_key = parse_presigned_url(tos_url)
    
    if bucket_name and object_key:
        # 成功解析
        logger.info(f"✅ Parsed URL: bucket={bucket_name}, key={object_key}")
    else:
        # 2. 回退到旧的解析方式（兼容性）
        logger.warning(f"⚠️ Failed to parse with new method, using legacy parser")
        tos_url_array = tos_url.split("/")
        bucket_name, folder, filename = tos_url_array[0], "/".join(tos_url_array[1:-1]), tos_url_array[-1]
        object_key = f"{folder}/{filename}" if folder else filename
        logger.info(f"Legacy parse: bucket={bucket_name}, key={object_key}")
    
    bucket_name = bucket_name or TOS['bucket']
    
    try:
        tos_client = RAGTOS()
        data = tos_client.get(bucket_name, object_key)
        
        # 检查返回数据是否有效
        if data is None:
            logger.error(f"❌ TOS returned None for {bucket_name}/{object_key}")
            raise RuntimeError(f'TOS 返回空数据: bucket={bucket_name}, key={object_key}')
        
        logger.info(f"✅ Successfully downloaded {len(data)} bytes from {bucket_name}/{object_key}")
        return data
    except Exception as e:
        logger.error(f"❌ Failed to download from TOS: {bucket_name}/{object_key}, error: {e}")
        raise RuntimeError(f'下载TOS对象失败: {e}')
