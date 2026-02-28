# Agent Knowledge - 智能知识库 RAG 系统

基于 RAG（检索增强生成）技术，让 AI 读懂你的文档并精准回答问题。

**技术栈**：Spring Boot 3 + Vue 3 + Python FastAPI + MySQL + OpenAI API

---

## 环境要求

| 依赖 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 后端运行 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 20+ | 前端运行 |
| pnpm | 8+ | 前端包管理 |
| MySQL | 8.0+ | 数据存储 |
| Python | 3.10+ | 文档解析服务 |

## 第一步：下载代码

```bash
git clone git@github.com:anjing-le/agent-knowledge.git
cd agent-knowledge
```

> 如果没有配置 SSH Key，也可以用 HTTPS：
> ```
> git clone https://github.com/anjing-le/agent-knowledge.git
> ```

## 第二步：API Key 准备

本项目需要两个 API Key（任何 OpenAI 兼容接口均可）：

| Key | 用途 | 推荐 |
|-----|------|------|
| Embedding API Key | 文本向量化 | text-embedding-3-small |
| LLM API Key | AI 对话回答 | gpt-4o-mini |

> 可以使用 OpenAI 官方、OpenRouter、或任何兼容 OpenAI 接口的服务

## 第三步：创建数据库

```bash
mysql -u root -p
```

```sql
CREATE DATABASE agent_knowledge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

## 第四步：配置后端

```bash
cd backend/src/main/resources

# 复制配置模板
cp application-local.yml.example application-local.yml
```

编辑 `application-local.yml`，填入你的真实值：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent_knowledge?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 你的MySQL密码          # ← 改这里

app:
  embedding:
    api-url: https://api.openai.com/v1/embeddings
    api-key: sk-你的API密钥           # ← 改这里（Embedding 用）

  llm:
    api-url: https://api.openai.com/v1/chat/completions
    api-key: sk-你的API密钥           # ← 改这里（LLM 对话用）
```

## 第五步：启动后端

```bash
cd backend

mvn clean compile -DskipTests
mvn spring-boot:run
```

**启动成功标志**：

```
Started Application in X.XXX seconds
[启动] 向量索引重建完成
```

> 首次启动 JPA 会自动建表，无需手动导入 SQL

**验证后端**：浏览器打开 http://localhost:10001/api/test/health ，返回正常即可

## 第六步：启动前端

```bash
cd frontend

pnpm install
pnpm dev
```

**启动成功标志**：

```
VITE vX.X.X ready in XXX ms
➜ Local: http://localhost:5173/
```

**验证前端**：浏览器打开 http://localhost:5173 ，能看到登录页面

## 第七步：启动文档解析服务

```bash
cd doc-parser

# 创建虚拟环境
python -m venv venv
source venv/bin/activate    # Windows: venv\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 启动服务
python -m uvicorn kparser.app:app --host 0.0.0.0 --port 9001
```

**启动成功标志**：

```
Uvicorn running on http://0.0.0.0:9001
```

**验证服务**：浏览器打开 http://localhost:9001/health ，返回 `{"status": "ok"}`

## 验证完整流程

三个服务都启动后，按以下步骤验证 RAG 是否工作：

1. 打开 http://localhost:5173 ，登录系统
2. 进入「知识库管理」→ 创建知识库
3. 进入知识库 → 上传一个 PDF/Word 文档
4. 等待文档状态变为「已完成」（解析→分块→向量化）
5. 进入「智能问答」→ 选择该知识库 → 提问文档相关问题
6. 确认回答中包含「来源」标注和引用内容

## 常见问题

### 后端启动报数据库连接失败
检查 `application-local.yml` 中的 MySQL 地址、用户名、密码是否正确，数据库 `agent_knowledge` 是否已创建。

### 文档上传后状态一直是"解析中"
确认 doc-parser 服务已启动在 9001 端口。后端日志会显示调用 doc-parser 的请求。

### 问答没有引用来源 / 返回通用回答
- 确认知识库已关联到对话（左侧知识库标签未被 x 掉）
- 确认文档状态是「已完成」，不是处理中或失败
- 检查后端日志中 `向量检索完成: candidateCount=` 是否大于 0

### Embedding 调用报 SSL 错误
项目已内置 SSL 证书信任配置，如仍有问题请检查网络代理设置。

### pip install 报错
doc-parser 依赖较多，建议使用虚拟环境。部分依赖可能需要系统级库（如 `libgl`），按报错提示安装即可。

## 项目结构

```
agent-knowledge/
├── backend/                     # Spring Boot 后端
│   └── src/main/
│       ├── java/com/anjing/
│       │   ├── knowledge/       # 知识库模块（文档处理、向量检索、Embedding）
│       │   └── chat/            # 问答模块（会话管理、LLM 调用）
│       └── resources/
│           ├── application.yml
│           └── application-local.yml.example
├── frontend/                    # Vue 3 前端
│   └── src/views/
│       ├── knowledge/           # 知识库管理页面
│       └── chat/                # 智能问答页面
└── doc-parser/                  # Python 文档解析服务
    └── kparser/                 # 解析核心代码
```
