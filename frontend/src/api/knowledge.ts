/**
 * @file knowledge.ts
 * @description 知识库相关 API 接口
 * @date 2025-01-02
 */

import { openApiRequest } from '@/api/openapiClient'
import { ApiPaths } from '@/api/paths'
import request from '@/utils/http'
import type {
  OpenApiOperationData,
  OpenApiOperationQuery,
  OpenApiOperationRequest
} from '@/contracts/openapi/operations'
import type { PaginatedResponse } from '@/types/common/response'

type KnowledgeBaseContract = OpenApiOperationData<'getKnowledgeBase'>
type KnowledgeBasePageContract = OpenApiOperationData<'listKnowledgeBases'>
type DocumentContract = OpenApiOperationData<'getDocument'>
type DocumentPageContract = OpenApiOperationData<'listDocuments'>
type DocumentProcessingTaskContract = OpenApiOperationData<'listDocumentTasks'>[number]
type ChunkContract = OpenApiOperationData<'getChunk'>
type ChunkPageContract = OpenApiOperationData<'listChunks'>

type PageContract<T> = {
  records?: T[]
  current?: number
  size?: number
  total?: number
}

/**
 * 知识库数据接口：后端字段来自 OpenAPI，页面必需字段在 API 边界做归一。
 */
export type KnowledgeBase = Omit<
  KnowledgeBaseContract,
  'kbId' | 'name' | 'description' | 'isEnabled'
> & {
  /** 知识库 ID */
  kbId: string
  /** 知识库名称 */
  name: string
  /** 知识库描述 */
  description: string
  /** 是否启用 */
  isEnabled: boolean
  /** 创建者 */
  createdBy?: string
}

/**
 * 文档数据接口：后端字段来自 OpenAPI，页面必需字段在 API 边界做归一。
 */
export type Document = Omit<
  DocumentContract,
  | 'docId'
  | 'kbId'
  | 'fileId'
  | 'docName'
  | 'docType'
  | 'docSize'
  | 'status'
  | 'progress'
  | 'chunkNum'
  | 'tokenNum'
  | 'isEnabled'
> & {
  /** 文档 ID */
  docId: string
  /** 知识库 ID */
  kbId: string
  /** 文件 ID */
  fileId: string
  /** 文档名称 */
  docName: string
  /** 文档类型 */
  docType: string
  /** 文档大小 */
  docSize: number
  /** 处理状态 */
  status: string
  /** 处理进度 */
  progress: number
  /** 分片数量 */
  chunkNum: number
  /** Token 数量 */
  tokenNum: number
  /** 是否启用 */
  isEnabled: boolean
}

/**
 * 文档处理任务
 */
export type DocumentProcessingTask = Omit<
  DocumentProcessingTaskContract,
  'taskId' | 'docId' | 'kbId' | 'taskType' | 'phase' | 'status' | 'progress'
> & {
  taskId: string
  docId: string
  kbId: string
  taskType: string
  phase: string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | string
  progress: number
}

/**
 * Chunk 元数据接口 - 用于追溯定位
 */
export interface ChunkMetadata {
  /** 在拼接文本中的起始位置 */
  start_index?: number
  /** 在拼接文本中的结束位置 */
  end_index?: number
  /** 覆盖的页码数组 */
  page_idx?: number[]
  /** 内容类型，如 TEXT/TABLE/IMAGE */
  content_type?: string
  /** 位置坐标数组 [页码, x0, y0, x1, y1] */
  positions?: number[][]
  /** 来源的 parser_result_id 列表 */
  source_parser_result_ids?: string[]
  /** 章节信息 */
  chapter?: string
  /** 小节 */
  section?: string
}

/**
 * 分片数据接口：后端字段来自 OpenAPI，页面必需字段在 API 边界做归一。
 */
export type Chunk = Omit<
  ChunkContract,
  'chunkId' | 'docId' | 'kbId' | 'content' | 'chunkIndex' | 'chunkLength' | 'isEnabled' | 'metadata'
> & {
  /** 分片 ID */
  chunkId: string
  /** 文档 ID */
  docId: string
  /** 知识库 ID */
  kbId: string
  /** 分片内容 */
  content: string
  /** 分片索引 */
  chunkIndex: number
  /** 分片长度 */
  chunkLength: number
  /** 是否启用 */
  isEnabled: boolean
  /** 元数据（包含追溯信息） */
  metadata?: ChunkMetadata | string
}

/**
 * 创建知识库请求
 */
export type CreateKnowledgeBaseRequest = OpenApiOperationRequest<'createKnowledgeBase'>

/**
 * 更新知识库请求
 */
export type UpdateKnowledgeBaseRequest = OpenApiOperationRequest<'updateKnowledgeBase'>

/**
 * 分页参数
 */
export type PaginationParams = NonNullable<OpenApiOperationQuery<'listKnowledgeBases'>>

const DEFAULT_PAGE_SIZE = 20

const normalizePage = <ContractItem, ViewItem>(
  page: PageContract<ContractItem>,
  normalize: (item: ContractItem) => ViewItem
): PaginatedResponse<ViewItem> => ({
  records: (page.records || []).map(normalize),
  current: page.current || 1,
  size: page.size || DEFAULT_PAGE_SIZE,
  total: page.total || 0
})

const normalizeKnowledgeBase = (knowledgeBase: KnowledgeBaseContract): KnowledgeBase => ({
  ...knowledgeBase,
  kbId: knowledgeBase.kbId || '',
  name: knowledgeBase.name || '未命名知识库',
  description: knowledgeBase.description || '',
  isEnabled: knowledgeBase.isEnabled ?? true
})

const normalizeDocument = (document: DocumentContract): Document => ({
  ...document,
  docId: document.docId || '',
  kbId: document.kbId || '',
  fileId: document.fileId || '',
  docName: document.docName || '未命名文档',
  docType: document.docType || '',
  docSize: document.docSize || 0,
  status: document.status || 'PENDING',
  progress: document.progress || 0,
  chunkNum: document.chunkNum || 0,
  tokenNum: document.tokenNum || 0,
  isEnabled: document.isEnabled ?? true
})

const normalizeDocumentTask = (task: DocumentProcessingTaskContract): DocumentProcessingTask => ({
  ...task,
  taskId: task.taskId || '',
  docId: task.docId || '',
  kbId: task.kbId || '',
  taskType: task.taskType || '',
  phase: task.phase || 'PENDING',
  status: task.status || 'PENDING',
  progress: task.progress || 0
})

const normalizeChunk = (chunk: ChunkContract): Chunk => ({
  ...chunk,
  chunkId: chunk.chunkId || '',
  docId: chunk.docId || '',
  kbId: chunk.kbId || '',
  content: chunk.content || '',
  chunkIndex: chunk.chunkIndex || 0,
  chunkLength: chunk.chunkLength || 0,
  isEnabled: chunk.isEnabled ?? true
})

/**
 * 知识库服务
 */
export class KnowledgeService {
  /**
   * 获取知识库列表
   */
  static async getList(params: PaginationParams): Promise<PaginatedResponse<KnowledgeBase>> {
    const page: KnowledgeBasePageContract = await openApiRequest('listKnowledgeBases', {
      query: params
    })
    return normalizePage(page, normalizeKnowledgeBase)
  }

  /**
   * 获取全部知识库
   */
  static async getAll(): Promise<KnowledgeBase[]> {
    const knowledgeBases = await openApiRequest('listAllKnowledgeBases')
    return (knowledgeBases || []).map(normalizeKnowledgeBase)
  }

  /**
   * 获取知识库详情
   */
  static async getDetail(kbId: string): Promise<KnowledgeBase> {
    const knowledgeBase = await openApiRequest('getKnowledgeBase', {
      pathParams: { kbId }
    })
    return normalizeKnowledgeBase(knowledgeBase)
  }

  /**
   * 创建知识库
   */
  static async create(data: CreateKnowledgeBaseRequest): Promise<KnowledgeBase> {
    const knowledgeBase = await openApiRequest('createKnowledgeBase', { body: data })
    return normalizeKnowledgeBase(knowledgeBase)
  }

  /**
   * 更新知识库
   */
  static async update(kbId: string, data: UpdateKnowledgeBaseRequest): Promise<KnowledgeBase> {
    const knowledgeBase = await openApiRequest('updateKnowledgeBase', {
      pathParams: { kbId },
      body: data
    })
    return normalizeKnowledgeBase(knowledgeBase)
  }

  /**
   * 删除知识库
   */
  static async delete(kbId: string): Promise<void> {
    await openApiRequest('deleteKnowledgeBase', {
      pathParams: { kbId }
    })
  }
}

/**
 * 文档服务
 */
export class DocumentService {
  /**
   * 获取文档列表
   */
  static async getList(
    kbId: string,
    params: PaginationParams
  ): Promise<PaginatedResponse<Document>> {
    const page: DocumentPageContract = await openApiRequest('listDocuments', {
      pathParams: { kbId },
      query: params
    })
    return normalizePage(page, normalizeDocument)
  }

  /**
   * 获取文档详情
   */
  static async getDetail(docId: string): Promise<Document> {
    const document = await openApiRequest('getDocument', {
      pathParams: { docId }
    })
    return normalizeDocument(document)
  }

  /**
   * 上传文档
   *
   * 单文件上传保留 FormData + ApiPaths，因为当前 OpenAPI 生成物还不能表达浏览器 File body。
   */
  static async upload(kbId: string, file: File): Promise<Document> {
    const formData = new FormData()
    formData.append('file', file)
    const document = await request.post<DocumentContract>({
      url: ApiPaths.knowledge.baseDocuments(kbId),
      data: formData
    })
    return normalizeDocument(document)
  }

  /**
   * 更新文档状态
   */
  static async updateStatus(docId: string, isEnabled: boolean): Promise<void> {
    await openApiRequest('setDocumentEnabled', {
      pathParams: { docId },
      body: { isEnabled }
    })
  }

  /**
   * 删除文档
   */
  static async delete(docId: string): Promise<void> {
    await openApiRequest('deleteDocument', {
      pathParams: { docId }
    })
  }

  /**
   * 批量删除文档
   */
  static async batchDelete(docIds: string[]): Promise<void> {
    await openApiRequest('batchDeleteDocuments', {
      body: { docIds }
    })
  }

  /**
   * 重新处理文档
   */
  static async reprocess(docId: string): Promise<void> {
    await openApiRequest('reprocessDocument', {
      pathParams: { docId }
    })
  }

  /**
   * 获取文档处理任务
   */
  static async getTasks(docId: string): Promise<DocumentProcessingTask[]> {
    const tasks = await openApiRequest('listDocumentTasks', {
      pathParams: { docId }
    })
    return (tasks || []).map(normalizeDocumentTask)
  }
}

/**
 * 分片服务
 */
export class ChunkService {
  /**
   * 获取分片列表
   */
  static async getList(docId: string, params: PaginationParams): Promise<PaginatedResponse<Chunk>> {
    const page: ChunkPageContract = await openApiRequest('listChunks', {
      pathParams: { docId },
      query: {
        page: params.page,
        size: params.size
      }
    })
    return normalizePage(page, normalizeChunk)
  }

  /**
   * 获取分片详情
   */
  static async getDetail(chunkId: string): Promise<Chunk> {
    const chunk = await openApiRequest('getChunk', {
      pathParams: { chunkId }
    })
    return normalizeChunk(chunk)
  }

  /**
   * 获取文档分片数量
   */
  static getCount(docId: string): Promise<number> {
    return openApiRequest('getChunkCount', {
      pathParams: { docId }
    })
  }

  /**
   * 更新分片状态
   */
  static async updateStatus(chunkId: string, isEnabled: boolean): Promise<void> {
    await openApiRequest('updateChunkStatus', {
      pathParams: { chunkId },
      body: { isEnabled }
    })
  }
}
