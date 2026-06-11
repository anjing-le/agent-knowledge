package com.anjing.model.constants;

/**
 * API path constants aligned with contracts/service-boundaries.json.
 */
public final class ApiConstants {

    public static final String API_PREFIX = PlatformContractConstants.API_PREFIX;

    public static final class Auth {
        public static final String BASE = API_PREFIX + "/auth";

        public static final String LOGIN = "/login";
        public static final String LOGOUT = "/logout";
        public static final String ME = "/me";
        public static final String REFRESH = "/refresh";

        public static final String LOGIN_FULL = BASE + LOGIN;
        public static final String LOGOUT_FULL = BASE + LOGOUT;
        public static final String ME_FULL = BASE + ME;
        public static final String REFRESH_FULL = BASE + REFRESH;

        private Auth() {
        }
    }

    public static final class Test {
        public static final String BASE = API_PREFIX + "/test";

        public static final String HEALTH = "/health";
        public static final String FEATURES = "/features";
        public static final String PING = "/ping";
        public static final String EXCEPTION_BIZ = "/exception/biz";
        public static final String EXCEPTION_SYSTEM = "/exception/system";
        public static final String ITEMS = "/items";
        public static final String ITEM_DETAIL = "/items/{id}";

        public static final String HEALTH_FULL = BASE + HEALTH;
        public static final String FEATURES_FULL = BASE + FEATURES;
        public static final String PING_FULL = BASE + PING;
        public static final String EXCEPTION_BIZ_FULL = BASE + EXCEPTION_BIZ;
        public static final String EXCEPTION_SYSTEM_FULL = BASE + EXCEPTION_SYSTEM;
        public static final String ITEMS_FULL = BASE + ITEMS;
        public static final String ITEM_DETAIL_FULL = BASE + ITEM_DETAIL;

        private Test() {
        }
    }

    public static final class Common {
        public static final String BASE = API_PREFIX + "/common";

        public static final String UPLOAD_FILE = "/upload";
        public static final String UPLOAD_IMAGE = "/upload/image";
        public static final String UPLOAD_WANG_EDITOR = "/upload/wangeditor";
        public static final String DOWNLOAD_FILE = "/download/{fileId}";
        public static final String DELETE_FILE = "/files/{fileId}";

        public static final String UPLOAD_FILE_FULL = BASE + UPLOAD_FILE;
        public static final String UPLOAD_IMAGE_FULL = BASE + UPLOAD_IMAGE;
        public static final String UPLOAD_WANG_EDITOR_FULL = BASE + UPLOAD_WANG_EDITOR;
        public static final String DOWNLOAD_FILE_FULL = BASE + DOWNLOAD_FILE;
        public static final String DELETE_FILE_FULL = BASE + DELETE_FILE;

        private Common() {
        }
    }

    public static final class Knowledge {
        public static final String BASE = API_PREFIX + "/knowledge";

        public static final String BASES = "/bases";
        public static final String BASES_ALL = "/bases/all";
        public static final String BASE_DETAIL = "/bases/{kbId}";
        public static final String BASE_DOCUMENTS = "/bases/{kbId}/documents";

        public static final String DOCUMENT_DETAIL = "/documents/{docId}";
        public static final String DOCUMENT_BATCH_DELETE = "/documents/batch-delete";
        public static final String DOCUMENT_ENABLED = "/documents/{docId}/enabled";
        public static final String DOCUMENT_REPROCESS = "/documents/{docId}/reprocess";
        public static final String DOCUMENT_CHUNKS = "/documents/{docId}/chunks";
        public static final String DOCUMENT_CHUNK_COUNT = "/documents/{docId}/chunks/count";

        public static final String CHUNK_DETAIL = "/chunks/{chunkId}";
        public static final String CHUNK_ENABLED = "/chunks/{chunkId}/enabled";

        public static final String BASES_FULL = BASE + BASES;
        public static final String BASES_ALL_FULL = BASE + BASES_ALL;
        public static final String BASE_DETAIL_FULL = BASE + BASE_DETAIL;
        public static final String BASE_DOCUMENTS_FULL = BASE + BASE_DOCUMENTS;
        public static final String DOCUMENT_DETAIL_FULL = BASE + DOCUMENT_DETAIL;
        public static final String DOCUMENT_BATCH_DELETE_FULL = BASE + DOCUMENT_BATCH_DELETE;
        public static final String DOCUMENT_ENABLED_FULL = BASE + DOCUMENT_ENABLED;
        public static final String DOCUMENT_REPROCESS_FULL = BASE + DOCUMENT_REPROCESS;
        public static final String DOCUMENT_CHUNKS_FULL = BASE + DOCUMENT_CHUNKS;
        public static final String DOCUMENT_CHUNK_COUNT_FULL = BASE + DOCUMENT_CHUNK_COUNT;
        public static final String CHUNK_DETAIL_FULL = BASE + CHUNK_DETAIL;
        public static final String CHUNK_ENABLED_FULL = BASE + CHUNK_ENABLED;

        private Knowledge() {
        }
    }

    public static final class Retrieval {
        public static final String BASE = API_PREFIX + "/retrieval";

        public static final String SEARCH = "/search";
        public static final String SIMPLE = "/simple";

        public static final String SEARCH_FULL = BASE + SEARCH;
        public static final String SIMPLE_FULL = BASE + SIMPLE;

        private Retrieval() {
        }
    }

    public static final class Chat {
        public static final String BASE = API_PREFIX + "/chat";

        public static final String CONVERSATIONS = "/conversations";
        public static final String CONVERSATION_DETAIL = "/conversations/{conversationId}";
        public static final String CONVERSATION_TITLE = "/conversations/{conversationId}/title";
        public static final String MESSAGES = "/conversations/{conversationId}/messages";

        public static final String CONVERSATIONS_FULL = BASE + CONVERSATIONS;
        public static final String CONVERSATION_DETAIL_FULL = BASE + CONVERSATION_DETAIL;
        public static final String CONVERSATION_TITLE_FULL = BASE + CONVERSATION_TITLE;
        public static final String MESSAGES_FULL = BASE + MESSAGES;

        private Chat() {
        }
    }

    public static final class Version {
        public static final String V1 = API_PREFIX + "/v1";
        public static final String V2 = API_PREFIX + "/v2";
        public static final String LATEST = API_PREFIX;

        private Version() {
        }
    }

    public static final class Permission {
        public static final String PUBLIC_PREFIX = API_PREFIX + "/public";
        public static final String COMMON_PREFIX = Common.BASE;
        public static final String KNOWLEDGE_PREFIX = Knowledge.BASE;
        public static final String RETRIEVAL_PREFIX = Retrieval.BASE;
        public static final String CHAT_PREFIX = Chat.BASE;

        private Permission() {
        }
    }

    private ApiConstants() {
    }
}
