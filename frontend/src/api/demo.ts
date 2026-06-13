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

export class RagDemoService {
  static async seedRagDemo(): Promise<RagDemoSeedResponse> {
    const response = await request.post<RagDemoSeedResponse>({
      url: ApiPaths.test.ragDemoSeed,
      showSuccessMessage: false
    })
    return normalizeSeedResponse(response)
  }
}
