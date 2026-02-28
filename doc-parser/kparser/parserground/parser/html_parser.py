import readability
import chardet
from bs4 import BeautifulSoup, Comment, Tag, NavigableString
import html
import uuid

from kparser.rag.nlp import find_codec, rag_tokenizer
from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)

def get_encoding(file):
    with open(file,'rb') as f:
        tmp = chardet.detect(f.read())
        return tmp['encoding']

BLOCK_TAGS = [
    "h1", "h2", "h3", "h4", "h5", "h6",
    "p", "div", "article", "section", "aside",
    "main", "header", "footer", "nav",  # HTML5语义标签
    "details", "summary",  # 交互元素
    "ul", "ol", "li",
    "table", "pre", "code", "blockquote",
    "figure", "figcaption",
    "dialog", "address",  # 其他语义标签
]
TITLE_TAGS = {"h1": "#", "h2": "##", "h3": "###", "h4": "#####", "h5": "#####", "h6": "######"}


def remove_extra_newlines(text):
    # 使用splitlines()将文本分割成行，然后过滤掉空行
    lines = text.splitlines()
    non_empty_lines = [line for line in lines if line.strip() != '']

    # 将非空行重新组合成文本，这里使用单个换行符作为行分隔符
    cleaned_text = '\n'.join(non_empty_lines)
    return cleaned_text

# class RAGHtmlParser:
#     def __call__(self, binary):
#         encoding = find_codec(binary)
#         txt = binary.decode(encoding, errors="ignore")
#         return self.parser_txt(txt)

#     @classmethod
#     def parser_txt(cls, txt):
#         if not isinstance(txt, str):
#             raise TypeError("txt type should be str!")
#         html_doc = readability.Document(txt)
#         title = html_doc.title()
#         soup = BeautifulSoup(txt, 'html.parser')
#         content = soup.get_text()
#         content = remove_extra_newlines(content)
#         txt = f"{title}\n{content}"
#         sections = [{"id": get_random_uuid(),
#                         "content": txt,
#                         "content_type": "TEXT",
#                         "page_idx": [1],
#                         "extra_data": {}
#                         }]
#         return sections

class RAGHtmlParser:
    def __call__(self, fnm, binary=None, chunk_token_num=None):
        if binary:
            encoding = find_codec(binary)
            txt = binary.decode(encoding, errors="ignore")
        else:
            with open(fnm, "r",encoding=get_encoding(fnm)) as f:
                txt = f.read()
        return self.parser_txt(txt, chunk_token_num)

    @classmethod
    def parser_txt(cls, txt, chunk_token_num):
        if not isinstance(txt, str):
            raise TypeError("txt type should be string!")

        temp_sections = []
        soup = BeautifulSoup(txt, "html5lib")
        
        # 提取移动端meta信息
        mobile_meta = cls.extract_mobile_meta(soup)
        if mobile_meta:
            temp_sections.append({
                "content": mobile_meta, 
                "tag_name": "mobile_meta", 
                "metadata": {"block_id": str(uuid.uuid1())}
            })
        
        # delete <style> tag
        for style_tag in soup.find_all(["style", "script"]):
            style_tag.decompose()
        # delete <script> tag in <div>
        for div_tag in soup.find_all("div"):
            for script_tag in div_tag.find_all("script"):
                script_tag.decompose()
        # delete inline style
        for tag in soup.find_all(True):
            if 'style' in tag.attrs:
                del tag.attrs['style']
        # delete HTML comment
        for comment in soup.find_all(string=lambda text: isinstance(text, Comment)):
            comment.extract()

        # 提取移动端结构化数据
        structured_data = cls.extract_structured_data(soup)
        if structured_data:
            temp_sections.append({
                "content": structured_data, 
                "tag_name": "structured_data", 
                "metadata": {"block_id": str(uuid.uuid1())}
            })

        cls.read_text_recursively(soup.body, temp_sections, chunk_token_num=chunk_token_num)
        block_txt_list, table_list = cls.merge_block_text(temp_sections)
        sections = cls.chunk_block(block_txt_list, chunk_token_num=chunk_token_num)
        for table in table_list:
            sections.append(table.get("content", ""))
        return sections

    @classmethod
    def extract_mobile_meta(cls, soup):
        """提取移动端相关的meta信息"""
        meta_info = []
        
        # 提取viewport信息
        viewport = soup.find("meta", attrs={"name": "viewport"})
        if viewport and viewport.get("content"):
            meta_info.append(f"移动端视口设置: {viewport.get('content')}")
        
        # 提取移动端应用相关meta
        mobile_metas = [
            ("mobile-web-app-capable", "移动端Web应用"),
            ("apple-mobile-web-app-capable", "iOS Safari全屏模式"),
            ("apple-mobile-web-app-status-bar-style", "iOS状态栏样式"),
            ("apple-mobile-web-app-title", "iOS应用标题"),
            ("theme-color", "主题颜色"),
            ("msapplication-TileColor", "Windows磁贴颜色"),
            ("format-detection", "格式检测设置")
        ]
        
        for name, desc in mobile_metas:
            meta = soup.find("meta", attrs={"name": name})
            if meta and meta.get("content"):
                meta_info.append(f"{desc}: {meta.get('content')}")
        
        # 提取触摸图标信息
        touch_icons = soup.find_all("link", rel=lambda x: x and ("apple-touch-icon" in x or "icon" in x))
        if touch_icons:
            meta_info.append(f"触摸图标数量: {len(touch_icons)}")
        
        return " | ".join(meta_info) if meta_info else ""

    @classmethod
    def extract_img_info(cls, img_element):
        """提取图片信息"""
        img_info = []
        alt_text = img_element.get("alt", "").strip()
        title_text = img_element.get("title", "").strip()
        src = img_element.get("src", "").strip()
        
        content_parts = []
        if alt_text:
            content_parts.append(f"图片描述: {alt_text}")
        if title_text:
            content_parts.append(f"图片标题: {title_text}")
        if src:
            # 提取文件名作为额外信息
            filename = src.split("/")[-1].split("?")[0] if "/" in src else src
            if filename:
                content_parts.append(f"图片文件: {filename}")
        
        if content_parts:
            content = " | ".join(content_parts)
            img_info.append({
                "content": content, 
                "tag_name": "image", 
                "metadata": {"media_type": "image", "src": src}
            })
        return img_info

    @classmethod
    def extract_video_info(cls, video_element):
        """提取视频信息"""
        video_info = []
        content_parts = []
        
        # 提取视频属性
        if video_element.get("title"):
            content_parts.append(f"视频标题: {video_element.get('title')}")
        
        # 查找视频描述文本
        video_text = video_element.get_text(strip=True)
        if video_text:
            content_parts.append(f"视频描述: {video_text}")
        
        # 提取source标签信息
        sources = video_element.find_all("source")
        if sources:
            content_parts.append(f"视频源数量: {len(sources)}")
        
        if content_parts:
            content = " | ".join(content_parts)
            video_info.append({
                "content": content, 
                "tag_name": "video", 
                "metadata": {"media_type": "video"}
            })
        return video_info

    @classmethod
    def extract_audio_info(cls, audio_element):
        """提取音频信息"""
        audio_info = []
        content_parts = []
        
        # 提取音频属性
        if audio_element.get("title"):
            content_parts.append(f"音频标题: {audio_element.get('title')}")
        
        # 查找音频描述文本
        audio_text = audio_element.get_text(strip=True)
        if audio_text:
            content_parts.append(f"音频描述: {audio_text}")
        
        # 提取source标签信息
        sources = audio_element.find_all("source")
        if sources:
            content_parts.append(f"音频源数量: {len(sources)}")
        
        if content_parts:
            content = " | ".join(content_parts)
            audio_info.append({
                "content": content, 
                "tag_name": "audio", 
                "metadata": {"media_type": "audio"}
            })
        return audio_info

    @classmethod
    def extract_picture_info(cls, picture_element):
        """提取响应式图片信息"""
        picture_info = []
        content_parts = []
        
        # 提取img标签信息（picture元素中的默认图片）
        img = picture_element.find("img")
        if img:
            alt_text = img.get("alt", "").strip()
            if alt_text:
                content_parts.append(f"响应式图片描述: {alt_text}")
        
        # 统计source标签数量（不同媒体查询的图片源）
        sources = picture_element.find_all("source")
        if sources:
            content_parts.append(f"响应式图片源数量: {len(sources)}")
        
        if content_parts:
            content = " | ".join(content_parts)
            picture_info.append({
                "content": content, 
                "tag_name": "picture", 
                "metadata": {"media_type": "responsive_image"}
            })
        return picture_info

    @classmethod
    def detect_mobile_features(cls, element):
        """检测移动端特征"""
        features = []
        
        # 检查移动端相关的CSS类名
        mobile_classes = [
            "mobile", "mobile-", "m-", "xs-", "sm-",
            "touch", "swipe", "scroll", "responsive",
            "hamburger", "menu-toggle", "nav-toggle",
            "modal", "drawer", "sidebar",
            "accordion", "collapsible", "expandable",
            "carousel", "slider", "gallery"
        ]
        
        class_attr = element.get("class", [])
        if isinstance(class_attr, str):
            class_attr = class_attr.split()
        
        for cls_name in class_attr:
            cls_lower = cls_name.lower()
            for mobile_cls in mobile_classes:
                if mobile_cls in cls_lower:
                    features.append(f"移动端CSS类: {cls_name}")
                    break
        
        # 检查移动端相关的data属性
        mobile_data_attrs = [
            "data-toggle", "data-slide", "data-swipe",
            "data-touch", "data-mobile", "data-responsive",
            "data-collapse", "data-accordion"
        ]
        
        for attr in mobile_data_attrs:
            if element.get(attr):
                features.append(f"移动端数据属性: {attr}={element.get(attr)}")
        
        # 检查触摸相关属性
        touch_attrs = ["ontouchstart", "ontouchmove", "ontouchend"]
        for attr in touch_attrs:
            if element.get(attr):
                features.append(f"触摸事件: {attr}")
        
        # 检查role属性中的移动端相关值
        role = element.get("role", "")
        mobile_roles = ["menubar", "tablist", "dialog", "navigation"]
        if role in mobile_roles:
            features.append(f"移动端角色: {role}")
        
        return " | ".join(features) if features else ""

    @classmethod
    def extract_structured_data(cls, soup):
        """提取结构化数据（JSON-LD, 微数据等）"""
        structured_info = []
        
        # 提取JSON-LD结构化数据
        json_ld_scripts = soup.find_all("script", type="application/ld+json")
        if json_ld_scripts:
            structured_info.append(f"JSON-LD结构化数据: {len(json_ld_scripts)}个")
        
        # 提取微数据（microdata）
        itemscope_elements = soup.find_all(attrs={"itemscope": True})
        if itemscope_elements:
            item_types = []
            for element in itemscope_elements:
                itemtype = element.get("itemtype", "")
                if itemtype and itemtype not in item_types:
                    item_types.append(itemtype.split("/")[-1])  # 提取类型名称
            if item_types:
                structured_info.append(f"微数据类型: {', '.join(item_types)}")
        
        # 提取Open Graph标签
        og_tags = soup.find_all("meta", property=lambda x: x and x.startswith("og:"))
        if og_tags:
            structured_info.append(f"Open Graph标签: {len(og_tags)}个")
        
        # 提取Twitter Card标签
        twitter_tags = soup.find_all("meta", name=lambda x: x and x.startswith("twitter:"))
        if twitter_tags:
            structured_info.append(f"Twitter Card标签: {len(twitter_tags)}个")
        
        return " | ".join(structured_info) if structured_info else ""

    @classmethod
    def split_table(cls, html_table, chunk_token_num=512):
        soup = BeautifulSoup(html_table, "html.parser")
        rows = soup.find_all("tr")
        tables = []
        current_table = []
        current_count = 0
        table_str_list = []
        for row in rows:
            tks_str = rag_tokenizer.tokenize(str(row))
            token_count = len(tks_str.split(" ")) if tks_str else 0
            if current_count + token_count > chunk_token_num:
                tables.append(current_table)
                current_table = []
                current_count = 0
            current_table.append(row)
            current_count += token_count
        if current_table:
            tables.append(current_table)

        for table_rows in tables:
            new_table = soup.new_tag("table")
            for row in table_rows:
                new_table.append(row)
            table_str_list.append(str(new_table))

        return table_str_list

    @classmethod
    def read_text_recursively(cls, element, parser_result, chunk_token_num=512, parent_name=None, block_id=None):
        if isinstance(element, NavigableString):
            content = element.strip()

            def is_valid_html(content):
                try:
                    soup = BeautifulSoup(content, "html.parser")
                    return bool(soup.find())
                except Exception:
                    return False

            return_info = []
            if content:
                if is_valid_html(content):
                    soup = BeautifulSoup(content, "html.parser")
                    child_info = cls.read_text_recursively(soup, parser_result, chunk_token_num, element.name, block_id)
                    parser_result.extend(child_info)
                else:
                    info = {"content": element.strip(), "tag_name": "inner_text", "metadata": {"block_id": block_id}}
                    if parent_name:
                        info["tag_name"] = parent_name
                    return_info.append(info)
            return return_info
        elif isinstance(element, Tag):

            if str.lower(element.name) == "table":
                table_info_list = []
                table_id = str(uuid.uuid1())
                table_list = [html.unescape(str(element))]
                for t in table_list:
                    table_info_list.append({"content": t, "tag_name": "table",
                                            "metadata": {"table_id": table_id, "index": table_list.index(t)}})
                return table_info_list
            # 处理图片元素
            elif str.lower(element.name) == "img":
                return cls.extract_img_info(element)
            # 处理视频元素
            elif str.lower(element.name) == "video":
                return cls.extract_video_info(element)
            # 处理音频元素
            elif str.lower(element.name) == "audio":
                return cls.extract_audio_info(element)
            # 处理响应式图片元素
            elif str.lower(element.name) == "picture":
                return cls.extract_picture_info(element)
            else:
                block_id = None
                if str.lower(element.name) in BLOCK_TAGS:
                    block_id = str(uuid.uuid1())
                
                # 检查移动端特有属性和类名
                mobile_features = cls.detect_mobile_features(element)
                if mobile_features:
                    # 为具有移动端特征的元素添加特殊标记
                    mobile_info = {
                        "content": f"移动端特征元素: {mobile_features}",
                        "tag_name": "mobile_feature",
                        "metadata": {"block_id": block_id, "mobile_features": mobile_features}
                    }
                    parser_result.append(mobile_info)
                
                for child in element.children:
                    child_info = cls.read_text_recursively(child, parser_result, chunk_token_num, element.name,
                                                           block_id)
                    parser_result.extend(child_info)
        return []

    @classmethod
    def merge_block_text(cls, parser_result):
        block_content = []
        current_content = ""
        table_info_list = []
        lask_block_id = None
        for item in parser_result:
            content = item.get("content")
            tag_name = item.get("tag_name")
            title_flag = tag_name in TITLE_TAGS
            block_id = item.get("metadata", {}).get("block_id")
            if block_id:
                if title_flag:
                    content = f"{TITLE_TAGS[tag_name]} {content}"
                if lask_block_id != block_id:
                    if lask_block_id is not None:
                        block_content.append(current_content)
                    current_content = content
                    lask_block_id = block_id
                else:
                    current_content += (" " if current_content else "") + content
            else:
                if tag_name == "table":
                    table_info_list.append(item)
                elif tag_name in ["image", "video", "audio", "picture", "mobile_meta", "mobile_feature", "structured_data"]:
                    # 移动端媒体、特征和结构化数据作为独立内容块处理
                    if current_content:
                        block_content.append(current_content)
                        current_content = ""
                    block_content.append(content)
                else:
                    current_content += (" " if current_content else "") + content
        if current_content:
            block_content.append(current_content)
        return block_content, table_info_list

    @classmethod
    def chunk_block(cls, block_txt_list, chunk_token_num=512):
        chunks = []
        current_block = ""
        current_token_count = 0

        for block in block_txt_list:
            tks_str = rag_tokenizer.tokenize(block)
            block_token_count = len(tks_str.split(" ")) if tks_str else 0
            if block_token_count > chunk_token_num:
                if current_block:
                    chunks.append(current_block)
                start = 0
                tokens = tks_str.split(" ")
                while start < len(tokens):
                    end = start + chunk_token_num
                    split_tokens = tokens[start:end]
                    chunks.append(" ".join(split_tokens))
                    start = end
                current_block = ""
                current_token_count = 0
            else:
                if current_token_count + block_token_count <= chunk_token_num:
                    current_block += ("\n" if current_block else "") + block
                    current_token_count += block_token_count
                else:
                    chunks.append(current_block)
                    current_block = block
                    current_token_count = block_token_count

        if current_block:
            chunks.append(current_block)

        return chunks