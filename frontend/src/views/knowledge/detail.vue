<template>
  <div class="knowledge-detail">
      <!-- 面包屑导航 -->
      <div class="breadcrumb-container">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>
            <router-link to="/kb/knowledge" class="breadcrumb-link">知识管理</router-link>
          </el-breadcrumb-item>
          <el-breadcrumb-item>知识库详情</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- 知识库详情卡片 -->
      <div class="knowledge-detail-card">
        <div class="detail-header">
          <div class="detail-info">
            <h1 class="knowledge-title">{{ knowledgeDetail.name }}</h1>
            <div class="knowledge-meta">
              <span class="meta-tag">{{ documentList.length }}个文件</span>
              <span class="meta-tag">{{ knowledgeDetail.chunkCount || 0 }}个分片</span>
            </div>
          </div>
          <div class="detail-actions">
            <el-button class="edit-btn" @click="handleEdit">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="primary" class="upload-btn" @click="handleUpload">
              <el-icon><Upload /></el-icon>
              上传知识
            </el-button>
          </div>
        </div>

        <p class="knowledge-description">{{ knowledgeDetail.description }}</p>

        <!-- RAG 配置展示 -->
        <div class="rag-config-bar">
          <div class="config-item">
            <span class="config-label">Embedding 模型</span>
            <span class="config-value">{{ knowledgeDetail.embeddingModel || 'text-embedding-3-small' }}</span>
          </div>
          <div class="config-divider"></div>
          <div class="config-item">
            <span class="config-label">分块大小</span>
            <span class="config-value">{{ knowledgeDetail.chunkSize || 500 }} 字符</span>
          </div>
          <div class="config-divider"></div>
          <div class="config-item">
            <span class="config-label">重叠大小</span>
            <span class="config-value">{{ knowledgeDetail.chunkOverlap || 50 }} 字符</span>
          </div>
        </div>

        <div class="doc-parser-health" :class="docParserHealthClass">
          <div class="doc-parser-health-main">
            <el-icon v-if="docParserHealthLoading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else-if="isDocParserReady"><Check /></el-icon>
            <el-icon v-else><Close /></el-icon>
            <span>{{ docParserHealthTitle }}</span>
          </div>
          <div class="doc-parser-health-desc">{{ docParserHealthDescription }}</div>
          <el-button
            link
            size="small"
            :loading="docParserHealthLoading"
            @click="loadDocParserHealth"
          >
            刷新
          </el-button>
        </div>
      </div>

      <section class="ingestion-workbench">
        <div class="ingestion-heading">
          <div>
            <p class="ingestion-kicker">Ingestion Workbench</p>
            <h2>上传解析工作台</h2>
            <p>把真实文件上传、Java 任务编排、Python doc-parser、切片向量化和检索验证放在同一个教学视图里。</p>
          </div>
          <el-tag :type="ingestionStatusTag.type" effect="plain">
            {{ ingestionStatusTag.label }}
          </el-tag>
        </div>

        <div class="ingestion-metrics">
          <article
            v-for="metric in ingestionMetrics"
            :key="metric.label"
            class="ingestion-metric"
            :class="metric.tone"
          >
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.hint }}</small>
          </article>
        </div>

        <div class="ingestion-evidence-chain">
          <div class="evidence-chain-heading">
            <div>
              <span>Evidence Chain</span>
              <p>{{ ingestionEvidenceSummary }}</p>
            </div>
            <el-button size="small" plain @click="copyIngestionEvidenceChain">
              <el-icon><Check /></el-icon>
              复制链路
            </el-button>
          </div>
          <div class="evidence-chain-track">
            <article
              v-for="item in ingestionEvidenceChain"
              :key="item.label"
              class="evidence-chain-step"
              :class="item.state"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.hint }}</small>
            </article>
          </div>
        </div>

        <div class="ingestion-body">
          <div class="ingestion-current">
            <div class="ingestion-current-title">
              <el-icon><Document /></el-icon>
              <div>
                <span>{{ currentIngestionDocument?.docName || '等待上传文档' }}</span>
                <p>{{ latestDocumentSummary }}</p>
              </div>
            </div>

            <div class="ingestion-current-meta">
              <div>
                <span>Status</span>
                <strong>{{ currentIngestionDocument ? getStatusText(currentIngestionDocument.status) : '-' }}</strong>
              </div>
              <div>
                <span>Chunks</span>
                <strong>{{ currentIngestionDocument?.chunkNum ?? '-' }}</strong>
              </div>
              <div>
                <span>Tokens</span>
                <strong>{{ currentIngestionDocument?.tokenNum ?? '-' }}</strong>
              </div>
              <div>
                <span>Updated</span>
                <strong>
                  {{ formatDateTime(currentIngestionDocument?.updatedAt || currentIngestionDocument?.createdAt) }}
                </strong>
              </div>
            </div>

            <div class="ingestion-actions">
              <el-button type="primary" @click="handleUpload">
                <el-icon><Upload /></el-icon>
                上传知识
              </el-button>
              <el-button :disabled="!currentIngestionDocument" @click="handleOpenLatestTasks">
                <el-icon><DataAnalysis /></el-icon>
                查看任务
              </el-button>
              <el-button :disabled="!latestCompletedDocument" @click="handleOpenLatestSlices">
                <el-icon><Search /></el-icon>
                查看切片
              </el-button>
              <el-button :disabled="!latestCompletedDocument" plain @click="handleOpenRetrievalProof">
                <el-icon><Position /></el-icon>
                检索验证
              </el-button>
              <el-button plain @click="copyIngestionProbeCommand">
                <el-icon><Check /></el-icon>
                复制探针
              </el-button>
            </div>
          </div>

          <div class="ingestion-stage-grid">
            <article
              v-for="stage in ingestionWorkbenchSteps"
              :key="stage.key"
              class="ingestion-stage"
              :class="`is-${stage.state}`"
            >
              <div class="ingestion-stage-top">
                <el-icon><component :is="stage.icon" /></el-icon>
                <el-tag size="small" :type="getPipelineTagType(stage.state)" effect="plain">
                  {{ getPipelineStateText(stage.state) }}
                </el-tag>
              </div>
              <h3>{{ stage.title }}</h3>
              <p>{{ stage.description }}</p>
              <code>{{ stage.boundary }}</code>
            </article>
          </div>
        </div>
      </section>

      <!-- 知识内容区域 -->
      <div class="knowledge-content">
        <div v-if="documentList.length === 0" class="empty-state">
          <div class="empty-icon">
            <el-icon><Box /></el-icon>
          </div>
          <p class="empty-text">暂无知识，请先上传知识</p>
          <el-button class="upload-btn-empty" @click="handleUpload">
            <el-icon><Upload /></el-icon>
            上传知识
          </el-button>
        </div>

        <div v-else class="file-table-container">
          <!-- 搜索和批量操作栏 -->
          <div class="table-toolbar">
            <div class="search-section">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索文档"
                class="search-input"
                clearable
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>
            <div v-if="processingPollActive" class="processing-watch">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>处理中</span>
            </div>
          </div>

          <!-- 文件表格 -->
          <div class="file-table">
            <el-table
              :data="filteredDocumentList"
              :row-class-name="getDocumentRowClassName"
              style="width: 100%"
            >
              <!-- 名称列 -->
              <el-table-column label="名称" min-width="200">
                <template #default="{ row }">
                  <div class="file-name-cell">
                    <div class="file-icon-wrapper">
                      <el-icon class="file-icon"><Document /></el-icon>
                    </div>
                    <span class="file-name">{{ row.docName }}</span>
                    <el-tag
                      v-if="isLatestUploadedDocument(row)"
                      class="latest-upload-tag"
                      size="small"
                      type="success"
                      effect="plain"
                    >
                      最新上传
                    </el-tag>
                  </div>
                </template>
              </el-table-column>

              <!-- 切片数量列 -->
              <el-table-column label="切片数量" width="120" align="center">
                <template #default="{ row }">
                  <span class="chunk-count">{{ row.chunkNum || '-' }}</span>
                </template>
              </el-table-column>

              <!-- Token 数量列 -->
              <el-table-column label="Token 数量" width="120" align="center">
                <template #default="{ row }">
                  <span class="token-count">{{ row.tokenNum || '-' }}</span>
                </template>
              </el-table-column>

              <!-- 解析进度列 -->
              <el-table-column label="解析进度" width="150">
                <template #default="{ row }">
                  <div class="progress-cell">
                    <div class="progress-status" :class="getStatusClass(row.status)">
                      <div class="status-icon">
                        <el-icon v-if="row.status === 'COMPLETED'"><Check /></el-icon>
                        <el-icon v-else-if="isFailedStatus(row.status)"><Close /></el-icon>
                        <el-icon v-else><Loading /></el-icon>
                      </div>
                      <span class="status-text">{{ getStatusText(row.status) }}</span>
                    </div>
                    <div v-if="isProcessingStatus(row.status)" class="progress-bar">
                      <el-progress
                        :percentage="toProgressPercent(row.progress)"
                        :show-text="false"
                        :stroke-width="4"
                      />
                    </div>
                  </div>
                </template>
              </el-table-column>

              <!-- 是否开启列 -->
              <el-table-column label="是否开启" width="100" align="center">
                <template #default="{ row }">
                  <el-switch
                    v-model="row.isEnabled"
                    @change="handleToggleEnabled(row)"
                    active-color="#67C23A"
                  />
                </template>
              </el-table-column>

              <!-- 操作列 -->
              <el-table-column label="操作" width="250">
                <template #default="{ row }">
                  <div class="action-buttons">
                    <el-button
                      v-if="row.status === 'COMPLETED'"
                      type="primary"
                      link
                      size="small"
                      @click="handleViewFile(row)"
                    >
                      详情
                    </el-button>
                    <el-button
                      v-if="row.status === 'COMPLETED'"
                      type="warning"
                      link
                      size="small"
                      @click="handleRetryFile(row)"
                    >
                      重新解析
                    </el-button>
                    <el-button
                      v-if="row.status !== 'COMPLETED' && !isProcessingStatus(row.status)"
                      type="warning"
                      link
                      size="small"
                      @click="handleRetryFile(row)"
                    >
                      重试
                    </el-button>
                    <el-button type="danger" link size="small" @click="handleDeleteFile(row)">
                      删除
                    </el-button>
                    <el-button type="info" link size="small" @click="handleViewTasks(row)">
                      任务
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 分页 -->
          <div class="table-pagination">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="taskDrawerVisible"
      title="文档处理任务"
      size="420px"
      class="task-drawer"
    >
      <div v-if="currentTaskDocument" class="task-document">
        <div class="task-document-name">{{ currentTaskDocument.docName }}</div>
        <div class="task-document-meta">
          {{ getStatusText(currentTaskDocument.status) }} · {{ currentTaskDocument.progressMsg || '暂无进度消息' }}
        </div>
      </div>

      <div v-if="currentTaskDocument" class="task-evidence-panel">
        <div class="task-evidence-header">
          <div>
            <span>Task Evidence Drawer</span>
            <p>{{ taskEvidenceSummary }}</p>
          </div>
          <el-tag :type="taskEvidenceStatus.type" effect="plain">
            {{ taskEvidenceStatus.label }}
          </el-tag>
        </div>

        <div class="task-evidence-stats">
          <div v-for="stat in taskEvidenceStats" :key="stat.label" class="task-evidence-stat">
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
            <small>{{ stat.hint }}</small>
          </div>
        </div>

        <div class="task-evidence-actions">
          <el-button size="small" plain @click="copyTaskEvidence">
            <el-icon><Check /></el-icon>
            复制证据
          </el-button>
          <el-button size="small" plain :disabled="!canRetryTaskEvidence" @click="handleTaskEvidenceRetry">
            <el-icon><Refresh /></el-icon>
            重新解析
          </el-button>
          <el-button size="small" plain :disabled="!canOpenTaskEvidenceSlices" @click="handleTaskEvidenceOpenSlices">
            <el-icon><Search /></el-icon>
            查看切片
          </el-button>
          <el-button
            size="small"
            type="primary"
            plain
            :disabled="!canRunTaskEvidenceRetrieval"
            @click="handleTaskEvidenceRetrievalProof"
          >
            <el-icon><Position /></el-icon>
            检索验证
          </el-button>
        </div>
      </div>

      <div v-if="taskLoading" class="task-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        加载任务中
      </div>

      <el-empty v-else-if="processingTasks.length === 0" description="暂无处理任务" />

      <template v-else>
        <div class="pipeline-panel">
          <div
            v-for="step in pipelineSteps"
            :key="step.phase"
            class="pipeline-step"
            :class="`is-${step.state}`"
          >
            <div class="pipeline-marker">
              <el-icon v-if="step.state === 'done'"><Check /></el-icon>
              <el-icon v-else-if="step.state === 'failed'"><Close /></el-icon>
              <el-icon v-else-if="step.state === 'running'" class="is-loading"><Loading /></el-icon>
              <span v-else>{{ step.order }}</span>
            </div>
            <div class="pipeline-content">
              <div class="pipeline-title-row">
                <span class="pipeline-title">{{ step.title }}</span>
                <el-tag size="small" :type="getPipelineTagType(step.state)">
                  {{ getPipelineStateText(step.state) }}
                </el-tag>
              </div>
              <div class="pipeline-description">{{ step.description }}</div>
            </div>
          </div>
        </div>

        <el-divider content-position="left">任务记录</el-divider>

        <el-timeline class="task-timeline">
          <el-timeline-item
            v-for="task in processingTasks"
            :key="task.taskId"
            :type="getTaskTimelineType(task.status)"
            :timestamp="task.updatedAt || task.createdAt"
            placement="top"
          >
            <div class="task-item">
              <div class="task-item-header">
                <span class="task-phase">{{ getTaskPhaseText(task.phase) }}</span>
                <el-tag size="small" :type="getTaskTagType(task.status)">
                  {{ getTaskStatusText(task.status) }}
                </el-tag>
              </div>
              <el-progress
                :percentage="toProgressPercent(task.progress)"
                :stroke-width="6"
                :status="task.status === 'FAILED' ? 'exception' : undefined"
              />
              <div class="task-message">{{ task.errorMessage || task.message || '暂无任务消息' }}</div>
              <div v-if="hasParserSnapshot(task)" class="task-parser-snapshot">
                <span v-if="task.parserTaskId">Parser {{ task.parserTaskId }}</span>
                <span v-if="task.parserStatus">{{ task.parserStatus }}</span>
                <span v-if="task.parserProgress !== undefined">
                  {{ toProgressPercent(task.parserProgress) }}%
                </span>
                <span v-if="task.parserStatusUpdateCount">
                  更新 {{ task.parserStatusUpdateCount }}
                </span>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 上传知识对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title=""
      width="800px"
      :before-close="handleUploadDialogClose"
      class="upload-dialog"
      :show-close="false"
    >
      <div class="upload-dialog-content">
        <!-- 对话框头部 -->
        <div class="dialog-header">
          <h3 class="dialog-title">上传知识</h3>
          <el-button
            class="close-btn"
            @click="handleUploadDialogClose"
            :icon="Close"
            circle
            size="small"
          />
        </div>

        <!-- 本地上传区域 -->
        <div class="upload-area">
          <el-alert
            class="upload-health-alert"
            :title="docParserHealthTitle"
            :description="docParserHealthDescription"
            :type="docParserAlertType"
            :closable="false"
            show-icon
          />

          <el-upload
            v-model:file-list="uploadFileList"
            class="file-uploader"
            drag
            :auto-upload="false"
            :limit="20"
            :on-exceed="handleExceed"
            accept=".pdf,.doc,.docx,.txt,.md"
            multiple
          >
            <div class="upload-dragger">
              <div class="upload-icon-large">
                <el-icon><Upload /></el-icon>
              </div>
              <div class="upload-text">
                <p class="upload-title">文件拖拽上传或<span class="highlight">点击上传</span></p>
              </div>
            </div>
          </el-upload>
          <p class="upload-limit">支持 PDF、Word、TXT、Markdown 格式，单次最多上传20个文件</p>
        </div>

        <!-- 文件列表 -->
        <div v-if="uploadFileList.length > 0" class="file-list">
          <div v-for="(file, index) in uploadFileList" :key="index" class="file-item">
            <div class="file-info">
              <div class="file-icon">
                <el-icon><Document /></el-icon>
              </div>
              <div class="file-details">
                <span class="file-name">{{ file.name }}</span>
              </div>
            </div>
            <div class="file-actions">
              <el-button @click="removeFile(index)" class="delete-btn" size="small" circle>
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>

        <!-- 对话框底部 -->
        <div class="dialog-footer">
          <el-button @click="handleUploadDialogClose" class="cancel-btn">取消</el-button>
          <el-button
            @click="handleUploadConfirm"
            type="primary"
            class="confirm-btn"
            :loading="uploadLoading"
          >
            确定上传
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑知识库对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑知识库"
      width="600px"
      :before-close="handleEditDialogClose"
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="formRules"
        label-width="100px"
        label-position="left"
      >
        <el-form-item label="知识库名称" prop="name">
          <el-input
            v-model="editForm.name"
            placeholder="请输入知识库名称"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="知识库描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-divider content-position="left">RAG 配置</el-divider>

        <el-form-item label="Embedding 模型">
          <el-select v-model="editForm.embeddingModel" placeholder="选择向量化模型" style="width: 100%">
            <el-option label="text-embedding-3-small (1536维)" value="text-embedding-3-small" />
            <el-option label="text-embedding-3-large (3072维)" value="text-embedding-3-large" />
            <el-option label="text-embedding-ada-002 (1536维)" value="text-embedding-ada-002" />
          </el-select>
        </el-form-item>

        <el-form-item label="分块大小">
          <el-input-number
            v-model="editForm.chunkSize"
            :min="100"
            :max="4000"
            :step="100"
            style="width: 100%"
          />
          <div class="form-tip">修改后需要对文档「重新解析」才会生效</div>
        </el-form-item>

        <el-form-item label="重叠大小">
          <el-input-number
            v-model="editForm.chunkOverlap"
            :min="0"
            :max="500"
            :step="10"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleEditDialogClose">取消</el-button>
          <el-button type="primary" @click="handleEditConfirm" :loading="editLoading">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, reactive, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  Document,
  Edit,
  Upload,
  Box,
  Search,
  Check,
  Close,
  Loading,
  Delete,
  DataAnalysis,
  Position,
  Refresh
} from '@element-plus/icons-vue'
import {
  KnowledgeService,
  DocumentService,
  type KnowledgeBase,
  type Document as DocType,
  type DocumentProcessingTask
} from '@/api/knowledge'
import { SystemService, type DownstreamHealth } from '@/api/system'

// 路由
const route = useRoute()
const router = useRouter()

// 获取知识库ID
const kbId = route.params.id as string

// 响应式数据
const knowledgeDetail = ref<Partial<KnowledgeBase>>({
  kbId: '',
  name: '',
  description: '',
  chunkCount: 0
})

const documentList = ref<DocType[]>([])
const uploadDialogVisible = ref(false)
const uploadLoading = ref(false)
const uploadFileList = ref<UploadFile[]>([])
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const taskDrawerVisible = ref(false)
const taskLoading = ref(false)
const currentTaskDocument = ref<DocType | null>(null)
const processingTasks = ref<DocumentProcessingTask[]>([])
const processingPollActive = ref(false)
const docParserHealth = ref<DownstreamHealth | null>(null)
const docParserHealthLoading = ref(false)
const docParserHealthChecked = ref(false)
const latestUploadedDocIds = ref<string[]>([])
let processingPollTimer: ReturnType<typeof setInterval> | undefined
let processingPollBusy = false
const PROCESSING_POLL_INTERVAL = 3000
const ingestionProbeCommand = './scripts/probe-rag-ingestion-runtime.sh'

type PipelineState = 'pending' | 'running' | 'done' | 'failed'

interface PipelineStep {
  order: number
  phase: string
  title: string
  description: string
  state: PipelineState
}

interface IngestionWorkbenchStage {
  key: string
  title: string
  description: string
  boundary: string
  state: PipelineState
  icon: object
}

const PIPELINE_DEFINITIONS = [
  {
    phase: 'PENDING',
    title: '上传完成',
    description: '文件已保存，等待 Java 后端调度处理'
  },
  {
    phase: 'PARSING',
    title: '文档解析',
    description: '调用独立 Python doc-parser 提取正文和结构信息'
  },
  {
    phase: 'CHUNKING',
    title: '文本切片',
    description: '按知识库配置切分 Chunk，并保留引用 metadata'
  },
  {
    phase: 'EMBEDDING',
    title: 'Embedding',
    description: '生成文本向量，为语义检索建立表示'
  },
  {
    phase: 'VECTOR_UPSERT',
    title: '向量写入',
    description: '将 Chunk 向量写入 VectorStoreService 边界'
  },
  {
    phase: 'COMPLETED',
    title: '处理完成',
    description: '文档可参与检索、上下文组装和答案引用'
  }
] as const

// 编辑对话框相关
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  name: '',
  description: '',
  embeddingModel: 'text-embedding-3-small',
  chunkSize: 500,
  chunkOverlap: 50
})

// 表单验证规则
const formRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 50, message: '知识库名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入知识库描述', trigger: 'blur' },
    { min: 10, max: 500, message: '知识库描述长度在 10 到 500 个字符', trigger: 'blur' }
  ]
}

// 获取知识库详情
const fetchKnowledgeDetail = async () => {
  try {
    const res = await KnowledgeService.getDetail(kbId)
    if (res) {
      knowledgeDetail.value = res
    }
  } catch (error) {
    console.error('获取知识库详情失败:', error)
    ElMessage.error('获取知识库详情失败')
  }
}

// 获取文档列表
const fetchDocumentList = async () => {
  try {
    const res = await DocumentService.getList(kbId, {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value
    })
    if (res) {
      documentList.value = res.records || []
      total.value = res.total || 0
      syncProcessingPoll()
    }
  } catch (error) {
    console.error('获取文档列表失败:', error)
    ElMessage.error('获取文档列表失败')
  }
}

// 过滤后的文档列表
const filteredDocumentList = computed(() => {
  if (!searchKeyword.value) return documentList.value
  return documentList.value.filter(doc =>
    doc.docName.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

const documentTime = (document: DocType) => {
  const rawTime = document.updatedAt || document.createdAt || ''
  const timestamp = rawTime ? new Date(rawTime).getTime() : 0
  return Number.isFinite(timestamp) ? timestamp : 0
}

const sortDocumentsByTime = (documents: DocType[]) => {
  return [...documents].sort((left, right) => documentTime(right) - documentTime(left))
}

const hasProcessingDocuments = computed(() =>
  documentList.value.some(doc => isProcessingStatus(doc.status) || doc.status === 'PENDING')
)

const activeDocuments = computed(() =>
  documentList.value.filter(doc => isProcessingStatus(doc.status) || doc.status === 'PENDING')
)

const completedDocuments = computed(() =>
  documentList.value.filter(doc => doc.status === 'COMPLETED')
)

const failedDocuments = computed(() =>
  documentList.value.filter(doc => isFailedStatus(doc.status))
)

const latestDocument = computed(() => sortDocumentsByTime(documentList.value)[0] || null)

const currentIngestionDocument = computed(() => {
  const latestUploadedDocument = documentList.value.find(doc => isLatestUploadedDocument(doc))
  return latestUploadedDocument || latestDocument.value
})

const latestCompletedDocument = computed(() =>
  sortDocumentsByTime(completedDocuments.value)[0] || null
)

const ingestionStatusTag = computed(() => {
  if (activeDocuments.value.length > 0) {
    return { type: 'warning' as const, label: 'Processing' }
  }
  if (failedDocuments.value.length > 0) {
    return { type: 'danger' as const, label: 'Needs Review' }
  }
  if (completedDocuments.value.length > 0) {
    return { type: 'success' as const, label: 'Retrievable' }
  }
  return { type: 'info' as const, label: 'Waiting Upload' }
})

const ingestionMetrics = computed(() => [
  {
    label: 'Documents',
    value: documentList.value.length,
    hint: '进入 DocumentService.upload 的文档数',
    tone: 'tone-blue'
  },
  {
    label: 'Processing',
    value: activeDocuments.value.length,
    hint: 'Java 任务正在推进',
    tone: 'tone-amber'
  },
  {
    label: 'Completed',
    value: completedDocuments.value.length,
    hint: '已可参与检索引用',
    tone: 'tone-green'
  },
  {
    label: 'Failed',
    value: failedDocuments.value.length,
    hint: '需要查看任务或重试',
    tone: 'tone-red'
  }
])

const ingestionEvidenceChain = computed(() => [
  {
    label: 'Upload',
    value: currentIngestionDocument.value?.docName || '等待文件',
    hint: 'DocumentService.upload',
    state: documentList.value.length > 0 ? 'ready' : 'pending'
  },
  {
    label: 'Task',
    value: activeDocuments.value.length > 0 ? 'Running' : completedDocuments.value.length > 0 ? 'Done' : 'Waiting',
    hint: 'DocumentProcessingTask',
    state: activeDocuments.value.length > 0 ? 'running' : completedDocuments.value.length > 0 ? 'ready' : 'pending'
  },
  {
    label: 'Parser',
    value: isDocParserReady.value ? 'UP' : docParserHealthChecked.value ? 'DOWN' : 'Checking',
    hint: 'Java HTTP -> Python doc-parser',
    state: isDocParserReady.value ? 'ready' : docParserHealthChecked.value ? 'blocked' : 'pending'
  },
  {
    label: 'Chunks',
    value: String(latestCompletedDocument.value?.chunkNum ?? 0),
    hint: 'Chunk metadata -> citation',
    state: latestCompletedDocument.value ? 'ready' : 'pending'
  },
  {
    label: 'Retrieval',
    value: latestCompletedDocument.value ? 'Ready' : 'Waiting',
    hint: 'scoreExplanation',
    state: latestCompletedDocument.value ? 'ready' : 'pending'
  },
  {
    label: 'Chat',
    value: latestCompletedDocument.value ? 'Groundable' : 'Waiting',
    hint: 'contextTrace -> references',
    state: latestCompletedDocument.value ? 'ready' : 'pending'
  }
])

const ingestionEvidenceSummary = computed(() => {
  if (latestCompletedDocument.value) {
    return `${latestCompletedDocument.value.docName} 已可从 chunk 进入 retrieval score、context trace 和 citation。`
  }
  if (activeDocuments.value.length > 0) {
    return 'Java 任务正在推进，完成后会开放切片、检索验证和问答引用。'
  }
  return '等待真实文件进入 upload -> parse -> chunk -> retrieval -> citation 链路。'
})

const latestDocumentSummary = computed(() => {
  const document = currentIngestionDocument.value
  if (!document) {
    return `等待真实文件进入 ${ingestionProbeCommand} 覆盖的上传解析链路。`
  }
  if (isProcessingStatus(document.status) || document.status === 'PENDING') {
    return `${getStatusText(document.status)}，进度 ${toProgressPercent(document.progress)}%，可打开任务抽屉观察 doc-parser 快照。`
  }
  if (document.status === 'COMPLETED') {
    return `已生成 ${document.chunkNum || 0} 个 chunk、${document.tokenNum || 0} tokens，可进入切片或检索验证。`
  }
  if (isFailedStatus(document.status)) {
    return `${getStatusText(document.status)}，建议查看任务记录中的 parserStatus、errorMessage 后重试。`
  }
  return `${getStatusText(document.status)}，等待后端任务继续推进。`
})

const ingestionWorkbenchSteps = computed<IngestionWorkbenchStage[]>(() => {
  const hasDocuments = documentList.value.length > 0
  const hasActive = activeDocuments.value.length > 0
  const hasCompleted = completedDocuments.value.length > 0
  const hasFailed = failedDocuments.value.length > 0
  const javaTaskState: PipelineState = hasActive ? 'running' : hasDocuments ? 'done' : 'pending'
  const parserState: PipelineState = hasFailed && !hasActive && !hasCompleted
    ? 'failed'
    : hasActive
      ? 'running'
      : hasCompleted
        ? 'done'
        : 'pending'
  const retrievalState: PipelineState = hasCompleted ? 'done' : 'pending'

  return [
    {
      key: 'upload-api',
      title: 'Upload API',
      description: '前端只通过脚手架 API 边界提交 multipart 文件。',
      boundary: 'DocumentService.upload -> ApiPaths.knowledge.baseDocuments',
      state: hasDocuments ? 'done' : 'pending',
      icon: markRaw(Upload)
    },
    {
      key: 'java-task',
      title: 'Java Task',
      description: '后端负责文档、任务、进度、重试和状态持久化。',
      boundary: 'DocumentProcessingTask / DocumentService.getTasks',
      state: javaTaskState,
      icon: markRaw(DataAnalysis)
    },
    {
      key: 'python-parser',
      title: 'Python Parse',
      description: '解析服务保持独立进程，Java 只通过 HTTP 调用。',
      boundary: 'agent-doc-parser / DocParserClient',
      state: parserState,
      icon: markRaw(Position)
    },
    {
      key: 'chunk-embed',
      title: 'Chunk & Embed',
      description: '解析结果进入切片、Embedding 和 VectorStoreService。',
      boundary: 'DocumentChunkingService / DocumentEmbeddingService',
      state: hasCompleted ? 'done' : hasActive ? 'running' : 'pending',
      icon: markRaw(Box)
    },
    {
      key: 'retrieval-proof',
      title: 'Retrieval Proof',
      description: '完成后的文档可以直接带入检索页验证 score 和引用。',
      boundary: '/kb/retrieval?autoSearch=1&source=ingestion',
      state: retrievalState,
      icon: markRaw(Search)
    }
  ]
})

const latestProcessingTask = computed(() => processingTasks.value[0] || null)

const compactEvidenceValue = (value?: string) => {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 8)}...${value.slice(-6)}` : value
}

const taskEvidenceStatus = computed(() => {
  const task = latestProcessingTask.value
  if (task?.status === 'SUCCEEDED' || currentTaskDocument.value?.status === 'COMPLETED') {
    return { type: 'success' as const, label: 'Evidence Ready' }
  }
  if (task?.status === 'FAILED' || isFailedStatus(currentTaskDocument.value?.status || '')) {
    return { type: 'danger' as const, label: 'Needs Retry' }
  }
  if (task?.status === 'RUNNING' || isProcessingStatus(currentTaskDocument.value?.status || '')) {
    return { type: 'warning' as const, label: 'Processing' }
  }
  return { type: 'info' as const, label: 'Waiting' }
})

const taskEvidenceSummary = computed(() => {
  const task = latestProcessingTask.value
  if (!currentTaskDocument.value) return '等待选择文档后展示任务证据。'
  if (!task) {
    return '当前文档暂无任务记录，可以复制文档状态或重新解析生成新任务。'
  }
  if (task.status === 'FAILED') {
    return task.parserErrorMessage || task.errorMessage || '任务失败，请查看 parserStatus 和错误消息后重试。'
  }
  if (task.parserStatus) {
    return `Python parser ${task.parserStatus}，Java 任务处于 ${getTaskPhaseText(task.phase)}。`
  }
  return `${getTaskPhaseText(task.phase)} · ${getTaskStatusText(task.status)}，等待 parser 快照回写。`
})

const taskEvidenceStats = computed(() => {
  const task = latestProcessingTask.value
  const document = currentTaskDocument.value
  const progress = toProgressPercent(task?.parserProgress ?? task?.progress ?? document?.progress)
  return [
    {
      label: 'Java Task',
      value: compactEvidenceValue(task?.taskId),
      hint: task ? `${getTaskPhaseText(task.phase)} / ${getTaskStatusText(task.status)}` : '等待任务记录'
    },
    {
      label: 'Parser',
      value: task?.parserStatus || '-',
      hint: task?.parserTaskId ? `parserTaskId ${compactEvidenceValue(task.parserTaskId)}` : 'agent-doc-parser HTTP boundary'
    },
    {
      label: 'Progress',
      value: `${progress}%`,
      hint: 'parserProgress -> document progress'
    },
    {
      label: 'Retry',
      value: String(task?.retryCount ?? 0),
      hint: task?.parserLastPolledAt
        ? `parserLastPolledAt ${formatDateTime(task.parserLastPolledAt)}`
        : '重新解析仍走同一 API 边界'
    }
  ]
})

const taskEvidenceCopyText = computed(() => {
  const document = currentTaskDocument.value
  const task = latestProcessingTask.value
  return [
    'Task Evidence Drawer',
    `docId=${document?.docId || '-'}`,
    `docName=${document?.docName || '-'}`,
    `documentStatus=${document?.status || '-'}`,
    `documentProgress=${toProgressPercent(document?.progress)}%`,
    `taskId=${task?.taskId || '-'}`,
    `phase=${task?.phase || '-'}`,
    `taskStatus=${task?.status || '-'}`,
    `parserTaskId=${task?.parserTaskId || '-'}`,
    `parserStatus=${task?.parserStatus || '-'}`,
    `parserProgress=${toProgressPercent(task?.parserProgress)}%`,
    `parserLastPolledAt=${task?.parserLastPolledAt || '-'}`,
    `message=${task?.parserErrorMessage || task?.errorMessage || task?.parserMessage || task?.message || '-'}`
  ].join('\n')
})

const canOpenTaskEvidenceSlices = computed(() => currentTaskDocument.value?.status === 'COMPLETED')
const canRunTaskEvidenceRetrieval = computed(() => currentTaskDocument.value?.status === 'COMPLETED')
const canRetryTaskEvidence = computed(() => {
  const status = currentTaskDocument.value?.status || ''
  return Boolean(currentTaskDocument.value) && !isProcessingStatus(status)
})

const pipelineSteps = computed<PipelineStep[]>(() => {
  const latestTask = latestProcessingTask.value
  const activePhase = normalizePipelinePhase(latestTask?.phase || currentTaskDocument.value?.status)
  const activeIndex = PIPELINE_DEFINITIONS.findIndex(step => step.phase === activePhase)
  const failed = latestTask?.status === 'FAILED' || isFailedStatus(currentTaskDocument.value?.status || '')

  return PIPELINE_DEFINITIONS.map((definition, index) => ({
    order: index + 1,
    ...definition,
    state: resolvePipelineState(index, activeIndex, failed)
  }))
})

const normalizePipelinePhase = (phase?: string) => {
  if (!phase) return 'PENDING'
  const failedPhaseMap: Record<string, string> = {
    PARSE_FAILED: 'PARSING',
    CHUNK_FAILED: 'CHUNKING',
    EMBEDDING_FAILED: 'EMBEDDING',
    RAPTOR_FAILED: 'EMBEDDING'
  }
  if (failedPhaseMap[phase]) return failedPhaseMap[phase]
  if (phase === 'FAILED') return latestProcessingTask.value?.phase || 'PENDING'
  if (phase === 'COMPLETED') return 'COMPLETED'
  if (phase === 'SUCCEEDED') return 'COMPLETED'
  if (phase === 'EMBEDDING') return 'EMBEDDING'
  return PIPELINE_DEFINITIONS.some(step => step.phase === phase) ? phase : 'PENDING'
}

const resolvePipelineState = (
  index: number,
  activeIndex: number,
  failed: boolean
): PipelineState => {
  if (activeIndex < 0) return index === 0 ? 'running' : 'pending'
  if (failed && index === activeIndex) return 'failed'
  if (index < activeIndex || activeIndex === PIPELINE_DEFINITIONS.length - 1) return 'done'
  if (index === activeIndex) return 'running'
  return 'pending'
}

const isDocParserReady = computed(() => docParserHealth.value?.status === 'UP')

const docParserHealthClass = computed(() => {
  if (docParserHealthLoading.value || !docParserHealthChecked.value) return 'is-checking'
  return isDocParserReady.value ? 'is-ready' : 'is-down'
})

const docParserAlertType = computed(() => {
  if (docParserHealthLoading.value || !docParserHealthChecked.value) return 'info'
  return isDocParserReady.value ? 'success' : 'warning'
})

const docParserHealthTitle = computed(() => {
  if (docParserHealthLoading.value || !docParserHealthChecked.value) return 'doc-parser 状态检查中'
  return isDocParserReady.value ? 'doc-parser 已就绪' : 'doc-parser 未就绪'
})

const docParserHealthDescription = computed(() => {
  if (docParserHealthLoading.value || !docParserHealthChecked.value) {
    return '正在通过 Java 后端健康检查确认 Python 文档解析服务状态'
  }
  if (isDocParserReady.value) {
    return 'Python 文档解析服务可用，上传后会进入解析、切片和 Embedding 流程'
  }
  return 'Python 文档解析服务不可用，上传后解析可能失败，请先启动 doc-parser'
})

const loadDocParserHealth = async () => {
  docParserHealthLoading.value = true
  try {
    const health = await SystemService.getHealth()
    docParserHealth.value = health.downstreams?.docParser || null
  } catch (error) {
    console.error('获取 doc-parser 健康状态失败:', error)
    docParserHealth.value = {
      serviceId: 'agent-doc-parser',
      status: 'DOWN',
      required: true
    }
  } finally {
    docParserHealthChecked.value = true
    docParserHealthLoading.value = false
  }
}

// 获取状态样式类
const getStatusClass = (status: string) => {
  if (status === 'COMPLETED') return 'status-completed'
  if (status?.endsWith('_FAILED')) return 'status-failed'
  if (['PARSING', 'CHUNKING', 'EMBEDDING', 'RAPTORING'].includes(status)) return 'status-parsing'
  return 'status-pending'
}

// 获取状态文本
const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    PENDING: '等待中',
    PARSING: '解析中',
    CHUNKING: '分块中',
    EMBEDDING: '向量化中',
    RAPTORING: 'RAPTOR处理中',
    COMPLETED: '已完成',
    PARSE_FAILED: '解析失败',
    CHUNK_FAILED: '分块失败',
    EMBEDDING_FAILED: '向量化失败',
    RAPTOR_FAILED: 'RAPTOR失败'
  }
  return statusMap[status] || status || '未知'
}

// 判断是否为失败状态
const isFailedStatus = (status: string) => {
  return status?.endsWith('_FAILED')
}

// 判断是否为处理中状态
const isProcessingStatus = (status: string) => {
  return ['PARSING', 'CHUNKING', 'EMBEDDING', 'RAPTORING'].includes(status)
}

const syncProcessingPoll = () => {
  if (hasProcessingDocuments.value) {
    startProcessingPoll()
  } else {
    stopProcessingPoll()
  }
}

const startProcessingPoll = () => {
  if (processingPollTimer) return
  processingPollActive.value = true
  processingPollTimer = setInterval(refreshProcessingDocuments, PROCESSING_POLL_INTERVAL)
}

const stopProcessingPoll = () => {
  if (processingPollTimer) {
    clearInterval(processingPollTimer)
    processingPollTimer = undefined
  }
  processingPollActive.value = false
}

const refreshProcessingDocuments = async () => {
  if (processingPollBusy) return
  processingPollBusy = true

  try {
    await fetchDocumentList()
    await refreshCurrentTaskDrawer()
  } finally {
    processingPollBusy = false
  }
}

const refreshCurrentTaskDrawer = async () => {
  if (!taskDrawerVisible.value || !currentTaskDocument.value) return

  const latestDocument = documentList.value.find(doc => doc.docId === currentTaskDocument.value?.docId)
  if (latestDocument) {
    currentTaskDocument.value = latestDocument
  }

  processingTasks.value = await DocumentService.getTasks(currentTaskDocument.value.docId)
}

const toProgressPercent = (progress?: number) => {
  if (!progress) return 0
  const normalized = progress <= 1 ? progress * 100 : progress
  return Math.min(100, Math.max(0, Math.round(normalized)))
}

const getTaskStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    PENDING: '等待中',
    RUNNING: '处理中',
    SUCCEEDED: '已完成',
    FAILED: '失败'
  }
  return statusMap[status] || status || '未知'
}

const getTaskPhaseText = (phase: string) => {
  const phaseMap: Record<string, string> = {
    PENDING: '等待调度',
    PARSING: '文档解析',
    CHUNKING: '文本切片',
    EMBEDDING: '向量化',
    VECTOR_UPSERT: '向量写入',
    COMPLETED: '处理完成',
    FAILED: '处理失败'
  }
  return phaseMap[phase] || phase || '未知阶段'
}

const hasParserSnapshot = (task: DocumentProcessingTask) =>
  Boolean(task.parserTaskId || task.parserStatus || task.parserStatusUpdateCount)

const getTaskTagType = (status: string) => {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

const getTaskTimelineType = (status: string) => {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

const getPipelineTagType = (state: PipelineState) => {
  if (state === 'done') return 'success'
  if (state === 'failed') return 'danger'
  if (state === 'running') return 'warning'
  return 'info'
}

const getPipelineStateText = (state: PipelineState) => {
  const stateMap: Record<PipelineState, string> = {
    pending: '等待',
    running: '进行中',
    done: '完成',
    failed: '失败'
  }
  return stateMap[state]
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const isLatestUploadedDocument = (doc: DocType) => {
  return latestUploadedDocIds.value.includes(doc.docId)
}

const getDocumentRowClassName = ({ row }: { row: DocType }) => {
  return isLatestUploadedDocument(row) ? 'is-latest-upload' : ''
}

const focusUploadedDocuments = async (uploadedDocuments: DocType[]) => {
  const uploadedDocIds = uploadedDocuments.map(doc => doc.docId).filter(Boolean)
  if (uploadedDocIds.length === 0) return

  latestUploadedDocIds.value = uploadedDocIds
  const focusedDocument = documentList.value.find(doc => uploadedDocIds.includes(doc.docId))
    || uploadedDocuments[0]

  if (focusedDocument) {
    await handleViewTasks(focusedDocument)
    ElMessage.success('已打开最新上传文档的处理任务')
  }
}

const handleOpenLatestTasks = () => {
  if (!currentIngestionDocument.value) return
  handleViewTasks(currentIngestionDocument.value)
}

const handleOpenLatestSlices = () => {
  if (!latestCompletedDocument.value) return
  handleViewFile(latestCompletedDocument.value)
}

const openRetrievalProofForDocument = (doc: DocType) => {
  router.push({
    path: '/kb/retrieval',
    query: {
      q: `请总结 ${doc.docName} 的核心内容`,
      kbIds: [doc.kbId || kbId],
      topK: '5',
      candidateCount: '20',
      hybrid: '1',
      autoSearch: '1',
      source: 'ingestion'
    }
  })
}

const handleOpenRetrievalProof = () => {
  if (!latestCompletedDocument.value) {
    ElMessage.warning('需要先有已完成文档才能检索验证')
    return
  }

  openRetrievalProofForDocument(latestCompletedDocument.value)
}

const handleTaskEvidenceOpenSlices = () => {
  if (!currentTaskDocument.value || currentTaskDocument.value.status !== 'COMPLETED') {
    ElMessage.warning('文档完成后才能查看切片')
    return
  }
  handleViewFile(currentTaskDocument.value)
}

const handleTaskEvidenceRetrievalProof = () => {
  if (!currentTaskDocument.value || currentTaskDocument.value.status !== 'COMPLETED') {
    ElMessage.warning('文档完成后才能检索验证')
    return
  }
  openRetrievalProofForDocument(currentTaskDocument.value)
}

const handleTaskEvidenceRetry = () => {
  if (!currentTaskDocument.value) return
  handleRetryFile(currentTaskDocument.value)
}

const copyTaskEvidence = async () => {
  try {
    await navigator.clipboard.writeText(taskEvidenceCopyText.value)
    ElMessage.success('任务证据已复制')
  } catch (error) {
    console.error('复制任务证据失败:', error)
    ElMessage.warning(taskEvidenceCopyText.value)
  }
}

const copyIngestionProbeCommand = async () => {
  try {
    await navigator.clipboard.writeText(ingestionProbeCommand)
    ElMessage.success('探针命令已复制')
  } catch (error) {
    console.error('复制探针命令失败:', error)
    ElMessage.warning(ingestionProbeCommand)
  }
}

const copyIngestionEvidenceChain = async () => {
  const lines = [
    '# Ingestion Evidence Chain',
    `- Knowledge Base: ${knowledgeDetail.value.name || kbId}`,
    `- Current Document: ${currentIngestionDocument.value?.docName || '-'}`,
    `- Latest Completed: ${latestCompletedDocument.value?.docName || '-'}`,
    `- Doc Parser: ${docParserHealth.value?.serviceId || 'agent-doc-parser'} / ${docParserHealth.value?.status || '-'}`,
    `- Probe: ${ingestionProbeCommand}`,
    '',
    ...ingestionEvidenceChain.value.map((item) => `- ${item.label}: ${item.value} (${item.hint})`)
  ]

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success('上传证据链已复制')
  } catch (error) {
    console.error('复制上传证据链失败:', error)
    ElMessage.warning(lines.join('\n'))
  }
}

// 编辑知识库
const handleEdit = () => {
  editForm.name = knowledgeDetail.value.name || ''
  editForm.description = knowledgeDetail.value.description || ''
  editForm.embeddingModel = knowledgeDetail.value.embeddingModel || 'text-embedding-3-small'
  editForm.chunkSize = knowledgeDetail.value.chunkSize || 500
  editForm.chunkOverlap = knowledgeDetail.value.chunkOverlap || 50
  editDialogVisible.value = true
}

const handleEditDialogClose = () => {
  editDialogVisible.value = false
  editFormRef.value?.resetFields()
}

const handleEditConfirm = async () => {
  if (!editFormRef.value) return

  try {
    await editFormRef.value.validate()
    editLoading.value = true

    await KnowledgeService.update(kbId, {
      name: editForm.name,
      description: editForm.description,
      embeddingModel: editForm.embeddingModel,
      chunkSize: editForm.chunkSize,
      chunkOverlap: editForm.chunkOverlap
    })

    ElMessage.success('知识库更新成功')
    editDialogVisible.value = false
    fetchKnowledgeDetail()
  } catch (error) {
    console.error('更新知识库失败:', error)
  } finally {
    editLoading.value = false
  }
}

// 上传知识
const handleUpload = () => {
  uploadDialogVisible.value = true
  loadDocParserHealth()
}

const handleUploadDialogClose = () => {
  uploadDialogVisible.value = false
  uploadFileList.value = []
}

const handleExceed = () => {
  ElMessage.warning('最多只能上传20个文件')
}

const removeFile = (index: number) => {
  uploadFileList.value.splice(index, 1)
}

const handleUploadConfirm = async () => {
  if (uploadFileList.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploadLoading.value = true

  try {
    const uploadedDocuments: DocType[] = []

    for (const file of uploadFileList.value) {
      if (file.raw) {
        const uploadedDocument = await DocumentService.upload(kbId, file.raw)
        uploadedDocuments.push(uploadedDocument)
      }
    }

    ElMessage.success(`成功上传 ${uploadFileList.value.length} 个文件`)
    uploadDialogVisible.value = false
    uploadFileList.value = []
    searchKeyword.value = ''
    currentPage.value = 1
    await fetchDocumentList()
    await focusUploadedDocuments(uploadedDocuments)
    syncProcessingPoll()
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error('上传失败，请重试')
  } finally {
    uploadLoading.value = false
  }
}

// 查看文件详情
const handleViewFile = (doc: DocType) => {
  router.push(`/kb/knowledge/detail/${kbId}/file/${doc.docId}/slices`)
}

// 重试文件
const handleRetryFile = async (doc: DocType) => {
  try {
    await DocumentService.reprocess(doc.docId)
    ElMessage.success(`文件「${doc.docName}」重新处理中...`)
    await fetchDocumentList()
    syncProcessingPoll()
  } catch (error) {
    console.error('重试失败:', error)
  }
}

const handleViewTasks = async (doc: DocType) => {
  currentTaskDocument.value = doc
  taskDrawerVisible.value = true
  taskLoading.value = true
  processingTasks.value = []

  try {
    processingTasks.value = await DocumentService.getTasks(doc.docId)
  } catch (error) {
    console.error('获取文档处理任务失败:', error)
    ElMessage.error('获取文档处理任务失败')
  } finally {
    taskLoading.value = false
  }
}

// 删除文件
const handleDeleteFile = async (doc: DocType) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件「${doc.docName}」吗？`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'error'
    })

    await DocumentService.delete(doc.docId)
    latestUploadedDocIds.value = latestUploadedDocIds.value.filter(docId => docId !== doc.docId)
    ElMessage.success('文件删除成功')
    fetchDocumentList()
  } catch {
    // 用户取消
  }
}

// 切换启用状态
const handleToggleEnabled = async (doc: DocType) => {
  try {
    await DocumentService.updateStatus(doc.docId, doc.isEnabled)
    ElMessage.success(`文件「${doc.docName}」已${doc.isEnabled ? '启用' : '禁用'}`)
  } catch (error) {
    doc.isEnabled = !doc.isEnabled
    console.error('更新状态失败:', error)
  }
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchDocumentList()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchDocumentList()
}

// 组件挂载时初始化数据
onMounted(() => {
  fetchKnowledgeDetail()
  fetchDocumentList()
  loadDocParserHealth()
})

onBeforeUnmount(() => {
  stopProcessingPoll()
})
</script>

<style lang="scss" scoped>
.knowledge-detail {
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color-page);
}

.breadcrumb-container {
  padding: 16px 24px;
  margin-bottom: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;

  .breadcrumb-link {
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      color: var(--el-color-primary-light-3);
    }
  }
}

.knowledge-detail-card {
  padding: 16px 24px;
  margin: 20px 24px 0;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgb(0 0 0 / 10%);

  .breadcrumb-link {
    color: #8b6914;
    text-decoration: none;

    &:hover {
      color: #ffc300;
    }
  }
}

.knowledge-detail-card {
  padding: 24px;
  margin-bottom: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;

  .detail-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 16px;

    .detail-info {
      flex: 1;

      .knowledge-title {
        margin: 0 0 12px;
        font-size: 24px;
        font-weight: 600;
        color: #131921;
      }

      .knowledge-meta {
        display: flex;
        gap: 8px;

        .meta-tag {
          padding: 4px 12px;
          font-size: 12px;
          color: #666;
          background: #f0f0f0;
          border-radius: 12px;
        }
      }
    }

    .detail-actions {
      display: flex;
      gap: 12px;

      .edit-btn {
        padding: 8px 16px;
        color: #131921;
        background: #fff;
        border: 1px solid #ddd;
        border-radius: 6px;

        &:hover {
          color: #f9c924;
          border-color: #f9c924;
        }
      }

      .upload-btn {
        padding: 8px 16px;
        font-weight: 600;
        color: #131921;
        background: #ffc300;
        border-color: #ffc300;
        border-radius: 6px;

        &:hover {
          background: #f0e68c;
          border-color: #f0e68c;
        }
      }
    }
  }

  .knowledge-description {
    margin: 0;
    font-size: 14px;
    line-height: 1.6;
    color: #666;
  }

  .rag-config-bar {
    display: flex;
    gap: 0;
    align-items: center;
    padding: 12px 16px;
    margin-top: 16px;
    background: #f8f9fa;
    border: 1px solid #e9ecef;
    border-radius: 8px;

    .config-item {
      display: flex;
      gap: 8px;
      align-items: center;
      padding: 0 16px;

      .config-label {
        font-size: 12px;
        font-weight: 500;
        color: #999;
        white-space: nowrap;
      }

      .config-value {
        font-size: 13px;
        font-weight: 600;
        color: #333;
        white-space: nowrap;
      }
    }

    .config-divider {
      width: 1px;
      height: 20px;
      background: #ddd;
    }
  }

  .doc-parser-health {
    display: grid;
    grid-template-columns: minmax(150px, auto) 1fr auto;
    gap: 12px;
    align-items: center;
    padding: 10px 12px;
    margin-top: 12px;
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;

    .doc-parser-health-main {
      display: inline-flex;
      gap: 6px;
      align-items: center;
      min-width: 0;
      font-size: 13px;
      font-weight: 650;
      white-space: nowrap;
    }

    .doc-parser-health-desc {
      min-width: 0;
      overflow: hidden;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    &.is-ready {
      color: var(--el-color-success);
      background: var(--el-color-success-light-9);
      border-color: var(--el-color-success-light-7);
    }

    &.is-down {
      color: var(--el-color-warning);
      background: var(--el-color-warning-light-9);
      border-color: var(--el-color-warning-light-7);
    }

    &.is-checking {
      color: var(--el-color-info);
      background: var(--el-fill-color-lighter);
    }
  }
}

.ingestion-workbench {
  padding: 20px;
  margin-bottom: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.ingestion-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 19px;
    font-weight: 700;
    color: var(--el-text-color-primary);
    letter-spacing: 0;
  }

  p {
    max-width: 760px;
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 1.55;
    color: var(--el-text-color-secondary);
  }
}

.ingestion-kicker {
  margin: 0 0 5px !important;
  font-size: 12px !important;
  font-weight: 700;
  color: #1f8a70 !important;
  text-transform: uppercase;
  letter-spacing: 0;
}

.ingestion-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.ingestion-metric {
  min-height: 92px;
  padding: 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  span {
    display: block;
    font-size: 12px;
    font-weight: 700;
    line-height: 1.2;
    color: var(--el-text-color-secondary);
  }

  strong {
    display: block;
    margin-top: 8px;
    font-size: 24px;
    font-weight: 750;
    line-height: 1.1;
    color: var(--el-text-color-primary);
    font-variant-numeric: tabular-nums;
  }

  small {
    display: block;
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.35;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }

  &.tone-green {
    border-color: rgba(31, 138, 112, 0.24);
    background: rgba(31, 138, 112, 0.04);

    strong {
      color: #1f8a70;
    }
  }

  &.tone-blue {
    border-color: rgba(47, 128, 237, 0.22);
    background: rgba(47, 128, 237, 0.03);

    strong {
      color: #2f80ed;
    }
  }

  &.tone-amber {
    border-color: rgba(183, 121, 31, 0.24);
    background: rgba(183, 121, 31, 0.04);

    strong {
      color: #b7791f;
    }
  }

  &.tone-red {
    border-color: rgba(245, 108, 108, 0.24);
    background: rgba(245, 108, 108, 0.04);

    strong {
      color: var(--el-color-danger);
    }
  }
}

.ingestion-evidence-chain {
  margin-bottom: 14px;
  padding: 14px;
  background: rgba(31, 138, 112, 0.04);
  border: 1px solid rgba(31, 138, 112, 0.2);
  border-radius: 8px;
}

.evidence-chain-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  span {
    display: block;
    font-size: 14px;
    font-weight: 700;
    line-height: 1.3;
    color: var(--el-text-color-primary);
  }

  p {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }
}

.evidence-chain-track {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}

.evidence-chain-step {
  min-width: 0;
  min-height: 88px;
  padding: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  &.ready {
    border-color: rgba(31, 138, 112, 0.24);
  }

  &.running {
    border-color: rgba(183, 121, 31, 0.28);
  }

  &.blocked {
    border-color: rgba(245, 108, 108, 0.28);
  }

  span,
  small {
    display: block;
    min-width: 0;
    overflow: hidden;
    font-size: 12px;
    line-height: 1.3;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    display: block;
    min-width: 0;
    margin: 8px 0 6px;
    overflow: hidden;
    font-size: 13px;
    font-weight: 700;
    line-height: 1.3;
    color: #1f8a70;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.ingestion-body {
  display: grid;
  grid-template-columns: minmax(320px, 0.82fr) minmax(0, 1.18fr);
  gap: 14px;
  align-items: stretch;
}

.ingestion-current {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 300px;
  padding: 16px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.ingestion-current-title {
  display: flex;
  gap: 12px;
  align-items: flex-start;

  .el-icon {
    display: inline-flex;
    flex: 0 0 38px;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    font-size: 18px;
    color: #1f8a70;
    background: rgba(31, 138, 112, 0.1);
    border-radius: 8px;
  }

  span {
    display: block;
    min-width: 0;
    font-size: 15px;
    font-weight: 700;
    line-height: 1.4;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }

  p {
    margin: 7px 0 0;
    font-size: 13px;
    line-height: 1.55;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }
}

.ingestion-current-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  div {
    min-height: 68px;
    padding: 10px;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
  }

  span {
    display: block;
    font-size: 12px;
    font-weight: 700;
    line-height: 1.2;
    color: var(--el-text-color-secondary);
  }

  strong {
    display: block;
    min-width: 0;
    margin-top: 8px;
    font-size: 13px;
    font-weight: 700;
    line-height: 1.35;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }
}

.ingestion-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: auto;
}

.ingestion-stage-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.ingestion-stage {
  min-height: 300px;
  padding: 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  &.is-done {
    border-color: rgba(31, 138, 112, 0.26);
  }

  &.is-running {
    border-color: rgba(183, 121, 31, 0.3);
  }

  &.is-failed {
    border-color: rgba(245, 108, 108, 0.3);
  }

  h3 {
    margin: 12px 0 0;
    font-size: 15px;
    font-weight: 700;
    line-height: 1.35;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }

  p {
    min-height: 76px;
    margin: 10px 0 12px;
    font-size: 13px;
    line-height: 1.55;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }

  code {
    display: block;
    padding: 8px;
    font-size: 12px;
    line-height: 1.45;
    color: #1f8a70;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    overflow-wrap: anywhere;
  }
}

.ingestion-stage-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;

  .el-icon {
    flex: 0 0 auto;
    font-size: 18px;
    color: #2f80ed;
  }
}

.knowledge-content {
  padding: 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  text-align: center;

  .empty-icon {
    margin-bottom: 16px;
    font-size: 64px;
    color: #ddd;
  }

  .empty-text {
    margin: 0 0 24px;
    font-size: 16px;
    color: #999;
  }

  .upload-btn-empty {
    padding: 8px 16px;
    color: #131921;
    background: #fff;
    border: 1px solid #ddd;
    border-radius: 6px;

    &:hover {
      color: #f9c924;
      border-color: #f9c924;
    }
  }
}

// 文件表格容器
.file-table-container {
  .table-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 0;
    margin-bottom: 16px;
    border-bottom: 1px solid #e5e5e5;

    .search-section {
      .search-input {
        width: 300px;
      }
    }

    .processing-watch {
      display: inline-flex;
      gap: 6px;
      align-items: center;
      min-height: 28px;
      padding: 0 10px;
      font-size: 12px;
      font-weight: 600;
      color: var(--el-color-warning);
      background: var(--el-color-warning-light-9);
      border: 1px solid var(--el-color-warning-light-7);
      border-radius: 6px;
      white-space: nowrap;
    }
  }

  .file-table {
    margin-bottom: 16px;

    .file-name-cell {
      display: flex;
      gap: 12px;
      align-items: center;

      .file-icon-wrapper {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        background: #f0f0f0;
        border-radius: 6px;

        .file-icon {
          font-size: 16px;
          color: #666;
        }
      }

      .file-name {
        max-width: 200px;
        overflow: hidden;
        font-size: 14px;
        font-weight: 500;
        color: #131921;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .latest-upload-tag {
        flex: 0 0 auto;
      }
    }

    .chunk-count,
    .token-count {
      font-size: 14px;
      font-weight: 500;
      color: #131921;
    }

    .progress-cell {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .progress-status {
        display: flex;
        gap: 8px;
        align-items: center;

        .status-icon {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 20px;
          height: 20px;
          font-size: 12px;
          border-radius: 50%;
        }

        .status-text {
          font-size: 12px;
          font-weight: 500;
          color: #666;
        }
      }

      .progress-bar {
        width: 100%;
        margin-top: 4px;
      }
    }

    .action-buttons {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    :deep(.el-table__row.is-latest-upload td.el-table__cell) {
      background: rgba(31, 138, 112, 0.05);
    }

    :deep(.el-table__row.is-latest-upload:hover td.el-table__cell) {
      background: rgba(31, 138, 112, 0.08);
    }
  }

  .table-pagination {
    display: flex;
    justify-content: center;
    padding: 16px 0;
    border-top: 1px solid #e5e5e5;
  }
}

// 上传对话框样式
.upload-dialog {
  :deep(.el-dialog) {
    overflow: hidden;
    border-radius: 12px;
    box-shadow: 0 20px 40px rgb(0 0 0 / 15%);

    .el-dialog__header {
      display: none;
    }

    .el-dialog__body {
      padding: 0;
    }
  }

  .upload-dialog-content {
    .dialog-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 24px 32px;
      background: #fff;
      border-bottom: 1px solid #e5e5e5;

      .dialog-title {
        margin: 0;
        font-size: 20px;
        font-weight: 600;
        color: #131921;
      }

      .close-btn {
        color: #666;
        background: #f5f5f5;
        border: none;

        &:hover {
          background: #e5e5e5;
        }
      }
    }

    .upload-area {
      padding: 32px;

      .upload-health-alert {
        margin-bottom: 16px;
      }

      .file-uploader {
        :deep(.el-upload-dragger) {
          width: 100%;
          height: 200px;
          background: #fafafa;
          border: 2px dashed #ffc300;
          border-radius: 8px;
          transition: all 0.3s ease;

          &:hover {
            background: rgb(255 195 0 / 5%);
            border-color: #e6b420;
          }

          .upload-dragger {
            display: flex;
            flex-direction: column;
            gap: 16px;
            align-items: center;
            justify-content: center;
            height: 100%;

            .upload-icon-large {
              font-size: 48px;
              color: #ffc300;
            }

            .upload-text {
              text-align: center;

              .upload-title {
                margin: 0;
                font-size: 16px;
                color: #131921;

                .highlight {
                  font-weight: 600;
                  color: #ffc300;
                  text-decoration: underline;
                }
              }
            }
          }
        }
      }

      .upload-limit {
        margin: 12px 0 0;
        font-size: 14px;
        color: #666;
        text-align: center;
      }
    }

    .file-list {
      padding: 0 32px 32px;

      .file-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 12px 16px;
        margin-bottom: 8px;
        background: #fff;
        border: 1px solid #e5e5e5;
        border-radius: 8px;

        .file-info {
          display: flex;
          flex: 1;
          gap: 12px;
          align-items: center;

          .file-icon {
            display: flex;
            align-items: center;
            justify-content: center;
            width: 32px;
            height: 32px;
            font-size: 16px;
            color: #fff;
            background: #409eff;
            border-radius: 6px;
          }

          .file-details {
            flex: 1;

            .file-name {
              font-size: 14px;
              font-weight: 500;
              color: #131921;
            }
          }
        }

        .file-actions {
          .delete-btn {
            color: #666;
            background: #f0f0f0;
            border: none;

            &:hover {
              color: #fff;
              background: #f56c6c;
            }
          }
        }
      }
    }

    .dialog-footer {
      display: flex;
      gap: 12px;
      align-items: center;
      justify-content: flex-end;
      padding: 20px 32px;
      background: #fff;
      border-top: 1px solid #e5e5e5;

      .cancel-btn {
        padding: 8px 16px;
        color: #666;
        background: #fff;
        border: 1px solid #ddd;
        border-radius: 8px;

        &:hover {
          color: #131921;
          border-color: #131921;
        }
      }

      .confirm-btn {
        padding: 8px 16px;
        font-weight: 600;
        color: #131921;
        background: #f9c924;
        border-color: #f9c924;
        border-radius: 8px;

        &:hover {
          background: #e6b420;
          border-color: #e6b420;
        }
      }
    }
  }
}

// 表单提示文字
.form-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-placeholder);
}

// 状态样式
.status-completed {
  color: #67c23a;

  .status-icon {
    color: #67c23a;
    background: rgb(103 194 58 / 10%);
  }
}

.status-failed {
  color: #f56c6c;

  .status-icon {
    color: #f56c6c;
    background: rgb(245 108 108 / 10%);
  }
}

.status-parsing,
.status-chunking,
.status-embedding,
.status-pending {
  color: #e6a23c;

  .status-icon {
    color: #e6a23c;
    background: rgb(230 162 60 / 10%);
  }
}

:deep(.task-drawer) {
  .el-drawer__body {
    padding: 20px 24px;
  }
}

.task-document {
  padding: 14px 16px;
  margin-bottom: 18px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;

  .task-document-name {
    font-size: 15px;
    font-weight: 650;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }

  .task-document-meta {
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }
}

.task-evidence-panel {
  padding: 14px;
  margin-bottom: 18px;
  background: rgba(31, 138, 112, 0.04);
  border: 1px solid rgba(31, 138, 112, 0.2);
  border-radius: 8px;
}

.task-evidence-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;

  span {
    display: block;
    font-size: 14px;
    font-weight: 700;
    line-height: 1.3;
    color: var(--el-text-color-primary);
  }

  p {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }
}

.task-evidence-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.task-evidence-stat {
  min-height: 78px;
  padding: 10px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  span {
    display: block;
    font-size: 12px;
    font-weight: 700;
    line-height: 1.2;
    color: var(--el-text-color-secondary);
  }

  strong {
    display: block;
    min-width: 0;
    margin-top: 8px;
    font-size: 13px;
    font-weight: 750;
    line-height: 1.35;
    color: var(--el-text-color-primary);
    overflow-wrap: anywhere;
  }

  small {
    display: block;
    margin-top: 6px;
    font-size: 12px;
    line-height: 1.35;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }
}

.task-evidence-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.task-loading {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: var(--el-text-color-secondary);
}

.pipeline-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 18px;
}

.pipeline-step {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 10px;
  align-items: flex-start;
  min-height: 52px;
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;

  .pipeline-marker {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    font-size: 12px;
    font-weight: 700;
    color: var(--el-text-color-secondary);
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color);
    border-radius: 50%;
  }

  .pipeline-content {
    min-width: 0;
  }

  .pipeline-title-row {
    display: flex;
    gap: 8px;
    align-items: center;
    justify-content: space-between;
  }

  .pipeline-title {
    min-width: 0;
    overflow: hidden;
    font-size: 14px;
    font-weight: 650;
    color: var(--el-text-color-primary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .pipeline-description {
    margin-top: 5px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }

  &.is-done {
    border-color: var(--el-color-success-light-7);

    .pipeline-marker {
      color: var(--el-color-success);
      background: var(--el-color-success-light-9);
      border-color: var(--el-color-success-light-7);
    }
  }

  &.is-running {
    border-color: var(--el-color-warning-light-7);

    .pipeline-marker {
      color: var(--el-color-warning);
      background: var(--el-color-warning-light-9);
      border-color: var(--el-color-warning-light-7);
    }
  }

  &.is-failed {
    border-color: var(--el-color-danger-light-7);

    .pipeline-marker {
      color: var(--el-color-danger);
      background: var(--el-color-danger-light-9);
      border-color: var(--el-color-danger-light-7);
    }
  }
}

.task-timeline {
  padding-left: 2px;

  .task-item {
    padding: 12px 14px;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
  }

  .task-item-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }

  .task-phase {
    font-size: 14px;
    font-weight: 650;
    color: var(--el-text-color-primary);
  }

  .task-message {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
    word-break: break-word;
  }

  .task-parser-snapshot {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;

    span {
      max-width: 100%;
      padding: 2px 6px;
      font-size: 12px;
      line-height: 18px;
      color: var(--el-color-primary);
      word-break: break-all;
      background: var(--el-color-primary-light-9);
      border: 1px solid var(--el-color-primary-light-7);
      border-radius: 4px;
    }
  }
}

@media (max-width: 1280px) {
  .ingestion-body {
    grid-template-columns: 1fr;
  }

  .evidence-chain-track {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .ingestion-stage-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .knowledge-detail {
    padding: 12px;
  }

  .ingestion-heading,
  .evidence-chain-heading,
  .knowledge-detail-card .detail-header,
  .file-table-container .table-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .ingestion-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ingestion-current-meta {
    grid-template-columns: 1fr;
  }

  .knowledge-detail-card .rag-config-bar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;

    .config-divider {
      width: 100%;
      height: 1px;
    }
  }
}

@media (max-width: 640px) {
  .ingestion-metrics,
  .evidence-chain-track,
  .ingestion-stage-grid {
    grid-template-columns: 1fr;
  }

  .knowledge-content,
  .knowledge-detail-card,
  .ingestion-workbench {
    padding: 16px;
  }

  .file-table-container .table-toolbar .search-section .search-input {
    width: 100%;
  }
}

// 按钮样式优化
:deep(.el-button) {
  font-weight: 500;
  border-radius: 6px;

  &.el-button--primary {
    color: #131921;
    background: #f9c924;
    border-color: #f9c924;

    &:hover {
      background: #e6b420;
      border-color: #e6b420;
    }
  }
}
</style>
