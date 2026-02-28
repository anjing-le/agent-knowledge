import io
import numpy as np
from PIL import Image

from kparser.rag.nlp import tokenize
from kparser.parserground.vision import OCR, LayoutRecognizer
from kparser.parserground.vision.seeit import draw_box
from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)

ocr = OCR()


def chunk(filename, real_filename, binary, **kwargs):
    """
    图片解析函数，支持OCR和布局识别
    
    Args:
        filename: 文件名
        real_filename: 真实文件名（可能包含查询参数）
        binary: 图片的二进制数据
        **kwargs: 其他参数
    
    Returns:
        list: 包含文本和图片元素的列表
    """
    # 加载图片
    try:
        img = Image.open(io.BytesIO(binary)).convert('RGB')
    except Exception as e:
        logger.error(f"Failed to load image: {e}")
        raise ValueError(f"无法加载图片数据: {e}")
    
    doc = {
        "docnm_kwd": filename,
        "image": img
    }
    
    # OCR识别
    try:
        bxs = ocr(np.array(img))
        # logger.info(f"0.4, OCR bxs: ({bxs})")
        txt = "\n".join([t[0] for _, t in bxs if t[0]])
        position = [
            max(min([b[0][0][0] for b in bxs]), 0),
            min(max([b[0][2][0] for b in bxs]), img.size[0]),
            max(min([b[0][0][1] for b in bxs]), 0),
            min(max([b[0][2][1] for b in bxs]), img.size[1]),
        ]
        if txt:
            logger.info(f"0.4, Finish OCR: ({txt[:12]} ...)")
        else:
            logger.warning("0.4, OCR did not recognize any text")
            txt = ""  # 确保 txt 不为 None
    except Exception as e:
        logger.error(f"OCR failed: {e}")
        txt = ""  # OCR 失败时使用空字符串
        position = [0,img.size[0],0,img.size[1]]
    
    tokenize(doc, txt)

    # 布局识别
    layout_image = None  # 初始化为 None
    lyt_image = None
    try:
        images = [img]
        detr = LayoutRecognizer("layout")
        layouts = detr.call_recognizer(images, thr=0.05)
        
        if layouts:
            for i, lyt in enumerate(layouts):
                lyt_image = lyt
                layout_image = draw_box(images[i], lyt, detr.labels, 0.05)
                logger.info("0.9, layout image done")
        else:
            logger.warning("0.9, No layouts detected, using original image")
            layout_image = img  # 如果没有检测到布局，使用原图
    except Exception as e:
        logger.error(f"Layout recognition failed: {e}")
        layout_image = img  # 布局识别失败时使用原图

    cks_by_page = []
    
    # 保存文本元素
    element_text_image = {
        "id": get_random_uuid(),
        "content": doc.get("content_with_weight", ""),  # 使用 get 避免 KeyError
        "content_type": "TEXT",
        "page_idx": [1],
        "extra_data": {"position": position,
                       "page_size": img.size}
    }
    # "position": bxs
    cks_by_page.append(element_text_image)

    # 保存图片元素
    element_image = {
        "id": get_random_uuid(),
        "content": "",
        "content_type": "IMAGE",
        "page_idx": [1],
        "extra_data": {"position": position,
                       "page_size": img.size,
                       "image": layout_image,
                       "image_name": filename}  # layout_image 现在保证不为 None
    }
    # "position": lyt_image
    cks_by_page.append(element_image)

    return cks_by_page
