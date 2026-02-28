import chardet
import csv
import io
from io import BytesIO

from kparser.common.types_utils import get_random_uuid
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def parse_csv_row_format(input_stream, method, encoding='utf-8'):
    """
    解析 CSV 数据流并返回字符串列表。

    每个字符串对应 CSV 文件中除表头外的一行数据。对于每个单元格：
    - 如果 method 等于 "ROW_HEADER"，则在单元格内容前加上对应的表头和冒号；
    - 否则，仅输出单元格内容；
    - 同时移除单元格中的换行符，并在每个单元格后添加换行符。

    :param input_stream: 一个二进制流，如 BytesIO 对象。
    :param method: 一个字符串，用于决定输出格式。
    :return: 一个字符串列表，每个字符串对应 CSV 文件中的一行数据（不包含表头）。
    """
    result = []

    # 将二进制流包装为文本流，等效于 Java 中的 InputStreamReader
    text_stream = io.TextIOWrapper(input_stream, encoding=encoding)
    try:
        csv_reader = csv.reader(text_stream)
        all_lines = list(csv_reader)
        if not all_lines:
            return result

        headers = all_lines[0]
        # 遍历除表头以外的每一行数据
        for row in all_lines[1:]:
            line = {}
            for j in range(len(headers)):
                # 若当前行中缺少该列数据，则使用空字符串
                cell_value = row[j] if j < len(row) else ""
                cell_value = cell_value.replace("\n", "")
                if method == "ROW_HEADER":
                    line[headers[j]] = cell_value
                else:
                    line[headers[j]] = cell_value
            result.append(line)
    finally:
        # 关闭文本流，同时会关闭底层的二进制流
        text_stream.close()

    return result


def parse_csv_first_col_group_by_column(input_stream, encoding='utf-8'):
    """
    将 CSV 中的第一列视为“行标签”，并将其余列按列进行分组，生成对应的多行字符串。

    假设 CSV 数据如下：
        1,qq,ee,rr
        2,aa,ss,tt
        3,zz,xx,gg
        4,xx,dd,bb
        5,cc,vv,hh

    则返回：
        [
            "1:qq\n2:aa\n3:zz\n4:xx\n5:cc\n",
            "1:ee\n2:ss\n3:xx\n4:dd\n5:vv\n",
            "1:rr\n2:tt\n3:gg\n4:bb\n5:hh\n"
        ]

    :param input_stream: 二进制流（BytesIO 等），其中包含 CSV 数据
    :return: 列表，每个元素为一个多行字符串，形如 "行标签:单元格内容\n..."
    """
    result = []

    # 将二进制流包装成文本流
    text_stream = io.TextIOWrapper(input_stream, encoding=encoding)
    try:
        # 读取全部行
        csv_reader = csv.reader(text_stream)
        all_lines = list(csv_reader)
        if not all_lines:
            return result

        # 找到 CSV 中最大列数，防止有些行列数不足
        max_cols = max(len(row) for row in all_lines)

        # 如果只有 1 列，那就没有“内容列”可分组
        if max_cols <= 1:
            return result

        # 为每个“内容列”准备一个空字符串，后续往里拼接
        # 比如有 4 列，第一列是行标签，则剩余 3 列是内容列，需要 3 个字符串
        column_outputs = [{} for _ in range(max_cols - 1)]

        # 逐行处理
        for row in all_lines:
            # 跳过空行
            if not row:
                continue

            # 第一列作为行标签
            row_header = row[0].replace("\n", "").strip()

            # 如果该行列数不足 max_cols，补齐空字符串
            row += [""] * (max_cols - len(row))

            # 从第 2 列（索引 1）开始到最后一列，逐列写入
            for j in range(1, max_cols):
                cell_value = row[j].replace("\n", "").strip()
                column_outputs[j - 1][row_header] = cell_value

        result = column_outputs
    finally:
        text_stream.close()

    return result


def parse_csv_group_columns(
        input_stream,
        skip_header=False,
        skip_first_column=False,
        encoding="utf-8"
):
    """
    将 CSV 按“列”进行分组，每一列输出一个多行字符串，并返回所有列组成的列表。

    参数说明：
    :param input_stream: 二进制流（BytesIO 等），包含 CSV 数据
    :param skip_header: 布尔值，是否跳过第一行（常见场景：第一行是表头）
    :param skip_first_column: 布尔值，是否跳过第一列（如第一列是不需要输出的索引列）
    :param encoding: 读取文本的编码，默认 utf-8
    :return: 列表，每个元素是一个多行字符串。行与行之间用换行符分隔。

    例子：
    如果 CSV 内容为：
        1,qq,ee,rr
        2,aa,ss,tt
        3,zz,xx,gg
        4,xx,dd,bb
        5,cc,vv,hh

    默认情况下（skip_header=False, skip_first_column=False），
    返回结果类似：
    [
      "1\n2\n3\n4\n5\n",
      "qq\naa\nzz\nxx\ncc\n",
      "ee\nss\nxx\ndd\nvv\n",
      "rr\ntt\ngg\nbb\nhh\n"
    ]

    如果 skip_header=True，则会跳过第一行 "1,qq,ee,rr"，只输出后面的行。
    如果 skip_first_column=True，则会跳过每行的第一列，只输出后续列。
    """
    result = []

    # 将二进制流包装为文本流
    text_stream = io.TextIOWrapper(input_stream, encoding=encoding)
    try:
        # 读取全部行并转成列表
        csv_reader = csv.reader(text_stream)
        all_rows = list(csv_reader)

        # 如果没有数据，直接返回空列表
        if not all_rows:
            return result

        # 如果要跳过第一行（通常是表头），则去掉 all_rows 的第 0 行
        if skip_header:
            all_rows = all_rows[1:]
            if not all_rows:
                return result

        # 找到 CSV 中最大列数，防止有些行列数不足
        max_cols = max(len(row) for row in all_rows)

        # 如果要跳过第一列，后续列才是真正需要输出的列
        start_col_index = 1 if skip_first_column else 0

        # 如果跳过第一列之后，没有列可输出，则直接返回空列表
        if start_col_index >= max_cols:
            return result

        # 根据要输出的列数，初始化对应数量的空字符串
        # （例如总共有 4 列，skip_first_column=False，则要输出 4 个字符串）
        # （如果 skip_first_column=True，则要输出 3 个字符串）
        column_count = max_cols - start_col_index
        column_outputs = ["" for _ in range(column_count)]

        # 逐行处理
        for row in all_rows:
            # 若当前行列数不足 max_cols，补齐空字符串，避免索引越界
            row += [""] * (max_cols - len(row))

            # 从 start_col_index 开始遍历到最后一列
            for col_index in range(start_col_index, max_cols):
                cell_value = row[col_index].replace("\n", "").strip()
                # 累加到对应列的输出中，每行末尾加一个换行符
                column_outputs[col_index - start_col_index] += cell_value + "\n"

        return column_outputs
    finally:
        text_stream.close()


def detect_encoding_from_bytes(byte_data):
    """检测字节数据编码（增强版）"""
    try:
        result = chardet.detect(byte_data)
        detected = result["encoding"] or "utf-8"
        # 中文编码兼容性处理
        if "GB" in detected.upper():
            return "gb18030"
        return detected
    except Exception as e:
        logger.warning(f"编码检测失败: {str(e)}，使用备选编码")
        return "utf-8"


def read_csv_as_text(file_stream):
    # 获取字节流内容
    byte_data = file_stream.read(4096)  # 只读取前 4KB，足够推测编码
    encoding = detect_encoding_from_bytes(byte_data)
    logger.info(f"检测到编码: {encoding}")

    # 重置文件流指针
    file_stream.seek(0)

    # 读取 CSV 内容
    lines = file_stream.read().decode(encoding, errors="replace").splitlines()  # 以检测到的编码读取并分割

    # 过滤空行
    lines = [line.strip() for line in lines if line.strip()]

    # 解析 CSV
    result = []
    for line in lines:
        result.append(line)  # 按分隔符拆分

    return result


def analyse_table(data):
    cks_by_page = []
    for sec in data:
        element_text = {"id": get_random_uuid(),
                        "content": sec,
                        "content_type": "DICT" if type(sec) == dict else "TEXT",
                        "page_idx": [1],
                        "extra_data": {}
                        }
        # 保存文本
        cks_by_page.append(element_text)

    return cks_by_page


def get_rule_config(parser_rules):
    parser_rule = "ROW_HEADER"
    for item in parser_rules:
        if item["rule_method"] == "3":
            parser_rule = item["feature_value"][0]
    return parser_rule


class RAGCsvParser:
    def __call__(self, binary, parser_config):
        data = BytesIO(binary)
        method = get_rule_config(parser_config["rules"])
        logger.info(f"csv parser rule={method}")
        return self.load(data, method)

    @classmethod
    def load(cls, data, method):
        # 编码检测逻辑
        data.seek(0)
        sample = data.read(4096)
        encoding = detect_encoding_from_bytes(sample)
        logger.info(f"检测到文件编码: {encoding}")
        data.seek(0)  # 重置流位置

        # 调用函数解析 CSV 数据，method 参数为 "3" 或其他值
        if "ROW" in method:
            parsed_result = parse_csv_row_format(data, method, encoding=encoding)
        else:
            if method == "COLUMN_HEADER":
                parsed_result = parse_csv_first_col_group_by_column(data, encoding=encoding)
            else:
                parsed_result = parse_csv_group_columns(data, encoding=encoding)

        res = analyse_table(parsed_result)
        return res