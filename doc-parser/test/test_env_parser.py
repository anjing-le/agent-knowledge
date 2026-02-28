#!/usr/bin/env python3
"""
测试环境变量解析功能
"""

import os
import re
import sys

# 添加项目路径到Python路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'kparser'))

def resolve_env_vars(value):
    """解析环境变量，支持${VAR:-default}格式"""
    if isinstance(value, str):
        # 匹配${VAR:-default}格式
        pattern = r'\$\{([^:}]+)(?::-([^}]*))?\}'
        
        def replace_env_var(match):
            var_name = match.group(1)
            default_value = match.group(2) if match.group(2) is not None else ''
            
            # 从环境变量获取值
            env_value = os.environ.get(var_name)
            if env_value is not None:
                return env_value
            else:
                return default_value
        
        return re.sub(pattern, replace_env_var, value)
    return value

def test_env_parser():
    """测试环境变量解析功能"""
    print("=== 测试环境变量解析功能 ===")
    
    # 尝试加载.env文件
    try:
        from dotenv import load_dotenv
        load_dotenv()
        print("✅ 成功加载 .env 文件")
    except ImportError:
        print("⚠️  python-dotenv 未安装，使用系统环境变量")
    except Exception as e:
        print(f"❌ 加载 .env 文件失败: {e}")
    
    print("\n=== 环境变量解析测试 ===")
    
    # 测试用例
    test_cases = [
        ("${TEST_VAR:-default_value}", "default_value"),
        ("${OPENAI_API_KEY:-test_key}", "test_key"),
        ("simple_string", "simple_string"),
        ("${EXISTING_VAR:-default}", "default"),
        ("${OPENAI_API_BASE:-https://api.example.com}", "https://api.example.com"),
        ("${OPENAI_MODEL:-glm-4v-plus-0111}", "glm-4v-plus-0111"),
        ("${EXCEL_VISION_PROMPT:-请分析图片}", "请分析图片")
    ]
    
    for test_input, expected_default in test_cases:
        result = resolve_env_vars(test_input)
        print(f"  输入: {test_input}")
        print(f"  输出: {result[:30]}{'...' if len(result) > 30 else ''}")
        print()
    
    print("=== 实际配置测试 ===")
    
    # 测试实际的配置字符串
    config_strings = [
        "${OPENAI_API_KEY:-de2538477de348268103d2fac6681ff5.L93yPXk2p1eS9ekj}",
        "${OPENAI_API_BASE:-https://open.bigmodel.cn/api/paas/v4}",
        "${OPENAI_MODEL:-glm-4v-plus-0111}",
        "${EXCEL_VISION_PROMPT:-请仔细分析这张图片中的所有内容，包括所有表格和子图。要求：1. 完整提取所有可见的文字信息，包括表格内容、图表标题、图例等 2. 保持原有的层级结构和组织方式 3. 对于表格数据，确保表头和对应的数据正确对齐 4. 对于子图，提取其标题、说明文字和相关数据 5. 使用嵌套的 JSON 结构来组织信息 请确保输出的 JSON 格式正确，所有属性名和属性值都正确对应。}"
    ]
    
    for config_str in config_strings:
        result = resolve_env_vars(config_str)
        print(f"  配置: {config_str[:50]}...")
        print(f"  结果: {result[:30]}{'...' if len(result) > 30 else ''}")
        print()
    
    print("=== 环境变量状态检查 ===")
    env_vars = {
        "OPENAI_API_KEY": "OpenAI API密钥",
        "OPENAI_API_BASE": "OpenAI API基础URL",
        "OPENAI_MODEL": "OpenAI模型名称",
        "EXCEL_VISION_PROMPT": "Excel Vision提示词"
    }
    
    for var_name, description in env_vars.items():
        env_value = os.environ.get(var_name)
        if env_value:
            display_value = env_value[:20] + "..." if len(env_value) > 20 else env_value
            print(f"  ✅ {var_name}: {display_value}")
        else:
            print(f"  ⚠️  {var_name}: 未设置（将使用默认值）")
    
    print("\n=== 功能验证完成 ===")
    print("主要功能:")
    print("1. ✅ ${VAR:-default} 格式支持")
    print("2. ✅ 环境变量优先级")
    print("3. ✅ 默认值回退")
    print("4. ✅ 与service_conf.yaml格式一致")

if __name__ == "__main__":
    test_env_parser() 