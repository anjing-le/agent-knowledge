# Contributing

感谢你改进 `agent-knowledge`。

这个仓库是 Anjing Agent 系列中的 RAG 智能知识库示例。贡献时请优先保持 RAG 链路清晰、三服务边界清楚、工程契约可验证。

## 开始之前

先阅读并遵守：

- `project_document/PROJECT_CONSTRAINTS.md`
- `project_document/NEW_MODULE_GUIDE.md`
- `project_document/UI_DESIGN_GUIDE.md`
- `project_document/DEMO_EVIDENCE.md`

## 贡献原则

- 优先修复 RAG 主链路和工程契约问题。
- doc-parser 必须保持独立 Python FastAPI 服务，不并入 Java 后端。
- 前后端生成契约要同步更新 Cursor Rules / Prompts、项目文档和自检脚本。
- 新增模块先补契约和可验证入口，再写页面和实现。
- 明显 UI 改动要遵守轻玻璃、虚线边界、少文字和 hover 承载次级信息的基线。
- 不提交真实密钥、个人机器路径、本地日志、上传文件或构建产物。

## 提交前检查

完整门禁：

```bash
./scripts/quality-gate.sh
```

快速自检：

```bash
./scripts/check-template.sh
./scripts/check-contracts.sh
node scripts/check-scaffold-governance.js
```

```bash
cd backend
mvn -q -DskipTests compile
```

```bash
cd frontend
pnpm build
pnpm -s clean:dev
```

## 文档同步

如果你修改了模板边界、复制流程、AI 资产或发布要求，请同步检查：

- `project_document/PROJECT_CONSTRAINTS.md`
- `project_document/NEW_MODULE_GUIDE.md`
- `project_document/UI_DESIGN_GUIDE.md`
- `project_document/DEMO_EVIDENCE.md`
- `project_document/ROADMAP.md`
- `project_document/STATUS.md`
- `project_document/RELEASE_CHECKLIST.md`
- `project_document/COPY_GUIDE.md`
- `project_document/TEMPLATE_BOUNDARIES.md`
- `project_document/AI_ASSETS.md`

## 上游说明

前端工程基于 Art Design Pro 定制，保留 `frontend/LICENSE` 中的上游 MIT License。修改前端基础能力时，请继续保留上游许可和必要归属说明。
