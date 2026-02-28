#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

def test_config_loading():
    """测试配置加载功能"""
    print("=== 测试配置加载功能 ===")
    
    try:
        from kparser.common.config import VISION, TOS, SERVICE
        
        print("✅ 成功导入配置模块")
        
        # 测试VISION配置
        print("\n--- VISION配置测试 ---")
        print(f"VLM API Key: {VISION.get('vlm_api_key', 'Not found')[:10]}...")
        print(f"VLM API Base: {VISION.get('vlm_api_base', 'Not found')}")
        print(f"VLM Model: {VISION.get('vlm_model', 'Not found')}")
        print(f"Excel Vision Prompt: {VISION.get('excel_vision_prompt', 'Not found')[:50]}...")
        print(f"Image Prompt: {VISION.get('image_prompt', 'Not found')[:50]}...")
        print(f"Table Prompt: {VISION.get('table_prompt', 'Not found')[:50]}...")
        
        # 测试TOS配置
        print("\n--- TOS配置测试 ---")
        print(f"TOS AK: {TOS.get('ak', 'Not found')[:10]}...")
        print(f"TOS Region: {TOS.get('region', 'Not found')}")
        print(f"TOS Endpoint: {TOS.get('endpoint', 'Not found')}")
        print(f"TOS Bucket: {TOS.get('bucket', 'Not found')}")
        print(f"TOS Temp Object Key Prefix: {TOS.get('temp_object_key_prefix', 'Not found')}")
        
        # 测试SERVICE配置
        print("\n--- SERVICE配置测试 ---")
        print(f"Environment: {SERVICE.get('environment', 'Not found')}")
        print(f"Use Storage: {SERVICE.get('use_storage', 'Not found')}")
        print(f"Max Job Number: {SERVICE.get('max_job_number', 'Not found')}")
        print(f"Time Out: {SERVICE.get('time_out', 'Not found')}")
        
        return True
        
    except Exception as e:
        print(f"❌ 配置加载测试失败: {e}")
        return False

def test_excel_vision_parser_config():
    """测试excel_vision_parser.py中的配置读取"""
    print("\n=== 测试Excel Vision Parser配置 ===")
    
    try:
        # 直接读取excel_vision_parser.py文件中的配置变量
        import ast
        
        with open('kparser/parserground/parser/excel_vision_parser.py', 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 提取配置变量
        lines = content.split('\n')
        config_vars = {}
        
        for line in lines:
            if line.strip().startswith('vlm_api_key = '):
                config_vars['vlm_api_key'] = line.split('=')[1].strip()
            elif line.strip().startswith('vlm_api_base = '):
                config_vars['vlm_api_base'] = line.split('=')[1].strip()
            elif line.strip().startswith('vlm_model = '):
                config_vars['vlm_model'] = line.split('=')[1].strip()
            elif line.strip().startswith('excel_vision_prompt = '):
                config_vars['excel_vision_prompt'] = line.split('=', 1)[1].strip()
        
        print("✅ 成功读取excel_vision_parser配置")
        print(f"VLM API Key: {config_vars.get('vlm_api_key', 'Not found')[:20]}...")
        print(f"VLM API Base: {config_vars.get('vlm_api_base', 'Not found')}")
        print(f"VLM Model: {config_vars.get('vlm_model', 'Not found')}")
        print(f"Excel Vision Prompt: {config_vars.get('excel_vision_prompt', 'Not found')[:50]}...")
        
        # 验证配置是否正确读取
        if all(key in config_vars for key in ['vlm_api_key', 'vlm_api_base', 'vlm_model', 'excel_vision_prompt']):
            print("✅ 所有配置变量都已正确读取")
            return True
        else:
            print("❌ 部分配置变量缺失")
            return False
        
    except Exception as e:
        print(f"❌ Excel Vision Parser配置测试失败: {e}")
        return False

def test_environment_variable_override():
    """测试环境变量覆盖功能"""
    print("\n=== 测试环境变量覆盖功能 ===")
    
    try:
        from kparser.common.config import VISION
        
        # 设置测试环境变量
        test_api_key = "test_api_key_12345"
        os.environ["VLM_API_KEY"] = test_api_key
        
        # 重新加载配置
        from kparser.common.config import read_config, resolve_env_vars_recursive
        config = read_config()
        config = resolve_env_vars_recursive(config)
        
        vision_config = config.get("vision", {})
        actual_api_key = vision_config.get("vlm_api_key")
        
        if actual_api_key == test_api_key:
            print("✅ 环境变量覆盖功能正常")
        else:
            print(f"❌ 环境变量覆盖失败，期望: {test_api_key}, 实际: {actual_api_key}")
            return False
        
        # 清理测试环境变量
        if "VLM_API_KEY" in os.environ:
            del os.environ["VLM_API_KEY"]
        
        return True
        
    except Exception as e:
        print(f"❌ 环境变量覆盖测试失败: {e}")
        return False

if __name__ == "__main__":
    print("开始测试配置加载功能...")
    
    # 测试基本配置加载
    config_test = test_config_loading()
    
    # 测试excel_vision_parser配置
    parser_test = test_excel_vision_parser_config()
    
    # 测试环境变量覆盖
    env_test = test_environment_variable_override()
    
    # 总结
    print("\n=== 测试总结 ===")
    if config_test and parser_test and env_test:
        print("✅ 所有测试通过")
        print("✅ 配置加载功能正常")
        print("✅ 移除load_dotenv后系统仍然正常工作")
        print("✅ 环境变量覆盖功能正常")
    else:
        print("❌ 部分测试失败")
        if not config_test:
            print("  - 基本配置加载测试失败")
        if not parser_test:
            print("  - Excel Vision Parser配置测试失败")
        if not env_test:
            print("  - 环境变量覆盖测试失败") 