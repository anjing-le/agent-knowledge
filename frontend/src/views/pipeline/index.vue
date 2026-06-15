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

    <section class="demo-ready-section">
      <div class="section-heading">
        <div>
          <h2>Demo Ready</h2>
          <p>运行态演示数据由 dev/test seed endpoint 生成，页面只消费脚手架 API 边界。</p>
        </div>
        <el-tag :type="demoSeed ? 'success' : 'info'" effect="plain">
          {{ demoSeed ? 'Seeded' : 'Dev/Test' }}
        </el-tag>
      </div>

      <div class="demo-ready-body">
        <div class="demo-status-copy">
          <div class="demo-status-line">
            <el-icon><CircleCheck /></el-icon>
            <span>{{ demoStatusTitle }}</span>
          </div>
          <p>{{ demoStatusText }}</p>
        </div>

        <div class="demo-metric-row">
          <div class="demo-metric">
            <span>KB</span>
            <strong>{{ demoSeed?.kbName || '-' }}</strong>
          </div>
          <div class="demo-metric">
            <span>Document</span>
            <strong>{{ demoSeed?.docName || '-' }}</strong>
          </div>
          <div class="demo-metric">
            <span>Vectors</span>
            <strong>{{ demoSeed?.vectorCount ?? '-' }}</strong>
          </div>
          <div class="demo-metric">
            <span>Hits</span>
            <strong>{{ demoSeed?.sampleResultCount ?? '-' }}</strong>
          </div>
        </div>

        <div class="demo-action-row">
          <el-button type="primary" :loading="seedingDemo" @click="seedDemo">
            <el-icon><Refresh /></el-icon>
            生成演示数据
          </el-button>
          <el-button :disabled="!demoSeed" @click="goSeedKnowledge">
            <el-icon><FolderOpened /></el-icon>
            知识库
          </el-button>
          <el-button :disabled="!demoSeed" @click="goSeedRetrieval">
            <el-icon><Search /></el-icon>
            检索
          </el-button>
          <el-button :disabled="!demoSeed" type="success" plain @click="goSeedChat">
            <el-icon><ChatLineRound /></el-icon>
            问答
          </el-button>
        </div>

        <div class="demo-loop-panel">
          <div class="demo-loop-heading">
            <div>
              <span>Seed -> Evaluate -> Retrieval -> Chat -> Evidence</span>
              <p>{{ demoLoopSummary }}</p>
            </div>
            <el-tag :type="demoSeed ? 'success' : 'info'" effect="plain">
              {{ demoSeed ? 'Ready' : 'Waiting' }}
            </el-tag>
          </div>

          <div class="demo-loop-track">
            <article
              v-for="step in demoTeachingSteps"
              :key="step.key"
              class="demo-loop-step"
              :class="{ ready: step.ready }"
            >
              <div class="demo-loop-icon">
                <el-icon><component :is="step.icon" /></el-icon>
              </div>
              <div class="demo-loop-content">
                <div class="demo-loop-title">
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.ready ? 'Ready' : 'Pending' }}</span>
                </div>
                <p>{{ step.description }}</p>
                <button
                  v-if="step.actionLabel"
                  class="demo-loop-action"
                  type="button"
                  :disabled="step.disabled"
                  @click="step.action"
                >
                  {{ step.actionLabel }}
                </button>
              </div>
            </article>
          </div>
        </div>

        <div class="retrieval-quality-panel">
          <div class="quality-heading">
            <div>
              <span>Retrieval Evaluation</span>
              <p>{{ retrievalEvaluationSummary }}</p>
            </div>
            <el-tag :type="retrievalEvaluationStatus.type" effect="plain">
              {{ retrievalEvaluationStatus.label }}
            </el-tag>
          </div>

          <div class="quality-metric-row">
            <div class="quality-metric">
              <span>Recall@{{ retrievalEvaluation?.topK || 3 }}</span>
              <strong>{{ recallAtKDisplay }}</strong>
            </div>
            <div class="quality-metric">
              <span>Cases</span>
              <strong>{{ retrievalCaseDisplay }}</strong>
            </div>
            <div class="quality-metric">
              <span>Suite</span>
              <strong>{{ retrievalEvaluation?.suiteName || '-' }}</strong>
            </div>
            <div class="quality-metric">
              <span>KB</span>
              <strong>{{ retrievalEvaluation?.kbId || demoSeed?.kbId || '-' }}</strong>
            </div>
          </div>

          <div class="quality-action-row">
            <el-button type="primary" plain :loading="evaluatingRetrieval" @click="evaluateRetrieval()">
              <el-icon><DataAnalysis /></el-icon>
              运行检索评估
            </el-button>
            <el-button :disabled="!retrievalEvaluation" @click="copyCommand('./scripts/evaluate-rag-retrieval.sh')">
              <el-icon><CircleCheck /></el-icon>
              复制评估脚本
            </el-button>
          </div>

          <div v-if="retrievalEvaluation?.cases?.length" class="quality-case-list">
            <article
              v-for="item in retrievalEvaluation.cases"
              :key="item.query"
              class="quality-case"
              :class="{ passed: item.passed }"
            >
              <div class="quality-case-title">
                <strong>{{ item.query }}</strong>
                <el-tag size="small" :type="item.passed ? 'success' : 'danger'" effect="plain">
                  rank {{ item.expectedRank || 'miss' }}
                </el-tag>
              </div>
              <div class="quality-case-chunks">
                <span>expected {{ item.expectedChunkIds.join(', ') || '-' }}</span>
                <span>top {{ item.topChunkId || '-' }}</span>
              </div>
              <p>{{ item.topScoreExplanation || '-' }}</p>
            </article>
          </div>
        </div>
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

    <section class="adapter-section">
      <div class="section-heading">
        <div>
          <h2>Adapter Matrix</h2>
          <p>默认教学态保持本地可跑，生产 provider 通过配置切换，不改变 RAG 编排代码。</p>
        </div>
        <div class="section-actions">
          <el-button size="small" :loading="adapterStatusLoading" @click="loadAdapterStatus">
            <el-icon><Refresh /></el-icon>
            刷新状态
          </el-button>
          <el-tag :type="adapterStatus ? 'success' : 'info'" effect="plain">
            {{ adapterStatusTag }}
          </el-tag>
        </div>
      </div>

      <div class="adapter-grid">
        <article v-for="adapter in adapterMatrix" :key="adapter.name" class="adapter-item">
          <div class="adapter-header">
            <div class="adapter-title-wrap">
              <div class="adapter-icon" :class="adapter.tone">
                <el-icon><component :is="adapter.icon" /></el-icon>
              </div>
              <div>
                <h3>{{ adapter.name }}</h3>
                <p>{{ adapter.boundary }}</p>
              </div>
            </div>
            <el-tag size="small" :type="adapter.statusType" effect="plain">
              {{ adapter.status }}
            </el-tag>
          </div>

          <div class="adapter-path" :aria-label="adapter.name">
            <template v-for="(provider, providerIndex) in adapter.providerPath" :key="provider.name">
              <span class="provider-chip" :class="provider.kind">{{ provider.name }}</span>
              <span v-if="providerIndex < adapter.providerPath.length - 1" class="provider-arrow">
                ->
              </span>
            </template>
          </div>

          <div class="adapter-runtime" :class="runtimeStatus(adapter.axis)">
            <span>Current</span>
            <strong>{{ runtimeProvider(adapter.axis) }}</strong>
            <small>{{ runtimeImplementation(adapter.axis) }}</small>
          </div>

          <div class="adapter-files">
            <el-tag v-for="file in adapter.files" :key="file" size="small" effect="plain">
              {{ file }}
            </el-tag>
          </div>

          <button class="adapter-command" type="button" @click="copyCommand(adapter.command)">
            <span>{{ adapter.commandLabel }}</span>
            <code>{{ adapter.command }}</code>
          </button>
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
            v-for="command in displayEvidenceCommands"
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
import { computed, markRaw, onMounted, ref } from 'vue'
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
  Refresh,
  Search
} from '@element-plus/icons-vue'
import {
  RagDemoService,
  type RagDemoSeedResponse,
  type RagRetrievalEvaluationResponse
} from '@/api/demo'
import {
  RetrievalService,
  type RetrievalAdapterStatusItem,
  type RetrievalAdapterStatusResponse
} from '@/api/retrieval'

const router = useRouter()
const demoSeed = ref<RagDemoSeedResponse | null>(null)
const retrievalEvaluation = ref<RagRetrievalEvaluationResponse | null>(null)
const adapterStatus = ref<RetrievalAdapterStatusResponse | null>(null)
const seedingDemo = ref(false)
const evaluatingRetrieval = ref(false)
const adapterStatusLoading = ref(false)

const demoStatusTitle = computed(() => {
  return demoSeed.value ? '演示数据已就绪' : '等待生成演示数据'
})

const demoStatusText = computed(() => {
  if (!demoSeed.value) {
    return '启动 dev 后端后，可以直接生成本地 RAG 教学数据。'
  }
  return `${demoSeed.value.kbName} 已生成 ${demoSeed.value.chunkIds.length} 个 chunk，检索样例命中 ${demoSeed.value.sampleResultCount} 条。`
})

const demoLoopSummary = computed(() => {
  if (!demoSeed.value) {
    return '等待 seed endpoint 写入一套可检索、可问答、可引用的本地教学数据。'
  }
  if (retrievalEvaluation.value) {
    return `检索评估 ${retrievalEvaluation.value.passedCases}/${retrievalEvaluation.value.totalCases} 通过，recall@${retrievalEvaluation.value.topK} 为 ${recallAtKDisplay.value}。`
  }
  return `${demoSeed.value.docName} 已完成 ${demoSeed.value.vectorCount} 条向量，检索命中 ${demoSeed.value.sampleResultCount} 条，可直接进入自动问答。`
})

const retrievalEvaluationStatus = computed(() => {
  if (!retrievalEvaluation.value) {
    return { type: 'info' as const, label: 'Not Run' }
  }
  return retrievalEvaluation.value.passed
    ? { type: 'success' as const, label: 'Passed' }
    : { type: 'danger' as const, label: 'Needs Review' }
})

const recallAtKDisplay = computed(() => {
  if (!retrievalEvaluation.value) return '-'
  return `${Math.round(retrievalEvaluation.value.recallAtK * 100)}%`
})

const retrievalCaseDisplay = computed(() => {
  if (!retrievalEvaluation.value) return '-'
  return `${retrievalEvaluation.value.passedCases}/${retrievalEvaluation.value.totalCases}`
})

const retrievalEvaluationSummary = computed(() => {
  if (!retrievalEvaluation.value) {
    return '用固定 query 和 expected chunk 校验本地 demo 的 recall@K、rank 和 score explanation。'
  }
  return retrievalEvaluation.value.passed
    ? `${retrievalEvaluation.value.suiteName} 已通过，所有教学 query 都命中期望 chunk。`
    : `${retrievalEvaluation.value.suiteName} 未完全通过，需要检查召回、hybrid 或 rerank 配置。`
})

const adapterStatusTag = computed(() => {
  if (adapterStatusLoading.value) {
    return 'Loading'
  }
  return adapterStatus.value ? 'Runtime' : 'Design'
})

const adapterStatusMap = computed<Record<string, RetrievalAdapterStatusItem>>(() => {
  return (adapterStatus.value?.adapters || []).reduce<Record<string, RetrievalAdapterStatusItem>>(
    (result, item) => {
      result[item.axis] = item
      return result
    },
    {}
  )
})

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

const adapterMatrix = [
  {
    axis: 'vectorStore',
    name: 'Vector Store',
    boundary: '向量召回只依赖 VectorStoreService，默认 memory，生产切 pgvector。',
    status: 'Implemented',
    statusType: 'success' as const,
    tone: 'blue',
    icon: markRaw(DataAnalysis),
    providerPath: [
      { name: 'memory', kind: 'default' },
      { name: 'pgvector', kind: 'production' }
    ],
    files: ['VectorStoreService', 'VectorStoreProperties', 'PgVectorStoreService'],
    commandLabel: '生产向量库',
    command: 'VECTOR_STORE_PROVIDER=pgvector'
  },
  {
    axis: 'keywordSearch',
    name: 'Keyword Search',
    boundary: '关键词召回只依赖 KeywordSearchProvider，先 BM25，再接搜索引擎。',
    status: 'Implemented',
    statusType: 'success' as const,
    tone: 'green',
    icon: markRaw(Search),
    providerPath: [
      { name: 'local', kind: 'default' },
      { name: 'bm25', kind: 'bridge' },
      { name: 'elasticsearch', kind: 'production' }
    ],
    files: ['LocalKeywordSearchProvider', 'Bm25KeywordSearchProvider', 'ElasticsearchKeywordSearchProvider'],
    commandLabel: '轻量 ranking',
    command: 'KEYWORD_SEARCH_PROVIDER=bm25'
  },
  {
    axis: 'rerank',
    name: 'Rerank',
    boundary: '重排编排留在 RetrievalRerankService，远程模型由 RerankProviderClient 接入。',
    status: 'Implemented',
    statusType: 'success' as const,
    tone: 'amber',
    icon: markRaw(Collection),
    providerPath: [
      { name: 'local-demo', kind: 'default' },
      { name: 'remote', kind: 'production' }
    ],
    files: ['RerankProperties', 'RetrievalRerankService', 'RerankProviderClient'],
    commandLabel: '远程重排',
    command: 'RERANK_PROVIDER=remote'
  },
  {
    axis: 'docParser',
    name: 'Doc Parser',
    boundary: 'Java 只管理任务生命周期，Python FastAPI doc-parser 独立解析。',
    status: 'HTTP boundary',
    statusType: 'info' as const,
    tone: 'teal',
    icon: markRaw(Position),
    providerPath: [
      { name: 'sync', kind: 'default' },
      { name: 'async', kind: 'production' },
      { name: 'recovery', kind: 'bridge' }
    ],
    files: ['DocParserClient', 'DocumentAsyncParsingService', 'doc-parser/kparser/app.py'],
    commandLabel: '异步解析',
    command: 'DOC_PARSER_MODE=async'
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
    label: '证据包模板',
    command: './scripts/create-demo-evidence.sh --dry-run'
  },
  {
    label: '一键证据收集',
    command: './scripts/collect-demo-evidence.sh --dry-run'
  },
  {
    label: 'doc-parser 边界',
    command: './scripts/probe-doc-parser-boundary.sh --contract-only'
  },
  {
    label: '检索 Adapter 探针',
    command: './scripts/probe-retrieval-adapters.sh --dry-run'
  },
  {
    label: '生产 Adapter Profile',
    command: './scripts/probe-production-adapter-profile.sh --dry-run'
  },
  {
    label: '运行态 Adapter 状态',
    command: 'curl -fsS http://localhost:10001/api/retrieval/adapters/status'
  },
  {
    label: '解析生命周期',
    command: './scripts/check-doc-parser-lifecycle.sh'
  },
  {
    label: '异步解析实测',
    command: './scripts/smoke-doc-parser-async.sh'
  },
  {
    label: '运行态 Demo 数据',
    command: './scripts/seed-rag-demo.sh'
  },
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

const commandLabels: Record<string, string> = {
  './scripts/create-demo-evidence.sh --dry-run': '证据包模板',
  './scripts/collect-demo-evidence.sh --dry-run': '一键证据收集',
  './scripts/probe-doc-parser-boundary.sh --contract-only': 'doc-parser 边界',
  './scripts/probe-retrieval-adapters.sh --dry-run': '检索 Adapter 探针',
  './scripts/probe-production-adapter-profile.sh --dry-run': '生产 Adapter Profile',
  'curl -fsS http://localhost:10001/api/retrieval/adapters/status': '运行态 Adapter 状态',
  './scripts/check-doc-parser-lifecycle.sh': '解析生命周期',
  './scripts/smoke-doc-parser-async.sh': '异步解析实测',
  './scripts/seed-rag-demo.sh': '运行态 Demo 数据',
  './scripts/evaluate-rag-retrieval.sh': '检索评估',
  './scripts/smoke-rag-demo.sh': 'RAG 最小闭环',
  './scripts/probe-backend-dev.sh': '后端轻启动探针',
  './scripts/check-template.sh': '模板身份检查',
  './scripts/check-contracts.sh': '契约与脚手架检查'
}

const displayEvidenceCommands = computed(() => {
  const runtimeCommands = [
    ...(demoSeed.value?.evidenceCommands || []),
    ...(retrievalEvaluation.value?.evidenceCommands || [])
  ]
  if (runtimeCommands.length === 0) {
    return evidenceCommands
  }

  return [...new Set(runtimeCommands)].map((command) => ({
    label: commandLabels[command] || '运行态证据',
    command
  }))
})

const goKnowledge = () => {
  router.push('/kb/knowledge')
}

const goRetrieval = () => {
  router.push('/kb/retrieval')
}

const goChat = () => {
  router.push('/kb/chat')
}

const pushDemoRoute = (route?: string) => {
  if (!route) return
  router.push(route)
}

const seedDemo = async () => {
  let shouldEvaluate = false
  seedingDemo.value = true
  try {
    demoSeed.value = await RagDemoService.seedRagDemo()
    retrievalEvaluation.value = null
    shouldEvaluate = true
    ElMessage.success('Demo 数据已生成')
  } catch (error) {
    console.error('生成 Demo 数据失败:', error)
    ElMessage.error('生成 Demo 数据失败')
  } finally {
    seedingDemo.value = false
  }
  if (shouldEvaluate) {
    await evaluateRetrieval(true)
  }
}

const evaluateRetrieval = async (silent = false) => {
  evaluatingRetrieval.value = true
  try {
    retrievalEvaluation.value = await RagDemoService.evaluateRetrieval()
    if (!silent) {
      if (retrievalEvaluation.value.passed) {
        ElMessage.success('检索评估已通过')
      } else {
        ElMessage.warning('检索评估未完全通过')
      }
    }
  } catch (error) {
    console.error('检索评估失败:', error)
    ElMessage.error('检索评估失败')
  } finally {
    evaluatingRetrieval.value = false
  }
}

const goSeedKnowledge = () => {
  pushDemoRoute(demoSeed.value?.knowledgeRoute)
}

const goSeedRetrieval = () => {
  pushDemoRoute(demoSeed.value?.retrievalRoute)
}

const goSeedChat = () => {
  pushDemoRoute(demoSeed.value?.chatRoute)
}

const demoTeachingSteps = computed(() => [
  {
    key: 'seed',
    title: 'Seed',
    description: demoSeed.value
      ? `${demoSeed.value.kbName} 已写入 H2/memory/local-demo 运行态。`
      : '通过 dev/test endpoint 写入知识库、文档、chunk 和向量。',
    ready: Boolean(demoSeed.value),
    icon: markRaw(Refresh),
    actionLabel: '生成',
    disabled: seedingDemo.value,
    action: seedDemo
  },
  {
    key: 'evaluate',
    title: 'Evaluate',
    description: retrievalEvaluation.value
      ? `recall@${retrievalEvaluation.value.topK} ${recallAtKDisplay.value}，${retrievalCaseDisplay.value} 个用例通过。`
      : '通过固定 query/expected chunk 校验检索质量证据。',
    ready: Boolean(retrievalEvaluation.value?.passed),
    icon: markRaw(DataAnalysis),
    actionLabel: '评估',
    disabled: evaluatingRetrieval.value,
    action: () => evaluateRetrieval()
  },
  {
    key: 'knowledge',
    title: 'Knowledge',
    description: demoSeed.value
      ? `${demoSeed.value.docName} 可在知识库详情里查看。`
      : '等待 seed 后查看知识库、文档和切片。',
    ready: Boolean(demoSeed.value?.knowledgeRoute),
    icon: markRaw(FolderOpened),
    actionLabel: '查看',
    disabled: !demoSeed.value,
    action: goSeedKnowledge
  },
  {
    key: 'retrieval',
    title: 'Retrieval',
    description: demoSeed.value
      ? `${demoSeed.value.retrievalQuery} -> top chunk ${demoSeed.value.topChunkId || '-'}。`
      : '等待 seed 后自动带入 query/kbIds 并执行检索。',
    ready: Boolean(demoSeed.value?.retrievalRoute),
    icon: markRaw(Search),
    actionLabel: '检索',
    disabled: !demoSeed.value,
    action: goSeedRetrieval
  },
  {
    key: 'chat',
    title: 'Chat',
    description: demoSeed.value
      ? `${demoSeed.value.chatQuestion} 将自动发送并展示引用。`
      : '等待 seed 后进入知识问答，自动创建会话并引用回答。',
    ready: Boolean(demoSeed.value?.chatRoute),
    icon: markRaw(ChatLineRound),
    actionLabel: '问答',
    disabled: !demoSeed.value,
    action: goSeedChat
  },
  {
    key: 'evidence',
    title: 'Evidence',
    description: demoSeed.value
      ? `${displayEvidenceCommands.value.length} 条脚本证据可复制复现。`
      : '等待 seed 后使用返回的 evidence commands 固化演示证据。',
    ready: Boolean(demoSeed.value?.evidenceCommands?.length),
    icon: markRaw(CircleCheck),
    actionLabel: '',
    disabled: false,
    action: undefined
  }
])

const copyCommand = async (command: string) => {
  try {
    await navigator.clipboard.writeText(command)
    ElMessage.success('命令已复制')
  } catch (error) {
    console.error('复制命令失败:', error)
    ElMessage.warning(command)
  }
}

const runtimeAdapter = (axis: string) => {
  return adapterStatusMap.value[axis]
}

const runtimeProvider = (axis: string) => {
  return runtimeAdapter(axis)?.currentProvider || 'design-only'
}

const runtimeImplementation = (axis: string) => {
  return runtimeAdapter(axis)?.currentImplementation || '等待后端状态'
}

const runtimeStatus = (axis: string) => {
  return runtimeAdapter(axis)?.runtimeStatus || 'unknown'
}

const loadAdapterStatus = async () => {
  adapterStatusLoading.value = true
  try {
    adapterStatus.value = await RetrievalService.adapterStatus()
  } catch (error) {
    console.warn('读取 Adapter 状态失败:', error)
    adapterStatus.value = null
  } finally {
    adapterStatusLoading.value = false
  }
}

onMounted(() => {
  loadAdapterStatus()
})
</script>

<style lang="scss" scoped>
.pipeline-workbench {
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color-page);
}

.workspace-header,
.demo-ready-section,
.foundation-section,
.adapter-section,
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
.demo-ready-section,
.adapter-section,
.pipeline-section,
.boundary-section,
.evidence-section {
  padding: 20px;
}

.demo-ready-section,
.foundation-section,
.adapter-section,
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

.section-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.foundation-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.demo-ready-body {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(360px, 1.2fr) auto;
  gap: 16px;
  align-items: center;
}

.demo-status-copy {
  min-width: 0;

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.6;
  }
}

.demo-status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1f8a70;
  font-size: 15px;
  font-weight: 700;
}

.demo-metric-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.demo-metric {
  min-height: 76px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);

  span {
    display: block;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.2;
  }

  strong {
    display: block;
    min-width: 0;
    margin-top: 10px;
    color: var(--el-text-color-primary);
    font-size: 14px;
    font-weight: 700;
    line-height: 1.3;
    overflow-wrap: anywhere;
  }
}

.demo-action-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  min-width: 280px;
}

.demo-loop-panel {
  grid-column: 1 / -1;
  padding: 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.demo-loop-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  span {
    color: var(--el-text-color-primary);
    font-size: 14px;
    font-weight: 700;
    line-height: 1.3;
  }

  p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
  }
}

.demo-loop-track {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.demo-loop-step {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  min-height: 154px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);

  &.ready {
    border-color: rgba(31, 138, 112, 0.28);
    background: rgba(31, 138, 112, 0.04);

    .demo-loop-icon {
      color: #1f8a70;
      background: rgba(31, 138, 112, 0.12);
    }
  }
}

.demo-loop-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  font-size: 16px;
}

.demo-loop-content {
  min-width: 0;

  p {
    min-height: 54px;
    margin: 8px 0 10px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}

.demo-loop-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  strong {
    color: var(--el-text-color-primary);
    font-size: 13px;
    font-weight: 700;
  }

  span {
    flex: 0 0 auto;
    color: var(--el-text-color-placeholder);
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
  }
}

.demo-loop-action {
  min-width: 56px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(31, 138, 112, 0.32);
  border-radius: 6px;
  color: #1f8a70;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;

  &:disabled {
    border-color: var(--el-border-color-lighter);
    color: var(--el-text-color-placeholder);
    cursor: not-allowed;
  }

  &:not(:disabled):hover {
    background: rgba(31, 138, 112, 0.08);
  }
}

.retrieval-quality-panel {
  grid-column: 1 / -1;
  padding: 14px;
  border: 1px solid rgba(47, 128, 237, 0.18);
  border-radius: 8px;
  background: rgba(47, 128, 237, 0.03);
}

.quality-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  span {
    color: var(--el-text-color-primary);
    font-size: 14px;
    font-weight: 700;
    line-height: 1.3;
  }

  p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
  }
}

.quality-metric-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.quality-metric {
  min-height: 74px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);

  span {
    display: block;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.2;
  }

  strong {
    display: block;
    min-width: 0;
    margin-top: 10px;
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 700;
    line-height: 1.3;
    overflow-wrap: anywhere;
  }
}

.quality-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.quality-case-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.quality-case {
  min-height: 148px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);

  &.passed {
    border-color: rgba(31, 138, 112, 0.28);
  }

  p {
    margin: 10px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}

.quality-case-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;

  strong {
    min-width: 0;
    color: var(--el-text-color-primary);
    font-size: 13px;
    font-weight: 700;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}

.quality-case-chunks {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;

  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }
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

.adapter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.adapter-item {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 260px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.adapter-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.adapter-title-wrap {
  display: flex;
  gap: 12px;
  min-width: 0;

  h3 {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 700;
    line-height: 1.3;
  }

  p {
    margin: 8px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
    overflow-wrap: anywhere;
  }
}

.adapter-icon {
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

.adapter-path {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-height: 34px;
}

.provider-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 999px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  font-size: 12px;
  font-weight: 700;

  &.default {
    color: #1f8a70;
    border-color: rgba(31, 138, 112, 0.24);
  }

  &.bridge {
    color: #b7791f;
    border-color: rgba(183, 121, 31, 0.26);
  }

  &.production {
    color: #2f80ed;
    border-color: rgba(47, 128, 237, 0.26);
  }
}

.provider-arrow {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  font-weight: 700;
}

.adapter-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 54px;
}

.adapter-runtime {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px 10px;
  min-height: 58px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);

  span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    min-width: 0;
    color: #1f8a70;
    font-size: 13px;
    font-weight: 700;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  small {
    grid-column: 1 / -1;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  &.production,
  &.async-recovery,
  &.async-submit-only {
    border-color: rgba(47, 128, 237, 0.24);

    strong {
      color: #2f80ed;
    }
  }

  &.unknown {
    strong {
      color: var(--el-text-color-placeholder);
    }
  }
}

.adapter-command {
  display: flex;
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-height: 68px;
  padding: 12px;
  margin-top: auto;
  border: 1px solid rgba(31, 138, 112, 0.24);
  border-radius: 8px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: rgba(31, 138, 112, 0.4);
    background: rgba(31, 138, 112, 0.04);
  }

  span {
    font-size: 12px;
    font-weight: 700;
  }

  code {
    color: #1f8a70;
    font-size: 12px;
    line-height: 1.4;
    overflow-wrap: anywhere;
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
  .foundation-grid,
  .adapter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stage-track {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .boundary-layout {
    grid-template-columns: 1fr;
  }

  .demo-ready-body {
    grid-template-columns: 1fr;
  }

  .demo-action-row {
    justify-content: flex-start;
    min-width: 0;
  }

  .demo-loop-track {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quality-case-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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

  .demo-metric-row,
  .quality-metric-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .demo-loop-heading,
  .quality-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .quality-case-list {
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
  .adapter-grid,
  .demo-metric-row,
  .quality-metric-row,
  .demo-loop-track,
  .stage-track {
    grid-template-columns: 1fr;
  }
}
</style>
