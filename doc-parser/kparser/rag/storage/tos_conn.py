import time
import os
from io import BytesIO
import tos
import boto3
from botocore.exceptions import ClientError
from botocore.client import Config
from kparser.common.config import TOS
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


# 本地 singleton 装饰器实现（避免循环导入）
def singleton(cls, *args, **kw):
    instances = {}

    def _singleton():
        key = str(cls) + str(os.getpid())
        if key not in instances:
            instances[key] = cls(*args, **kw)
        return instances[key]

    return _singleton


# =============================================================================
# 旧实现：基于火山引擎原生 SDK（保留作为备份）
# =============================================================================
@singleton
class RAGTOS_old(object):
    """基于火山引擎原生 TOS SDK 的实现（已弃用）"""
    
    def __init__(self):
        self.conn = None
        self.__open__()

    def __open__(self):
        try:
            if self.conn:
                self.__close__()
        except Exception:
            pass
        try:
            self.conn = tos.TosClientV2(
                TOS['ak'],
                TOS['sk'],
                TOS['endpoint'],
                TOS['region']
            )
        except Exception:
            logger.exception(f"Fail to connect {TOS['endpoint']}")

    def __close__(self):
        del self.conn
        self.conn = None

    def health(self):
        bucket, fnm, binary = TOS['bucket'], "health_check", b"_t@@@1"
        try:
            self.conn.put_object(bucket, fnm, content=binary)
            return True
        except Exception:
            logger.exception(f"Fail to health check {bucket}/{fnm}")
            self.__open__()
            return False

    def put(self, bucket, fnm, binary):
        for _ in range(3):
            try:
                # TOS无需显式创建bucket，假设已存在
                if isinstance(binary, bytes):
                    self.conn.put_object(bucket, fnm, content=binary)
                else:
                    binary.seek(0, 2)
                    length = binary.tell()
                    binary.seek(0)
                    self.conn.put_object(bucket, fnm, content=binary, content_length=length)
                return True
            except Exception:
                logger.exception(f"Fail to put {bucket}/{fnm}:")
                self.__open__()
                time.sleep(1)
        return False

    def rm(self, bucket, fnm):
        try:
            self.conn.delete_object(bucket, fnm)
        except Exception:
            logger.exception(f"Fail to remove {bucket}/{fnm}:")

    def get(self, bucket, fnm):
        for _ in range(2):
            try:
                obj = self.conn.get_object(bucket, fnm)
                return obj.read()
            except Exception:
                logger.exception(f"Fail to get {bucket}/{fnm}")
                self.__open__()
                time.sleep(1)
        return None

    def obj_exist(self, bucket, fnm):
        try:
            # TOS无直接stat_object，尝试get_object
            obj = self.conn.get_object(bucket, fnm)
            obj.close()
            return True
        except tos.exceptions.TosClientError as e:
            if hasattr(e, 'code') and e.code in ["NoSuchKey", "NoSuchBucket", "ResourceNotFound"]:
                return False
            return False
        except Exception:
            logger.exception(f"obj_exist {bucket}/{fnm} got exception")
            return False

    def get_presigned_url(self, bucket, fnm, expires):
        for _ in range(3):
            try:
                return self.conn.pre_signed_url("GET", bucket, fnm, expires)
            except Exception:
                logger.exception(f"Fail to get_presigned {bucket}/{fnm}:")
                self.__open__()
                time.sleep(1)
        return None


# =============================================================================
# 新实现：基于 S3 兼容协议（推荐使用）
# =============================================================================
@singleton
class RAGTOS(object):
    """基于 S3 协议的 TOS 实现（兼容火山引擎 TOS S3 API）"""
    
    def __init__(self):
        self.conn = None
        self.public_conn = None  # 用于生成公网预签名URL的客户端
        self.endpoint = TOS.get('endpoint', '')
        self.public_endpoint = TOS.get('public_endpoint', '')  # 公网endpoint
        self.access_key = TOS.get('ak', '')
        self.secret_key = TOS.get('sk', '')
        self.region = TOS.get('region', 'cn-beijing')
        
        # 获取签名版本配置（v2 或 v4），默认 v4
        signature_version_raw = TOS.get('signature_version', 'v4')
        # 转换为 boto3 格式：v2 -> s3v2, v4 -> s3v4
        if signature_version_raw.lower() in ['v2', 's3v2']:
            self.signature_version = 's3v2'
        elif signature_version_raw.lower() in ['v4', 's3v4']:
            self.signature_version = 's3v4'
        else:
            logger.warning(f"Unknown signature version '{signature_version_raw}', defaulting to 's3v4'")
            self.signature_version = 's3v4'
        
        # 处理内网endpoint
        # 如果 endpoint 不是 S3 兼容格式，自动转换
        # 原生格式：tos-cn-beijing.volces.com
        # S3 格式：tos-s3-cn-beijing.volces.com
        if 'tos-' in self.endpoint and 'tos-s3-' not in self.endpoint:
            self.endpoint = self.endpoint.replace('tos-', 'tos-s3-')
            logger.info(f"TOS endpoint converted to S3 compatible format: {self.endpoint}")
        
        # 确保使用 HTTPS
        if not self.endpoint.startswith('http'):
            self.endpoint = f"https://{self.endpoint}"
        
        # 处理公网endpoint（如果配置了）
        if self.public_endpoint:
            if 'tos-' in self.public_endpoint and 'tos-s3-' not in self.public_endpoint:
                self.public_endpoint = self.public_endpoint.replace('tos-', 'tos-s3-')
                logger.info(f"TOS public endpoint converted to S3 compatible format: {self.public_endpoint}")
            
            if not self.public_endpoint.startswith('http'):
                self.public_endpoint = f"https://{self.public_endpoint}"
            
            logger.info(f"✅ TOS dual endpoint mode enabled:")
            logger.info(f"  - Internal endpoint: {self.endpoint} (for upload/download)")
            logger.info(f"  - Public endpoint: {self.public_endpoint} (for presigned URLs)")
        else:
            logger.info(f"TOS single endpoint mode: {self.endpoint}")
        
        logger.info(f"TOS S3 signature version: {self.signature_version}")
        self.__open__()

    def __open__(self):
        """初始化 S3 客户端连接（内网和公网）"""
        try:
            if self.conn:
                self.__close__()
        except Exception:
            pass

        try:
            # 配置 S3 客户端，使用虚拟主机风格寻址（适合火山引擎 TOS）
            config = Config(
                s3={
                    'addressing_style': 'virtual'  # 使用虚拟主机风格
                },
                signature_version=self.signature_version  # 使用配置的签名版本
            )

            # 初始化内网客户端（用于上传下载）
            self.conn = boto3.client(
                's3',
                endpoint_url=self.endpoint,
                region_name=self.region,
                aws_access_key_id=self.access_key,
                aws_secret_access_key=self.secret_key,
                config=config
            )
            
            logger.info(f"✅ TOS internal S3 client initialized: {self.endpoint} (signature: {self.signature_version})")
            
            # 如果配置了公网endpoint，初始化公网客户端（用于生成预签名URL）
            if self.public_endpoint:
                self.public_conn = boto3.client(
                    's3',
                    endpoint_url=self.public_endpoint,
                    region_name=self.region,
                    aws_access_key_id=self.access_key,
                    aws_secret_access_key=self.secret_key,
                    config=config
                )
                logger.info(f"✅ TOS public S3 client initialized: {self.public_endpoint} (for presigned URLs)")
            
        except Exception as e:
            logger.exception(f"Fail to connect TOS: {e}")

    def __close__(self):
        """关闭连接"""
        if self.conn:
            del self.conn
            self.conn = None
        if self.public_conn:
            del self.public_conn
            self.public_conn = None

    def bucket_exists(self, bucket):
        """检查 bucket 是否存在"""
        try:
            self.conn.head_bucket(Bucket=bucket)
            return True
        except ClientError:
            return False
        except Exception as e:
            logger.exception(f"Error checking bucket {bucket}: {e}")
            return False

    def health(self):
        """健康检查"""
        bucket = TOS.get('bucket', 'health-check-bucket')
        fnm = "health_check"
        binary = b"_t@@@1"
        
        try:
            # 尝试上传一个测试文件
            self.conn.put_object(
                Bucket=bucket,
                Key=fnm,
                Body=binary
            )
            return True
        except Exception as e:
            logger.exception(f"Fail to health check {bucket}/{fnm}: {e}")
            self.__open__()
            return False

    def put(self, bucket, fnm, binary):
        """上传文件到 TOS"""
        for attempt in range(3):
            try:
                # 确保 bucket 存在（可选，TOS 通常预先创建）
                if not self.bucket_exists(bucket):
                    logger.warning(f"Bucket {bucket} does not exist, attempting to create...")
                    try:
                        self.conn.create_bucket(Bucket=bucket)
                        logger.info(f"Bucket {bucket} created successfully")
                    except Exception as e:
                        logger.warning(f"Failed to create bucket {bucket}: {e}")
                
                # 转换为 BytesIO 对象
                if isinstance(binary, bytes):
                    body = BytesIO(binary)
                else:
                    body = binary
                
                # 上传对象
                self.conn.upload_fileobj(body, bucket, fnm)
                logger.debug(f"Successfully uploaded {bucket}/{fnm}")
                return True
                
            except Exception as e:
                logger.exception(f"Fail to put {bucket}/{fnm} (attempt {attempt + 1}/3): {e}")
                self.__open__()
                time.sleep(1)
        
        return False

    def rm(self, bucket, fnm):
        """删除对象"""
        try:
            self.conn.delete_object(Bucket=bucket, Key=fnm)
            logger.debug(f"Successfully deleted {bucket}/{fnm}")
        except Exception as e:
            logger.exception(f"Fail to remove {bucket}/{fnm}: {e}")

    def get(self, bucket, fnm):
        """下载对象"""
        for attempt in range(2):
            try:
                response = self.conn.get_object(Bucket=bucket, Key=fnm)
                data = response['Body'].read()
                logger.debug(f"Successfully got {bucket}/{fnm}")
                return data
            except Exception as e:
                logger.exception(f"Fail to get {bucket}/{fnm} (attempt {attempt + 1}/2): {e}")
                self.__open__()
                time.sleep(1)
        
        return None

    def obj_exist(self, bucket, fnm):
        """检查对象是否存在"""
        try:
            self.conn.head_object(Bucket=bucket, Key=fnm)
            return True
        except ClientError as e:
            # 404 表示对象不存在
            if e.response['Error']['Code'] == '404':
                return False
            logger.warning(f"Error checking object {bucket}/{fnm}: {e}")
            return False
        except Exception as e:
            logger.exception(f"obj_exist {bucket}/{fnm} got exception: {e}")
            return False

    def get_presigned_url(self, bucket, fnm, expires):
        """
        生成预签名 URL
        
        如果配置了 public_endpoint，将使用公网客户端生成URL（外网可访问）
        否则使用内网客户端生成URL
        """
        # 选择使用哪个客户端生成预签名URL
        client_to_use = self.public_conn if self.public_conn else self.conn
        endpoint_type = "public" if self.public_conn else "internal"
        
        for attempt in range(3):
            try:
                url = client_to_use.generate_presigned_url(
                    'get_object',
                    Params={
                        'Bucket': bucket,
                        'Key': fnm
                    },
                    ExpiresIn=expires
                )
                logger.info(f"✅ Generated {endpoint_type} presigned URL for {bucket}/{fnm} (expires in {expires}s)")
                logger.debug(f"Presigned URL: {url[:100]}...")  # 只打印前100个字符
                return url
            except Exception as e:
                logger.exception(f"Fail to generate {endpoint_type} presigned URL for {bucket}/{fnm} (attempt {attempt + 1}/3): {e}")
                self.__open__()
                time.sleep(1)
        
        return None 