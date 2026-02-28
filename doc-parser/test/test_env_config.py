#!/usr/bin/env python3
"""
测试环境变量配置的脚本
"""

import os
import sys

# 添加项目路径到Python路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'kparser'))

def test_env_config():
    """测试环境变量配置"""
    print("=== 测试环境变量配置 ===")
    
    # 尝试加载.env文件
    try:
        from dotenv import load_dotenv
        load_dotenv()
        print("✅ 成功加载 .env 文件")
    except ImportError:
        print("⚠️  python-dotenv 未安装，使用系统环境变量")
    except Exception as e:
        print(f"❌ 加载 .env 文件失败: {e}")
    
    # 检查环境变量
    env_vars = {
        "OPENAI_API_KEY": "OpenAI API密钥",
        "OPENAI_API_BASE": "OpenAI API基础URL", 
        "OPENAI_MODEL": "OpenAI模型名称",
        "EXCEL_VISION_PROMPT": "Excel Vision提示词"
    }
    
    print("\n=== 环境变量状态 ===")
    for var_name, description in env_vars.items():
        value = os.environ.get(var_name)
        if value:
            # 对于敏感信息，只显示前几个字符
            if "KEY" in var_name and len(value) > 10:
                display_value = value[:10] + "..." + value[-4:]
            else:
                display_value = value[:50] + "..." if len(value) > 50 else value
            print(f"✅ {var_name}: {display_value}")
        else:
            print(f"❌ {var_name}: 未设置")
    
    # 测试导入excel_vision_parser模块
    print("\n=== 测试模块导入 ===")
    try:
        from kparser.parserground.parser.excel_vision_parser import (
            openai_api_key, 
            openai_api_base, 
            openai_model, 
            excel_vision_prompt
        )
        print("✅ 成功导入 excel_vision_parser 模块")
        print(f"   - API Key: {openai_api_key[:10]}...{openai_api_key[-4:] if len(openai_api_key) > 10 else ''}")
        print(f"   - API Base: {openai_api_base}")
        print(f"   - Model: {openai_model}")
        print(f"   - Prompt长度: {len(excel_vision_prompt)} 字符")
    except Exception as e:
        print(f"❌ 导入模块失败: {e}")

if __name__ == "__main__":
    test_env_config() 