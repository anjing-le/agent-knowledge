import subprocess
import os

from kparser.common.config import SERVICE
from kparser.common.download_oss import from_oss_to_local, get_convert_dir
from kparser.common.upload_oss import upload_file
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def ppt2pptx(original_url, object_name):
    try:
        # 从oss下载ppt文件到本地临时转换文件夹
        logger.info(f"[starting] load oss file")
        ppt_path = from_oss_to_local(original_url, object_name)
        logger.info(f"[done] load oss file")

        logger.info(f"[starting] convert ppt to pptx")
        convert_dir = get_convert_dir()
        # 构建命令行命令
        cmd = ["soffice", "--headless", "--convert-to", "pptx", "--outdir", convert_dir, ppt_path]
        # 执行命令
        subprocess.run(cmd)
        logger.info(f"[done] converted '{ppt_path}' to pptx format")

        # 上传本地转换文件到oss
        logger.info(f"[starting] save pptx version of file {object_name} to oss")
        pptx_path = ppt_path + "x"
        object_name_x = object_name + "x"
        if SERVICE["environment"] == "ATOM":
            pptx_original_url = upload_file(pptx_path, object_name_x)
        else:
            pptx_original_url = upload_file(pptx_path, object_name_x)
        logger.info(f"[done] object_name={object_name} with original_url={original_url} convert to pptx_original_url={pptx_original_url}")

        # 删除临时文件
        logger.info(f"[starting] delete tmp file {ppt_path}")
        os.remove(ppt_path)
        logger.info(f"[starting] delete tmp file {pptx_path}")
        os.remove(pptx_path)
        logger.info(f"[done] delete tmp files")
        return pptx_original_url
    except subprocess.CalledProcessError as e:
        logger.error(f"转换失败: {e}")
        raise Exception(f"转换失败: {e}")