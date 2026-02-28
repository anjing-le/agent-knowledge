# -*- coding: utf-8 -*-
"""
文档类型配置加载器
动态从 JSON 文件加载文档类型配置
"""
import json
import os
from pathlib import Path
from typing import Dict, List, Optional
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


class DocTypeConfigLoader:
    """文档类型配置加载器"""
    
    def __init__(self):
        # 配置文件目录
        self.config_dir = Path(__file__).parent / "doc_type_configs"
        self.configs: Dict[str, Dict] = {}
        self.load_all_configs()
    
    def load_all_configs(self):
        """加载所有配置文件"""
        if not self.config_dir.exists():
            logger.error(f"配置目录不存在: {self.config_dir}")
            return
        
        # 遍历所有 JSON 文件
        for config_file in self.config_dir.glob("*.json"):
            try:
                with open(config_file, 'r', encoding='utf-8') as f:
                    config_data = json.load(f)
                    doc_type = config_data.get("doc_type")
                    
                    if doc_type:
                        self.configs[doc_type] = config_data
                        logger.info(f"加载文档类型配置: {doc_type} <- {config_file.name}")
                    else:
                        logger.warning(f"配置文件缺少 doc_type 字段: {config_file}")
                        
            except json.JSONDecodeError as e:
                logger.error(f"配置文件解析失败: {config_file}, 错误: {e}")
            except Exception as e:
                logger.error(f"加载配置文件失败: {config_file}, 错误: {e}")
        
        logger.info(f"共加载 {len(self.configs)} 个文档类型配置")
    
    def get_config(self, doc_type: str) -> Optional[Dict]:
        """获取指定文档类型的配置"""
        return self.configs.get(doc_type)
    
    def get_parse_params(self, doc_type: str) -> Optional[Dict]:
        """获取解析参数"""
        config = self.get_config(doc_type)
        if config:
            return config.get("config")
        return None
    
    def list_doc_types(self) -> List[str]:
        """列出所有支持的文档类型"""
        return list(self.configs.keys())
    
    def is_valid_doc_type(self, doc_type: str) -> bool:
        """检查文档类型是否有效"""
        return doc_type in self.configs
    
    def get_doc_type_info(self, doc_type: str) -> Optional[Dict]:
        """获取文档类型信息（包含描述）"""
        config = self.get_config(doc_type)
        if config:
            return {
                "doc_type": config.get("doc_type"),
                "description": config.get("description", ""),
                "available": True
            }
        return None
    
    def reload_configs(self):
        """重新加载所有配置"""
        logger.info("重新加载文档类型配置...")
        self.configs.clear()
        self.load_all_configs()


# 全局单例
_doc_type_loader: Optional[DocTypeConfigLoader] = None


def get_doc_type_loader() -> DocTypeConfigLoader:
    """获取文档类型配置加载器单例"""
    global _doc_type_loader
    
    if _doc_type_loader is None:
        _doc_type_loader = DocTypeConfigLoader()
    
    return _doc_type_loader


def validate_and_get_params(doc_type: str) -> tuple[bool, Optional[Dict], str]:
    """
    验证文档类型并获取参数
    
    Returns:
        (是否有效, 参数字典, 错误消息)
    """
    loader = get_doc_type_loader()
    
    if not loader.is_valid_doc_type(doc_type):
        valid_types = ", ".join(loader.list_doc_types())
        return False, None, f"不支持的文档类型: {doc_type}。支持的类型: {valid_types}"
    
    params = loader.get_parse_params(doc_type)
    if params is None:
        return False, None, f"文档类型 {doc_type} 配置异常"
    
    return True, params, ""

