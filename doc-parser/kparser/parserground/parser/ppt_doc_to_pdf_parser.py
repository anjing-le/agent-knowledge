import subprocess
import os

from kparser.common.config import SERVICE
from kparser.common.download_oss import from_oss_to_local, get_convert_dir
from kparser.common.upload_oss import upload_file
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def ppt_doc_to_pdf(original_url):
    try:
        # 从oss下载ppt/doc文件到本地临时转换文件夹
        logger.info(f"[starting] load oss file")

        object_name = original_url.split("?")[0].split("/")[-1]
        logger.info("convert to pdf: loading file={}".format(object_name))

        suffix = "pdfs"
        input_path = from_oss_to_local(original_url, object_name, suffix)
        logger.info(f"[done] load oss file")

        logger.info(f"[starting] convert file={object_name} to pdf type")
        convert_dir = get_convert_dir() + suffix
        logger.info(f"convert_dir={convert_dir}, input_path={input_path}")
        # 构建命令行命令
        cmd = ["soffice", "--headless", "--convert-to", "pdf", "--outdir", convert_dir, input_path]
        # 执行命令
        subprocess.run(cmd, check=True, stderr=subprocess.STDOUT)
        logger.info(f"[done] converted '{input_path}' to pdf format")

        # 上传本地转换文件到oss
        logger.info(f"[starting] save pdf version of file {object_name} to oss")
        object_name_x = ".".join(object_name.split(".")[0:-1]) + ".pdf"
        logger.debug("object_name_x={}".format(object_name_x))
        output_path = convert_dir + "/" + object_name_x
        logger.info("output_path={}, object_name_x={}".format(output_path, object_name_x))
        if SERVICE["environment"] == "ATOM":
            pdf_original_url = upload_file(output_path, object_name_x)
        else:
            pdf_original_url = upload_file(output_path, object_name_x)
        logger.info(f"[done] object_name={object_name} with original_url={original_url} convert to pdf_original_url={pdf_original_url}")

        # 删除临时文件
        logger.info(f"[starting] delete tmp file {input_path}")
        os.remove(input_path)
        logger.info(f"[starting] delete tmp file {output_path}")
        os.remove(output_path)
        logger.info(f"[done] delete tmp files")
        return pdf_original_url
    except subprocess.CalledProcessError as e:
        logger.error(f"转换pdf失败: {e}")
        raise Exception(f"转换pdf失败: {e}")