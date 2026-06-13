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

const normalizeSearchResult = (result: SearchResultContract): SearchResult => ({
  ...result,
  chunkId: result.chunkId || '',
  docId: result.docId || '',
  kbId: result.kbId || '',
  content: result.content || '',
  metadata: result.metadata as SearchResultMetadata | undefined
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
}
