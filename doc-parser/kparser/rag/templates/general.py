from io import BytesIO
from docx import Document
from docx.document import Document as DOC
from docx.table import _Cell, Table
from docx.oxml.table import CT_Tbl
from docx.oxml.text.paragraph import CT_P
from docx.text.paragraph import Paragraph
from timeit import default_timer as timer
import re
from PIL import Image
from functools import reduce
from markdown import markdown
from docx.image.exceptions import UnrecognizedImageError, UnexpectedEndOfFileError, InvalidImageStreamError

from kparser.rag.nlp import naive_merge_content_by_page, tokenize_table_custom, find_codec, concat_img, \
    naive_merge_docx, merge_text_table, get_page_idx_value, sort_text_blocks, merge_vision_text_res
from kparser.parserground.parser import PdfParser, ExcelParser, DocxParser, HtmlParser, JsonParser, MarkdownParser, TxtParser, CsvParser, PDFLoader, ExcelVisionParser, DeepseekOCRParser
from kparser.rag.storage import num_tokens_from_string
from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger
from kparser.common.config import VISION

logger = get_logger(__name__)


# 顺序处理word文档元素
def iter_block_items(parent):
    """
    Yield each paragraph and table child within *parent*, in document order.
    Each returned value is an instance of either Table or Paragraph. *parent*
    would most commonly be a reference to a main Document object, but
    also works for a _Cell object, which itself can contain paragraphs and tables.
    """
    if isinstance(parent, DOC):
        parent_elm = parent.element.body
    elif isinstance(parent, _Cell):
        parent_elm = parent._tc
    else:
        raise ValueError("document module: something's not right")

    for child in parent_elm.iterchildren():
        if isinstance(child, CT_P):
            yield Paragraph(child, parent)
        elif isinstance(child, CT_Tbl):
            yield Table(child, parent)


class Docx(DocxParser):
    def __init__(self):
        pass

    def get_picture(self, document, paragraph):
        img = paragraph._element.xpath('.//pic:pic')
        if not img:
            return None
        img = img[0]
        xpath_content = img.xpath('.//a:blip/@r:embed')
        if not xpath_content:
            logger.info("img.xpath('.//a:blip/@r:embed') is null, Skipping image.")
            return None
        embed = img.xpath('.//a:blip/@r:embed')[0]
        related_part = document.part.related_parts[embed]
        try:
            image_blob = related_part.image.blob
        except UnrecognizedImageError:
            logger.info("Unrecognized image format. Skipping image.")
            return None
        except UnexpectedEndOfFileError:
            logger.info("EOF was unexpectedly encountered while reading an image stream. Skipping image.")
            return None
        except InvalidImageStreamError:
            logger.info("The recognized image stream appears to be corrupted. Skipping image.")
            return None
        try:
            image = Image.open(BytesIO(image_blob)).convert('RGB')
            return image
        except Exception:
            return None

    def __clean(self, line):
        line = re.sub(r"\u3000", " ", line).strip()
        return line

    def __call__(self, binary, from_page=0, to_page=100000):
        self.doc = Document(BytesIO(binary))
        pn = 0
        lines = []
        last_image = None

        # 使用页脚文本更新pn变量
        for block_item in iter_block_items(self.doc):
            if isinstance(block_item, Paragraph):
                p = block_item
                if pn > to_page:
                    break
                if from_page <= pn < to_page:
                    if p.text.strip():
                        if p.style and p.style.name == 'Caption':
                            former_image = None
                            if lines and lines[-1][1] and lines[-1][2] != 'Caption':
                                former_image = lines[-1][1].pop()
                            elif last_image:
                                former_image = last_image
                                last_image = None
                            lines.append((self.__clean(p.text), [former_image], p.style.name))
                        else:
                            current_image = self.get_picture(self.doc, p)
                            image_list = [current_image]
                            if last_image:
                                image_list.insert(0, last_image)
                                last_image = None
                            lines.append((self.__clean(p.text), image_list, p.style.name if p.style else "", pn))
                    else:
                        if current_image := self.get_picture(self.doc, p):
                            if lines:
                                lines[-1][1].append(current_image)
                            else:
                                last_image = current_image

                for run in p.runs:
                    if 'lastRenderedPageBreak' in run._element.xml:
                        pn += 1
                        continue
                    if 'w:br' in run._element.xml and 'type="page"' in run._element.xml:
                        pn += 1

            elif isinstance(block_item, Table):
                tb = block_item
                html = "<table>"
                for r in tb.rows:
                    html += "<tr>"
                    i = 0
                    while i < len(r.cells):
                        span = 1
                        c = r.cells[i]
                        for j in range(i + 1, len(r.cells)):
                            if c.text == r.cells[j].text:
                                span += 1
                                i = j
                        i += 1
                        html += f"<td>{c.text}</td>" if span == 1 else f"<td colspan='{span}'>{c.text}</td>"
                    html += "</tr>"
                html += "</table>"
                lines.append((html, [], "Table", pn))
            else:
                logger.warning("Unknown block type: {}".format(type(block_item)))

        new_line = []
        for line in lines:
            if line[2] != "Table":
                new_line.append((line[0], reduce(concat_img, line[1]) if line[1] else None, line[2], line[3]))
            else:
                new_line.append((line[0], line[1], line[2], line[3]))

        return new_line


class Pdf(PdfParser):
    def __call__(self,
                 binary,
                 from_page=0,
                 to_page=100000,
                 zoomin=3,
                 callback=None):
        start = timer()
        logger.info("OCR is running...")
        self.__images__(
            binary,
            zoomin,
            from_page,
            to_page,
            callback
        )
        logger.info("OCR finished")
        logger.info("OCR cost: {}s".format(timer() - start))
        start = timer()
        self._layouts_rec(zoomin)
        logger.info("0.63， Layout analysis finished.")
        self._table_transformer_job(zoomin)
        logger.info("0.65, Table analysis finished.")
        self._text_merge()
        logger.info("0.67, Text merging finished")
        tbls = self._extract_table_figure(True, zoomin, True, True)
        logger.info("0.75, Extract_table_figure finished")
        self._concat_downward()
        logger.info("0.78, Concat downward finished")
        logger.info("layouts cost: {}s".format(timer() - start))
        return [(b["text"], self._line_tag(b, zoomin))
                for b in self.boxes], tbls


class Markdown(MarkdownParser):
    def __call__(self, binary):
        encoding = find_codec(binary)
        txt = binary.decode(encoding, errors="ignore")
        remainder, tables = self.extract_tables_and_remainder(f'{txt}\n')
        sections = []
        tbls = []
        for sec in remainder.split("\n"):
            if num_tokens_from_string(sec) > 10 * self.chunk_token_num:
                sections.append(sec[:int(len(sec) / 2)])
                sections.append(sec[int(len(sec) / 2):])
            else:
                if sections and sections[-1][0].strip().find("#") == 0:
                    sec_ = sections.pop(-1)
                    sections.append(sec_+"\n"+sec)
                else:
                    sections.append(sec)

        for table in tables:
            tbls.append((markdown(table, extensions=['markdown.extensions.tables'])))

        cks = []
        for sec in sections:
            element_text = {"id": get_random_uuid(),
                            "content": sec,
                            "content_type": "TEXT",
                            "page_idx": [1],
                            "extra_data": {}
                            }
            cks.append(element_text)

        for tbl in tbls:
            element_table = {"id": get_random_uuid(),
                             'content': tbl,
                             'content_type': 'TABLE',
                             'page_idx': [1],
                             'extra_data': {}
                             }
            cks.append(element_table)

        return cks


def chunk(filename, real_filename, binary, from_page=0, to_page=100000,
          lang="Chinese", callback=None, **kwargs):
    eng = lang.lower() == "english"  # is_english(cks)
    parser_config = kwargs.get("parser_config", {"layout": True,
                                                 "ocr_content": True,
                                                 "rules": [{"rule_method":"3","feature_value":["ROW_HEADER"]}]})
    doc = {"docnm_kwd": filename}
    res = []
    pdf_parser = None
    
    # 移除查询参数（支持预签名URL）
    real_filename_clean = real_filename.split("?")[0] if real_filename else real_filename
    logger.debug("filename={}, real_filename={}, real_filename_clean={}".format(filename, real_filename, real_filename_clean))
    
    try:
        if re.search(r"\.docx?$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            sections = Docx()(binary)
            logger.info("0.8, Finish parsing.")
            st = timer()
            # 按照docx元素顺序保存
            res= naive_merge_docx(sections)
            logger.info("naive_merge({}): {}".format(filename, timer() - st))
            return res

        elif re.search(r"\.pdf$", real_filename_clean, re.IGNORECASE):
            logger.info("parser_config={}".format(parser_config))
            if parser_config.get("vision"):
                logger.info("pdf: vision mode")
                # pdf_parser = VisionParser(LLMBundle(llm_name=VISION.get("vlm_model"),
                #                                     lang="Chinese",
                #                                     api_url=VISION.get("vlm_api_base")+"/chat/completions",
                #                                     api_key=VISION.get("vlm_api_key"),
                #                                     timeout=90))
                pdf_parser = DeepseekOCRParser()
                res = pdf_parser(binary,
                                from_page=from_page,
                                to_page=to_page)
                return merge_vision_text_res(res)
            elif not parser_config.get("layout"):
                logger.info("pdf: no layout")
                pdf_parser = PDFLoader()
                res = pdf_parser(binary,
                                from_page=from_page,
                                to_page=to_page)
                return res
            elif "rules" in parser_config and int(parser_config["rules"][0]["rule_method"])==2:
                # 无需版面分析
                logger.info("pdf: special rules")
                pdf_parser = PDFLoader()
                res = pdf_parser(binary,
                                from_page=from_page,
                                to_page=to_page)
                return res
            else:
                # 默认需要版面分析逻辑
                logger.info("pdf: layout mode")
                pdf_parser = Pdf()
                sections, tbls = pdf_parser(binary,
                                            from_page=from_page,
                                            to_page=to_page,
                                            callback=callback)
                logger.info("0.8, post processing")
                res = tokenize_table_custom(tbls, doc, eng)  # 表格内容抽取
                # 新增按页对解析内容处理
                text_content_by_page = naive_merge_content_by_page(sections)
                # 文本与表格的顺序处理，构建完整的解析结果
                res = merge_text_table(text_content_by_page, res)
                logger.info("0.85, post processing finished")
                # 顺序处理, 按照 page_idx 和 position[2] 排序
                if parser_config.get("layout"):
                    res = sorted(res, key=lambda x: (get_page_idx_value(x["page_idx"])))
                else:
                    res = sort_text_blocks(res)
                return res

        elif re.search(r"\.xlsx?$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            # vision 分支
            vision_mode = parser_config.get("vision", False)
            if vision_mode:
                excel_vision_parser = ExcelVisionParser()
                res = excel_vision_parser(binary, parser_config)
                logger.info("0.8, Finish vision parsing.")
                return res
            else:
                excel_parser = ExcelParser()
                res = excel_parser(binary, parser_config)
                logger.info("0.8, Finish parsing.")
                return res

        elif re.search(r"\.csv$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            res = CsvParser()(binary, parser_config)
            logger.info("0.8, Finish parsing.")
            return res

        elif re.search(r"\.(txt|py|js|java|c|cpp|h|php|go|ts|sh|cs|kt|sql|jsonl)$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            res = TxtParser()(binary, parser_config)
            logger.info("0.8, Finish parsing.")
            return res

        elif re.search(r"\.(md|markdown)$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            res = Markdown()(binary)
            logger.info("0.8, Finish parsing.")
            return res

        elif re.search(r"\.(htm|html)$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            res = HtmlParser()(binary)
            logger.info("0.8, Finish parsing.")
            return res

        elif re.search(r"\.json$", real_filename_clean, re.IGNORECASE):
            logger.info("0.1, Start to parse.")
            res = JsonParser()(binary, parser_config)
            logger.info("0.8, Finish parsing.")
            return res

        else:
            raise NotImplementedError(
                "file type not supported yet")
    except Exception as e:
        raise e