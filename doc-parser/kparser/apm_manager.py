"""
APM 管理器 - 集成配置和初始化功能
非侵入式设计，只需要在应用中简单调用即可
"""
import os
from typing import Optional, Dict, Any
from fastapi import FastAPI
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)

def _load_env_smartly():
    """
    智能加载环境变量：
    1. 如果存在 .env 文件，先加载系统环境变量，再加载 .env（.env 中的变量可以引用环境变量）
    2. 如果不存在 .env 文件，直接使用系统环境变量
    3. 不影响已经加载过 dotenv 的应用
    """
    env_file_path = '.env'
    if os.path.exists(env_file_path):
        try:
            # 尝试导入 dotenv，如果失败则跳过
            from dotenv import load_dotenv
            # 加载 .env 文件，不覆盖已存在的环境变量
            # override=False 确保系统环境变量优先级更高
            load_dotenv(env_file_path, override=False)
        except ImportError:
            # 如果没有安装 python-dotenv，跳过 .env 文件加载
            pass
        except Exception:
            # 如果加载失败，也跳过
            pass
# 初始化时智能加载环境变量
_load_env_smartly()
# 全局变量
APM_CLIENT = None
APM_ENABLED = False
# 默认 APM 配置
DEFAULT_APM_CONFIG = {
    # 服务配置
    'SERVICE_NAME': os.getenv('ELASTIC_APM_SERVICE_NAME', 'knowledge-center-parser-prod'),
    'SERVICE_VERSION': os.getenv('ELASTIC_APM_SERVICE_VERSION', '1.0.0'),
    'ENVIRONMENT': os.getenv('ELASTIC_APM_ENVIRONMENT', 'prod'),
    # APM Server 配置
    'SERVER_URL': os.getenv('ELASTIC_APM_SERVER_URL', 'http://localhost:8200'),
    # 认证配置
    'SECRET_TOKEN': os.getenv('ELASTIC_APM_SECRET_TOKEN', ''),
    'API_KEY': os.getenv('ELASTIC_APM_API_KEY', ''),
    # 监控配置
    'DEBUG': os.getenv('ELASTIC_APM_DEBUG', 'True').lower() == 'true',
    'TRANSACTION_SAMPLE_RATE': float(os.getenv('ELASTIC_APM_TRANSACTION_SAMPLE_RATE', '1.0')),
    'CAPTURE_BODY': os.getenv('ELASTIC_APM_CAPTURE_BODY', 'all'),
    'CAPTURE_HEADERS': os.getenv('ELASTIC_APM_CAPTURE_HEADERS', 'True').lower() == 'true',
    # 全局标签
    'GLOBAL_LABELS': {
        'team': 'backend',
        'project': 'apm-demo'
    }
}
def get_apm_config() -> Dict[str, Any]:
    """获取 APM 配置"""
    return DEFAULT_APM_CONFIG.copy()

def init_apm(app: FastAPI, config: Optional[Dict[str, Any]] = None) -> bool:
    """
    初始化 APM 监控
    Args:
        app: FastAPI 应用实例
        config: 可选的 APM 配置字典，如果不提供则使用默认配置
    Returns:
        bool: 初始化是否成功
    """
    global APM_CLIENT, APM_ENABLED
    # 检查是否禁用 APM
    if os.getenv('DISABLE_APM', '').lower() == 'true':
        logger.info("✓ APM 已禁用 (DISABLE_APM=true)")
        return False
    try:
        # 动态导入 APM 模块
        from elasticapm.contrib.starlette import make_apm_client, ElasticAPM
        # 使用提供的配置或默认配置
        final_config = config or get_apm_config()
        # 创建 APM 客户端
        APM_CLIENT = make_apm_client(final_config)
        # 添加 APM 中间件
        app.add_middleware(ElasticAPM, client=APM_CLIENT)
        APM_ENABLED = True
        logger.info(f"✓ Elastic APM 已启用 - 服务名: {final_config['SERVICE_NAME']}")
        logger.info(f"  └─ Server: {final_config['SERVER_URL']}")
        logger.info(f"  └─ Environment: {final_config['ENVIRONMENT']}")
        return True
    except ImportError as e:
        logger.info(f"⚠ APM 模块导入失败: {e}")
        logger.info("  请安装: pip install elastic-apm")
        return False
    except Exception as e:
        logger.info(f"⚠ Elastic APM 初始化失败: {e}")
        logger.info("  应用将继续运行，但不会发送 APM 数据")
        return False

def is_apm_enabled() -> bool:
    """检查 APM 是否已启用"""
    return APM_ENABLED

def get_apm_client():
    """获取 APM 客户端实例"""
    return APM_CLIENT

def configure_apm(**kwargs) -> Dict[str, Any]:
    """
    配置 APM 参数
    Args:
        **kwargs: APM 配置参数
    Returns:
        Dict[str, Any]: 更新后的配置
    """
    config = get_apm_config()
    config.update(kwargs)
    return config
