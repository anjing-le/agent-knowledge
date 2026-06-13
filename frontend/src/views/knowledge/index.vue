<template>
  <div class="knowledge-management">
    <div class="workspace-header">
      <div class="header-content">
        <div class="header-left">
          <p class="page-kicker">RAG Workspace</p>
          <h1 class="page-title">知识库工作台</h1>
          <p class="page-subtitle">管理文档、切片和向量化状态，并把可信引用交给问答链路。</p>
        </div>
        <div class="header-right">
          <el-button class="retrieval-btn" @click="handleGoRetrieval">
            <el-icon><DataAnalysis /></el-icon>
            检索调试
          </el-button>
          <el-button class="chat-btn" @click="handleGoChat">
            <el-icon><ChatLineRound /></el-icon>
            知识问答
          </el-button>
          <el-button type="primary" class="create-btn" @click="handleCreateKnowledge">
            <el-icon><Plus /></el-icon>
            新建知识库
          </el-button>
        </div>
      </div>

      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon><FolderOpened /></el-icon>
          </div>
          <div>
            <div class="stat-value">{{ workspaceStats.totalBases }}</div>
            <div class="stat-label">知识库</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon active">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div>
            <div class="stat-value">{{ workspaceStats.enabledBases }}</div>
            <div class="stat-label">已启用</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div>
            <div class="stat-value">{{ workspaceStats.documents }}</div>
            <div class="stat-label">文档</div>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon">
            <el-icon><DataAnalysis /></el-icon>
          </div>
          <div>
            <div class="stat-value">{{ workspaceStats.chunks }}</div>
            <div class="stat-label">切片</div>
          </div>
        </div>
      </div>
    </div>

    <div class="content-area">
      <div class="knowledge-container">
        <div class="section-header">
          <div>
            <h2>知识库</h2>
            <p>每个知识库都保留自己的 chunk 策略、Embedding 模型和检索边界。</p>
          </div>
        </div>

        <div class="knowledge-list">
          <div
            v-for="knowledge in knowledgeList"
            :key="knowledge.kbId"
            class="knowledge-card"
            :class="{ disabled: !knowledge.isEnabled }"
            @click="handleCardClick(knowledge)"
          >
            <div class="card-header" @click.stop>
              <h3 class="knowledge-title">{{ knowledge.name }}</h3>
              <div class="card-actions">
                <el-switch
                  v-model="knowledge.isEnabled"
                  @change="handleToggleActive(knowledge)"
                  class="knowledge-switch"
                />
                <el-dropdown
                  v-if="knowledge.isEnabled"
                  trigger="click"
                  class="more-actions"
                  @command="(command: string) => handleDropdownCommand(command, knowledge)"
                >
                  <el-icon class="more-icon"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit" :icon="Edit">编辑</el-dropdown-item>
                      <el-dropdown-item command="delete" :icon="Delete" class="delete-item"
                        >删除</el-dropdown-item
                      >
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <p class="knowledge-description">{{ knowledge.description }}</p>

            <div class="knowledge-meta">
              <span class="meta-item">{{ knowledge.documentCount || 0 }}个文档</span>
              <span class="meta-divider">|</span>
              <span class="meta-item">{{ knowledge.chunkCount || 0 }}个分片</span>
              <span class="meta-divider">|</span>
              <span class="meta-item">{{ knowledge.createdBy || '系统' }}</span>
              <span class="meta-divider">|</span>
              <span class="meta-item">{{ formatDate(knowledge.createdAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 分页区域 -->
        <div class="pagination-container">
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

    <!-- 新建知识库对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      title="新建知识库"
      width="600px"
      :before-close="handleCreateDialogClose"
      class="create-dialog"
    >
      <div class="create-content">
        <el-form
          ref="createFormRef"
          :model="createForm"
          :rules="formRules"
          label-width="100px"
          label-position="left"
        >
          <el-form-item label="知识库名称" prop="name">
            <el-input
              v-model="createForm.name"
              placeholder="请输入知识库名称"
              maxlength="50"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="知识库描述" prop="description">
            <el-input
              v-model="createForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入知识库描述"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-divider content-position="left">RAG 配置</el-divider>

          <el-form-item label="Embedding 模型">
            <el-select v-model="createForm.embeddingModel" placeholder="选择向量化模型" style="width: 100%">
              <el-option label="text-embedding-3-small (1536维)" value="text-embedding-3-small" />
              <el-option label="text-embedding-3-large (3072维)" value="text-embedding-3-large" />
              <el-option label="text-embedding-ada-002 (1536维)" value="text-embedding-ada-002" />
            </el-select>
          </el-form-item>

          <el-form-item label="分块大小">
            <el-input-number
              v-model="createForm.chunkSize"
              :min="100"
              :max="4000"
              :step="100"
              style="width: 100%"
            />
            <div class="form-tip">每个分块的最大字符数，推荐 300~800</div>
          </el-form-item>

          <el-form-item label="重叠大小">
            <el-input-number
              v-model="createForm.chunkOverlap"
              :min="0"
              :max="500"
              :step="10"
              style="width: 100%"
            />
            <div class="form-tip">相邻分块间的重叠字符数，推荐为分块大小的 10%</div>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleCreateDialogClose">取消</el-button>
          <el-button type="primary" @click="handleCreateConfirm" :loading="createLoading">
            创建知识库
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  ChatLineRound,
  CircleCheck,
  DataAnalysis,
  Document,
  FolderOpened,
  MoreFilled,
  Edit,
  Delete,
  Plus
} from '@element-plus/icons-vue'
import { KnowledgeService, type KnowledgeBase } from '@/api/knowledge'

// 路由
const router = useRouter()

// 响应式数据
const knowledgeList = ref<KnowledgeBase[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const workspaceStats = computed(() => {
  return knowledgeList.value.reduce(
    (stats, item) => {
      stats.totalBases += 1
      stats.enabledBases += item.isEnabled ? 1 : 0
      stats.documents += item.documentCount || 0
      stats.chunks += item.chunkCount || 0
      return stats
    },
    {
      totalBases: 0,
      enabledBases: 0,
      documents: 0,
      chunks: 0
    }
  )
})

// 编辑对话框相关
const editDialogVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  kbId: '',
  name: '',
  description: '',
  embeddingModel: 'text-embedding-3-small',
  chunkSize: 500,
  chunkOverlap: 50
})

// 新建对话框相关
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
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

// 格式化日期
const formatDate = (dateStr?: string) => {
  if (!dateStr) return '未知'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

// 获取知识库列表
const fetchKnowledgeList = async () => {
  loading.value = true
  try {
    const res = await KnowledgeService.getList({
      page: currentPage.value,
      size: pageSize.value
    })
    if (res) {
      knowledgeList.value = res.records || []
      total.value = res.total || 0
    }
  } catch (error) {
    console.error('获取知识库列表失败:', error)
    ElMessage.error('获取知识库列表失败')
  } finally {
    loading.value = false
  }
}

// 新建知识库
const handleCreateKnowledge = () => {
  createDialogVisible.value = true
}

const handleGoChat = () => {
  router.push('/kb/chat')
}

const handleGoRetrieval = () => {
  router.push('/kb/retrieval')
}

// 知识库卡片点击跳转
const handleCardClick = (knowledge: KnowledgeBase) => {
  if (!knowledge.isEnabled) {
    ElMessage.warning('该知识库已禁用，无法进入')
    return
  }
  router.push(`/kb/knowledge/detail/${knowledge.kbId}`)
}

// 切换知识库状态
const handleToggleActive = async (knowledge: KnowledgeBase) => {
  if (!knowledge.isEnabled) {
    try {
      await ElMessageBox.confirm(
        `确定要禁用知识库「${knowledge.name}」吗？`,
        '禁用确认',
        {
          confirmButtonText: '确定禁用',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      await KnowledgeService.update(knowledge.kbId, { isEnabled: false })
      ElMessage.success(`知识库「${knowledge.name}」已禁用`)
    } catch {
      knowledge.isEnabled = true
    }
  } else {
    try {
      await KnowledgeService.update(knowledge.kbId, { isEnabled: true })
      ElMessage.success(`知识库「${knowledge.name}」已启用`)
    } catch {
      knowledge.isEnabled = false
    }
  }
}

// 下拉菜单命令处理
const handleDropdownCommand = (command: string, knowledge: KnowledgeBase) => {
  switch (command) {
    case 'edit':
      handleEdit(knowledge)
      break
    case 'delete':
      handleDelete(knowledge)
      break
  }
}

// 编辑知识库
const handleEdit = (knowledge: KnowledgeBase) => {
  editForm.kbId = knowledge.kbId
  editForm.name = knowledge.name
  editForm.description = knowledge.description
  editForm.embeddingModel = knowledge.embeddingModel || 'text-embedding-3-small'
  editForm.chunkSize = knowledge.chunkSize || 500
  editForm.chunkOverlap = knowledge.chunkOverlap || 50
  editDialogVisible.value = true
}

// 编辑对话框关闭
const handleEditDialogClose = () => {
  editDialogVisible.value = false
  editFormRef.value?.resetFields()
}

// 编辑确认
const handleEditConfirm = async () => {
  if (!editFormRef.value) return

  try {
    await editFormRef.value.validate()
    editLoading.value = true

    await KnowledgeService.update(editForm.kbId, {
      name: editForm.name,
      description: editForm.description,
      embeddingModel: editForm.embeddingModel,
      chunkSize: editForm.chunkSize,
      chunkOverlap: editForm.chunkOverlap
    })

    ElMessage.success('知识库更新成功')
    editDialogVisible.value = false
    fetchKnowledgeList()
  } catch (error) {
    console.error('更新知识库失败:', error)
  } finally {
    editLoading.value = false
  }
}

// 删除知识库
const handleDelete = async (knowledge: KnowledgeBase) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除知识库「${knowledge.name}」吗？删除后将无法恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    await KnowledgeService.delete(knowledge.kbId)
    ElMessage.success('知识库删除成功')
    fetchKnowledgeList()
  } catch {
    // 用户取消
  }
}

// 新建对话框关闭
const handleCreateDialogClose = () => {
  createDialogVisible.value = false
  createFormRef.value?.resetFields()
  Object.assign(createForm, { name: '', description: '', embeddingModel: 'text-embedding-3-small', chunkSize: 500, chunkOverlap: 50 })
}

// 新建确认
const handleCreateConfirm = async () => {
  if (!createFormRef.value) return

  try {
    await createFormRef.value.validate()
    createLoading.value = true

    const res = await KnowledgeService.create({
      name: createForm.name,
      description: createForm.description,
      embeddingModel: createForm.embeddingModel,
      chunkSize: createForm.chunkSize,
      chunkOverlap: createForm.chunkOverlap
    })

    ElMessage.success('知识库创建成功')
    createDialogVisible.value = false

    if (res?.kbId) {
      router.push(`/kb/knowledge/detail/${res.kbId}`)
    } else {
      fetchKnowledgeList()
    }
  } catch (error) {
    console.error('创建知识库失败:', error)
  } finally {
    createLoading.value = false
  }
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchKnowledgeList()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchKnowledgeList()
}

// 组件挂载时初始化数据
onMounted(() => {
  fetchKnowledgeList()
})
</script>

<style lang="scss" scoped>
.knowledge-management {
  min-height: 100%;
  padding: 20px;
  background: var(--el-bg-color-page);
}

.workspace-header {
  padding: 20px 24px;
  margin-bottom: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  box-shadow: 0 2px 4px rgb(0 0 0 / 5%);

  .header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    margin-bottom: 20px;

    .header-left {
      min-width: 0;
    }

    .page-kicker {
      margin: 0 0 6px;
      font-size: 12px;
      font-weight: 700;
      color: var(--el-color-primary);
      letter-spacing: 0;
      text-transform: uppercase;
    }

    .page-title {
      margin: 0;
      font-size: 24px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .page-subtitle {
      margin: 8px 0 0;
      font-size: 14px;
      line-height: 1.6;
      color: var(--el-text-color-secondary);
    }

    .header-right {
      display: flex;
      flex-shrink: 0;
      gap: 12px;
      align-items: center;
    }

    .retrieval-btn,
    .chat-btn,
    .create-btn {
      padding: 10px 20px;
      font-weight: 600;
    }
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;

    .stat-item {
      display: flex;
      gap: 12px;
      align-items: center;
      min-height: 74px;
      padding: 14px 16px;
      background: var(--el-fill-color-lighter);
      border: 1px solid var(--el-border-color-light);
      border-radius: 8px;
    }

    .stat-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
      border-radius: 8px;

      &.active {
        color: var(--el-color-success);
        background: var(--el-color-success-light-9);
      }
    }

    .stat-value {
      font-size: 22px;
      font-weight: 700;
      line-height: 1.1;
      color: var(--el-text-color-primary);
    }

    .stat-label {
      margin-top: 4px;
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }
}

.content-area {
  .knowledge-container {
    padding: 24px;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
    box-shadow: 0 2px 4px rgb(0 0 0 / 5%);
  }
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;

  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 650;
    color: var(--el-text-color-primary);
  }

  p {
    margin: 6px 0 0;
    font-size: 13px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }
}

.knowledge-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.knowledge-card {
  padding: 24px;
  cursor: pointer;
  background: var(--el-color-warning-light-9);
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 5%);
  transition: all 0.3s ease;

  &:hover {
    border-color: var(--el-color-warning);
    box-shadow: 0 4px 16px rgb(0 0 0 / 10%);
    transform: translateY(-2px);
  }

  &.disabled {
    background: var(--el-fill-color-light);
    border-color: var(--el-border-color);
    opacity: 0.6;

    &:hover {
      border-color: var(--el-border-color);
      box-shadow: 0 2px 8px rgb(0 0 0 / 5%);
      transform: none;
    }
  }

  .card-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 16px;

    .knowledge-title {
      flex: 1;
      margin: 0;
      margin-right: 16px;
      font-size: 18px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }

    .card-actions {
      display: flex;
      gap: 12px;
      align-items: center;

      .more-actions {
        padding: 4px;
        cursor: pointer;
        border-radius: 4px;
        transition: all 0.3s ease;

        &:hover {
          background: var(--el-fill-color);
        }

        .more-icon {
          font-size: 16px;
          color: var(--el-text-color-secondary);
        }
      }
    }
  }

  .knowledge-description {
    display: -webkit-box;
    margin: 0 0 16px;
    overflow: hidden;
    font-size: 14px;
    line-height: 1.6;
    color: var(--el-text-color-regular);
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
  }

  .knowledge-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    .meta-divider {
      color: var(--el-border-color);
    }
  }
}

// 分页区域样式
.pagination-container {
  display: flex;
  justify-content: center;
  padding: 16px 0;
  border-top: 1px solid var(--el-border-color-light);
}

// 删除按钮样式
:deep(.delete-item) {
  color: var(--el-color-danger) !important;

  &:hover {
    color: var(--el-color-danger) !important;
    background-color: var(--el-color-danger-light-9) !important;
  }
}

// 对话框样式
:deep(.el-dialog) {
  border-radius: 12px;

  .el-dialog__header {
    padding: 20px 24px 16px;
    border-bottom: 1px solid var(--el-border-color-light);

    .el-dialog__title {
      font-size: 18px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }
  }

  .el-dialog__body {
    padding: 24px;
  }

  .el-dialog__footer {
    padding: 16px 24px 20px;
    text-align: right;
    border-top: 1px solid var(--el-border-color-light);

    .dialog-footer {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
    }
  }
}

// 表单样式优化
:deep(.el-form) {
  .el-form-item__label {
    font-weight: 500;
    color: var(--el-text-color-primary);
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

// 新建知识库对话框样式
:deep(.create-dialog) {
  .el-dialog__body {
    padding: 0;
  }

  .create-content {
    padding: 24px;
  }
}

@media (width <= 900px) {
  .workspace-header {
    .header-content {
      align-items: flex-start;
      flex-direction: column;
    }

    .stats-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
}

@media (width <= 560px) {
  .workspace-header {
    .header-content {
      .header-right {
        width: 100%;
      }

      .retrieval-btn,
      .chat-btn,
      .create-btn {
        flex: 1;
        padding-right: 12px;
        padding-left: 12px;
      }
    }

    .stats-grid {
      grid-template-columns: 1fr;
    }
  }

  .knowledge-list {
    grid-template-columns: 1fr;
  }
}
</style>
