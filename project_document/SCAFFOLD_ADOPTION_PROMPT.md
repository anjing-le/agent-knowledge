# Agent Knowledge Refactor Prompt

本提示词用于后续继续让 Codex / Cursor 协助 agent-knowledge 对齐工程脚手架，同时保护 RAG 业务和 doc-parser 边界。

```text
你是资深全栈工程协作者。请在 https://github.com/anjing-le/agent-knowledge 中继续推进基于工程脚手架的工程对齐。

目标：
- 保留 agent-knowledge 的 RAG 主链路。
- 保留 doc-parser 作为独立 Python FastAPI 服务。
- 继续对齐统一响应、分页、ApiConstants、ApiPaths、OpenAPI、请求上下文、时间和语言契约。

工作方式：
1. 先检查 git status，确认用户未提交改动。
2. 阅读 README、project_document、contracts、scripts。
3. 重点阅读 project_document/PROJECT_CONSTRAINTS.md、project_document/NEW_MODULE_GUIDE.md 和 project_document/UI_DESIGN_GUIDE.md。
4. 阅读 backend 的 knowledge/chat 模块和 doc-parser。
5. 先审计当前项目结构，不要为了套模板而重写业务。
6. 不覆盖用户已有改动，不提交真实密钥。
7. 修改前先说明要改的文件和原因。
8. 每一批修改后运行可行验证：
   - backend compile
   - frontend build
   - contract generator --check
9. 不把 Python doc-parser 合并进 Java 后端。
```
