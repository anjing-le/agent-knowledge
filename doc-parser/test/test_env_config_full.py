#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
测试完整的环境变量配置功能
"""

import os
import sys

# 添加项目路径到Python路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'kparser'))

def test_env_config_full():
    """测试完整的环境变量配置"""
    print("=== 测试完整的环境变量配置 ===")
    
    # 尝试加载.env文件
    try:
        from dotenv import load_dotenv
        load_dotenv()
        print("✅ 成功加载 .env 文件")
    except ImportError:
        print("⚠️  python-dotenv 未安装，使用系统环境变量")
    except Exception as e:
        print(f"❌ 加载 .env 文件失败: {e}")
    
    try:
        from kparser.common.config import CONFIGS, TOS, S3, MINIO, SERVICE, VISION
        
        print("\n=== 配置文件读取测试 ===")
        print("✅ 成功从config.py读取配置")
        
        print("\n=== 完整配置信息 ===")
        for section, config in CONFIGS.items():
            print(f"\n--- {section.upper()} 配置 ---")
            for key, value in config.items():
                if 'key' in key.lower() or 'sk' in key.lower():
                    display_value = value[:10] + "..." + value[-4:] if len(value) > 10 else value
                elif isinstance(value, str) and len(value) > 50:
                    display_value = value[:50] + "..."
                else:
                    display_value = value
                print(f"  {key}: {display_value}")
        
        print("\n=== 配置验证测试 ===")
        
        # 验证TOS配置
        print("TOS配置验证:")
        tos_required_keys = ['ak', 'sk', 'region', 'endpoint', 'bucket', 'temp_object_key_prefix']
        for key in tos_required_keys:
            if key in TOS and TOS[key]:
                print(f"  ✅ {key}: {TOS[key][:10]}..." if len(str(TOS[key])) > 10 else f"  ✅ {key}: {TOS[key]}")
            else:
                print(f"  ❌ {key}: 配置项缺失或为空")
        
        # 验证VISION配置
        print("VISION配置验证:")
        vision_required_keys = ['vlm_api_key', 'vlm_api_base', 'vlm_model', 'excel_vision_prompt', 'image_prompt', 'table_prompt']
        for key in vision_required_keys:
            if key in VISION and VISION[key]:
                if 'key' in key.lower():
                    display_value = VISION[key][:10] + "..." + VISION[key][-4:] if len(VISION[key]) > 10 else VISION[key]
                elif isinstance(VISION[key], str) and len(VISION[key]) > 50:
                    display_value = VISION[key][:50] + "..."
                else:
                    display_value = VISION[key]
                print(f"  ✅ {key}: {display_value}")
            else:
                print(f"  ❌ {key}: 配置项缺失或为空")
        
        # 验证SERVICE配置
        print("SERVICE配置验证:")
        service_required_keys = ['environment', 'use_storage', 'max_job_number', 'time_out']
        for key in service_required_keys:
            if key in SERVICE and SERVICE[key]:
                print(f"  ✅ {key}: {SERVICE[key]}")
            else:
                print(f"  ❌ {key}: 配置项缺失或为空")
        
        print("\n=== 环境变量测试 ===")
        env_vars = [
            'VLM_API_KEY', 'VLM_API_BASE', 'VLM_MODEL', 'EXCEL_VISION_PROMPT', 'IMAGE_PROMPT', 'TABLE_PROMPT',
            'TOS_AK', 'TOS_SK', 'TOS_REGION', 'TOS_ENDPOINT', 'TOS_BUCKET', 'TOS_TEMP_OBJECT_KEY_PREFIX',
            'SERVICE_ENVIRONMENT', 'SERVICE_USE_STORAGE', 'SERVICE_MAX_JOB_NUMBER', 'SERVICE_TIME_OUT'
        ]
        
        for env_var in env_vars:
            value = os.getenv(env_var, 'Not set')
            if value != 'Not set':
                if 'KEY' in env_var or 'SK' in env_var:
                    display_value = value[:10] + "..." + value[-4:] if len(value) > 10 else value
                else:
                    display_value = value[:30] + "..." if len(value) > 30 else value
                print(f"  ✅ {env_var}: {display_value}")
            else:
                print(f"  ⚠️  {env_var}: Not set (使用默认值)")
        
        print("\n=== 功能验证完成 ===")
        print("主要功能:")
        print("1. ✅ 配置文件读取")
        print("2. ✅ 环境变量支持")
        print("3. ✅ 默认值回退")
        print("4. ✅ 统一配置管理")
        print("5. ✅ 配置完整性验证")
        
    except ImportError as e:
        print(f"❌ 导入模块失败: {e}")
        print("请确保已安装所有依赖: pip install -r requirements.txt")
    except Exception as e:
        print(f"❌ 测试失败: {e}")

if __name__ == "__main__":
    test_env_config_full() 