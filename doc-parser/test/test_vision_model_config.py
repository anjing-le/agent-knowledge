#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from kparser.model.model_api import get_vision_model
from kparser.model.vlm_service import API_GLM
from kparser.common.config import VISION

def test_vision_model_config():
    """测试vision model配置"""
    print("=== 测试Vision Model配置 ===")
    
    # 测试空的vlm_config，应该使用VISION配置中的默认值
    print("\n--- 测试使用VISION配置默认值 ---")
    empty_config = {}
    try:
        vision_model = get_vision_model(empty_config)
        print("✅ 成功创建vision model（使用VISION配置默认值）")
        print(f"  Model类型: {type(vision_model)}")
        print(f"  Model Code: {vision_model.model_code}")
        print(f"  Temperature: {vision_model.temperature}")
        print(f"  Top P: {vision_model.top_p}")
        print(f"  Max Tokens: {vision_model.max_tokens}")
    except Exception as e:
        print(f"❌ 创建vision model失败: {e}")
    
    # 测试部分配置覆盖
    print("\n--- 测试部分配置覆盖 ---")
    partial_config = {
        "cls": "API_GLM",
        "temperature": 0.5,
        "top_p": 0.8
    }
    try:
        vision_model = get_vision_model(partial_config)
        print("✅ 成功创建vision model（部分配置覆盖）")
        print(f"  Model Code: {vision_model.model_code}")
        print(f"  Temperature: {vision_model.temperature} (覆盖为0.5)")
        print(f"  Top P: {vision_model.top_p} (覆盖为0.8)")
        print(f"  Max Tokens: {vision_model.max_tokens} (使用默认值)")
    except Exception as e:
        print(f"❌ 创建vision model失败: {e}")
    
    # 测试完整配置覆盖
    print("\n--- 测试完整配置覆盖 ---")
    full_config = {
        "cls": "API_GLM",
        "api_key": "test_api_key",
        "url": "https://test.api.com",
        "model_code": "test-model",
        "temperature": 0.3,
        "top_p": 0.7,
        "max_tokens": 2048
    }
    try:
        vision_model = get_vision_model(full_config)
        print("✅ 成功创建vision model（完整配置覆盖）")
        print(f"  Model Code: {vision_model.model_code} (覆盖为test-model)")
        print(f"  Temperature: {vision_model.temperature} (覆盖为0.3)")
        print(f"  Top P: {vision_model.top_p} (覆盖为0.7)")
        print(f"  Max Tokens: {vision_model.max_tokens} (覆盖为2048)")
    except Exception as e:
        print(f"❌ 创建vision model失败: {e}")
    
    # 测试VISION配置
    print("\n--- VISION配置验证 ---")
    print(f"VISION配置: {VISION}")
    print(f"  VLM API Key: {VISION.get('vlm_api_key', 'Not found')[:10]}...")
    print(f"  VLM API Base: {VISION.get('vlm_api_base', 'Not found')}")
    print(f"  VLM Model: {VISION.get('vlm_model', 'Not found')}")
    print(f"  Excel Vision Prompt: {VISION.get('excel_vision_prompt', 'Not found')[:50]}...")
    print(f"  Image Prompt: {VISION.get('image_prompt', 'Not found')[:50]}...")
    print(f"  Table Prompt: {VISION.get('table_prompt', 'Not found')[:50]}...")
    
    # 测试不支持的model_channel
    print("\n--- 测试不支持的model_channel ---")
    invalid_config = {"cls": "UNSUPPORTED_MODEL"}
    try:
        vision_model = get_vision_model(invalid_config)
        print("❌ 应该抛出异常但没有")
    except Exception as e:
        print(f"✅ 正确抛出异常: {e}")
    
    # 测试实际使用场景（空配置）
    print("\n--- 测试实际使用场景（空配置） ---")
    try:
        # 模拟实际调用场景，传入空配置
        vision_model = get_vision_model({})
        print("✅ 实际使用场景测试通过")
        print(f"  使用VISION配置中的默认值:")
        print(f"    - API Key: {VISION.get('vlm_api_key', 'Not found')[:10]}...")
        print(f"    - API Base: {VISION.get('vlm_api_base', 'Not found')}")
        print(f"    - Model: {VISION.get('vlm_model', 'Not found')}")
    except Exception as e:
        print(f"❌ 实际使用场景测试失败: {e}")

if __name__ == "__main__":
    test_vision_model_config() 