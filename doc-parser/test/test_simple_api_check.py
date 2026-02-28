#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import re

def test_vision_config_removal():
    """测试vision_config参数是否已被移除"""
    print("=== 测试vision_config参数移除 ===")
    
    # 检查loader_dispatch.py文件
    try:
        with open('kparser/core/loader_dispatch.py', 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查函数定义中是否还有vision_config参数
        function_def_pattern = r'def parse_file_deepdoc\([^)]*\):'
        match = re.search(function_def_pattern, content, re.DOTALL)
        
        if match:
            function_def = match.group(0)
            print(f"函数定义: {function_def}")
            
            if 'vision_config' in function_def:
                print("❌ vision_config参数仍然存在于函数定义中")
                return False
            else:
                print("✅ vision_config参数已从函数定义中移除")
        
        # 检查函数调用中是否还有vision_config参数
        if 'vision_config=' in content:
            print("❌ 代码中仍然存在vision_config参数的使用")
            return False
        else:
            print("✅ 代码中已移除vision_config参数的使用")
        
        return True
        
    except FileNotFoundError:
        print("❌ 找不到loader_dispatch.py文件")
        return False
    except Exception as e:
        print(f"❌ 检查文件时出错: {e}")
        return False

def test_app_py_removal():
    """测试app.py中vision_config参数是否已被移除"""
    print("\n=== 测试app.py中vision_config参数移除 ===")
    
    try:
        with open('kparser/app.py', 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 检查API接口定义中是否还有vision_config参数
        if 'vision_config:' in content:
            print("❌ app.py中仍然存在vision_config参数")
            return False
        else:
            print("✅ app.py中已移除vision_config参数")
        
        # 检查vision_config_dict的使用
        if 'vision_config_dict' in content:
            print("❌ app.py中仍然存在vision_config_dict的使用")
            return False
        else:
            print("✅ app.py中已移除vision_config_dict的使用")
        
        return True
        
    except FileNotFoundError:
        print("❌ 找不到app.py文件")
        return False
    except Exception as e:
        print(f"❌ 检查文件时出错: {e}")
        return False

def test_vision_model_creation():
    """测试Vision Model创建是否正常工作"""
    print("\n=== 测试Vision Model创建 ===")
    
    try:
        # 直接导入并测试
        import sys
        import os
        sys.path.append(os.path.dirname(os.path.abspath(__file__)))
        
        from kparser.model.model_api import get_vision_model
        
        # 测试使用空配置创建vision model
        vision_model = get_vision_model({})
        print("✅ 成功使用空配置创建vision model")
        print(f"  Model类型: {type(vision_model)}")
        print(f"  Model Code: {vision_model.model_code}")
        
        return True
        
    except Exception as e:
        print(f"❌ Vision Model创建失败: {e}")
        return False

if __name__ == "__main__":
    print("开始测试vision_config参数移除...")
    
    # 测试loader_dispatch.py
    dispatch_test = test_vision_config_removal()
    
    # 测试app.py
    app_test = test_app_py_removal()
    
    # 测试Vision Model创建
    model_test = test_vision_model_creation()
    
    # 总结
    print("\n=== 测试总结 ===")
    if dispatch_test and app_test and model_test:
        print("✅ 所有测试通过")
        print("✅ vision_config参数已成功移除")
        print("✅ 系统现在完全使用VISION配置中的默认值")
        print("✅ API接口已简化，配置管理更加统一")
    else:
        print("❌ 部分测试失败")
        if not dispatch_test:
            print("  - loader_dispatch.py测试失败")
        if not app_test:
            print("  - app.py测试失败")
        if not model_test:
            print("  - Vision Model创建测试失败") 