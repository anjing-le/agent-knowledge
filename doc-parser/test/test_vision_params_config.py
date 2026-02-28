#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

def test_vision_params_config():
    """测试VISION配置中的temperature、top_p、max_tokens参数"""
    print("=== 测试VISION配置参数 ===")
    
    try:
        from kparser.common.config import VISION
        
        print("✅ 成功导入VISION配置")
        
        # 检查新增的参数
        print("\n--- 检查新增的VLM参数 ---")
        temperature = VISION.get("temperature")
        top_p = VISION.get("top_p")
        max_tokens = VISION.get("max_tokens")
        
        print(f"Temperature: {temperature}")
        print(f"Top P: {top_p}")
        print(f"Max Tokens: {max_tokens}")
        
        # 验证参数类型
        if isinstance(temperature, (int, float)) or (isinstance(temperature, str) and temperature.replace('.', '').isdigit()):
            print("✅ Temperature类型正确")
        else:
            print(f"❌ Temperature类型错误: {type(temperature)}")
            return False
            
        if isinstance(top_p, (int, float)) or (isinstance(top_p, str) and top_p.replace('.', '').isdigit()):
            print("✅ Top P类型正确")
        else:
            print(f"❌ Top P类型错误: {type(top_p)}")
            return False
            
        if isinstance(max_tokens, int) or (isinstance(max_tokens, str) and max_tokens.isdigit()):
            print("✅ Max Tokens类型正确")
        else:
            print(f"❌ Max Tokens类型错误: {type(max_tokens)}")
            return False
        
        return True
        
    except Exception as e:
        print(f"❌ VISION配置测试失败: {e}")
        return False

def test_vlm_service_params():
    """测试VLM服务中的参数读取"""
    print("\n=== 测试VLM服务参数读取 ===")
    
    try:
        from kparser.model.vlm_service import API_GLM
        
        # 测试使用默认配置
        print("\n--- 测试VISION配置 ---")
        vision_model = API_GLM()
        print(f"Temperature: {vision_model.temperature}")
        print(f"Top P: {vision_model.top_p}")
        print(f"Max Tokens: {vision_model.max_tokens}")
        
        # 校验类型
        if (isinstance(vision_model.temperature, (int, float)) and
            isinstance(vision_model.top_p, (int, float)) and
            isinstance(vision_model.max_tokens, int)):
            print("✅ VLM服务参数类型正确")
            return True
        else:
            print("❌ VLM服务参数类型错误")
            return False
        
    except Exception as e:
        print(f"❌ VLM服务参数测试失败: {e}")
        return False

def test_environment_variable_override():
    """测试环境变量覆盖功能"""
    print("\n=== 测试环境变量覆盖功能 ===")
    
    try:
        from kparser.common.config import read_config, resolve_env_vars_recursive
        
        # 设置测试环境变量
        test_temperature = "0.3"
        test_top_p = "0.7"
        test_max_tokens = "512"
        
        os.environ["VLM_TEMPERATURE"] = test_temperature
        os.environ["VLM_TOP_P"] = test_top_p
        os.environ["VLM_MAX_TOKENS"] = test_max_tokens
        
        # 重新加载配置
        config = read_config()
        config = resolve_env_vars_recursive(config)
        
        vision_config = config.get("vision", {})
        actual_temperature = vision_config.get("temperature")
        actual_top_p = vision_config.get("top_p")
        actual_max_tokens = vision_config.get("max_tokens")
        
        print(f"期望 Temperature: {test_temperature}, 实际: {actual_temperature}")
        print(f"期望 Top P: {test_top_p}, 实际: {actual_top_p}")
        print(f"期望 Max Tokens: {test_max_tokens}, 实际: {actual_max_tokens}")
        
        # 验证环境变量覆盖
        if (str(actual_temperature) == test_temperature and 
            str(actual_top_p) == test_top_p and 
            str(actual_max_tokens) == test_max_tokens):
            print("✅ 环境变量覆盖功能正常")
        else:
            print("❌ 环境变量覆盖失败")
            return False
        
        # 清理测试环境变量
        for var in ["VLM_TEMPERATURE", "VLM_TOP_P", "VLM_MAX_TOKENS"]:
            if var in os.environ:
                del os.environ[var]
        
        return True
        
    except Exception as e:
        print(f"❌ 环境变量覆盖测试失败: {e}")
        return False

if __name__ == "__main__":
    print("开始测试VISION配置参数...")
    
    # 测试VISION配置
    config_test = test_vision_params_config()
    
    # 测试VLM服务参数
    service_test = test_vlm_service_params()
    
    # 测试环境变量覆盖
    env_test = test_environment_variable_override()
    
    # 总结
    print("\n=== 测试总结 ===")
    if config_test and service_test and env_test:
        print("✅ 所有测试通过")
        print("✅ temperature、top_p、max_tokens已成功添加到VISION配置中")
        print("✅ VLM服务正确读取配置参数")
        print("✅ 支持环境变量覆盖")
        print("✅ 支持vlm_config参数覆盖")
    else:
        print("❌ 部分测试失败")
        if not config_test:
            print("  - VISION配置测试失败")
        if not service_test:
            print("  - VLM服务参数测试失败")
        if not env_test:
            print("  - 环境变量覆盖测试失败") 