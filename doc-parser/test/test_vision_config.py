#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os

# 添加项目根目录到Python路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from kparser.common.config import TOS, VISION

def test_vision_config():
    """测试VISION配置读取"""
    print("=== 测试VISION配置读取 ===")
    
    # 测试TOS配置
    print("\n--- TOS配置 ---")
    print(f"TOS配置: {TOS}")
    print(f"TOS AK: {TOS.get('ak', 'Not found')}")
    print(f"TOS SK: {TOS.get('sk', 'Not found')}")
    print(f"TOS Region: {TOS.get('region', 'Not found')}")
    print(f"TOS Endpoint: {TOS.get('endpoint', 'Not found')}")
    print(f"TOS Bucket: {TOS.get('bucket', 'Not found')}")
    print(f"TOS Temp Object Key Prefix: {TOS.get('temp_object_key_prefix', 'Not found')}")
    
    # 测试VISION配置
    print("\n--- VISION配置 ---")
    print(f"VISION配置: {VISION}")
    print(f"VLM API Key: {VISION.get('vlm_api_key', 'Not found')}")
    print(f"VLM API Base: {VISION.get('vlm_api_base', 'Not found')}")
    print(f"VLM Model: {VISION.get('vlm_model', 'Not found')}")
    print(f"Excel Vision Prompt: {VISION.get('excel_vision_prompt', 'Not found')}")
    print(f"Image Prompt: {VISION.get('image_prompt', 'Not found')}")
    print(f"Table Prompt: {VISION.get('table_prompt', 'Not found')}")
    
    # 测试环境变量覆盖
    print("\n--- 环境变量测试 ---")
    import os
    print(f"环境变量 VLM_API_KEY: {os.getenv('VLM_API_KEY', 'Not set')}")
    print(f"环境变量 VLM_API_BASE: {os.getenv('VLM_API_BASE', 'Not set')}")
    print(f"环境变量 VLM_MODEL: {os.getenv('VLM_MODEL', 'Not set')}")
    print(f"环境变量 EXCEL_VISION_PROMPT: {os.getenv('EXCEL_VISION_PROMPT', 'Not set')}")
    print(f"环境变量 IMAGE_PROMPT: {os.getenv('IMAGE_PROMPT', 'Not set')}")
    print(f"环境变量 TABLE_PROMPT: {os.getenv('TABLE_PROMPT', 'Not set')}")
    
    # 测试配置完整性
    print("\n--- 配置完整性测试 ---")
    required_tos_keys = ['ak', 'sk', 'region', 'endpoint', 'bucket', 'temp_object_key_prefix']
    required_vision_keys = ['vlm_api_key', 'vlm_api_base', 'vlm_model', 'excel_vision_prompt', 'image_prompt', 'table_prompt']
    
    print("TOS配置完整性:")
    for key in required_tos_keys:
        if key in TOS and TOS[key]:
            print(f"  ✓ {key}: {TOS[key][:10]}..." if len(str(TOS[key])) > 10 else f"  ✓ {key}: {TOS[key]}")
        else:
            print(f"  ✗ {key}: Missing or empty")
    
    print("VISION配置完整性:")
    for key in required_vision_keys:
        if key in VISION and VISION[key]:
            print(f"  ✓ {key}: {VISION[key][:10]}..." if len(str(VISION[key])) > 10 else f"  ✓ {key}: {VISION[key]}")
        else:
            print(f"  ✗ {key}: Missing or empty")

if __name__ == "__main__":
    test_vision_config() 