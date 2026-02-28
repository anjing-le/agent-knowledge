#!/usr/bin/env python3
"""
测试TOS上传功能的脚本
"""

import os
import sys
import tempfile

# 添加项目路径到Python路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'kparser'))

def test_tos_upload():
    """测试TOS上传功能"""
    print("=== 测试TOS上传功能 ===")
    
    try:
        from kparser.common.file_utils import upload2tos
        from kparser.common.types_utils import get_random_uuid
        from kparser.common.config import TOS
        
        print("✅ 成功导入TOS上传模块")
        print(f"TOS配置: temp_object_key_prefix = {TOS.get('temp_object_key_prefix', 'N/A')}")
        
        # 创建一个临时的测试图片文件
        with tempfile.NamedTemporaryFile(suffix='.png', delete=False) as tmp_file:
            # 创建一个简单的PNG图片数据（1x1像素的透明图片）
            png_data = b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\nIDATx\x9cc\x00\x00\x00\x02\x00\x01\xe5\x27\xde\xfc\x00\x00\x00\x00IEND\xaeB`\x82'
            tmp_file.write(png_data)
            tmp_file_path = tmp_file.name
        
        print(f"✅ 创建临时测试文件: {tmp_file_path}")
        
        try:
            # 测试上传功能，使用TOS配置中的temp_object_key_prefix
            object_key = f"{TOS['temp_object_key_prefix']}/test_excel_vision/{get_random_uuid()}_test.png"
            print(f"测试上传到TOS，对象键: {object_key}")
            
            tos_url = upload2tos(object_key, local_file_path=tmp_file_path)
            if tos_url:
                print(f"✅ TOS上传成功: {tos_url}")
            else:
                print("❌ TOS上传失败")
                
        except Exception as e:
            print(f"⚠️ TOS上传测试失败: {e}")
            print("可能的原因:")
            print("1. TOS配置不正确")
            print("2. 网络连接问题")
            print("3. 权限问题")
        finally:
            # 清理临时文件
            try:
                os.unlink(tmp_file_path)
                print("✅ 清理临时文件成功")
            except Exception as e:
                print(f"⚠️ 清理临时文件失败: {e}")
        
        print("\n=== 功能验证完成 ===")
        
    except ImportError as e:
        print(f"❌ 导入模块失败: {e}")
        print("请确保已安装所有依赖: pip install -r requirements.txt")
    except Exception as e:
        print(f"❌ 测试失败: {e}")

if __name__ == "__main__":
    test_tos_upload() 