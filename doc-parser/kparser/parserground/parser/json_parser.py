import json
from typing import Any

from kparser.common.types_utils import get_random_uuid
from kparser.rag.nlp import find_codec
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def get_rule_config(parser_rules):
    delimiter = "=="
    for item in parser_rules:
        if item["rule_method"] == "2":
            delimiter = item["feature_value"][0]
    return delimiter


class RAGJsonParser:
    def __call__(self, binary, parser_config):
        encoding = find_codec(binary)
        txt = binary.decode(encoding, errors="ignore")
        json_data = json.loads(txt)
        delimiter = get_rule_config(parser_config["rules"])
        logger.info(f"delimiter={delimiter}")

        cks_by_page = []
        for sec in json_data:
            if isinstance(sec, dict):
                key, content = self._find_split_key(sec)
                if key:
                    parts = self._split_content(content, delimiter)
                else:
                    parts = [json.dumps(sec, ensure_ascii=False)]
                for part in parts:
                    element_text = self._create_element(sec, key, part)
                    cks_by_page.append(element_text)
            else:
                element_text = self._handle_non_dict_sec(sec)
                cks_by_page.append(element_text)
        return cks_by_page

    def _find_split_key(self, sec: dict) -> tuple[str | None, str | None]:
        if "content" in sec:
            return "content", sec["content"]
        return None, None

    def _split_content(self, content: str, delimiter):
        parts = [content.strip()]
        # 处理多个分隔符（按｜进行拆分）
        delimiters = delimiter.split("|")
        for symbol in delimiters:
            new_parts = []
            for part in parts:
                split_parts = part.split(symbol)
                stripped_parts = [p.strip() for p in split_parts if p.strip()]
                new_parts.extend(stripped_parts)
            parts = new_parts
        return parts

    def _create_element(self, sec: dict, key: str | None, part: str) -> dict:
        new_sec = sec.copy()
        if key:
            new_sec[key] = part
            content_str = json.dumps(new_sec, ensure_ascii=False)
        else:
            content_str = part

        return {
            "id": get_random_uuid(),
            "content": content_str,
            "content_type": "DICT" if type(sec) == dict else "TEXT",
            "page_idx": [1],
            "extra_data": {}
        }

    def _handle_non_dict_sec(self, sec: Any) -> dict:
        return {
            "id": get_random_uuid(),
            "content": json.dumps(sec, ensure_ascii=False),
            "content_type": "DICT" if type(sec) == dict else "TEXT",
            "page_idx": [1],
            "extra_data": {}
        }