/**
 * @file demo.ts
 * @description Dev/test teaching demo APIs for the RAG workspace.
 */

import { ApiPaths } from '@/api/paths'
import request from '@/utils/http'
import type { RetrievalAdapterStatusItem, RetrievalAdapterStatusResponse } from '@/api/retrieval'

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

export interface RagEvidenceReportStat {
  label: string
  value: string
  hint: string
}

export interface RagEvidenceReportIngestionBoundary {
  uploadApi: string
  javaBoundary: string
  pythonBoundary: string
  parserContract: string
  probeCommand: string
}

export interface RagEvidenceCitationChunk {
  rank: number
  chunkId: string
  docId: string
  docName: string
  kbId: string
  kbName: string
  retrievalSource: string
  finalScore: number
  contentChars: number
  scoreExplanation: string
}

export interface RagEvidenceCitationReference {
  rank: number
  chunkId: string
  docId: string
  docName: string
  kbId: string
  kbName: string
  retrievalSource: string
  similarityScore: number
  finalScore: number
  keywordScore: number
  hybridScore: number
  rerankScore: number
  rerankProvider: string
  scoreExplanation: string
}

export interface RagEvidenceCitationEvidence {
  chatQuestion: string
  answerPreview: string
  chatRoute: string
  assemblyStrategy: string
  contextWindowPolicy: string
  referenceCount: number
  includedChunkCount: number
  promptCharCount: number
  contextCharCount: number
  promptSections: string[]
  includedChunks: RagEvidenceCitationChunk[]
  references: RagEvidenceCitationReference[]
}

export interface RagEvidenceReportResponse {
  status: string
  summary: string
  markdown: string
  demo: RagDemoSeedResponse
  evaluation: RagRetrievalEvaluationResponse
  adapterStatus: RetrievalAdapterStatusResponse
  stats: RagEvidenceReportStat[]
  scaffoldStack: string[]
  ingestionBoundary: RagEvidenceReportIngestionBoundary
  citationEvidence: RagEvidenceCitationEvidence
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

const normalizeAdapterStatusItem = (
  item: Partial<RetrievalAdapterStatusItem> = {}
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
  response: Partial<RetrievalAdapterStatusResponse> = {}
): RetrievalAdapterStatusResponse => ({
  ...response,
  summary: response.summary || '',
  adapters: Array.isArray(response.adapters)
    ? response.adapters.map(item => normalizeAdapterStatusItem(item))
    : []
})

const normalizeCitationChunk = (
  item: Partial<RagEvidenceCitationChunk> = {}
): RagEvidenceCitationChunk => ({
  rank: item.rank || 0,
  chunkId: item.chunkId || '',
  docId: item.docId || '',
  docName: item.docName || '',
  kbId: item.kbId || '',
  kbName: item.kbName || '',
  retrievalSource: item.retrievalSource || '',
  finalScore: item.finalScore || 0,
  contentChars: item.contentChars || 0,
  scoreExplanation: item.scoreExplanation || ''
})

const normalizeCitationReference = (
  item: Partial<RagEvidenceCitationReference> = {}
): RagEvidenceCitationReference => ({
  rank: item.rank || 0,
  chunkId: item.chunkId || '',
  docId: item.docId || '',
  docName: item.docName || '',
  kbId: item.kbId || '',
  kbName: item.kbName || '',
  retrievalSource: item.retrievalSource || '',
  similarityScore: item.similarityScore || 0,
  finalScore: item.finalScore || 0,
  keywordScore: item.keywordScore || 0,
  hybridScore: item.hybridScore || 0,
  rerankScore: item.rerankScore || 0,
  rerankProvider: item.rerankProvider || '',
  scoreExplanation: item.scoreExplanation || ''
})

const normalizeCitationEvidence = (
  response: Partial<RagEvidenceCitationEvidence> = {}
): RagEvidenceCitationEvidence => ({
  chatQuestion: response.chatQuestion || '',
  answerPreview: response.answerPreview || '',
  chatRoute: response.chatRoute || '/kb/chat',
  assemblyStrategy: response.assemblyStrategy || '',
  contextWindowPolicy: response.contextWindowPolicy || '',
  referenceCount: response.referenceCount || 0,
  includedChunkCount: response.includedChunkCount || 0,
  promptCharCount: response.promptCharCount || 0,
  contextCharCount: response.contextCharCount || 0,
  promptSections: normalizeStringArray(response.promptSections),
  includedChunks: Array.isArray(response.includedChunks)
    ? response.includedChunks.map(item => normalizeCitationChunk(item))
    : [],
  references: Array.isArray(response.references)
    ? response.references.map(item => normalizeCitationReference(item))
    : []
})

const normalizeEvidenceReportResponse = (
  response: Partial<RagEvidenceReportResponse> = {}
): RagEvidenceReportResponse => ({
  status: response.status || 'Template',
  summary: response.summary || '',
  markdown: response.markdown || '',
  demo: normalizeSeedResponse(response.demo),
  evaluation: normalizeEvaluationResponse(response.evaluation),
  adapterStatus: normalizeAdapterStatus(response.adapterStatus),
  stats: Array.isArray(response.stats)
    ? response.stats.map(item => ({
        label: item.label || '',
        value: item.value || '',
        hint: item.hint || ''
      }))
    : [],
  scaffoldStack: normalizeStringArray(response.scaffoldStack),
  ingestionBoundary: {
    uploadApi: response.ingestionBoundary?.uploadApi || '',
    javaBoundary: response.ingestionBoundary?.javaBoundary || '',
    pythonBoundary: response.ingestionBoundary?.pythonBoundary || '',
    parserContract: response.ingestionBoundary?.parserContract || '',
    probeCommand: response.ingestionBoundary?.probeCommand || ''
  },
  citationEvidence: normalizeCitationEvidence(response.citationEvidence),
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

  static async buildEvidenceReport(): Promise<RagEvidenceReportResponse> {
    const response = await request.post<RagEvidenceReportResponse>({
      url: ApiPaths.test.ragDemoEvidenceReport,
      showSuccessMessage: false
    })
    return normalizeEvidenceReportResponse(response)
  }
}
