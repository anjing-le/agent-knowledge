/**
 * @file knowledge.ts
 * @description 知识库相关API接口
 * @date 2025-01-02
 */

import request from '@/utils/http'
import { ApiPaths } from '@/api/paths'
import type { PaginatedResponse } from '@/types/common/response'

/**
 * 知识库数据接口
 */
export interface KnowledgeBase {
  /** 知识库ID */
  kbId: string
  /** 知识库名称 */
  name: string
  /** 知识库描述 */
  description: string
  /** 知识库头像 */
  avatar?: string
  /** 嵌入模型 */
  embeddingModel?: string
  /** 分块大小 */
  chunkSize?: number
  /** 分块重叠 */
  chunkOverlap?: number
  /** 知识库类型 */
  kbType?: string
  /** 是否启用Raptor */
  raptorEnabled?: boolean
  /** Raptor配置 */
  raptorConfig?: string
  /** 是否启用 */
  isEnabled: boolean
  /** 文档数量 */
  documentCount?: number
  /** 分片数量 */
  chunkCount?: number
  /** 创建者 */
  createdBy?: string
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
}

/**
 * 文档数据接口
 */
export interface Document {
  /** 文档ID */
  docId: string
  /** 知识库ID */
  kbId: string
  /** 文件ID */
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
  /** 进度信息 */
  progressMsg?: string
  /** 分片数量 */
  chunkNum: number
  /** Token数量 */
  tokenNum: number
  /** 是否启用 */
  isEnabled: boolean
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
  /** 完成时间 */
  completedAt?: string
}

/**
 * 文档处理任务
 */
export interface DocumentProcessingTask {
  taskId: string
  docId: string
  kbId: string
  taskType: string
  phase: string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | string
  progress: number
  message?: string
  errorMessage?: string
  parserTaskId?: string
  retryCount?: number
  startedAt?: string
  completedAt?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * Chunk元数据接口 - 用于追溯定位
 */
export interface ChunkMetadata {
  /** 在拼接文本中的起始位置 */
  start_index?: number
  /** 在拼接文本中的结束位置 */
  end_index?: number
  /** 覆盖的页码数组 */
  page_idx?: number[]
  /** 位置坐标数组 [页码, x0, y0, x1, y1] */
  positions?: number[][]
  /** 来源的parser_result_id列表 */
  source_parser_result_ids?: string[]
  /** 章节信息 */
  chapter?: string
  /** 小节 */
  section?: string
}

/**
 * 分片数据接口
 */
export interface Chunk {
  /** 分片ID */
  chunkId: string
  /** 文档ID */
  docId: string
  /** 知识库ID */
  kbId: string
  /** 分片内容 */
  content: string
  /** 分片索引 */
  chunkIndex: number
  /** 分片长度 */
  chunkLength: number
  /** Token数量 */
  tokenCount?: number
  /** 是否启用 */
  isEnabled: boolean
  /** 元数据（包含追溯信息） */
  metadata?: ChunkMetadata | string
  /** 创建时间 */
  createdAt?: string
}

/**
 * 创建知识库请求
 */
export interface CreateKnowledgeBaseRequest {
  /** 知识库名称 */
  name: string
  /** 知识库描述 */
  description: string
  /** 嵌入模型 */
  embeddingModel?: string
  /** 分块大小 */
  chunkSize?: number
  /** 分块重叠 */
  chunkOverlap?: number
  /** 是否启用Raptor */
  raptorEnabled?: boolean
}

/**
 * 更新知识库请求
 */
export interface UpdateKnowledgeBaseRequest {
  /** 知识库名称 */
  name?: string
  /** 知识库描述 */
  description?: string
  /** 嵌入模型 */
  embeddingModel?: string
  /** 分块大小 */
  chunkSize?: number
  /** 分块重叠 */
  chunkOverlap?: number
  /** 是否启用 */
  isEnabled?: boolean
  /** 是否启用Raptor */
  raptorEnabled?: boolean
}

/**
 * 分页参数
 */
export interface PaginationParams {
  page?: number
  size?: number
  keyword?: string
}

/**
 * 知识库服务
 */
export class KnowledgeService {
  /**
   * 获取知识库列表
   */
  static getList(params: PaginationParams) {
    return request.get<PaginatedResponse<KnowledgeBase>>({
      url: ApiPaths.knowledge.bases,
      params
    })
  }

  /**
   * 获取全部知识库
   */
  static getAll() {
    return request.get<KnowledgeBase[]>({
      url: ApiPaths.knowledge.basesAll
    })
  }

  /**
   * 获取知识库详情
   */
  static getDetail(kbId: string) {
    return request.get<KnowledgeBase>({
      url: ApiPaths.knowledge.baseDetail(kbId)
    })
  }

  /**
   * 创建知识库
   */
  static create(data: CreateKnowledgeBaseRequest) {
    return request.post<KnowledgeBase>({
      url: ApiPaths.knowledge.bases,
      data
    })
  }

  /**
   * 更新知识库
   */
  static update(kbId: string, data: UpdateKnowledgeBaseRequest) {
    return request.put<KnowledgeBase>({
      url: ApiPaths.knowledge.baseDetail(kbId),
      data
    })
  }

  /**
   * 删除知识库
   */
  static delete(kbId: string) {
    return request.del<void>({
      url: ApiPaths.knowledge.baseDetail(kbId)
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
  static getList(kbId: string, params: PaginationParams) {
    return request.get<PaginatedResponse<Document>>({
      url: ApiPaths.knowledge.baseDocuments(kbId),
      params
    })
  }

  /**
   * 获取文档详情
   */
  static getDetail(docId: string) {
    return request.get<Document>({
      url: ApiPaths.knowledge.documentDetail(docId)
    })
  }

  /**
   * 上传文档
   */
  static upload(kbId: string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<Document>({
      url: ApiPaths.knowledge.baseDocuments(kbId),
      data: formData
    })
  }

  /**
   * 更新文档状态
   */
  static updateStatus(docId: string, isEnabled: boolean) {
    return request.put<void>({
      url: ApiPaths.knowledge.documentEnabled(docId),
      data: { isEnabled }
    })
  }

  /**
   * 删除文档
   */
  static delete(docId: string) {
    return request.del<void>({
      url: ApiPaths.knowledge.documentDetail(docId)
    })
  }

  /**
   * 批量删除文档
   */
  static batchDelete(docIds: string[]) {
    return request.post<void>({
      url: ApiPaths.knowledge.documentBatchDelete,
      data: { docIds }
    })
  }

  /**
   * 重新处理文档
   */
  static reprocess(docId: string) {
    return request.post<void>({
      url: ApiPaths.knowledge.documentReprocess(docId)
    })
  }

  /**
   * 获取文档处理任务
   */
  static getTasks(docId: string) {
    return request.get<DocumentProcessingTask[]>({
      url: ApiPaths.knowledge.documentTasks(docId)
    })
  }
}

/**
 * 分片服务
 */
export class ChunkService {
  /**
   * 获取分片列表
   */
  static getList(docId: string, params: PaginationParams) {
    return request.get<PaginatedResponse<Chunk>>({
      url: ApiPaths.knowledge.documentChunks(docId),
      params
    })
  }

  /**
   * 获取分片详情
   */
  static getDetail(chunkId: string) {
    return request.get<Chunk>({
      url: ApiPaths.knowledge.chunkDetail(chunkId)
    })
  }

  /**
   * 获取文档分片数量
   */
  static getCount(docId: string) {
    return request.get<number>({
      url: ApiPaths.knowledge.documentChunkCount(docId)
    })
  }

  /**
   * 更新分片状态
   */
  static updateStatus(chunkId: string, isEnabled: boolean) {
    return request.put<void>({
      url: ApiPaths.knowledge.chunkEnabled(chunkId),
      data: { isEnabled }
    })
  }
}
