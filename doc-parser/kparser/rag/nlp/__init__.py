import random
import re
import copy
import roman_numbers as r
from word2number import w2n
from cn2an import cn2an
from PIL import Image
import json
import chardet
from collections import Counter

from kparser.rag.storage import num_tokens_from_string
from . import rag_tokenizer
from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


all_codecs = [
    'utf-8', 'gb2312', 'gbk', 'utf_16', 'ascii', 'big5', 'big5hkscs',
    'cp037', 'cp273', 'cp424', 'cp437',
    'cp500', 'cp720', 'cp737', 'cp775', 'cp850', 'cp852', 'cp855', 'cp856', 'cp857',
    'cp858', 'cp860', 'cp861', 'cp862', 'cp863', 'cp864', 'cp865', 'cp866', 'cp869',
    'cp874', 'cp875', 'cp932', 'cp949', 'cp950', 'cp1006', 'cp1026', 'cp1125',
    'cp1140', 'cp1250', 'cp1251', 'cp1252', 'cp1253', 'cp1254', 'cp1255', 'cp1256',
    'cp1257', 'cp1258', 'euc_jp', 'euc_jis_2004', 'euc_jisx0213', 'euc_kr',
    'gb2312', 'gb18030', 'hz', 'iso2022_jp', 'iso2022_jp_1', 'iso2022_jp_2',
    'iso2022_jp_2004', 'iso2022_jp_3', 'iso2022_jp_ext', 'iso2022_kr', 'latin_1',
    'iso8859_2', 'iso8859_3', 'iso8859_4', 'iso8859_5', 'iso8859_6', 'iso8859_7',
    'iso8859_8', 'iso8859_9', 'iso8859_10', 'iso8859_11', 'iso8859_13',
    'iso8859_14', 'iso8859_15', 'iso8859_16', 'johab', 'koi8_r', 'koi8_t', 'koi8_u',
    'kz1048', 'mac_cyrillic', 'mac_greek', 'mac_iceland', 'mac_latin2', 'mac_roman',
    'mac_turkish', 'ptcp154', 'shift_jis', 'shift_jis_2004', 'shift_jisx0213',
    'utf_32', 'utf_32_be', 'utf_32_le', 'utf_16_be', 'utf_16_le', 'utf_7', 'windows-1250', 'windows-1251',
    'windows-1252', 'windows-1253', 'windows-1254', 'windows-1255', 'windows-1256',
    'windows-1257', 'windows-1258', 'latin-2'
]


def find_codec(blob):
    detected = chardet.detect(blob[:1024])
    if detected['confidence'] > 0.5:
        return detected['encoding']

    for c in all_codecs:
        try:
            blob[:1024].decode(c)
            return c
        except Exception:
            pass
        try:
            blob.decode(c)
            return c
        except Exception:
            pass

    return "utf-8"

QUESTION_PATTERN = [
    r"第([零一二三四五六七八九十百0-9]+)问",
    r"第([零一二三四五六七八九十百0-9]+)条",
    r"[\(（]([零一二三四五六七八九十百]+)[\)）]",
    r"第([0-9]+)问",
    r"第([0-9]+)条",
    r"([0-9]{1,2})[\. 、]",
    r"([零一二三四五六七八九十百]+)[ 、]",
    r"[\(（]([0-9]{1,2})[\)）]",
    r"QUESTION (ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN)",
    r"QUESTION (I+V?|VI*|XI|IX|X)",
    r"QUESTION ([0-9]+)",
]

def has_qbullet(reg, box, last_box, last_index, last_bull, bull_x0_list):
    section, last_section = box['text'], last_box['text']
    q_reg = r'(\w|\W)*?(?:？|\?|\n|$)+'
    full_reg = reg + q_reg
    has_bull = re.match(full_reg, section)
    index_str = None
    if has_bull:
        if 'x0' not in last_box:
            last_box['x0'] = box['x0']
        if 'top' not in last_box:
            last_box['top'] = box['top']
        if last_bull and box['x0']-last_box['x0']>10:
            return None, last_index
        if not last_bull and box['x0'] >= last_box['x0'] and box['top'] - last_box['top'] < 20:
            return None, last_index
        avg_bull_x0 = 0
        if bull_x0_list:
            avg_bull_x0 = sum(bull_x0_list) / len(bull_x0_list)
        else:
            avg_bull_x0 = box['x0']
        if box['x0'] - avg_bull_x0 > 10:
            return None, last_index
        index_str = has_bull.group(1)
        index = index_int(index_str)
        if last_section[-1] == ':' or last_section[-1] == '：':
            return None, last_index
        if not last_index or index >= last_index:
            bull_x0_list.append(box['x0'])
            return has_bull, index
        if section[-1] == '?' or section[-1] == '？':
            bull_x0_list.append(box['x0'])
            return has_bull, index
        if box['layout_type'] == 'title':
            bull_x0_list.append(box['x0'])
            return has_bull, index
        pure_section = section.lstrip(re.match(reg, section).group()).lower()
        ask_reg = r'(what|when|where|how|why|which|who|whose|为什么|为啥|哪)'
        if re.match(ask_reg, pure_section):
            bull_x0_list.append(box['x0'])
            return has_bull, index
    return None, last_index

def index_int(index_str):
    res = -1
    try:
        res=int(index_str)
    except ValueError:
        try:
            res=w2n.word_to_num(index_str)
        except ValueError:
            try:
                res = cn2an(index_str)
            except ValueError:
                try:
                    res = r.number(index_str)
                except ValueError:
                    return -1
    return res

def qbullets_category(sections):
    global QUESTION_PATTERN
    hits = [0] * len(QUESTION_PATTERN)
    for i, pro in enumerate(QUESTION_PATTERN):
        for sec in sections:
            if re.match(pro, sec) and not not_bullet(sec):
                hits[i] += 1
                break
    maxium = 0
    res = -1
    for i, h in enumerate(hits):
        if h <= maxium:
            continue
        res = i
        maxium = h
    return res, QUESTION_PATTERN[res]


BULLET_PATTERN = [[
    r"第[零一二三四五六七八九十百0-9]+(分?编|部分)",
    r"第[零一二三四五六七八九十百0-9]+章",
    r"第[零一二三四五六七八九十百0-9]+节",
    r"第[零一二三四五六七八九十百0-9]+条",
    r"[\(（][零一二三四五六七八九十百]+[\)）]",
], [
    r"第[0-9]+章",
    r"第[0-9]+节",
    r"[0-9]{,2}[\. 、]",
    r"[0-9]{,2}\.[0-9]{,2}[^a-zA-Z/%~-]",
    r"[0-9]{,2}\.[0-9]{,2}\.[0-9]{,2}",
    r"[0-9]{,2}\.[0-9]{,2}\.[0-9]{,2}\.[0-9]{,2}",
], [
    r"第[零一二三四五六七八九十百0-9]+章",
    r"第[零一二三四五六七八九十百0-9]+节",
    r"[零一二三四五六七八九十百]+[ 、]",
    r"[\(（][零一二三四五六七八九十百]+[\)）]",
    r"[\(（][0-9]{,2}[\)）]",
], [
    r"PART (ONE|TWO|THREE|FOUR|FIVE|SIX|SEVEN|EIGHT|NINE|TEN)",
    r"Chapter (I+V?|VI*|XI|IX|X)",
    r"Section [0-9]+",
    r"Article [0-9]+"
]
]


def random_choices(arr, k):
    k = min(len(arr), k)
    return random.choices(arr, k=k)


def not_bullet(line):
    patt = [
        r"0", r"[0-9]+ +[0-9~个只-]", r"[0-9]+\.{2,}"
    ]
    return any([re.match(r, line) for r in patt])


def bullets_category(sections):
    global BULLET_PATTERN
    hits = [0] * len(BULLET_PATTERN)
    for i, pro in enumerate(BULLET_PATTERN):
        for sec in sections:
            for p in pro:
                if re.match(p, sec) and not not_bullet(sec):
                    hits[i] += 1
                    break
    maxium = 0
    res = -1
    for i, h in enumerate(hits):
        if h <= maxium:
            continue
        res = i
        maxium = h
    return res


def is_english(texts):
    eng = 0
    if not texts: return False
    for t in texts:
        if re.match(r"[ `a-zA-Z.,':;/\"?<>!\(\)-]", t.strip()):
            eng += 1
    if eng / len(texts) > 0.8:
        return True
    return False


def tokenize(d, t):
    # 原始表格数据
    d["content_with_weight"] = t
    # 去除表格标识符的文本数据
    t = re.sub(r"</?(table|td|caption|tr|th)( [^<>]{0,12})?>", " ", t)
    d["content_ltks"] = t


def tokenize_chunks(chunks, doc, pdf_parser=None):
    res = []
    # wrap up as es documents
    for ck in chunks:
        print("ck={}".format(ck))
        if len(ck.strip()) == 0:continue
        logger.debug("-- {}".format(ck))
        d = copy.deepcopy(doc)
        if pdf_parser:
            try:
                d["image"], poss = pdf_parser.crop(ck, need_position=True)
                add_positions(d, poss)
                ck = pdf_parser.remove_tag(ck)
            except NotImplementedError:
                pass
        d["content_with_weight"] = ck
        res.append(d)
    return res


def tokenize_chunks_docx(chunks, doc, images):
    res = []
    # wrap up as es documents
    for ck, image in zip(chunks, images):
        if len(ck.strip()) == 0:continue
        logger.debug("-- {}".format(ck))
        d = copy.deepcopy(doc)
        d["image"] = image
        tokenize(d, ck)
        res.append(d)
    return res


def tokenize_table(tbls, doc, eng=False, batch_size=10):  # 暂时默认都是中文文档
    res = []
    # add tables
    for (img, rows), poss in tbls:
        if not rows:
            continue
        if isinstance(rows, str):
            d = copy.deepcopy(doc)
            tokenize(d, rows)
            if img: d["image"] = img
            if poss: add_positions(d, poss)
            res.append(d)
            continue
        # 兜底策略
        de = "; " if eng else "； "
        for i in range(0, len(rows), batch_size):
            d = copy.deepcopy(doc)
            r = de.join(rows[i:i + batch_size])
            tokenize(d, r)
            d["image"] = img
            add_positions(d, poss)
            res.append(d)
    return res


def tokenize_table_custom(tbls, doc, eng=False, batch_size=10):  # 暂时默认都是中文文档
    res = []
    # add tables
    for (img, rows), poss in tbls:
        if not rows:
            continue
        if isinstance(rows, str):
            d = copy.deepcopy(doc)
            tokenize(d, rows)
            if img: d["image"] = img
            if poss: add_positions_custom(d, poss)
            res.append(d)
            continue
        # 兜底策略
        de = "; " if eng else "； "
        for i in range(0, len(rows), batch_size):
            d = copy.deepcopy(doc)
            r = de.join(rows[i:i + batch_size])
            tokenize(d, r)
            d["image"] = img
            add_positions_custom(d, poss)
            res.append(d)
    return res


def add_positions(d, poss):
    if not poss:
        return
    page_num_list = []
    position_list = []
    top_list = []
    for pn, left, right, top, bottom in poss:
        page_num_list.append(int(pn + 1))
        top_list.append(int(top))
        position_list.append((int(pn + 1), int(left), int(right), int(top), int(bottom)))
    d["page_num_list"] = json.dumps(page_num_list)
    d["position_list"] = json.dumps(position_list)
    d["top_list"] = json.dumps(top_list)


def add_positions_custom(d, poss):
    if not poss:
        return
    page_num_list = []
    position_list = []
    top_list = []
    page_size_list = []
    
    for pos_tuple in poss:
        # 兼容旧格式(5个元素)和新格式(7个元素)
        if len(pos_tuple) == 5:
            pn, left, right, top, bottom = pos_tuple
            page_size = None
        elif len(pos_tuple) == 7:
            pn, left, right, top, bottom, width, height = pos_tuple
            page_size = [float(width), float(height)]
        else:
            logger.warning(f"位置元组格式不正确: {pos_tuple}, 长度={len(pos_tuple)}")
            continue
            
        page_num_list.append(int(pn + 1))
        top_list.append(int(top))
        position_list.append([int(left), int(right), int(top), int(bottom)])
        if page_size is not None:
            page_size_list.append(page_size)
    
    d["page_num_list"] = page_num_list
    d["position_list"] = position_list
    d["top_list"] = top_list
    if page_size_list:
        d["page_size_list"] = page_size_list


def remove_contents_table(sections, eng=False):
    i = 0
    while i < len(sections):
        def get(i):
            nonlocal sections
            return (sections[i] if isinstance(sections[i],
                    type("")) else sections[i][0]).strip()

        if not re.match(r"(contents|目录|目次|table of contents|致谢|acknowledge)$",
                        re.sub(r"( | |\u3000)+", "", get(i).split("@@")[0], re.IGNORECASE)):
            i += 1
            continue
        sections.pop(i)
        if i >= len(sections):
            break
        prefix = get(i)[:3] if not eng else " ".join(get(i).split()[:2])
        while not prefix:
            sections.pop(i)
            if i >= len(sections):
                break
            prefix = get(i)[:3] if not eng else " ".join(get(i).split()[:2])
        sections.pop(i)
        if i >= len(sections) or not prefix:
            break
        for j in range(i, min(i + 128, len(sections))):
            if not re.match(prefix, get(j)):
                continue
            for _ in range(i, j):
                sections.pop(i)
            break


def make_colon_as_title(sections):
    if not sections:
        return []
    if isinstance(sections[0], type("")):
        return sections
    i = 0
    while i < len(sections):
        txt, layout = sections[i]
        i += 1
        txt = txt.split("@")[0].strip()
        if not txt:
            continue
        if txt[-1] not in ":：":
            continue
        txt = txt[::-1]
        arr = re.split(r"([。？！!?;；]| \.)", txt)
        if len(arr) < 2 or len(arr[1]) < 32:
            continue
        sections.insert(i - 1, (arr[0][::-1], "title"))
        i += 1


def title_frequency(bull, sections):
    bullets_size = len(BULLET_PATTERN[bull])
    levels = [bullets_size+1 for _ in range(len(sections))]
    if not sections or bull < 0:
        return bullets_size+1, levels

    for i, (txt, layout) in enumerate(sections):
        for j, p in enumerate(BULLET_PATTERN[bull]):
            if re.match(p, txt.strip()) and not not_bullet(txt):
                levels[i] = j
                break
        else:
            if re.search(r"(title|head)", layout) and not not_title(txt.split("@")[0]):
                levels[i] = bullets_size
    most_level = bullets_size+1
    for l, c in sorted(Counter(levels).items(), key=lambda x:x[1]*-1):
        if l <= bullets_size:
            most_level = l
            break
    return most_level, levels


def not_title(txt):
    if re.match(r"第[零一二三四五六七八九十百0-9]+条", txt):
        return False
    if len(txt.split()) > 12 or (txt.find(" ") < 0 and len(txt) >= 32):
        return True
    return re.search(r"[,;，。；！!]", txt)


def hierarchical_merge(bull, sections, depth):
    if not sections or bull < 0:
        return []
    if isinstance(sections[0], type("")):
        sections = [(s, "") for s in sections]
    sections = [(t, o) for t, o in sections if
                t and len(t.split("@")[0].strip()) > 1 and not re.match(r"[0-9]+$", t.split("@")[0].strip())]
    bullets_size = len(BULLET_PATTERN[bull])
    levels = [[] for _ in range(bullets_size + 2)]


    for i, (txt, layout) in enumerate(sections):
        for j, p in enumerate(BULLET_PATTERN[bull]):
            if re.match(p, txt.strip()):
                levels[j].append(i)
                break
        else:
            if re.search(r"(title|head)", layout) and not not_title(txt):
                levels[bullets_size].append(i)
            else:
                levels[bullets_size + 1].append(i)
    sections = [t for t, _ in sections]

    # for s in sections: print("--", s)

    def binary_search(arr, target):
        if not arr:
            return -1
        if target > arr[-1]:
            return len(arr) - 1
        if target < arr[0]:
            return -1
        s, e = 0, len(arr)
        while e - s > 1:
            i = (e + s) // 2
            if target > arr[i]:
                s = i
                continue
            elif target < arr[i]:
                e = i
                continue
            else:
                assert False
        return s

    cks = []
    readed = [False] * len(sections)
    levels = levels[::-1]
    for i, arr in enumerate(levels[:depth]):
        for j in arr:
            if readed[j]:
                continue
            readed[j] = True
            cks.append([j])
            if i + 1 == len(levels) - 1:
                continue
            for ii in range(i + 1, len(levels)):
                jj = binary_search(levels[ii], j)
                if jj < 0:
                    continue
                if jj > cks[-1][-1]:
                    cks[-1].pop(-1)
                cks[-1].append(levels[ii][jj])
            for ii in cks[-1]:
                readed[ii] = True

    if not cks:
        return cks

    for i in range(len(cks)):
        cks[i] = [sections[j] for j in cks[i][::-1]]
        logger.debug("\n* ".join(cks[i]))

    res = [[]]
    num = [0]
    for ck in cks:
        if len(ck) == 1:
            n = num_tokens_from_string(re.sub(r"@@[0-9]+.*", "", ck[0]))
            if n + num[-1] < 218:
                res[-1].append(ck[0])
                num[-1] += n
                continue
            res.append(ck)
            num.append(n)
            continue
        res.append(ck)
        num.append(218)

    return res

# 按页维度进行内容存储聚合
def naive_merge_content_by_page(sections):
    if not sections:
        return []
    if isinstance(sections[0], type("")):
        sections = [(s, "") for s in sections]

    # 按页维度进行保存
    cks_by_page = []
    for sec, pos in sections:
        if "#" in pos:
            poss = []
            parts = pos.strip("#").strip("@").split("\t")
            
            # 兼容旧格式和新格式
            if len(parts) == 5:
                # 旧格式: pn, left, right, top, bottom
                pn, left, right, top, bottom = parts
                page_size = None
            elif len(parts) == 7:
                # 新格式: pn, left, right, top, bottom, width, height
                pn, left, right, top, bottom, width, height = parts
                page_size = [float(width), float(height)]
            else:
                logger.warning(f"位置信息格式不正确: {pos}, 使用默认值")
                pn = "1"
                left, right, top, bottom = 0, 0, 0, 0
                page_size = None
            
            left, right, top, bottom = float(left), float(right), float(top), float(bottom)
            poss.append(([int(p) - 1 for p in pn.split("-")],
                         left, right, top, bottom))
            
            extra_data = {"position": [left, right, top, bottom]}
            if page_size is not None:
                extra_data["page_size"] = page_size
            
            if "-" not in pn:
                element = {"id": get_random_uuid(),
                           "content": sec,
                           "content_type": "TEXT",
                           "page_idx": [int(pn)],
                           "extra_data": extra_data
                           }
            else:
                element = {"id": get_random_uuid(),
                           "content": sec,
                           "content_type": "TEXT",
                           "page_idx": [int(pn.split("-")[0])],
                           "extra_data": extra_data
                           }
        else:
            element = {"id": get_random_uuid(),
                       "content": sec,
                       "content_type": "TEXT",
                       "page_idx": [int(pos)],
                       "extra_data":{}
                       }
        cks_by_page.append(element)

    return cks_by_page


def naive_merge(sections, chunk_token_num=128, delimiter="\n。；！？"):
    if not sections:
        return []
    if isinstance(sections[0], type("")):
        sections = [(s, "") for s in sections]
    cks = [""]
    tk_nums = [0]

    def add_chunk(t, pos):
        nonlocal cks, tk_nums, delimiter
        tnum = num_tokens_from_string(t)
        if not pos: pos = ""
        if tnum < 8:
            pos = ""
        # Ensure that the length of the merged chunk does not exceed chunk_token_num  
        if tk_nums[-1] > chunk_token_num:

            if t.find(pos) < 0:
                t += pos
            cks.append(t)
            tk_nums.append(tnum)
        else:
            if cks[-1].find(pos) < 0:
                t += pos
            cks[-1] += t
            tk_nums[-1] += tnum

    for sec, pos in sections:
        add_chunk(sec, pos)

    return cks


def docx_question_level(p, bull = -1):
    txt = re.sub(r"\u3000", " ", p.text).strip()
    if p.style.name.startswith('Heading'):
        return int(p.style.name.split(' ')[-1]), txt
    else:
        if bull < 0:
            return 0, txt
        for j, title in enumerate(BULLET_PATTERN[bull]):
            if re.match(title, txt):
                return j+1, txt
    return len(BULLET_PATTERN[bull]), txt

    
def concat_img(img1, img2):
    if img1 and not img2:
        return img1
    if not img1 and img2:
        return img2
    if not img1 and not img2:
        return None
    width1, height1 = img1.size
    width2, height2 = img2.size

    new_width = max(width1, width2)
    new_height = height1 + height2
    new_image = Image.new('RGB', (new_width, new_height))

    new_image.paste(img1, (0, 0))
    new_image.paste(img2, (0, height1))

    return new_image


def merge_text_table(text_data_list, table_data_list):
    # 将表格数据转换为与文本数据相同的格式
    formatted_table_data_list = []
    for table_data in table_data_list:
        page_num_list = table_data['page_num_list']
        position_list = table_data['position_list']
        text_data = table_data['content_with_weight']
        if "table" in text_data:
            formatted_table_data = {"id": get_random_uuid(),
                                    'content': table_data['content_with_weight'],
                                    'content_type': 'TABLE',
                                    'page_idx': page_num_list,
                                    'extra_data': {
                                        'position': position_list
                                    }
                                    }
            # 添加 page_size_list 如果存在
            if 'page_size_list' in table_data:
                formatted_table_data["extra_data"]["page_size"] = table_data['page_size_list'][0] if table_data['page_size_list'] else None
            
            # 添加image对应的图片数据
            if "image" in table_data:
                image_content = table_data['image']
                formatted_table_data["extra_data"]["image"] = image_content

            formatted_table_data_list.append(formatted_table_data)
        else:
            if "临时示例图片" in text_data:
                text_data = text_data.replace("临时示例图片", "")
                text_data_no_space = re.sub(r"\s+", "", text_data)
                if text_data_no_space == "":
                    text_data = ""
            formatted_image_data = {"id": get_random_uuid(),
                                    'content': text_data,
                                    'content_type': 'IMAGE',
                                    'page_idx': page_num_list,
                                    'extra_data': {
                                        'position': position_list
                                    }
                                    }
            
            # 添加 page_size_list 如果存在
            if 'page_size_list' in table_data:
                formatted_image_data["extra_data"]["page_size"] = table_data['page_size_list'][0] if table_data['page_size_list'] else None

            if "\n" in text_data:
                text_data = text_data.split("\n")[0]
                formatted_image_text_data = {"id": get_random_uuid(),
                                            'content': text_data,
                                            'content_type': 'TEXT',
                                            'page_idx': page_num_list,
                                            'extra_data': {
                                                'position': position_list
                                            }
                                            }
                # 添加 page_size_list 如果存在
                if 'page_size_list' in table_data:
                    formatted_image_text_data["extra_data"]["page_size"] = table_data['page_size_list'][0] if table_data['page_size_list'] else None
            else:
                if text_data != "":
                    formatted_image_text_data = {"id": get_random_uuid(),
                                                 'content': text_data,
                                                 'content_type': 'TEXT',
                                                 'page_idx': page_num_list,
                                                 'extra_data': {
                                                     'position': position_list
                                                 }
                                                 }
                    # 添加 page_size_list 如果存在
                    if 'page_size_list' in table_data:
                        formatted_image_text_data["extra_data"]["page_size"] = table_data['page_size_list'][0] if table_data['page_size_list'] else None
                else:
                    formatted_image_text_data = None

            # 添加image对应的图片数据
            if "image" in table_data:
                image_content = table_data['image']
                formatted_image_data["extra_data"]["image"] = image_content

            formatted_table_data_list.append(formatted_image_data)
            if formatted_image_text_data is not None:
                formatted_table_data_list.append(formatted_image_text_data)

    # 将表格数据插入到文本数据列表中
    merged_data_list = []
    text_idx = 0
    table_idx = 0

    while text_idx < len(text_data_list) and table_idx < len(formatted_table_data_list):
        text_data = text_data_list[text_idx]
        table_data = formatted_table_data_list[table_idx]

        if isinstance(table_data['page_idx'], list):
            table_data_page_idx = table_data['page_idx'][0]
        else:
            table_data_page_idx = table_data['page_idx']

        if text_data['page_idx'][0] == table_data_page_idx:
            text_top = text_data['extra_data']['position'][2]
            table_top = table_data['extra_data']['position'][0][2]
            if text_top < table_top:
                merged_data_list.append(text_data)
                text_idx += 1
            else:
                merged_data_list.append(table_data)
                table_idx += 1
        elif text_data['page_idx'][0] < table_data_page_idx:
            merged_data_list.append(text_data)
            text_idx += 1
        else:
            merged_data_list.append(table_data)
            table_idx += 1

    # 将剩余的文本数据或表格数据添加到合并后的列表中
    while text_idx < len(text_data_list):
        merged_data_list.append(text_data_list[text_idx])
        text_idx += 1

    while table_idx < len(formatted_table_data_list):
        merged_data_list.append(formatted_table_data_list[table_idx])
        table_idx += 1

    return merged_data_list


def merge_vision_text_res(vision_text_list):
    """
    合并视觉文本识别结果
    
    注意：对于 DeepseekOCRParser 的新格式输出，此函数直接返回已处理好的块列表，
         无需额外处理。仅为兼容旧格式保留转换逻辑。
    
    Args:
        vision_text_list: 可以是以下两种格式之一：
            1. 旧格式: [[(txt, poss), ...], []]  - poss 格式为 "页码 x0 x1 top bottom"
            2. 新格式: [blocks, []]  - blocks 是结构化的块列表（DeepseekOCRParser 输出）
               - 每个块包含 id, content, content_type, page_idx, extra_data
               - content_type 支持: TEXT, IMAGE, TABLE
               - IMAGE 和 TABLE 类型的块已包含裁剪后的图片 (extra_data['image'])
    
    Returns:
        list: 统一的结构化块列表
    """
    if not vision_text_list or not vision_text_list[0]:
        return []
    
    # 判断是新格式还是旧格式
    first_item = vision_text_list[0][0] if vision_text_list[0] else None
    
    # 新格式：DeepseekOCRParser 已经返回完整的结构化块列表，直接返回即可
    if isinstance(first_item, dict):
        # 可选：记录日志便于调试
        if logger.isEnabledFor(10):  # DEBUG level
            block_types = {}
            for block in vision_text_list[0]:
                content_type = block.get('content_type', 'UNKNOWN')
                block_types[content_type] = block_types.get(content_type, 0) + 1
            logger.debug(f"处理 {len(vision_text_list[0])} 个块，类型统计: {block_types}")
        
        return vision_text_list[0]
    
    # 旧格式：转换为新格式（保持向后兼容）
    elif isinstance(first_item, (tuple, list)) and len(first_item) == 2:
        logger.debug(f"转换旧格式 {len(vision_text_list[0])} 个元组")
        merged_data_list = []
        
        for txt, poss in vision_text_list[0]:
            try:
                pn, x0, x1, top, bott = poss.split(" ")
                position = [float(x0), float(x1), float(top), float(bott)]
                itm = {
                    "id": get_random_uuid(),
                    'content': txt,
                    'content_type': 'TEXT',
                    'page_idx': [int(pn)],
                    'extra_data': {
                        'position': position
                    }
                }
                merged_data_list.append(itm)
            except Exception as e:
                logger.warning(f"转换旧格式失败: {e}")
                continue
        
        return merged_data_list
    
    else:
        logger.warning(f"未知的格式: {type(first_item)}")
        return []


def naive_merge_docx(sections):
    if not sections:
        return []

    cks_by_page = []
    for sec, image, type, pn in sections:
        if type == "Table":
            element_table = {"id": get_random_uuid(),
                               "content": sec,
                               "content_type": "TABLE",
                               "page_idx": [pn + 1],
                               "extra_data": {}
                               }
            # 保存表格
            cks_by_page.append(element_table)
        else:
            if image is None:
                element_text = {"id": get_random_uuid(),
                               "content": sec,
                               "content_type": "TEXT",
                               "page_idx": [pn + 1],
                               "extra_data":{}
                               }
                # 保存文本
                cks_by_page.append(element_text)
            else:
                element_text_image = {"id": get_random_uuid(),
                                       "content": sec,
                                       "content_type": "TEXT",
                                       "page_idx": [pn + 1],
                                       "extra_data": {}
                                       }
                # 保存文本
                cks_by_page.append(element_text_image)

                # 保存图片
                element_image = {"id": get_random_uuid(),
                           "content_type": "IMAGE",
                           "page_idx": [pn + 1],
                           "extra_data": {"image":image,
                                          "image_name": sec}
                          }
                cks_by_page.append(element_image)

    return cks_by_page


def get_position_value(position, ith):
    """
    根据 position 的结构提取第三个数字。
    """
    if isinstance(position, list):
        if all(isinstance(item, (list, tuple)) for item in position):  # 双层列表
            if len(position) == 1:
                return position[0][ith]  # 取第一个子列表的第三个数字
            else:
                return position[1][ith]
        elif all(isinstance(item, (int, float)) for item in position):  # 单层列表
            return position[ith]
    return 0  # 如果格式不符合预期，返回 0


def get_page_idx_value(page_idx):
    """
    根据 page_idx 的结构提取排序值。
    如果 page_idx 是列表，返回第一个值；如果是 int，直接返回。
    """
    if isinstance(page_idx, list):
        if len(page_idx) == 1:
            return page_idx[0]
        else:
            return page_idx[1]
    return page_idx  # 如果是 int，直接返回


def extract_max_page_width(blocks):
    """
    提取页面的最大宽度。
    """
    page_width_list = []
    for block in blocks:
        if isinstance(block["extra_data"]["position"], list) and len(
                block["extra_data"]["position"]) == 1 and isinstance(block["extra_data"]["position"][0], list):
            page_width = block["extra_data"]["position"][0][1]
            page_width_list.append(page_width)
        elif isinstance(block["extra_data"]["position"], list) and isinstance(block["extra_data"]["position"][0],
                                                                              list) and len(
                block["extra_data"]["position"]) > 1:
            for sub_pos in block["extra_data"]["position"]:
                page_width_list.append(sub_pos[1])
        else:
            page_width = block["extra_data"]["position"][1]
            page_width_list.append(page_width)

    return max(page_width_list) if page_width_list else 0


'''
(1)支持单栏和双栏判定
通过 页面横坐标极差 判定是否可能是双栏。
计算 页面中心线 (page_center_x)，用于左右栏归类。
计算文本块在左栏和右栏的 覆盖比例，以90%为阈值：
左栏： 90%以上内容在左侧
右栏： 90%以上内容在右侧
通栏： 既不完全在左，也不完全在右

(2)排序逻辑优化
左栏和右栏分别按 top 坐标递增排序，保持阅读顺序。
通栏特殊处理：确保通栏上下的左右栏按顺序排列，避免错乱。
'''
def sort_text_blocks(res):
    """
    对文本块进行排序，支持单栏、双栏、通栏布局
    :param res: 文本块列表，每个文本块包含 'page_idx' 和 'extra_data'（包含 'position' 坐标）
    :return: 经过排序的文本块列表
    """
    # 按页码排序
    res.sort(key=lambda x: get_page_idx_value(x["page_idx"]))

    # 遍历每一页，按单栏/双栏方式排序
    sorted_res = []
    pages = {}

    # 先按页分组
    for block in res:
        page_idx = get_page_idx_value(block["page_idx"])
        if page_idx not in pages:
            pages[page_idx] = []
        pages[page_idx].append(block)

    # 处理每一页
    for page_idx, blocks in pages.items():
        if not blocks:
            continue

        # 计算页面最大宽度，推测页面中心线
        max_page_width = extract_max_page_width(blocks)
        page_center_x = max_page_width / 2
        left_column, right_column, full_column = [], [], []

        # 分类文本块
        for block in blocks:
            position = block["extra_data"]["position"]
            # logger.info("position={}".format(position))
            if isinstance(position, list) and len(position) == 1 and isinstance(position[0], list):
                x1, x2, y1, y2 = position[0]
            elif isinstance(position, list) and len(position) > 1 and isinstance(position[0], list):
                x1, x2, y1, y2 = position[1]
            else:
                x1, x2, y1, y2 = position

            block_width = x2 - x1
            left_part = max(0, min(x2, page_center_x) - x1)
            right_part = max(0, x2 - max(x1, page_center_x))

            left_ratio = left_part / block_width if block_width > 0 else 0
            right_ratio = right_part / block_width if block_width > 0 else 0

            if left_ratio >= 0.9:  # 90%以上内容在左侧
                left_column.append(block)
            elif right_ratio >= 0.9:  # 90%以上内容在右侧
                right_column.append(block)
            else:  # 既不完全在左，也不完全在右，归为通栏
                full_column.append(block)

        # 对每个栏进行从上到下排序
        left_column.sort(key=lambda b: get_position_value(b["extra_data"]["position"], 2))
        right_column.sort(key=lambda b: get_position_value(b["extra_data"]["position"],2))
        full_column.sort(key=lambda b: get_position_value(b["extra_data"]["position"],2))

        # 处理通栏情况
        if full_column:
            # 假设通栏块可能分割了左右栏
            sorted_blocks = []
            min_full_top = get_position_value(full_column[0]["extra_data"]["position"], 2)  # 通栏的最上端
            max_full_bottom = get_position_value(full_column[-1]["extra_data"]["position"], 3)  # 通栏的最下端

            # for b in left_column:
            #     logger.info("b[extra_data][position]={}".format(b["extra_data"]["position"]))
            #
            # for b in right_column:
            #     logger.info("b[extra_data][position]={}".format(b["extra_data"]["position"]))

            # 先添加通栏上方的左栏
            sorted_blocks += [b for b in left_column if get_position_value(b["extra_data"]["position"],3) < min_full_top]
            # 再添加通栏上方的右栏
            sorted_blocks += [b for b in right_column if get_position_value(b["extra_data"]["position"],3) < min_full_top]
            # 添加通栏
            sorted_blocks += full_column
            # 添加通栏下方的左栏
            sorted_blocks += [b for b in left_column if get_position_value(b["extra_data"]["position"],2) > max_full_bottom]
            # 添加通栏下方的右栏
            sorted_blocks += [b for b in right_column if get_position_value(b["extra_data"]["position"],2) > max_full_bottom]
        else:
            # 没有通栏，按正常顺序排列
            sorted_blocks = left_column + right_column

        sorted_res.extend(sorted_blocks)

    return sorted_res


def clean_markdown_block(text):
    text = re.sub(r'^\s*```markdown\s*\n?', '', text)
    text = re.sub(r'\n?\s*```\s*$', '', text)
    return text.strip()