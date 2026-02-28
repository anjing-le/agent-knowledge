import os
from io import BytesIO
from kparser.rag.storage.storage_factory import STORAGE_IMPL
from kparser.common.file_utils import upload2cdn, upload2tos
from kparser.common.types_utils import get_random_uuid
from kparser.common.config import SERVICE, MINIO
from kparser.common.log_utils import get_logger

logger = get_logger(__name__)


# 上传文件的目标桶名称和文件路径
temp_object_key_prefix = "shark"  # 目标桶名称

def upload_oss_api(file):
    # 打开文件并读取为二进制格式
    try:
        file_data = file.file.read()
        object_name = file.filename
        logger.info("upload file ={} to oss".format(object_name))

        # 将文件数据上传到 MinIO 存储
        if SERVICE["environment"] == "ATOM":
            STORAGE_IMPL.put(default_bucket, object_name, file_data)
            url = MINIO['prefix'] + default_bucket + "/" + object_name
        else:
            if SERVICE["use_storage"] == "TOS":
                output_buffer = BytesIO(file_data)
                print("output_buffer={}".format(output_buffer))
                # 上传到tos
                url = upload2tos(object_key=temp_object_key_prefix + "/" + object_name, file_bytes=output_buffer)

        logger.info(f"File '{object_name}' uploaded successfully with location '{url}'.")
        return url
    except Exception as e:
        logger.error(f"Error while uploading file: {e}")


def upload_file(file_path, object_name):
    # 打开文件并读取为二进制格式
    try:
        with open(file_path, "rb") as file:
            file_data = file.read()
            # 将文件数据上传到 MinIO 存储
            if SERVICE["environment"] == "ATOM":
                STORAGE_IMPL.put(default_bucket, object_name, file_data)
                url = MINIO['prefix'] + default_bucket + "/" + object_name
            else:
                if SERVICE["use_storage"] == "TOS":
                    output_buffer = BytesIO(file_data)
                    url = upload2tos(object_key=temp_object_key_prefix + "/" + object_name, file_bytes=output_buffer)
                else:
                    output_buffer = BytesIO(file_data)
                    url = upload2cdn(object_name, output_buffer.getvalue())
            logger.info(f"File '{file_path}' uploaded successfully as '{object_name}'.")
            return url
    except Exception as e:
        logger.exception(f"Error while uploading file: {e}")


def find_pdf_and_pptx_files(directory):
    pdf_files = []
    pptx_files = []

    # Traverse the directory
    for root, dirs, files in os.walk(directory):
        for file in files:
            if file.endswith(".pdf"):
                pdf_files.append(os.path.join(root, file))
            elif file.endswith(".pptx"):
                pptx_files.append(os.path.join(root, file))

    return pdf_files, pptx_files


def process_folder(directory, output_filepath):
    pdf_files, pptx_files = find_pdf_and_pptx_files(directory)

    filenames = []
    for pdf in pdf_files:
        filepath = pdf
        file_name = pdf.split("/")[-1]
        upload_file(filepath, file_name)
        filenames.append(file_name)

    for pptx in pptx_files:
        filepath = pptx
        file_name = pptx.split("/")[-1]
        upload_file(filepath, file_name)
        filenames.append(file_name)

    print("Files info saving ...")
    with open(output_filepath, 'w') as f:
        for item in filenames:
            request_id = get_random_uuid()
            f.write(item + "####@@@@" + request_id + '\n')
    print("Files info saving done")


if __name__ == "__main__":
    file_path = "../../data/ch5.pdf"  # 本地文件路径
    object_name = "ch5.pdf"  # 上传后的文件名称
    upload_file(file_path, object_name)

    # Specify the root directory to start the search
    # directory = "/Users/nicolas/Downloads/second_batch"
    # output_filepath = "../../data/files_to_be_parsed_v2.txt"
    # process_folder(directory, output_filepath)





