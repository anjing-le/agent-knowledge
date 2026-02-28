# Agent Knowledge - 智能知识库系统

基于 **RAG (Retrieval-Augmented Generation)** 技术的智能知识库，让 AI 能读懂你的文档并精准回答问题。

## 项目架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                         │
│          知识库管理 · 文档上传 · RAG 配置 · 智能问答                 │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP API
┌──────────────────────────▼──────────────────────────────────────┐
│                   Backend (Spring Boot 3)                        │
│  ┌──────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────┐ │
│  │ 知识库管理 │  │   文档处理     │  │  向量检索   │  │  智能问答  │ │
│  │ CRUD+配置 │  │ 解析→分块→向量 │  │ Embedding  │  │ LLM 生成  │ │
│  └──────────┘  └──────┬───────┘  │ + 余弦相似度 │  │ 防幻觉+历史│ │
│                       │          └────────────┘  └───────────┘ │
│                       ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              数据存储层                                       ││
│  │  MySQL (元数据) · 内存向量库 (向量检索，启动自动重建)             ││
│  └─────────────────────────────────────────────────────────────┘│
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP 调用
┌──────────────────────────▼──────────────────────────────────────┐
│                 Doc-Parser (Python FastAPI)                      │
│              PDF · Word · Excel · TXT 文档解析（必需）             │
└─────────────────────────────────────────────────────────────────┘
```

## RAG 核心流程

```
文档上传 → 文档解析(doc-parser) → 文本分块(Chunking) → 向量化(Embedding) → 向量存储
                                                                              │
用户提问 → 问题向量化 → 向量检索(余弦相似度) → 阈值过滤 → LLM生成回答 ←────────┘
                                                            ↑
                                                     对话历史(10轮)
```

### 关键技术点

| 环节 | 技术方案 | 说明 |
|------|---------|------|
| 文档解析 | doc-parser (Python) | 支持 PDF/Word/Excel/TXT，必需服务 |
| 文本分块 | 固定窗口 + 重叠 | 可配置大小和重叠，句子边界优化 |
| 向量化 | text-embedding-3-small | OpenAI 兼容接口，1536 维向量，模型可配置 |
| 向量存储 | 内存向量库 | ConcurrentHashMap，重启自动从 DB 重建 |
| 向量检索 | 余弦相似度 | Top-K 召回 + 相似度阈值过滤（0.3） |
| 回答生成 | GPT-4o-mini | 防幻觉 Prompt + 相关性判断 + 来源标注 |
| 对话历史 | MySQL 持久化 | 最近 10 轮上下文，重启不丢失 |

## 功能清单

### 知识库管理
- 创建/编辑/删除知识库
- 知识库启用/禁用
- **RAG 配置可视化**：Embedding 模型、分块大小、重叠大小

### 文档管理
- 文档上传（支持 PDF/Word/Excel/TXT）
- 文档处理进度追踪（解析→分块→向量化）
- 文档重新解析（配置变更后可重新处理）
- 精细状态显示（PARSE_FAILED / CHUNK_FAILED / EMBEDDING_FAILED）

### 文档切片
- 查看文档切片列表
- 切片启用/禁用
- 切片向量化状态追踪

### 智能问答
- 基于知识库的 RAG 问答
- **防幻觉**：严格基于知识库内容回答，不编造
- **相关性判断**：检索内容与问题不相关时明确告知
- **来源标注**：回答标注 ✅/⚠️/❌ 和来源文档名
- **参考来源展示**：显示引用原文、文档名、相似度
- **对话历史**：支持多轮对话（10轮上下文）
- **动态知识库选择**：可实时切换/取消关联知识库
- 会话管理（创建/删除/切换/历史消息）

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Element Plus + Tailwind CSS |
| 后端 | Spring Boot 3.4 + JPA + MySQL 8 |
| 文档解析 | Python FastAPI (doc-parser) |
| 向量化 | OpenAI Embedding API (text-embedding-3-small) |
| LLM | OpenAI Chat API (gpt-4o-mini) |
| 构建 | Maven (后端) + pnpm + Vite (前端) |

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+ & pnpm
- MySQL 8.0+
- Python 3.10+ (doc-parser **必需**)

### 1. 配置后端

```bash
cd backend

# 创建数据库
mysql -u root -p -e "CREATE DATABASE agent_knowledge DEFAULT CHARACTER SET utf8mb4;"

# 复制配置模板并填写 API Key
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml，填入 MySQL 密码、Embedding API Key、LLM API Key

# 启动（JPA 会自动建表）
mvn clean compile -DskipTests
mvn spring-boot:run
```

### 2. 启动前端

```bash
cd frontend
pnpm install
pnpm dev
```

### 3. 启动文档解析服务（必需）

```bash
cd doc-parser
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001
```

### 4. 访问系统

- 前端页面：http://localhost:5173
- 后端 API：http://localhost:10001

## 核心后端服务说明

| 服务类 | 职责 |
|--------|------|
| `DocumentProcessingService` | RAG 管道编排：解析→分块→向量化（无降级） |
| `EmbeddingService` | 调用 Embedding API 生成向量（支持指定模型） |
| `VectorStoreService` | 内存向量存储与余弦相似度检索 |
| `VectorIndexRebuilder` | 启动时自动从 DB 重建内存向量索引 |
| `RetrievalService` | 知识检索 + 相似度阈值过滤 |
| `LLMService` | 调用 LLM 生成回答（防幻觉 Prompt + 对话历史） |
| `ChatService` | 会话管理、动态知识库选择、对话历史 |
| `DocParserClient` | 调用 Python doc-parser 解析文档 |

## License

本项目仅供学习和教学使用。
