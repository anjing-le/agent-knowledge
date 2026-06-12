package com.anjing.model.constants;

/**
 * Generated from contracts/service-boundaries.json. Do not edit manually.
 * Run: node scripts/generate-service-boundaries-backend.js
 */
public final class ServiceBoundaryConstants {

    public static final int SCHEMA_VERSION = 1;
    public static final String APPLICATION_ID = "agent-knowledge";
    public static final String API_PREFIX = "/api";
    public static final String[] BOUNDARY_IDS = { "auth", "test", "common", "knowledge", "retrieval", "chat" };

    private ServiceBoundaryConstants() {
    }

    public static final class Auth {
        public static final String ID = "auth";
        public static final String NAME = "Authentication";
        public static final String KIND = "runtime";
        public static final String OWNER = "infra-auth";
        public static final String CURRENT_HOST = "agent-knowledge";
        public static final String BASE_PATH = "/api/auth";
        public static final String API_CONSTANTS_CLASS = "Auth";
        public static final String API_PATHS_KEY = "auth";
        public static final boolean OPENAPI = true;
        public static final String COPY_ACTION = "replace with real auth center or database-backed auth";
        public static final String[] ROUTES = { "login", "logout", "currentUser", "refreshToken" };

        private Auth() {
        }
    }

    public static final class Test {
        public static final String ID = "test";
        public static final String NAME = "Agent Knowledge Test";
        public static final String KIND = "sample";
        public static final String OWNER = "agent-knowledge";
        public static final String CURRENT_HOST = "agent-knowledge";
        public static final String BASE_PATH = "/api/test";
        public static final String API_CONSTANTS_CLASS = "Test";
        public static final String API_PATHS_KEY = "test";
        public static final boolean OPENAPI = true;
        public static final String COPY_ACTION = "keep as local smoke endpoints or remove after real health endpoints are ready";
        public static final String[] ROUTES = { "health", "features", "ping", "businessException", "systemException", "items", "itemDetail" };

        private Test() {
        }
    }

    public static final class Common {
        public static final String ID = "common";
        public static final String NAME = "Common Platform";
        public static final String KIND = "reserved-runtime";
        public static final String OWNER = "infra-common";
        public static final String CURRENT_HOST = "agent-knowledge";
        public static final String BASE_PATH = "/api/common";
        public static final String API_CONSTANTS_CLASS = "Common";
        public static final String API_PATHS_KEY = "common";
        public static final boolean OPENAPI = false;
        public static final String COPY_ACTION = "keep only endpoints implemented by agent-knowledge";
        public static final String[] ROUTES = { "upload", "uploadImage", "uploadWangEditor", "download", "deleteFile" };

        private Common() {
        }
    }

    public static final class Knowledge {
        public static final String ID = "knowledge";
        public static final String NAME = "Knowledge Base";
        public static final String KIND = "runtime";
        public static final String OWNER = "agent-knowledge";
        public static final String CURRENT_HOST = "agent-knowledge";
        public static final String BASE_PATH = "/api/knowledge";
        public static final String API_CONSTANTS_CLASS = "Knowledge";
        public static final String API_PATHS_KEY = "knowledge";
        public static final boolean OPENAPI = true;
        public static final String COPY_ACTION = "core RAG knowledge management boundary";
        public static final String[] ROUTES = { "listKnowledgeBases", "listAllKnowledgeBases", "knowledgeBaseDetail", "knowledgeBaseDocuments", "documentDetail", "documentBatchDelete", "documentEnabled", "documentReprocess", "documentTasks", "documentChunks", "documentChunkCount", "chunkDetail", "chunkEnabled" };

        private Knowledge() {
        }
    }

    public static final class Retrieval {
        public static final String ID = "retrieval";
        public static final String NAME = "Retrieval";
        public static final String KIND = "runtime";
        public static final String OWNER = "agent-knowledge";
        public static final String CURRENT_HOST = "agent-knowledge";
        public static final String BASE_PATH = "/api/retrieval";
        public static final String API_CONSTANTS_CLASS = "Retrieval";
        public static final String API_PATHS_KEY = "retrieval";
        public static final boolean OPENAPI = true;
        public static final String COPY_ACTION = "core RAG search and context assembly boundary";
        public static final String[] ROUTES = { "search", "simpleSearch" };

        private Retrieval() {
        }
    }

    public static final class Chat {
        public static final String ID = "chat";
        public static final String NAME = "RAG Chat";
        public static final String KIND = "runtime";
        public static final String OWNER = "agent-knowledge";
        public static final String CURRENT_HOST = "agent-knowledge";
        public static final String BASE_PATH = "/api/chat";
        public static final String API_CONSTANTS_CLASS = "Chat";
        public static final String API_PATHS_KEY = "chat";
        public static final boolean OPENAPI = true;
        public static final String COPY_ACTION = "core RAG answer generation and citation boundary";
        public static final String[] ROUTES = { "conversations", "conversationDetail", "conversationTitle", "messages" };

        private Chat() {
        }
    }
}
