from kparser.rag.storage.minio_conn import RAGMinio
from kparser.rag.storage.s3_conn import RAGS3
from kparser.rag.storage.tos_conn import RAGTOS
from kparser.common.config import SERVICE


class StorageFactory:
    storage_mapping = {
        "MINIO": RAGMinio,
        "S3": RAGS3,
        "TOS": RAGTOS,
    }

    @classmethod
    def create(cls, storage):
        return cls.storage_mapping[storage]()


STORAGE_IMPL_TYPE = SERVICE['use_storage']
STORAGE_IMPL = StorageFactory.create(STORAGE_IMPL_TYPE)
