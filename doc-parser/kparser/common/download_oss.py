import os
from kparser.rag.storage.storage_factory import STORAGE_IMPL
from kparser.common.file_utils import read_file_from_original_url, upload2cdn
from kparser.common.config import get_project_base_directory
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


def get_convert_dir():
    root_path = get_project_base_directory()
    convert_path = root_path + "/convert/"
    return convert_path


# 下载并保存文件到本地指定目录
def from_oss_to_local(original_url, object_name, suffix=None):
    try:
        # 从 MinIO 获取文件数据（二进制流）
        binary = read_file_from_original_url(original_url)

        if binary is not None:
            # 生成本地文件路径
            if suffix is not None:
                convert_path = get_convert_dir() + suffix
            else:
                convert_path = get_convert_dir()

            if not os.path.exists(convert_path):
                os.makedirs(convert_path)

            local_file_path = "".join([convert_path, "/", object_name])

            # 将二进制流数据保存为本地文件
            with open(local_file_path, "wb") as file:
                file.write(binary)
            logger.info(f"File '{object_name}' downloaded successfully to '{local_file_path}'.")
            return local_file_path
        else:
            raise Exception("File is empty or not found in OSS")
    except Exception as e:
        logger.exception(f"Error while downloading file: {e}")


# 下载并保存文件到本地指定目录
def download_file_to_local(default_bucket, object_name, suffix=None):
    try:
        # 从 MinIO 获取文件数据（二进制流）
        if "/" in default_bucket:
            default_bucket = default_bucket.split("/")[0]
        binary = STORAGE_IMPL.get(default_bucket, object_name)

        if binary is not None:
            # 生成本地文件路径
            if suffix is not None:
                convert_path = get_convert_dir() + suffix
            else:
                convert_path = get_convert_dir()
            logger.info("saving path={}".format(convert_path))
            if not os.path.exists(convert_path):
                os.makedirs(convert_path)

            local_file_path = "".join([convert_path, "/", object_name])

            logger.info("local_file_path={}".format(local_file_path))

            # 将二进制流数据保存为本地文件
            with open(local_file_path, "wb") as file:
                file.write(binary)
            logger.info(f"File '{object_name}' downloaded successfully to '{local_file_path}'.")
            return local_file_path
        else:
            raise Exception("File is empty or not found in MinIO")
    except Exception as e:
        logger.exception(f"Error while downloading file: {e}")