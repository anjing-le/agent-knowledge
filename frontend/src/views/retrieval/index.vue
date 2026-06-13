<template>
  <div class="retrieval-workbench">
    <div class="workspace-header">
      <div>
        <p class="page-kicker">RAG Retrieval</p>
        <h1 class="page-title">检索调试</h1>
        <p class="page-subtitle">观察 query 如何命中 chunk、score 和 metadata，再进入问答链路生成引用回答。</p>
      </div>
      <div class="header-actions">
        <el-button @click="loadKnowledgeBases" :loading="knowledgeLoading">
          <el-icon><Refresh /></el-icon>
          刷新知识库
        </el-button>
        <el-button type="primary" @click="goChat">
          <el-icon><ChatLineRound /></el-icon>
          知识问答
        </el-button>
      </div>
    </div>

    <div class="retrieval-layout">
      <section class="query-panel">
        <div class="panel-heading">
          <div>
            <h2>检索参数</h2>
            <p>{{ selectedKbIds.length }} 个知识库参与本次检索</p>
          </div>
          <el-tag type="success" effect="plain">OpenAPI search</el-tag>
        </div>

        <el-form label-position="top" class="query-form">
          <el-form-item label="知识库范围">
            <el-select
              v-model="selectedKbIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              filterable
              placeholder="选择知识库"
              class="full-control"
              :loading="knowledgeLoading"
            >
              <el-option
                v-for="kb in enabledKnowledgeBases"
                :key="kb.kbId"
                :label="kb.name"
                :value="kb.kbId"
              >
                <div class="kb-option">
                  <span>{{ kb.name }}</span>
                  <small>{{ kb.documentCount || 0 }} 文档 / {{ kb.chunkCount || 0 }} 切片</small>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="查询文本">
            <el-input
              v-model="query"
              type="textarea"
              :rows="5"
              maxlength="500"
              show-word-limit
              placeholder="输入要验证的检索问题"
            />
          </el-form-item>

          <div class="param-grid">
            <el-form-item label="Top K">
              <el-input-number v-model="topK" :min="1" :max="20" :step="1" class="full-control" />
            </el-form-item>
            <el-form-item label="候选数量">
              <el-input-number
                v-model="candidateCount"
                :min="topK"
                :max="100"
                :step="5"
                class="full-control"
              />
            </el-form-item>
          </div>

          <el-form-item label="相似度阈值">
            <el-slider v-model="similarityThreshold" :min="0" :max="1" :step="0.01" />
          </el-form-item>

          <div class="query-footer">
            <el-checkbox v-model="rerank">启用 rerank</el-checkbox>
            <div class="query-actions">
              <el-button :disabled="!canAskInChat" @click="goChat">
                <el-icon><ChatLineRound /></el-icon>
                带入问答
              </el-button>
              <el-button type="primary" :loading="searching" :disabled="!canSearch" @click="runSearch">
                <el-icon><Search /></el-icon>
                开始检索
              </el-button>
            </div>
          </div>
        </el-form>
      </section>

      <section class="results-panel">
        <div class="panel-heading">
          <div>
            <h2>命中结果</h2>
            <p>{{ results.length }} 个 chunk</p>
          </div>
          <el-tag v-if="lastSearchedAt" type="info" effect="plain">{{ lastSearchedAt }}</el-tag>
        </div>

        <el-empty v-if="!searching && results.length === 0" description="暂无检索结果" />

        <div v-else class="result-list">
          <article v-for="(item, index) in results" :key="item.chunkId || index" class="result-item">
            <div class="result-header">
              <div>
                <div class="result-title">
                  <span class="rank">#{{ item.rank || index + 1 }}</span>
                  <span>{{ item.docName || '未命名文档' }}</span>
                </div>
                <div class="result-source">
                  <el-icon><Document /></el-icon>
                  <span>{{ item.kbName || item.kbId || '未知知识库' }}</span>
                  <span v-if="item.chunkId">Chunk {{ compactId(item.chunkId) }}</span>
                </div>
              </div>
              <div class="score-stack">
                <span v-if="formatScore(item.finalScore)" class="score-chip primary">
                  final {{ formatScore(item.finalScore) }}
                </span>
                <span v-if="formatScore(item.similarityScore)" class="score-chip">
                  sim {{ formatScore(item.similarityScore) }}
                </span>
                <span v-if="formatScore(item.rerankScore)" class="score-chip">
                  rerank {{ formatScore(item.rerankScore) }}
                </span>
              </div>
            </div>

            <p class="result-content">{{ item.highlightContent || item.content }}</p>

            <div v-if="item.scoreExplanation" class="score-explanation">
              <el-icon><DataAnalysis /></el-icon>
              <span>{{ item.scoreExplanation }}</span>
            </div>

            <div class="result-footer">
              <div class="metadata-tags">
                <el-tag
                  v-for="tag in formatMetadata(item.metadata)"
                  :key="tag"
                  size="small"
                  effect="plain"
                >
                  {{ tag }}
                </el-tag>
              </div>
              <el-button
                text
                type="primary"
                :disabled="!canOpenSlices(item)"
                @click="openSlices(item)"
              >
                <el-icon><Aim /></el-icon>
                查看切片
              </el-button>
            </div>
          </article>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Aim, ChatLineRound, DataAnalysis, Document, Refresh, Search } from '@element-plus/icons-vue'
import { KnowledgeService, type KnowledgeBase } from '@/api/knowledge'
import { RetrievalService, type SearchResult, type SearchResultMetadata } from '@/api/retrieval'

const router = useRouter()
const route = useRoute()

const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKbIds = ref<string[]>([])
const query = ref('')
const topK = ref(5)
const candidateCount = ref(20)
const similarityThreshold = ref(0.3)
const rerank = ref(false)
const searching = ref(false)
const knowledgeLoading = ref(false)
const results = ref<SearchResult[]>([])
const lastSearchedAt = ref('')

const enabledKnowledgeBases = computed(() => knowledgeBases.value.filter(item => item.isEnabled))

const canSearch = computed(() => {
  return selectedKbIds.value.length > 0 && query.value.trim().length > 0
})

const canAskInChat = computed(() => {
  return selectedKbIds.value.length > 0 || query.value.trim().length > 0
})

const queryValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return String(value[0] || '')
  }
  return typeof value === 'string' ? value : ''
}

const queryList = (value: unknown) => {
  const values = Array.isArray(value) ? value : [value]
  return values
    .flatMap((item) => String(item || '').split(','))
    .map((item) => item.trim())
    .filter(Boolean)
}

const queryNumber = (value: unknown, fallback: number) => {
  const rawValue = queryValue(value)
  if (!rawValue) return fallback
  const parsed = Number(rawValue)
  return Number.isFinite(parsed) ? parsed : fallback
}

const isAutoSearchRoute = () => {
  return queryValue(route.query.autoSearch) === '1' || route.query.source === 'demo'
}

const applyRouteHandoff = () => {
  const handoffQuery = queryValue(route.query.q)
  const handoffKbIds = queryList(route.query.kbIds)

  if (handoffQuery) {
    query.value = handoffQuery
  }
  if (handoffKbIds.length > 0) {
    selectedKbIds.value = handoffKbIds
  }

  topK.value = Math.max(1, Math.min(20, queryNumber(route.query.topK, topK.value)))
  candidateCount.value = Math.max(topK.value, Math.min(100, queryNumber(route.query.candidateCount, candidateCount.value)))
  similarityThreshold.value = Math.max(0, Math.min(1, queryNumber(route.query.similarityThreshold, similarityThreshold.value)))

  return Boolean(handoffQuery || handoffKbIds.length > 0)
}

const loadKnowledgeBases = async () => {
  knowledgeLoading.value = true
  try {
    const page = await KnowledgeService.getList({ page: 1, size: 100 })
    knowledgeBases.value = page.records || []
    if (!selectedKbIds.value.length) {
      selectedKbIds.value = enabledKnowledgeBases.value.slice(0, 1).map(item => item.kbId)
    }
  } catch (error) {
    console.error('加载知识库失败:', error)
    ElMessage.error('加载知识库失败')
  } finally {
    knowledgeLoading.value = false
  }
}

const runSearch = async () => {
  if (!canSearch.value) {
    ElMessage.warning('请选择知识库并输入查询文本')
    return
  }

  searching.value = true
  try {
    results.value = await RetrievalService.search({
      query: query.value.trim(),
      kbIds: selectedKbIds.value,
      topK: topK.value,
      candidateCount: candidateCount.value,
      similarityThreshold: similarityThreshold.value,
      rerank: rerank.value
    })
    lastSearchedAt.value = new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch (error) {
    console.error('检索失败:', error)
    ElMessage.error('检索失败')
  } finally {
    searching.value = false
  }
}

const formatScore = (score?: number) => {
  if (typeof score !== 'number') return ''
  const normalized = score <= 1 ? score * 100 : score
  return `${Math.max(0, Math.min(100, normalized)).toFixed(1)}%`
}

const formatMetadata = (metadata?: SearchResultMetadata) => {
  const tags: string[] = []
  if (!metadata) return tags
  if (Array.isArray(metadata.page_idx) && metadata.page_idx.length > 0) {
    tags.push(`页码 ${metadata.page_idx.join(', ')}`)
  }
  if (typeof metadata.content_type === 'string' && metadata.content_type) {
    tags.push(`类型 ${metadata.content_type}`)
  }
  if (Array.isArray(metadata.source_parser_result_ids) && metadata.source_parser_result_ids.length > 0) {
    tags.push(`解析片段 ${metadata.source_parser_result_ids.length}`)
  }
  return tags
}

const compactId = (id: string) => {
  return id.length > 12 ? `${id.slice(0, 6)}...${id.slice(-4)}` : id
}

const canOpenSlices = (item: SearchResult) => {
  return Boolean(item.kbId && item.docId)
}

const openSlices = (item: SearchResult) => {
  if (!canOpenSlices(item)) return
  router.push(`/kb/knowledge/detail/${item.kbId}/file/${item.docId}/slices`)
}

const goChat = () => {
  const trimmedQuery = query.value.trim()
  router.push({
    path: '/kb/chat',
    query: {
      ...(trimmedQuery ? { q: trimmedQuery } : {}),
      ...(selectedKbIds.value.length ? { kbIds: selectedKbIds.value } : {}),
      source: 'retrieval'
    }
  })
}

onMounted(() => {
  const hasRouteHandoff = applyRouteHandoff()
  loadKnowledgeBases().then(() => {
    if (hasRouteHandoff && isAutoSearchRoute() && canSearch.value) {
      ElMessage.success('已带入 Demo 检索参数')
      runSearch()
    }
  })
})
</script>

<style lang="scss" scoped>
.retrieval-workbench {
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color-page);
}

.workspace-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
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
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
}

.page-subtitle {
  max-width: 720px;
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

.retrieval-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.query-panel,
.results-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.query-panel {
  position: sticky;
  top: 16px;
  padding: 18px;
}

.results-panel {
  min-height: 560px;
  padding: 18px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;

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
  }
}

.query-form {
  :deep(.el-form-item__label) {
    font-weight: 600;
  }
}

.full-control {
  width: 100%;
}

.kb-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  small {
    color: var(--el-text-color-secondary);
  }
}

.param-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.query-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.query-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-item {
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.result-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 700;
}

.rank {
  color: #1f8a70;
  font-variant-numeric: tabular-nums;
}

.result-source {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.score-stack {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.score-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  font-variant-numeric: tabular-nums;

  &.primary {
    border-color: rgba(31, 138, 112, 0.28);
    color: #1f8a70;
    background: rgba(31, 138, 112, 0.08);
  }
}

.result-content {
  margin: 14px 0;
  color: var(--el-text-color-regular);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.score-explanation {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 14px;
  padding: 8px 10px;
  border: 1px solid rgba(31, 138, 112, 0.18);
  border-radius: 6px;
  color: #1f8a70;
  background: rgba(31, 138, 112, 0.06);
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.result-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metadata-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

@media (max-width: 960px) {
  .workspace-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }

  .retrieval-layout {
    grid-template-columns: 1fr;
  }

  .query-panel {
    position: static;
  }
}

@media (max-width: 640px) {
  .retrieval-workbench {
    padding: 12px;
  }

  .param-grid {
    grid-template-columns: 1fr;
  }

  .result-header,
  .result-footer,
  .query-footer,
  .query-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .score-stack {
    justify-content: flex-start;
  }
}
</style>
