/**
 * @file retrieval.ts
 * @description RAG 检索相关 API 接口
 */

import { openApiRequest } from '@/api/openapiClient'
import type {
  OpenApiOperationData,
  OpenApiOperationQuery,
  OpenApiOperationRequest
} from '@/contracts/openapi/operations'

type SearchResultContract = OpenApiOperationData<'search'>[number]
type RetrievalAdapterStatusContract = OpenApiOperationData<'adapterStatus'>
type RetrievalAdapterStatusItemContract = NonNullable<RetrievalAdapterStatusContract['adapters']>[number]

export type SearchRequest = OpenApiOperationRequest<'search'>
export type SimpleSearchParams = NonNullable<OpenApiOperationQuery<'simpleSearch'>>

export type SearchResultMetadata = Record<string, unknown> & {
  page_idx?: number[]
  content_type?: string
  source_parser_result_ids?: string[]
}

/**
 * 检索结果：后端字段来自 OpenAPI，页面必需字段在 API 边界做归一。
 */
export type SearchResult = Omit<
  SearchResultContract,
  'chunkId' | 'docId' | 'kbId' | 'content' | 'metadata'
> & {
  chunkId: string
  docId: string
  kbId: string
  content: string
  metadata?: SearchResultMetadata
}

export type RetrievalAdapterStatusItem = Omit<
  RetrievalAdapterStatusItemContract,
  | 'axis'
  | 'displayName'
  | 'currentProvider'
  | 'defaultProvider'
  | 'bridgeProviders'
  | 'productionProviders'
  | 'currentImplementation'
  | 'boundary'
  | 'configKey'
  | 'switchCommand'
  | 'contractPath'
  | 'runtimeStatus'
> & {
  axis: string
  displayName: string
  currentProvider: string
  defaultProvider: string
  bridgeProviders: string[]
  productionProviders: string[]
  currentImplementation: string
  boundary: string
  configKey: string
  switchCommand: string
  contractPath: string
  runtimeStatus: string
}

export type RetrievalAdapterStatusResponse = Omit<
  RetrievalAdapterStatusContract,
  'summary' | 'adapters'
> & {
  summary: string
  adapters: RetrievalAdapterStatusItem[]
}

const normalizeSearchResult = (result: SearchResultContract): SearchResult => ({
  ...result,
  chunkId: result.chunkId || '',
  docId: result.docId || '',
  kbId: result.kbId || '',
  content: result.content || '',
  metadata: result.metadata as SearchResultMetadata | undefined
})

const normalizeStringArray = (value?: string[]) => (Array.isArray(value) ? value : [])

const normalizeAdapterStatusItem = (
  item: Partial<RetrievalAdapterStatusItemContract> = {}
): RetrievalAdapterStatusItem => ({
  ...item,
  axis: item.axis || '',
  displayName: item.displayName || '',
  currentProvider: item.currentProvider || '',
  defaultProvider: item.defaultProvider || '',
  bridgeProviders: normalizeStringArray(item.bridgeProviders),
  productionProviders: normalizeStringArray(item.productionProviders),
  currentImplementation: item.currentImplementation || '',
  boundary: item.boundary || '',
  configKey: item.configKey || '',
  switchCommand: item.switchCommand || '',
  contractPath: item.contractPath || '',
  runtimeStatus: item.runtimeStatus || ''
})

const normalizeAdapterStatus = (
  status: Partial<RetrievalAdapterStatusContract> = {}
): RetrievalAdapterStatusResponse => ({
  ...status,
  summary: status.summary || '',
  adapters: Array.isArray(status.adapters)
    ? status.adapters.map(item => normalizeAdapterStatusItem(item))
    : []
})

/**
 * 检索服务
 */
export class RetrievalService {
  /**
   * 多知识库检索
   */
  static async search(data: SearchRequest): Promise<SearchResult[]> {
    const results = await openApiRequest('search', { body: data })
    return (results || []).map(normalizeSearchResult)
  }

  /**
   * 单知识库快速检索
   */
  static async simpleSearch(params: SimpleSearchParams): Promise<SearchResult[]> {
    const results = await openApiRequest('simpleSearch', { query: params })
    return (results || []).map(normalizeSearchResult)
  }

  /**
   * 查询当前运行态 RAG adapter provider。
   */
  static async adapterStatus(): Promise<RetrievalAdapterStatusResponse> {
    const status = await openApiRequest('adapterStatus', { showErrorMessage: false })
    return normalizeAdapterStatus(status)
  }
}
