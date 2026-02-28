import os
import re
import time
import subprocess
from pdf2image import convert_from_path
from PIL import Image, ImageChops
from kparser.common.log_utils import get_logger
from kparser.common.file_utils import upload2tos
from kparser.common.config import TOS
from kparser.common.types_utils import get_random_uuid
from PIL import Image as PILImage
from io import BytesIO

# LibreOffice UNO相关导入 - 处理在某些环境中无法导入的情况
try:
    import uno
    from com.sun.star.beans import PropertyValue
    UNO_AVAILABLE = True
    logger = get_logger(__name__)
    logger.info("LibreOffice UNO components loaded successfully")
except ImportError as e:
    UNO_AVAILABLE = False
    logger = get_logger(__name__)
    logger.warning(f"LibreOffice UNO components not available: {e}")
    logger.info("Excel processing will use fallback methods where possible")
    
    # 创建PropertyValue的替代实现
    class PropertyValue:
        def __init__(self):
            self.Name = ""
            self.Value = None


logger = get_logger(__name__)

def make_prop(name, value):
    prop = PropertyValue()
    prop.Name = name
    prop.Value = value
    return prop

def is_libreoffice_running():
    try:
        result = subprocess.run(['libreoffice', '--version'], 
                              stdout=subprocess.PIPE, 
                              stderr=subprocess.PIPE,
                              text=True,
                              timeout=5)
        return result.returncode == 0 and 'LibreOffice' in result.stdout
    except:
        return False

def start_libreoffice_uno_service():
    try:
        subprocess.Popen(['soffice', '--headless', 
                         '--accept="socket,host=localhost,port=2002;urp;"', 
                         '--nofirststartwizard'])
        # 等待几秒钟让服务启动
        time.sleep(5)
    except Exception as e:
        print(f"启动LibreOffice UNO服务失败: {e}")
        raise

def is_libreoffice_running():
    try:
        result = subprocess.run(['libreoffice', '--version'], 
                              stdout=subprocess.PIPE, 
                              stderr=subprocess.PIPE,
                              text=True,
                              timeout=5)
        return result.returncode == 0 and 'LibreOffice' in result.stdout
    except:
        return False

def start_libreoffice_uno_service():
    try:
        subprocess.Popen(['soffice', '--headless', 
                         '--accept="socket,host=localhost,port=2002;urp;"', 
                         '--nofirststartwizard'])
        # 等待几秒钟让服务启动
        time.sleep(5)
    except Exception as e:
        print(f"启动LibreOffice UNO服务失败: {e}")
        raise

def set_scale_to_one_page(doc, sheet):
    # 获取页面样式名
    page_style_name = sheet.PageStyle
    # 获取页面样式对象
    style_families = doc.getStyleFamilies()
    page_styles = style_families.getByName("PageStyles")
    page_style = page_styles.getByName(page_style_name)
    # 设置缩放为一页
    page_style.setPropertyValue("ScaleToPagesX", 1)
    page_style.setPropertyValue("ScaleToPagesY", 1)

def crop_image_border(img, bg_color=(255,255,255)):
    # 自动裁剪图片四周空白区域
    if img.mode != "RGB":
        img = img.convert("RGB")
    bg = Image.new("RGB", img.size, bg_color)
    diff = ImageChops.difference(img, bg)
    bbox = diff.getbbox()
    if bbox:
        return img.crop(bbox)
    else:
        return img  # 没有内容则不裁剪

def excel_to_images_with_uno(excel_path, output_folder, resolution=300):
    # 检查LibreOffice服务是否运行
    if not is_libreoffice_running():
        logger.info("LibreOffice UNO服务未运行，正在启动...")
        start_libreoffice_uno_service()
        logger.info("LibreOffice UNO服务启动完成")
    else:
        logger.info("LibreOffice UNO服务已运行")

    # 尝试连接UNO服务
    max_retries = 3
    for attempt in range(max_retries):
        try:
            # 1. 启动LibreOffice UNO
            localContext = uno.getComponentContext()
            resolver = localContext.ServiceManager.createInstanceWithContext(
                "com.sun.star.bridge.UnoUrlResolver", localContext)
            ctx = resolver.resolve(
                "uno:socket,host=localhost,port=2002;urp;StarOffice.ComponentContext")
            smgr = ctx.ServiceManager
            desktop = smgr.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)

            # 2. 打开Excel文件
            url = uno.systemPathToFileUrl(os.path.abspath(excel_path))
            properties = (make_prop("Hidden", True),)
            doc = desktop.loadComponentFromURL(url, "_blank", 0, properties)

            # 3. 遍历每个sheet，设置缩放
            sheets = doc.getSheets()
            for i in range(sheets.getCount()):
                sheet = sheets.getByIndex(i)
                set_scale_to_one_page(doc, sheet)

            # 4. 导出为PDF
            os.makedirs(output_folder, exist_ok=True)
            base_name = os.path.splitext(os.path.basename(excel_path))[0]
            pdf_path = os.path.join(output_folder, f"{base_name}.pdf")
            pdf_url = uno.systemPathToFileUrl(os.path.abspath(pdf_path))
            export_args = (make_prop("FilterName", "calc_pdf_Export"),)
            doc.storeToURL(pdf_url, export_args)
            doc.close(True)

            # 5. PDF转图片并自动裁剪空白
            image_paths = []
            images = convert_from_path(pdf_path, dpi=resolution)
            for i, img in enumerate(images):
                # 保存原图
                raw_path = os.path.join(output_folder, f"{base_name}_sheet_{i+1}.png")
                img.save(raw_path, "PNG")
                # 自动裁剪空白区域
                cropped_img = crop_image_border(img)
                cropped_path = os.path.join(output_folder, f"{base_name}_sheet_{i+1}_cropped.png")
                cropped_img.save(cropped_path, "PNG")
                print(f"✅ 已保存: {cropped_path}")
                image_paths.append(cropped_path)
                os.remove(raw_path)
            os.remove(pdf_path)
            return image_paths

        except Exception as e:
            if attempt == max_retries - 1:
                raise
            logger.error(f"连接UNO服务失败，尝试 {attempt + 1}/{max_retries}... 错误: {e}")
            time.sleep(2)
            if not is_libreoffice_running():
                start_libreoffice_uno_service()


def upload_image_to_tos(image_path):
    """上传图片到TOS并返回URL"""
    try:
        logger.info(f"start upload_image_to_tos, image_path: {image_path}")
        # 生成唯一的对象键，使用TOS配置中的temp_object_key_prefix
        filename = os.path.basename(image_path)
        object_key = f"{TOS['temp_object_key_prefix']}/excel_vision/{get_random_uuid()}_{filename}"
        
        # 上传到TOS
        tos_url = upload2tos(object_key, local_file_path=image_path)
        logger.info(f"Successfully uploaded image to TOS: {tos_url}")
        return tos_url
    except Exception as e:
        logger.error(f"Failed to upload image to TOS: {e}")
        return None


def is_image_file(data: bytes) -> bool:
    """检查字节数据是否为有效的图片"""
        # 使用PIL验证图片
    with BytesIO(data) as img_stream:
        PILImage.open(img_stream).verify()
    return True


def extract_image_id_from_dispimg(dispimg_text: str) -> str:
    """
    从DISPIMG格式的文本中提取图片ID，支持多种格式
    
    Args:
        dispimg_text: DISPIMG格式的文本，如 '=DISPIMG("ID_xxxxxxxx",1)' 或 '=_xlfn.DISPIMG("ID_xxxxxxxx",1)'
        
    Returns:
        提取的图片ID，如 'xxxxxxxx'
    """
    # 支持两种格式：=DISPIMG 和 =_xlfn.DISPIMG
    patterns = [
        r'=_xlfn\.DISPIMG\("ID_([^"]+)",\d+\)',  # Excel中的_xlfn格式
        r'=DISPIMG\("ID_([^"]+)",\d+\)'          # 标准格式
    ]
    
    for pattern in patterns:
        match = re.search(pattern, dispimg_text)
        if match:
            return match.group(1)
    return None


def extract_image_from_cell(cell_value: str, workbook) -> bytes:
    """
    从Excel单元格中提取图片数据流
    
    Args:
        cell_value: Excel单元格的值，可能包含DISPIMG格式的图片引用
        workbook: openpyxl或xlrd的workbook对象
        
    Returns:
        bytes: 图片的二进制数据流，如果没有图片则返回None
    """
    try:
        cell_value = cell_value.strip()
        if cell_value.startswith('=') and 'DISPIMG' in cell_value:
            image_id = extract_image_id_from_dispimg(cell_value)
            if image_id and hasattr(workbook, 'images'):
                # 对于openpyxl workbook
                for image in workbook.images:
                    if image.ref_id == image_id:
                        return image._data()
            elif image_id and hasattr(workbook, '_images'):
                # 对于xlrd workbook 
                for image in workbook._images:
                    if image.id == image_id:
                        return image.data
                        
    except Exception as e:
        logger.error(f"提取单元格图片失败: {e}")
        
    return None


def excel_to_images_fallback(excel_path, output_folder, resolution=300):
    """
    UNO不可用时的fallback方法 - 使用LibreOffice命令行转换
    
    Args:
        excel_path: Excel文件路径
        output_folder: 输出图片文件夹
        resolution: 图片分辨率（DPI）
    
    Returns:
        List[str]: 生成的图片文件路径列表
    """
    try:
        logger.info(f"Using fallback method for Excel to image conversion: {excel_path}")
        
        # 确保输出目录存在
        os.makedirs(output_folder, exist_ok=True)
        
        # 生成PDF作为中间格式
        pdf_path = os.path.join(output_folder, "temp_conversion.pdf")
        
        # 使用LibreOffice命令行将Excel转换为PDF
        cmd = [
            'libreoffice',
            '--headless',
            '--convert-to', 'pdf',
            '--outdir', output_folder,
            excel_path
        ]
        
        logger.info(f"Running LibreOffice command: {' '.join(cmd)}")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        
        if result.returncode != 0:
            logger.error(f"LibreOffice conversion failed: {result.stderr}")
            return []
        
        # 查找生成的PDF文件
        excel_basename = os.path.splitext(os.path.basename(excel_path))[0]
        pdf_path = os.path.join(output_folder, f"{excel_basename}.pdf")
        
        if not os.path.exists(pdf_path):
            logger.error(f"PDF conversion output not found: {pdf_path}")
            return []
        
        # 将PDF转换为图片
        logger.info(f"Converting PDF to images: {pdf_path}")
        images = convert_from_path(pdf_path, dpi=resolution)
        
        image_paths = []
        for i, image in enumerate(images):
            image_path = os.path.join(output_folder, f"sheet_{i+1}.png")
            image.save(image_path, "PNG")
            image_paths.append(image_path)
            logger.info(f"Generated image: {image_path}")
        
        # 清理临时PDF文件
        try:
            os.remove(pdf_path)
        except Exception as cleanup_error:
            logger.warning(f"Failed to cleanup temporary PDF: {cleanup_error}")
        
        logger.info(f"Fallback conversion completed: {len(image_paths)} images generated")
        return image_paths
        
    except subprocess.TimeoutExpired:
        logger.error("LibreOffice conversion timeout")
        return []
    except Exception as e:
        logger.error(f"Fallback Excel to image conversion failed: {e}")
        return []


if __name__ == "__main__":
    # 先用命令行启动LibreOffice监听端口
    # libreoffice --headless --accept=\"socket,host=localhost,port=2002;urp;\" --norestore --nofirststartwizard &
    excel_to_images_with_uno("input.xlsx", "output_images", resolution=300)