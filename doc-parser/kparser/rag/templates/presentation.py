import copy
import re
import json
from io import BytesIO
from PyPDF2 import PdfReader as pdf2_read
from PIL import Image

from kparser.rag.nlp import tokenize, is_english
from kparser.parserground.parser import PdfParser, PptxParser, PlainParser
from kparser.rag.storage import singleton
from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


# 加载缺省图片
default_image_path = "kparser/rag/external/default_image.jpeg"
default_image = Image.open(default_image_path)


@singleton
class Ppt(PptxParser):
    def __call__(self, fnm, from_page, to_page, callback=None):
        txts = super().__call__(fnm, from_page, to_page)

        logger.info("0.5, Text extraction finished.")
        import aspose.slides as slides
        import aspose.pydrawing as drawing
        logger.info("0.6, aspose load finished.")
        imgs = []
        with slides.Presentation(BytesIO(fnm)) as presentation:
            for i, slide in enumerate(presentation.slides[from_page: to_page]):
                try:
                    buffered = BytesIO()
                    slide.get_thumbnail(
                        1.0, 1.0).save(
                        buffered, drawing.imaging.ImageFormat.jpeg)
                    imgs.append(Image.open(buffered))
                except Exception as e:
                    logger.error(f"跳过第 {i} 张幻灯片，原因: {e}, 建议将pptx转化为pdf进行解析")
                    imgs.append(default_image)
        assert len(imgs) == len(
            txts), "Slides text and image do not match: {} vs. {}".format(len(imgs), len(txts))
        logger.info("0.8, Image extraction finished")
        self.is_english = is_english(txts)
        return [(txts[i], imgs[i]) for i in range(len(txts))]

@singleton
class Pdf(PdfParser):
    def __init__(self):
        super().__init__()

    def __garbage(self, txt):
        txt = txt.lower().strip()
        if re.match(r"[0-9\.,%/-]+$", txt):
            return True
        if len(txt) < 3:
            return True
        return False

    def __call__(self, filename, binary=None, from_page=0,
                 to_page=100000, zoomin=3, callback=None):
        callback(msg="OCR is running...")
        self.__images__(filename if not binary else binary,
                        zoomin, from_page, to_page, callback)
        logger.info("0.8, Page {}~{}: OCR finished".format(
            from_page, min(to_page, self.total_page)))
        assert len(self.boxes) == len(self.page_images), "{} vs. {}".format(
            len(self.boxes), len(self.page_images))
        res = []
        for i in range(len(self.boxes)):
            lines = "\n".join([b["text"] for b in self.boxes[i]
                              if not self.__garbage(b["text"])])
            res.append((lines, self.page_images[i]))
        logger.info("0.9, Page {}~{}: Parsing finished".format(
            from_page, min(to_page, self.total_page)))
        return res

@singleton
class PlainPdf(PlainParser):
    def __call__(self, filename, binary=None, from_page=0,
                 to_page=100000, callback=None, **kwargs):
        self.pdf = pdf2_read(filename if not binary else BytesIO(binary))
        page_txt = []
        for page in self.pdf.pages[from_page: to_page]:
            page_txt.append(page.extract_text())
        logger.info("0.9, Parsing finished")
        return [(txt, None) for txt in page_txt]


def ppt_format(res, page_sizes):
    cks = []
    for i, section in enumerate(res):
        if "position_list" in section:
            position = section["position_list"]
        else:
            logger.info("section={}".format(section))
        coordinates = re.sub(r"[\'\[\]]", "", position).split(",")
        pn, left, right, top, bottom = coordinates

        if "content_with_weight" in section:
            element_text = {"id": get_random_uuid(),
                           "content": section["content_with_weight"],
                           "content_type": "TEXT",
                           "page_idx": [int(pn)],
                           "extra_data": {"position": [int(left), int(right), int(top), int(bottom)],
                                          "page_size": page_sizes[i]
                                          }
                           } if page_sizes else None

            cks.append(element_text)

        if "image" in section:
            element_image = {"id": get_random_uuid(),
                           "content_type": "IMAGE",
                           "page_idx": [int(pn)],
                           "extra_data": {
                               "image": section["image"],
                               "position": [int(left), int(right), int(top), int(bottom)],
                               "page_size": page_sizes[i]
                               } if page_sizes else None
                           }
            cks.append(element_image)

    return cks

def chunk(filename, real_filename, binary=None, from_page=0, to_page=100000, callback=None, **kwargs):
    """
    The supported file formats are pdf, pptx.
    Every page will be treated as a chunk. And the thumbnail of every page will be stored.
    PPT file will be parsed by using this method automatically, setting-up for every PPT file is not necessary.
    """
    doc = {"docnm_kwd": filename}
    logger.info("0.1, start parsing {}".format(filename))
    res = []
    
    # 移除查询参数（支持预签名URL）
    real_filename_clean = real_filename.split("?")[0] if real_filename else real_filename
    logger.debug("filename={}, real_filename={}, real_filename_clean={}".format(filename, real_filename, real_filename_clean))
    
    if re.search(r"\.pptx?$", real_filename_clean, re.IGNORECASE):
        ppt_parser = Ppt()
        page_sizes = []
        for pn, (txt, img) in enumerate(ppt_parser(
                filename if not binary else binary, from_page, 1000000, callback)):
            d = copy.deepcopy(doc)
            pn += from_page
            d["image"] = img
            d["page_num_list"] = json.dumps([pn + 1])
            d["top_list"] = json.dumps([0])
            d["position_list"] = json.dumps([(pn + 1, 0, img.size[0], 0, img.size[1])])
            tokenize(d, txt)
            page_sizes.append(img.size)
            res.append(d)
        cks = ppt_format(res, page_sizes)
        logger.info("0.9, parsing done {}".format(filename))
        return cks

    elif re.search(r"\.pdf$", real_filename_clean, re.IGNORECASE):
        pdf_parser = Pdf() if kwargs.get(
            "parser_config", {}).get(
            "layout_recognize", True) else PlainPdf()
        page_sizes = []
        for pn, (txt, img) in enumerate(pdf_parser(filename, binary,
                                                   from_page=from_page, to_page=to_page, callback=callback)):
            d = copy.deepcopy(doc)
            pn += from_page
            if img:
                d["image"] = img
            d["page_num_list"] = json.dumps([pn + 1])
            d["top_list"] = json.dumps([0])
            d["position_list"] = json.dumps([
                (pn + 1, 0, img.size[0] if img else 0, 0, img.size[1] if img else 0)])
            tokenize(d, txt)
            page_sizes.append(img.size)
            res.append(d)

        cks = ppt_format(res, page_sizes)
        return cks

    raise NotImplementedError(
        "file type not supported yet(pptx, pdf supported)")
