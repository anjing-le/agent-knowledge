# 设计与进度

本目录存放 agent-knowledge 的设计文档、工程规范、契约说明和进度记录。

## 文档规范

- 设计文档：架构设计、技术选型、方案对比、模块边界。
- 进度记录：里程碑、当前状态、下一步计划。
- 契约文档：API、响应、分页、请求上下文、doc-parser 边界。
- 质量文档：本地验证命令、发布检查和 CI 门禁。

## 文档索引

- [ROADMAP.md](./ROADMAP.md)：V1/V2/V3 路线图。
- [STATUS.md](./STATUS.md)：当前重构进度和验证证据。
- [SCAFFOLD_TO_RAG_AGENT_GUIDE.md](./SCAFFOLD_TO_RAG_AGENT_GUIDE.md)：如何从脚手架生长出 RAG agent。
- [PROJECT_CONSTRAINTS.md](./PROJECT_CONSTRAINTS.md)：RAG 工程约束和防破窗规则。
- [NEW_MODULE_GUIDE.md](./NEW_MODULE_GUIDE.md)：新增模块时如何沿用脚手架约定。
- [SCAFFOLD_ADOPTION_PROMPT.md](./SCAFFOLD_ADOPTION_PROMPT.md)：后续 agent 继续采纳脚手架的协作提示。
- [UI_DESIGN_GUIDE.md](./UI_DESIGN_GUIDE.md)：前端体验和视觉约束。
- [DEMO_EVIDENCE.md](./DEMO_EVIDENCE.md)：演示证据与验收截图记录。
- [SERVICE_BOUNDARY_GUIDE.md](./SERVICE_BOUNDARY_GUIDE.md)：Java 后端服务边界和 API prefix。
- [DOC_PARSER_SERVICE_GUIDE.md](./DOC_PARSER_SERVICE_GUIDE.md)：Python doc-parser 独立服务边界。
- [VECTOR_STORE_ADAPTER_GUIDE.md](./VECTOR_STORE_ADAPTER_GUIDE.md)：向量库 provider 配置和 adapter 边界。
- [API_CONTRACT_GUIDE.md](./API_CONTRACT_GUIDE.md)：统一响应、分页、OpenAPI 和前端 API 约定。
- [LOCAL_STARTUP_GUIDE.md](./LOCAL_STARTUP_GUIDE.md)：本地三服务启动和验证。
- [REMOTE_CALL_GUIDE.md](./REMOTE_CALL_GUIDE.md)：服务间调用、doc-parser 调用和上下文透传。
- [ERROR_CODE_GUIDE.md](./ERROR_CODE_GUIDE.md)：错误码分段。
- [OPENAPI_CONTRACT_GUIDE.md](./OPENAPI_CONTRACT_GUIDE.md)：OpenAPI 生成链路。
- [RELEASE_CHECKLIST.md](./RELEASE_CHECKLIST.md)：发布前检查。
- [ci/quality-gate.yml](./ci/quality-gate.yml)：CI 质量门禁示例。

机器可读契约：

- [../contracts/platform-contract.json](../contracts/platform-contract.json)
- [../contracts/service-boundaries.json](../contracts/service-boundaries.json)
- [../contracts/doc-parser-contract.json](../contracts/doc-parser-contract.json)
