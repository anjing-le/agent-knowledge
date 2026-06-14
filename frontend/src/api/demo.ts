/**
 * @file demo.ts
 * @description Dev/test teaching demo APIs for the RAG workspace.
 */

import { ApiPaths } from '@/api/paths'
import request from '@/utils/http'

export interface RagDemoSeedResponse {
  kbId: string
  kbName: string
  docId: string
  docName: string
  chunkIds: string[]
  vectorCount: number
  retrievalQuery: string
  sampleResultCount: number
  topChunkId: string
  topScoreExplanation: string
  chatQuestion: string
  pipelineRoute: string
  knowledgeRoute: string
  retrievalRoute: string
  chatRoute: string
  evidenceCommands: string[]
}

export interface RagRetrievalEvaluationCase {
  query: string
  expectedChunkIds: string[]
  hitChunkIds: string[]
  topChunkId: string
  expectedRank?: number
  passed: boolean
  topScoreExplanation: string
}

export interface RagRetrievalEvaluationResponse {
  suiteName: string
  kbId: string
  topK: number
  totalCases: number
  passedCases: number
  recallAtK: number
  passed: boolean
  cases: RagRetrievalEvaluationCase[]
  evidenceCommands: string[]
}

const normalizeStringArray = (value?: string[]) => (Array.isArray(value) ? value : [])

const normalizeSeedResponse = (
  response: Partial<RagDemoSeedResponse> = {}
): RagDemoSeedResponse => ({
  kbId: response.kbId || '',
  kbName: response.kbName || '',
  docId: response.docId || '',
  docName: response.docName || '',
  chunkIds: normalizeStringArray(response.chunkIds),
  vectorCount: response.vectorCount || 0,
  retrievalQuery: response.retrievalQuery || '',
  sampleResultCount: response.sampleResultCount || 0,
  topChunkId: response.topChunkId || '',
  topScoreExplanation: response.topScoreExplanation || '',
  chatQuestion: response.chatQuestion || '',
  pipelineRoute: response.pipelineRoute || '/kb/pipeline',
  knowledgeRoute: response.knowledgeRoute || '/kb/knowledge',
  retrievalRoute: response.retrievalRoute || '/kb/retrieval',
  chatRoute: response.chatRoute || '/kb/chat',
  evidenceCommands: normalizeStringArray(response.evidenceCommands)
})

const normalizeEvaluationResponse = (
  response: Partial<RagRetrievalEvaluationResponse> = {}
): RagRetrievalEvaluationResponse => ({
  suiteName: response.suiteName || '',
  kbId: response.kbId || '',
  topK: response.topK || 0,
  totalCases: response.totalCases || 0,
  passedCases: response.passedCases || 0,
  recallAtK: response.recallAtK || 0,
  passed: Boolean(response.passed),
  cases: Array.isArray(response.cases)
    ? response.cases.map(item => ({
        query: item.query || '',
        expectedChunkIds: normalizeStringArray(item.expectedChunkIds),
        hitChunkIds: normalizeStringArray(item.hitChunkIds),
        topChunkId: item.topChunkId || '',
        expectedRank: item.expectedRank,
        passed: Boolean(item.passed),
        topScoreExplanation: item.topScoreExplanation || ''
      }))
    : [],
  evidenceCommands: normalizeStringArray(response.evidenceCommands)
})

export class RagDemoService {
  static async seedRagDemo(): Promise<RagDemoSeedResponse> {
    const response = await request.post<RagDemoSeedResponse>({
      url: ApiPaths.test.ragDemoSeed,
      showSuccessMessage: false
    })
    return normalizeSeedResponse(response)
  }

  static async evaluateRetrieval(): Promise<RagRetrievalEvaluationResponse> {
    const response = await request.post<RagRetrievalEvaluationResponse>({
      url: ApiPaths.test.ragDemoRetrievalEvaluation,
      showSuccessMessage: false
    })
    return normalizeEvaluationResponse(response)
  }
}
