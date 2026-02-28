import re
import uuid

from kparser.rag.nlp import find_codec
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def get_rule_config(parser_rules):
    delimiter = "=="
    for item in parser_rules:
        if item["rule_method"] == "2":
            delimiter = item["feature_value"][0]
    return delimiter


class RAGTxtParser:
    def __call__(self, binary, parser_config):
        encoding = find_codec(binary)
        txt = binary.decode(encoding, errors="ignore")
        return self.parse_txt(txt)

    @staticmethod
    def parse_txt(txt):
        if not isinstance(txt, str):
            raise TypeError("txt type should be str!")

        lines = txt.splitlines()
        parsed_data = [
            {
                "id": str(uuid.uuid4()),
                "content": line,
                "content_type": "TEXT",
                "page_idx": [1],
                "extra_data": {}
            }
            for line in lines if line.strip()  # 只输出有内容的行
        ]

        return parsed_data