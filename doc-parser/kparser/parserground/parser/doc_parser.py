import subprocess
import os

from kparser.common.config import SERVICE
from kparser.common.download_oss import from_oss_to_local, get_convert_dir
from kparser.common.upload_oss import upload_file
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def doc2docx(original_url, object_name):
    try:
        # 从oss下载doc文件到本地临时转换文件夹
        logger.info(f"[starting] load oss file")
        doc_path = from_oss_to_local(original_url, object_name)
        logger.info(f"[done] load oss file")

        logger.info(f"[starting] convert doc to docx")
        convert_dir = get_convert_dir()
        # 构建命令行命令
        cmd = ["soffice", "--headless", "--convert-to", "docx", "--outdir", convert_dir, doc_path]
        # 执行命令
        subprocess.run(cmd)
        logger.info(f"[done] converted '{doc_path}' to docx format")

        # 上传本地转换文件到oss
        logger.info(f"[starting] save docx version of file {object_name} to oss")
        docx_path = doc_path + "x"
        object_name_x = object_name + "x"
        if SERVICE["environment"] == "ATOM":
            docx_original_url = upload_file(docx_path, object_name_x)
        else:
            docx_original_url = upload_file(docx_path, object_name_x)
        logger.info(f"[done] object_name={object_name} with original_url={original_url} convert to docx_original_url={docx_original_url}")

        # 删除临时文件
        logger.info(f"[starting] delete tmp file {doc_path}")
        os.remove(doc_path)
        logger.info(f"[starting] delete tmp file {docx_path}")
        os.remove(docx_path)
        logger.info(f"[done] delete tmp files")
        return docx_original_url
    except subprocess.CalledProcessError as e:
        logger.error(f"转换失败: {e}")
        raise Exception(f"转换失败: {e}")