import io
import re
import time
import numpy as np
import fitz
import pdfplumber
from io import BytesIO
from typing import List
from PIL import Image
from pandas import DataFrame
from numpy import ndarray
from pydantic import BaseModel

from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def crop_page_region_with_pdf2image(pdf_binary, page_num, bbox, dpi=150):
    """
    使用pdf2image裁剪PDF页面的指定区域
    
    Args:
        pdf_binary: PDF二进制内容
        page_num: 页码（1-based）
        bbox: 边界框 (x0, top, x1, bottom)，基于72 DPI
        dpi: 渲染DPI
    
    Returns:
        PIL.Image: 裁剪后的图片
    """
    from pdf2image import convert_from_bytes
    
    try:
        # 将bbox坐标从72 DPI转换到目标DPI
        scale = dpi / 72.0
        x0, top, x1, bottom = bbox
        x0_scaled = int(x0 * scale)
        x1_scaled = int(x1 * scale)
        top_scaled = int(top * scale)
        bottom_scaled = int(bottom * scale)
        
        # 🔥 使用pdf2image渲染指定页面
        images = convert_from_bytes(
            pdf_binary,
            dpi=dpi,
            first_page=page_num,
            last_page=page_num,
            fmt='png'
        )
        
        if not images:
            logger.warning(f"无法转换PDF第{page_num}页")
            return None
        
        page_image = images[0]
        
        # 裁剪指定区域
        cropped = page_image.crop((x0_scaled, top_scaled, x1_scaled, bottom_scaled))
        return cropped
        
    except Exception as e:
        logger.exception(f"裁剪PDF区域失败: page={page_num}, bbox={bbox}, error={e}")
        return None


def wmf_to_png(wmf_blob):
    with io.BytesIO(wmf_blob) as wmf_file:
        try:
            # 创建一个新的PDF文档
            doc = fitz.open()
            page = doc.new_page()
            # 将WMF数据作为图像插入PDF
            img_rect = page.insert_image(page.rect, stream=wmf_file)
            # 将PDF页面渲染为PNG
            pix = page.get_pixmap()
            img_data = pix.tobytes("png")
            doc.close()
            return img_data
        except Exception as e:
            logger.error(f"Error converting WMF to PNG: {e}")
            return None

class BBox(BaseModel):
    x0: float
    y0: float
    x1: float
    y1: float

    @property
    def sort_key(self):
        return (self.y0, self.x0)

    def to_tuple(self):
        return (self.x0, self.y0, self.x1, self.y1)

def TextChunk(content, page_idx):
    text_chunk = {"id": get_random_uuid(),
                  'content': content,
                  'content_type': 'TEXT',
                  'page_idx': [page_idx],
                  'extra_data': {}
                  }
    return text_chunk

def merge_chunks(chunks, joiner="\n"):
    merged_chunks = []
    acc = ""
    cur_text_page_idx = 1
    for chunk in chunks:
        if chunk["content_type"] == "TEXT" and not chunk["content"]:
            continue
        if chunk["content_type"] == "TEXT":
            if chunk["page_idx"][0] == cur_text_page_idx:
                acc = joiner.join([acc, chunk["content"]])
            else:
                if acc.strip():
                    merged_chunks.append(TextChunk(content=acc, page_idx=cur_text_page_idx))
                acc = chunk["content"]
                cur_text_page_idx = chunk["page_idx"][0]
        else:
            if acc.strip():
                merged_chunks.append(TextChunk(content=acc, page_idx=cur_text_page_idx))
            acc = ""
            merged_chunks.append(chunk)
    if acc.strip():
        merged_chunks.append(TextChunk(content=acc, page_idx=cur_text_page_idx))
    return merged_chunks

def get_image_name_path(page_idx: int, image_idx: int):
    image_name = f"page{page_idx}-image{image_idx}-time{time.time()}.png"
    return image_name

def data2markdown(data: ndarray | DataFrame) -> str:
    if isinstance(data, ndarray):
        data = DataFrame(data)
    data.fillna("", inplace=True)
    return data.to_markdown(index=False)

def data2table(data: ndarray | DataFrame, sep="\t") -> str:
    """
    当 table_format 为 html 时，返回 HTML 表格格式
    """
    if isinstance(data, DataFrame):
        data.fillna("", inplace=True)
        data = np.vstack([data.columns, data.to_numpy()])
    html = "<table border='1'>\n"
    for row in data:
        html += "  <tr>" + "".join([f"<td>{cell}</td>" for cell in row]) + "</tr>\n"
    html += "</table>"
    return html

def compute_toc_likehood(page_content, idx):
    if idx > 10:
        return 0
    page_content = re.sub(r'\n\s*\n', '\n', page_content)
    nums = re.findall(r'\b\d+\s*\n', page_content)
    N_lines = len(page_content.strip().split('\n'))
    return len(nums) / N_lines if N_lines else 0

def get_toc_layer(content, last_layer, size, font_size):
    if "、" in content:
        return 0, 0, None
    elif re.search(r'(\d+\.\d+)\s+(.*)$', content):
        return 1, 1, None
    else:
        page_number = re.findall(r'.+?\s*[-.]+\s+(\d+)', content)
        if page_number:
            if size not in font_size:
                font_size.append(size)
                font_size.sort(reverse=True)
            idx = font_size.index(size)
            return last_layer + idx + 1, last_layer, int(page_number[-1])
        else:
            return None, last_layer, None

def analyse_page(idx: int,
                 page: pdfplumber.page.Page,
                 resolution: int = 400,
                 table_format="html",
                 pdf_parser="pdfplumber"):

    logger.debug(f"analyzing page:{idx} with pdf_parser={pdf_parser}")

    chunks = []

    # 提取文本内容以判断是否为目录页
    page_text = page.extract_text() or ""
    toc_ratio = compute_toc_likehood(page_text, idx)

    # 提取文本行
    lines = page.extract_text_lines(x_tolerance=2, y_tolerance=1)

    if toc_ratio >= 0.25 and pdf_parser == "pdfplumber":
        font_size: List[int] = []
        last_layer = 0
        i = 0
        while i < len(lines):
            line = lines[i]
            line_content = line['text']
            if line_content and line_content[0] == '-' and (i + 1) < len(lines):
                line_content = lines[i + 1]['text'] + ' ' + line_content
                size = int(lines[i + 1]['chars'][0]['size'])
                i += 2
            else:
                size = int(line['chars'][0]['size'])
                i += 1
            layer, last_layer, page_number = get_toc_layer(line_content, last_layer, size, font_size)
            # outline切片
            outline_chunk = {"id": get_random_uuid(),
                             'content': line_content,
                             'content_type': 'TEXT',
                             'page_idx': [idx],
                             'layer': layer,  # 目录层级
                             'content_range': page_number,  # 页码范围
                             'extra_data': {'position': [line["x0"], line["top"], line["x1"], line["bottom"]],
                                            'page_size': [page.mediabox[2], page.mediabox[3]]}
                            }
            chunks.append((outline_chunk, BBox(x0=line["x0"], y0=line["top"], x1=line["x1"], y1=line["bottom"])))
    else:
        for i in range(len(lines)):
            text_chunk = {"id": get_random_uuid(),
                          'content': lines[i]['text'],
                          'content_type': 'TEXT',
                          'page_idx': [idx],
                          'extra_data': {
                            'position': [lines[i].get("x0", 0),
                                         lines[i].get("top", 0),
                                         lines[i].get("x1", 0),
                                         lines[i].get("bottom", 0)],
                            'page_size': [page.mediabox[2], page.mediabox[3]]
                                        }
                         }
            chunks.append((text_chunk, BBox(x0=lines[i].get("x0", 0), y0=lines[i].get("top", 0),
                                            x1=lines[i].get("x1", 0), y1=lines[i].get("bottom", 0))))

    # 定位表格：使用 find_tables 得到包含坐标信息的表格对象
    table_objs = page.find_tables()
    for table_obj in table_objs:
        bbox = table_obj.bbox  # (x0, top, x1, bottom)
        table_arr = np.array(table_obj.extract())
        # 根据参数决定生成 HTML 或 markdown 格式表格
        if table_format != "markdown":
            content = data2table(table_arr, "\t")
        else:
            content = data2markdown(table_arr)

        # 从原页面切出表格区域图像
        table_img = page.within_bbox(bbox).to_image(resolution=resolution)
        # 将切出的表格图像路径和坐标信息保存到 extra_data 中
        table_chunk = {"id": get_random_uuid(),
                       'content': content,
                       'content_type': 'TABLE',
                       'page_idx': [idx],
                       'extra_data': {'position': [bbox[0], bbox[1], bbox[2], bbox[3]],
                                      'page_size': [page.mediabox[2], page.mediabox[3]],
                                      'image': table_img}
                      }
        chunks.append((table_chunk, BBox(x0=bbox[0], y0=bbox[1], x1=bbox[2], y1=bbox[3])))

    # 提取图片
    page_x0, page_y0, page_x1, page_y1 = page.bbox
    page_width = page_x1 - page_x0
    page_height = page_y1 - page_y0

    for i, img in enumerate(page.images):
        x0, y0, x1, y1 = img["x0"], img["top"], img["x1"], img["bottom"]
        if y1 < 0 and y0 > 0:
            y1 = page_height + y1
        if x1 < 0 and x0 > 0:
            x1 = page_width + x1
        BUFFER = 2.0
        x0 = max(page_x0 + BUFFER, x0)
        y0 = max(page_y0 + BUFFER, y0)
        x1 = min(page_x1 - BUFFER, x1)
        y1 = min(page_y1 - BUFFER, y1)
        if y1 <= y0 + BUFFER or x1 <= x0 + BUFFER:
            logger.debug(f"Skipping image with invalid or too small bbox: x0={x0}, x1={x1}, y0={y0}, y1={y1}")
            continue
        bbox_img = BBox(x0=x0, y0=y0, x1=x1, y1=y1)
        try:
            image = page.within_bbox((x0, y0, x1, y1)).to_image(resolution=resolution)
            image_name = get_image_name_path(idx, i)
            if img.get('ext', '').lower() == 'wmf':
                image_bytes = image.original.tobytes()
                png_bytes = wmf_to_png(image_bytes)
                if png_bytes:
                    image = Image.open(io.BytesIO(png_bytes))
                else:
                    logger.warning(f"Failed to convert WMF image on page {idx}")
                    continue
            image_chunk = {"id": get_random_uuid(),
                           "content": "__no_ocr__",
                           "content_type": "IMAGE",
                           "page_idx": [idx],
                           "extra_data": {"image": image,
                                          "position": [x0, y0, x1, y1],
                                          "page_size": [page.mediabox[2], page.mediabox[3]],
                                          "image_name": image_name}
                          }
            chunks.append((image_chunk, bbox_img))
        except Exception as e:
            logger.error(f"error when extract image with bbox={bbox_img}, error={e}")

    chunks.sort(key=lambda x: x[1].sort_key)
    results = [x[0] for x in chunks]
    return results

class PDFLoader:
    def __call__(self, binary, from_page=1, to_page=None):
        logger.info(f"loading pdf file from start_page={from_page} to end_page={to_page}")
        pages = pdfplumber.open(BytesIO(binary))
        all_chunks = []
        for idx, page in enumerate(pages.pages, start=1):
            if idx < from_page:
                continue
            if to_page and idx > to_page:
                break
            page_chunks = analyse_page(idx, page)
            merged = merge_chunks(chunks=page_chunks)
            all_chunks.extend(merged)
        return all_chunks


def filt_img_table_chunks(chunks):
    text_chunks = []
    table_chunks = []
    for chunk in chunks:
        if chunk["content_type"] == "IMAGE":
            continue
        elif chunk["content_type"] == "TABLE":
            table_chunks.append(chunk)
        else:
            text_chunks.append(chunk)
    return text_chunks, table_chunks


def is_bbox_contained_in_table(text_bbox, table_bbox):
    """
    检查文本块的边界框是否完全包含在表格的边界框内
    边界框格式: [x0, y0, x1, y1]
    """
    text_x0, text_y0, text_x1, text_y1 = text_bbox
    table_x0, table_y0, table_x1, table_y1 = table_bbox
    
    # 检查文本块是否完全在表格边界框内
    return (text_x0 >= table_x0 and text_y0 >= table_y0 and 
            text_x1 <= table_x1 and text_y1 <= table_y1)


def filt_repeat_chunks(text_chunks, table_chunks):
    remain_text_chunks = []
    for text_chunk in text_chunks:
        is_repeat = False
        for table_chunk in table_chunks:
            # 检查内容是否包含在表格中
            if text_chunk["content"] in table_chunk["content"]:
                is_repeat = True
                break
            
            # 检查是否同页且边界框完全在表格内
            if (text_chunk["page_idx"][0] == table_chunk["page_idx"][0] and
                is_bbox_contained_in_table(text_chunk["extra_data"]["position"], 
                                         table_chunk["extra_data"]["position"])):
                is_repeat = True
                break
                
        if not is_repeat:
            remain_text_chunks.append(text_chunk)
        else:
            logger.info(f"filter out text chunk: {text_chunk}")
    return remain_text_chunks


class PDFLoader_enhanced:
    def __call__(self, binary, from_page=1, to_page=None):
        logger.info(f"loading pdf file from start_page={from_page} to end_page={to_page}")
        pages = pdfplumber.open(BytesIO(binary))
        all_text_chunks = []
        all_table_chunks = []
        for idx, page in enumerate(pages.pages, start=1):
            if idx < from_page:
                continue
            if to_page and idx > to_page:
                break
            page_chunks = analyse_page(idx, page)
            text_chunks, table_chunks = filt_img_table_chunks(page_chunks)
            text_chunks = filt_repeat_chunks(text_chunks, table_chunks)
            all_text_chunks.extend(text_chunks)
            all_table_chunks.extend(table_chunks)
        return [[(b["content"], "@@{}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}##".format(
            b["page_idx"][0], 
            b["extra_data"]["position"][0], 
            b["extra_data"]["position"][1], 
            b["extra_data"]["position"][2], 
            b["extra_data"]["position"][3],
            b["extra_data"].get("page_size", [612.0, 792.0])[0],
            b["extra_data"].get("page_size", [612.0, 792.0])[1]
        )) for b in all_text_chunks], [(t["content"], "@@{}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}\t{:.1f}##".format(
            t["page_idx"][0], 
            t["extra_data"]["position"][0], 
            t["extra_data"]["position"][1], 
            t["extra_data"]["position"][2], 
            t["extra_data"]["position"][3],
            t["extra_data"].get("page_size", [612.0, 792.0])[0],
            t["extra_data"].get("page_size", [612.0, 792.0])[1]
        )) for t in all_table_chunks]]