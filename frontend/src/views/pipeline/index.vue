<template>
  <div class="pipeline-workbench">
    <section class="workspace-header">
      <div>
        <p class="page-kicker">Scaffold To RAG</p>
        <h1 class="page-title">RAG Pipeline 教学视图</h1>
        <p class="page-subtitle">
          agent-knowledge 继承 infra-dev-scaffolding 的工程底座，只把文档解析、向量检索、
          上下文组装和引用回答作为业务模块长出来。
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="goKnowledge">
          <el-icon><FolderOpened /></el-icon>
          知识库
        </el-button>
        <el-button @click="goRetrieval">
          <el-icon><Search /></el-icon>
          检索调试
        </el-button>
        <el-button type="primary" @click="goChat">
          <el-icon><ChatLineRound /></el-icon>
          知识问答
        </el-button>
      </div>
    </section>

    <section class="foundation-section">
      <div class="section-heading">
        <div>
          <h2>脚手架地基</h2>
          <p>这些能力来自 infra-dev-scaffolding，agent 项目不重复造轮子。</p>
        </div>
        <el-tag type="success" effect="plain">Spring Boot + Vue/Vite + Python FastAPI</el-tag>
      </div>

      <div class="foundation-grid">
        <article v-for="item in scaffoldCapabilities" :key="item.name" class="foundation-item">
          <div class="capability-icon" :class="item.tone">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <h3>{{ item.name }}</h3>
            <p>{{ item.description }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="pipeline-section">
      <div class="section-heading">
        <div>
          <h2>RAG 全链路</h2>
          <p>业务能力按阶段服务沉淀，Controller 保持薄，页面按工作区组织。</p>
        </div>
        <el-tag effect="plain">RAG Demo Smoke</el-tag>
      </div>

      <div class="stage-track">
        <article v-for="(stage, index) in ragStages" :key="stage.name" class="stage-item">
          <div class="stage-index">{{ String(index + 1).padStart(2, '0') }}</div>
          <div class="stage-body">
            <div class="stage-title">
              <el-icon><component :is="stage.icon" /></el-icon>
              <h3>{{ stage.name }}</h3>
            </div>
            <p>{{ stage.description }}</p>
            <div class="stage-files">
              <el-tag v-for="file in stage.files" :key="file" size="small" effect="plain">
                {{ file }}
              </el-tag>
            </div>
          </div>
        </article>
      </div>
    </section>

    <div class="boundary-layout">
      <section class="boundary-section">
        <div class="section-heading compact">
          <div>
            <h2>Java 与 Python 边界</h2>
            <p>Spring Boot 管业务生命周期，doc-parser 只做解析服务。</p>
          </div>
        </div>

        <div class="service-map">
          <article class="service-column">
            <div class="service-title">
              <el-icon><Box /></el-icon>
              <h3>Java Backend</h3>
            </div>
            <ul>
              <li>知识库、文档、任务、Chunk、向量检索和 Chat 编排</li>
              <li>APIResponse、PageResult、ApiConstants、OpenAPI</li>
              <li>通过 RemoteHttpClient 调用 agent-doc-parser</li>
            </ul>
          </article>

          <div class="boundary-arrow">
            <el-icon><Position /></el-icon>
            <span>HTTP contract</span>
          </div>

          <article class="service-column parser">
            <div class="service-title">
              <el-icon><Document /></el-icon>
              <h3>Python FastAPI doc-parser</h3>
            </div>
            <ul>
              <li>文件解析、URL 解析、布局和 metadata 提取</li>
              <li>保持独立进程、独立依赖和独立健康检查</li>
              <li>不把 Python 解析依赖塞进 Java 后端</li>
            </ul>
          </article>
        </div>
      </section>

      <section class="evidence-section">
        <div class="section-heading compact">
          <div>
            <h2>教学验证</h2>
            <p>每条命令都在证明：底座稳定，RAG 链路可演示。</p>
          </div>
        </div>

        <div class="command-list">
          <button
            v-for="command in evidenceCommands"
            :key="command.command"
            class="command-item"
            type="button"
            @click="copyCommand(command.command)"
          >
            <span>{{ command.label }}</span>
            <code>{{ command.command }}</code>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Box,
  ChatLineRound,
  CircleCheck,
  Collection,
  DataAnalysis,
  Document,
  FolderOpened,
  Position,
  Search
} from '@element-plus/icons-vue'

const router = useRouter()

const scaffoldCapabilities = [
  {
    name: 'APIResponse / PageResult',
    description: '统一响应和分页模型继承脚手架，前后端只关心业务 data。',
    icon: markRaw(CircleCheck),
    tone: 'green'
  },
  {
    name: 'ApiConstants / ApiPaths',
    description: '后端路径和前端路径从 service-boundary 契约生成，避免散落 URL。',
    icon: markRaw(Collection),
    tone: 'blue'
  },
  {
    name: 'OpenAPI operation',
    description: '普通运行接口通过 operationId 和生成类型调用，接口变更可校验。',
    icon: markRaw(DataAnalysis),
    tone: 'amber'
  },
  {
    name: 'RemoteHttpClient',
    description: '模型服务和 doc-parser 都走脚手架远程调用边界，保留观测和 Header 透传。',
    icon: markRaw(Position),
    tone: 'teal'
  }
]

const ragStages = [
  {
    name: '知识库配置',
    description: '定义 chunk size、overlap、Embedding 模型和启用状态。',
    icon: markRaw(FolderOpened),
    files: ['KnowledgeBaseController', 'KnowledgeService']
  },
  {
    name: '文档 Ingestion',
    description: '上传、批量上传、重新处理和任务查询进入应用服务。',
    icon: markRaw(Document),
    files: ['DocumentIngestionService', 'DocumentProcessingTask']
  },
  {
    name: 'Python 解析',
    description: 'Java 通过 HTTP 请求 doc-parser，解析结果保留 layout 和 metadata。',
    icon: markRaw(Position),
    files: ['DocParserClient', 'doc-parser/kparser/app.py']
  },
  {
    name: '切片与落库',
    description: '解析结果转换为 Chunk，并回写文档 chunk/token 统计。',
    icon: markRaw(Collection),
    files: ['DocumentChunkingService', 'DocumentChunkPersistenceService']
  },
  {
    name: 'Embedding 与向量库',
    description: 'local-demo 支持教学，remote provider 继续由 RemoteHttpClient 承接。',
    icon: markRaw(DataAnalysis),
    files: ['DocumentEmbeddingService', 'VectorStoreService']
  },
  {
    name: '检索与上下文',
    description: 'query embedding 后召回 chunk，补全引用信息并组装 RAG prompt。',
    icon: markRaw(Search),
    files: ['RetrievalService', 'RagPromptBuilderService']
  },
  {
    name: '问答与引用',
    description: 'Chat 编排复用检索结果，回答引用可回跳到切片证据。',
    icon: markRaw(ChatLineRound),
    files: ['RagChatOrchestrationService', 'ChatMessagePersistenceService']
  }
]

const evidenceCommands = [
  {
    label: 'RAG 最小闭环',
    command: './scripts/smoke-rag-demo.sh'
  },
  {
    label: '后端轻启动探针',
    command: './scripts/probe-backend-dev.sh'
  },
  {
    label: '模板身份检查',
    command: './scripts/check-template.sh'
  },
  {
    label: '契约与脚手架检查',
    command: './scripts/check-contracts.sh'
  }
]

const goKnowledge = () => {
  router.push('/kb/knowledge')
}

const goRetrieval = () => {
  router.push('/kb/retrieval')
}

const goChat = () => {
  router.push('/kb/chat')
}

const copyCommand = async (command: string) => {
  try {
    await navigator.clipboard.writeText(command)
    ElMessage.success('命令已复制')
  } catch (error) {
    console.error('复制命令失败:', error)
    ElMessage.warning(command)
  }
}
</script>

<style lang="scss" scoped>
.pipeline-workbench {
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color-page);
}

.workspace-header,
.foundation-section,
.pipeline-section,
.boundary-section,
.evidence-section {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.workspace-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 22px 24px;
  margin-bottom: 18px;
}

.page-kicker {
  margin: 0 0 6px;
  color: #1f8a70;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0;
}

.page-subtitle {
  max-width: 820px;
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.foundation-section,
.pipeline-section,
.boundary-section,
.evidence-section {
  padding: 20px;
}

.foundation-section,
.pipeline-section {
  margin-bottom: 18px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  &.compact {
    margin-bottom: 14px;
  }

  h2 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 18px;
    font-weight: 700;
    letter-spacing: 0;
  }

  p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.5;
  }
}

.foundation-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.foundation-item {
  display: flex;
  gap: 12px;
  min-height: 128px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);

  h3 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 700;
  }

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.55;
  }
}

.capability-icon {
  display: flex;
  flex: 0 0 38px;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  font-size: 18px;

  &.green {
    color: #1f8a70;
    background: rgba(31, 138, 112, 0.1);
  }

  &.blue {
    color: #2f80ed;
    background: rgba(47, 128, 237, 0.1);
  }

  &.amber {
    color: #b7791f;
    background: rgba(183, 121, 31, 0.12);
  }

  &.teal {
    color: #0f766e;
    background: rgba(15, 118, 110, 0.1);
  }
}

.stage-track {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
}

.stage-item {
  min-height: 210px;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.stage-index {
  color: #1f8a70;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.stage-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  color: var(--el-text-color-primary);

  h3 {
    margin: 0;
    font-size: 15px;
    font-weight: 700;
  }
}

.stage-body {
  p {
    min-height: 64px;
    margin: 10px 0 12px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.55;
  }
}

.stage-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.boundary-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.6fr);
  gap: 18px;
}

.service-map {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

.service-column {
  min-height: 220px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);

  &.parser {
    border-color: rgba(47, 128, 237, 0.24);
  }

  ul {
    padding-left: 18px;
    margin: 12px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.8;
  }
}

.service-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
}

.boundary-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #1f8a70;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.command-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.command-item {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-height: 72px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-blank);
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: rgba(31, 138, 112, 0.36);
    background: rgba(31, 138, 112, 0.04);
  }

  span {
    font-size: 13px;
    font-weight: 700;
  }

  code {
    color: #1f8a70;
    font-size: 12px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }
}

@media (max-width: 1280px) {
  .foundation-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stage-track {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .boundary-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .workspace-header,
  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .service-map {
    grid-template-columns: 1fr;
  }

  .boundary-arrow {
    min-height: 54px;
  }
}

@media (max-width: 640px) {
  .pipeline-workbench {
    padding: 12px;
  }

  .foundation-grid,
  .stage-track {
    grid-template-columns: 1fr;
  }
}
</style>
