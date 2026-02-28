<template>
  <div class="file-slices">
    <!-- 面包屑导航 -->
    <div class="breadcrumb-container">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>
          <router-link to="/kb/knowledge" class="breadcrumb-link">知识管理</router-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item>
          <router-link :to="`/kb/knowledge/detail/${kbId}`" class="breadcrumb-link"
            >知识库详情</router-link
          >
        </el-breadcrumb-item>
        <el-breadcrumb-item>知识切片</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 文件信息卡片 -->
    <div class="file-info-card">
      <div class="file-header">
        <div class="file-info">
          <div class="file-icon-name">
            <el-icon class="file-icon"><Document /></el-icon>
            <span class="file-name">{{ docInfo.docName || '加载中...' }}</span>
          </div>
        </div>
        <div class="file-actions">
          <el-button 
            type="primary" 
            :icon="RefreshRight" 
            @click="fetchChunks"
            :loading="loading"
          >
            刷新
          </el-button>
        </div>
      </div>
      <div class="file-meta">
        <div class="meta-item">
          <span class="meta-label">切片数量:</span>
          <span class="meta-value">{{ docInfo.chunkNum || 0 }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">Token 总数:</span>
          <span class="meta-value">{{ docInfo.tokenNum || 0 }}</span>
        </div>
        <div class="meta-item">
          <span class="meta-label">状态:</span>
          <el-tag :type="getStatusType(docInfo.status)" size="small">
            {{ getStatusText(docInfo.status) }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 双栏内容区域 -->
    <div class="slices-main-content">
      <!-- 左侧：文件原始内容预览 -->
      <div class="preview-panel">
        <div class="panel-header">
          <h3 class="panel-title">
            <el-icon><DocumentCopy /></el-icon>
            原始内容
          </h3>
          <div class="panel-actions">
            <el-tooltip content="点击右侧切片可定位到对应内容" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </div>
        <div class="preview-content" ref="previewContentRef">
          <div v-if="loadingPreview" class="loading-placeholder">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载原始内容中...</span>
          </div>
          <div v-else-if="!originalContent" class="empty-preview">
            <el-icon><Document /></el-icon>
            <span>暂无原始内容预览</span>
          </div>
          <div v-else class="content-wrapper">
            <!-- 逐段渲染内容，支持高亮 -->
            <div 
              v-for="(segment, index) in contentSegments" 
              :key="index"
              :id="`segment-${index}`"
              class="content-segment"
              :class="{ 
                'highlighted': highlightedChunkIndex === segment.chunkIndex,
                'has-chunk': segment.chunkIndex !== null
              }"
              @click="segment.chunkIndex !== null && scrollToChunk(segment.chunkIndex)"
            >
              <span class="segment-text">{{ segment.text }}</span>
              <span v-if="segment.chunkIndex !== null" class="chunk-badge">
                #{{ segment.chunkIndex + 1 }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：切片列表 -->
      <div class="slices-panel">
        <div class="panel-header">
          <h3 class="panel-title">
            <el-icon><Collection /></el-icon>
            切片列表
          </h3>
          <div class="search-section">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索切片内容"
              class="search-input"
              clearable
              size="small"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
        </div>

        <!-- 切片列表 -->
        <div class="slices-list" ref="slicesListRef">
          <div v-if="loading" class="loading-placeholder">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载切片中...</span>
          </div>
          <div v-else-if="filteredChunks.length === 0" class="empty-slices">
            <el-icon><Box /></el-icon>
            <span>{{ searchKeyword ? '未找到匹配的切片' : '暂无切片数据' }}</span>
          </div>
          <div
            v-else
            v-for="chunk in filteredChunks"
            :key="chunk.chunkId"
            :id="`chunk-${chunk.chunkIndex}`"
            class="slice-card"
            :class="{ 
              'slice-card-disabled': !chunk.isEnabled,
              'slice-card-active': activeChunkIndex === chunk.chunkIndex
            }"
            @click="handleChunkClick(chunk)"
            @mouseenter="handleChunkHover(chunk)"
            @mouseleave="handleChunkLeave"
          >
            <div class="slice-header">
              <span class="slice-number">#{{ chunk.chunkIndex + 1 }}</span>
              <div class="slice-meta">
                <el-tag 
                  v-if="getChunkPages(chunk).length > 0" 
                  size="small" 
                  type="info"
                >
                  页码: {{ getChunkPages(chunk).join(', ') }}
                </el-tag>
              </div>
              <div class="slice-actions" @click.stop>
                <el-switch
                  v-model="chunk.isEnabled"
                  @change="handleToggleChunk(chunk)"
                  active-color="#67C23A"
                  size="small"
                />
              </div>
            </div>
            <div class="slice-content">
              <p class="slice-text">{{ chunk.content }}</p>
            </div>
            <div class="slice-footer">
              <span class="token-count">
                <el-icon><Coin /></el-icon>
                {{ chunk.tokenCount || chunk.chunkLength }} tokens
              </span>
              <span class="length-count">
                <el-icon><EditPen /></el-icon>
                {{ chunk.chunkLength }} 字符
              </span>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="slices-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            small
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  Document, 
  Search, 
  RefreshRight, 
  DocumentCopy, 
  Collection, 
  Loading,
  InfoFilled,
  Box,
  Coin,
  EditPen
} from '@element-plus/icons-vue'
import { DocumentService, ChunkService, type Document as DocType, type Chunk, type ChunkMetadata } from '@/api/knowledge'

// 路由
const route = useRoute()

// 获取路由参数
const kbId = route.params.id as string
const docId = route.params.fileId as string

// 响应式数据
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const loadingPreview = ref(false)

// 文件信息
const docInfo = ref<Partial<DocType>>({
  docId: '',
  docName: '',
  chunkNum: 0,
  tokenNum: 0,
  status: ''
})

// 切片数据
const chunks = ref<Chunk[]>([])

// 原始内容
const originalContent = ref('')

// 高亮和交互状态
const highlightedChunkIndex = ref<number | null>(null)
const activeChunkIndex = ref<number | null>(null)

// DOM引用
const previewContentRef = ref<HTMLElement | null>(null)
const slicesListRef = ref<HTMLElement | null>(null)

// 内容片段（用于渲染和高亮）
interface ContentSegment {
  text: string
  chunkIndex: number | null
  startPos: number
  endPos: number
}

const contentSegments = computed<ContentSegment[]>(() => {
  if (!originalContent.value || chunks.value.length === 0) {
    return [{ text: originalContent.value, chunkIndex: null, startPos: 0, endPos: originalContent.value.length }]
  }

  // 根据chunk的metadata中的位置信息构建片段
  const segments: ContentSegment[] = []
  let lastEndPos = 0

  // 按起始位置排序的chunks
  const sortedChunks = [...chunks.value]
    .map(chunk => {
      const metadata = parseMetadata(chunk.metadata)
      return {
        chunk,
        startIndex: metadata?.start_index ?? 0,
        endIndex: metadata?.end_index ?? (chunk.content?.length ?? 0)
      }
    })
    .filter(c => c.startIndex !== undefined && c.endIndex !== undefined)
    .sort((a, b) => a.startIndex - b.startIndex)

  for (const { chunk, startIndex, endIndex } of sortedChunks) {
    // 添加chunk之前的普通文本
    if (startIndex > lastEndPos) {
      segments.push({
        text: originalContent.value.slice(lastEndPos, startIndex),
        chunkIndex: null,
        startPos: lastEndPos,
        endPos: startIndex
      })
    }

    // 添加chunk对应的文本
    segments.push({
      text: originalContent.value.slice(startIndex, endIndex),
      chunkIndex: chunk.chunkIndex,
      startPos: startIndex,
      endPos: endIndex
    })

    lastEndPos = Math.max(lastEndPos, endIndex)
  }

  // 添加最后剩余的文本
  if (lastEndPos < originalContent.value.length) {
    segments.push({
      text: originalContent.value.slice(lastEndPos),
      chunkIndex: null,
      startPos: lastEndPos,
      endPos: originalContent.value.length
    })
  }

  return segments.length > 0 ? segments : [{ text: originalContent.value, chunkIndex: null, startPos: 0, endPos: originalContent.value.length }]
})

// 解析metadata
const parseMetadata = (metadata: ChunkMetadata | string | undefined): ChunkMetadata | null => {
  if (!metadata) return null
  if (typeof metadata === 'string') {
    try {
      return JSON.parse(metadata)
    } catch {
      return null
    }
  }
  return metadata
}

// 过滤后的切片列表
const filteredChunks = computed(() => {
  if (!searchKeyword.value) return chunks.value
  return chunks.value.filter(chunk =>
    chunk.content.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 获取状态文本
const getStatusText = (status?: string) => {
  const statusMap: Record<string, string> = {
    PENDING: '等待中',
    PARSING: '解析中',
    CHUNKING: '分片中',
    EMBEDDING: '嵌入中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return statusMap[status || ''] || '未知'
}

// 获取状态类型
const getStatusType = (status?: string): 'success' | 'warning' | 'danger' | 'info' => {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    COMPLETED: 'success',
    FAILED: 'danger',
    PENDING: 'info',
    PARSING: 'warning',
    CHUNKING: 'warning',
    EMBEDDING: 'warning'
  }
  return typeMap[status || ''] || 'info'
}

// 获取chunk覆盖的页码
const getChunkPages = (chunk: Chunk): number[] => {
  const metadata = parseMetadata(chunk.metadata)
  return metadata?.page_idx || []
}

// 获取文档详情
const fetchDocInfo = async () => {
  try {
    const res = await DocumentService.getDetail(docId)
    if (res) {
      docInfo.value = res
    }
  } catch (error) {
    console.error('获取文档详情失败:', error)
    ElMessage.error('获取文档详情失败')
  }
}

// 获取切片列表
const fetchChunks = async () => {
  loading.value = true
  try {
    const res = await ChunkService.getList(docId, {
      page: currentPage.value,
      size: pageSize.value
    })
    if (res) {
      chunks.value = res.records || []
      total.value = res.total || 0
    }
  } catch (error) {
    console.error('获取切片列表失败:', error)
    ElMessage.error('获取切片列表失败')
  } finally {
    loading.value = false
  }
}

// 获取原始内容预览
const fetchOriginalContent = async () => {
  loadingPreview.value = true
  try {
    // 尝试拼接所有chunk的内容作为原始内容预览
    // 实际项目中可能需要调用专门的API获取原始文件内容
    if (chunks.value.length > 0) {
      // 按chunkIndex排序并拼接
      const sortedChunks = [...chunks.value].sort((a, b) => a.chunkIndex - b.chunkIndex)
      originalContent.value = sortedChunks.map(c => c.content).join('\n\n')
    }
  } catch (error) {
    console.error('获取原始内容失败:', error)
  } finally {
    loadingPreview.value = false
  }
}

// 点击切片 - 定位到左侧对应内容
const handleChunkClick = (chunk: Chunk) => {
  activeChunkIndex.value = chunk.chunkIndex
  highlightedChunkIndex.value = chunk.chunkIndex

  // 滚动到左侧对应的segment
  nextTick(() => {
    const segmentEl = document.getElementById(`segment-${findSegmentIndex(chunk.chunkIndex)}`)
    if (segmentEl && previewContentRef.value) {
      segmentEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

// 查找对应的segment索引
const findSegmentIndex = (chunkIndex: number): number => {
  return contentSegments.value.findIndex(s => s.chunkIndex === chunkIndex)
}

// 悬停切片
const handleChunkHover = (chunk: Chunk) => {
  highlightedChunkIndex.value = chunk.chunkIndex
}

// 离开切片
const handleChunkLeave = () => {
  if (activeChunkIndex.value === null) {
    highlightedChunkIndex.value = null
  } else {
    highlightedChunkIndex.value = activeChunkIndex.value
  }
}

// 点击左侧内容 - 滚动到右侧对应切片
const scrollToChunk = (chunkIndex: number) => {
  activeChunkIndex.value = chunkIndex
  highlightedChunkIndex.value = chunkIndex

  nextTick(() => {
    const chunkEl = document.getElementById(`chunk-${chunkIndex}`)
    if (chunkEl && slicesListRef.value) {
      chunkEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

// 切换切片状态
const handleToggleChunk = async (chunk: Chunk) => {
  try {
    await ChunkService.updateStatus(chunk.chunkId, chunk.isEnabled)
    ElMessage.success(`切片 #${chunk.chunkIndex + 1} 已${chunk.isEnabled ? '启用' : '禁用'}`)
  } catch (error) {
    chunk.isEnabled = !chunk.isEnabled
    console.error('更新状态失败:', error)
    ElMessage.error('更新状态失败')
  }
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchChunks()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchChunks()
}

// 组件挂载时初始化
onMounted(async () => {
  await fetchDocInfo()
  await fetchChunks()
  await fetchOriginalContent()
})
</script>

<style lang="scss" scoped>
.file-slices {
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
    color: #8b6914;
    text-decoration: none;

    &:hover {
      color: #ffc300;
    }
  }
}

// 文件信息卡片
.file-info-card {
  padding: 20px 24px;
  margin-bottom: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;

  .file-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;

    .file-info {
      .file-icon-name {
        display: flex;
        align-items: center;

        .file-icon {
          margin-right: 8px;
          font-size: 24px;
          color: #f9c924;
        }

        .file-name {
          font-size: 18px;
          font-weight: 600;
          color: var(--el-text-color-primary);
        }
      }
    }
  }

  .file-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    font-size: 14px;
    color: var(--el-text-color-secondary);

    .meta-item {
      display: flex;
      gap: 8px;
      align-items: center;

      .meta-label {
        font-weight: 500;
      }

      .meta-value {
        color: var(--el-text-color-primary);
      }
    }
  }
}

// 双栏主内容区域
.slices-main-content {
  display: flex;
  gap: 20px;
  height: calc(100vh - 280px);
  min-height: 500px;
}

// 面板通用样式
.preview-panel,
.slices-panel {
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;

  .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    border-bottom: 1px solid var(--el-border-color-light);
    background: #fafafa;

    .panel-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);

      .el-icon {
        color: #f9c924;
      }
    }

    .info-icon {
      color: var(--el-text-color-secondary);
      cursor: help;
    }
  }
}

// 左侧预览面板
.preview-panel {
  flex: 1;
  min-width: 400px;

  .preview-content {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    font-size: 14px;
    line-height: 1.8;
    color: var(--el-text-color-regular);

    .content-wrapper {
      .content-segment {
        position: relative;
        display: inline;
        padding: 2px 0;
        transition: all 0.3s ease;

        &.has-chunk {
          cursor: pointer;
          border-radius: 4px;

          &:hover {
            background: rgba(249, 201, 36, 0.1);
          }
        }

        &.highlighted {
          background: rgba(249, 201, 36, 0.3);
          
          .chunk-badge {
            opacity: 1;
          }
        }

        .chunk-badge {
          display: inline-block;
          padding: 0 4px;
          margin-left: 4px;
          font-size: 10px;
          font-weight: 600;
          color: #fff;
          vertical-align: super;
          background: #f9c924;
          border-radius: 4px;
          opacity: 0;
          transition: opacity 0.2s ease;
        }
      }
    }
  }

  .loading-placeholder,
  .empty-preview {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    gap: 12px;
    color: var(--el-text-color-secondary);

    .el-icon {
      font-size: 48px;
    }
  }
}

// 右侧切片面板
.slices-panel {
  flex: 1;
  min-width: 450px;

  .search-section {
    .search-input {
      width: 200px;
    }
  }

  .slices-list {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
  }

  .loading-placeholder,
  .empty-slices {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 200px;
    gap: 12px;
    color: var(--el-text-color-secondary);

    .el-icon {
      font-size: 48px;
    }
  }

  .slice-card {
    padding: 16px;
    margin-bottom: 12px;
    background: #fffef5;
    border: 2px solid transparent;
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      border-color: #f9c924;
      box-shadow: 0 2px 8px rgb(249 201 36 / 15%);
    }

    &.slice-card-active {
      border-color: #f9c924;
      background: rgba(249, 201, 36, 0.08);
    }

    &.slice-card-disabled {
      background: #f8f8f8;
      opacity: 0.6;

      .slice-text {
        color: #999;
      }
    }

    .slice-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 10px;

      .slice-number {
        font-size: 14px;
        font-weight: 700;
        color: #f9c924;
      }

      .slice-meta {
        flex: 1;
      }

      .slice-actions {
        margin-left: auto;
      }
    }

    .slice-content {
      margin-bottom: 10px;

      .slice-text {
        display: -webkit-box;
        margin: 0;
        overflow: hidden;
        font-size: 13px;
        line-height: 1.6;
        color: var(--el-text-color-regular);
        -webkit-line-clamp: 4;
        line-clamp: 4;
        -webkit-box-orient: vertical;
      }
    }

    .slice-footer {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: var(--el-text-color-secondary);

      .token-count,
      .length-count {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }

  .slices-pagination {
    padding: 12px 16px;
    border-top: 1px solid var(--el-border-color-light);
  }
}

// 搜索框样式
:deep(.el-input__wrapper) {
  border-radius: 6px;
  transition: all 0.3s ease;

  &:hover {
    border-color: #f9c924;
  }

  &.is-focus {
    border-color: #f9c924;
    box-shadow: 0 0 0 2px rgb(249 201 36 / 20%);
  }
}

// 按钮样式
:deep(.el-button--primary) {
  color: #131921;
  background: #f9c924;
  border-color: #f9c924;

  &:hover {
    background: #e6b420;
    border-color: #e6b420;
  }
}

// 响应式适配
@media (max-width: 1200px) {
  .slices-main-content {
    flex-direction: column;
    height: auto;

    .preview-panel,
    .slices-panel {
      min-width: 100%;
      max-height: 50vh;
    }
  }
}
</style>

