/**
 * @file chat.ts
 * @description 聊天相关 API 接口
 * @date 2025-01-02
 */

import { openApiRequest } from '@/api/openapiClient'
import type {
  OpenApiOperationData,
  OpenApiOperationQuery,
  OpenApiOperationRequest
} from '@/contracts/openapi/operations'
import type { ReferenceInfo } from '@/contracts/openapi/schemas'
import type { PaginatedResponse } from '@/types/common/response'

type ConversationContract = OpenApiOperationData<'createConversation'>
type ConversationPageContract = OpenApiOperationData<'listConversations'>
type MessageContract = OpenApiOperationData<'sendMessage'>

/**
 * 会话数据接口：后端字段来自 OpenAPI，页面必需字段在 API 边界做归一。
 */
export type Conversation = Omit<ConversationContract, 'conversationId' | 'title' | 'kbIds'> & {
  /** 会话 ID */
  conversationId: string
  /** 会话标题 */
  title: string
  /** 关联的知识库 ID 列表 */
  kbIds: string[]
  /** 最后一条消息 */
  lastMessage?: string
  /** 最后消息时间 */
  lastMessageAt?: string
}

/**
 * 消息引用来源
 */
export type MessageReference = Omit<ReferenceInfo, 'metadata'> & {
  /** 检索与解析元数据 */
  metadata?: Record<string, unknown> & {
    page_idx?: number[]
    content_type?: string
    source_parser_result_ids?: string[]
  }
}

/**
 * 消息数据接口：后端字段来自 OpenAPI，页面必需字段在 API 边界做归一。
 */
export type Message = Omit<
  MessageContract,
  'messageId' | 'conversationId' | 'role' | 'content' | 'references'
> & {
  /** 消息 ID */
  messageId: string
  /** 会话 ID */
  conversationId: string
  /** 消息角色：user/assistant/system */
  role: 'user' | 'assistant' | 'system'
  /** 消息内容 */
  content: string
  /** 引用来源 */
  references?: MessageReference[]
}

/**
 * 创建会话请求
 */
export type CreateConversationRequest = OpenApiOperationRequest<'createConversation'>

/**
 * 发送消息请求
 */
export type SendMessageRequest = OpenApiOperationRequest<'sendMessage'>

/**
 * 分页参数
 */
export type PaginationParams = NonNullable<OpenApiOperationQuery<'listConversations'>>

const DEFAULT_PAGE_SIZE = 20

const normalizeConversation = (conversation: ConversationContract): Conversation => ({
  ...conversation,
  conversationId: conversation.conversationId || '',
  title: conversation.title || '新对话',
  kbIds: conversation.kbIds || []
})

const normalizeConversationPage = (
  page: ConversationPageContract
): PaginatedResponse<Conversation> => ({
  records: (page.records || []).map(normalizeConversation),
  current: page.current || 1,
  size: page.size || DEFAULT_PAGE_SIZE,
  total: page.total || 0
})

const normalizeMessageRole = (role?: string): Message['role'] => {
  if (role === 'user' || role === 'assistant' || role === 'system') {
    return role
  }
  return 'assistant'
}

const normalizeMessage = (message: MessageContract): Message => ({
  ...message,
  messageId: message.messageId || '',
  conversationId: message.conversationId || '',
  role: normalizeMessageRole(message.role),
  content: message.content || '',
  references: message.references as MessageReference[] | undefined
})

/**
 * 会话服务
 */
export class ConversationService {
  /**
   * 获取会话列表
   */
  static async getList(params: PaginationParams): Promise<PaginatedResponse<Conversation>> {
    const page = await openApiRequest('listConversations', { query: params })
    return normalizeConversationPage(page)
  }

  /**
   * 获取会话详情
   */
  static async getDetail(conversationId: string): Promise<Conversation> {
    const conversation = await openApiRequest('getConversation', {
      pathParams: { conversationId }
    })
    return normalizeConversation(conversation)
  }

  /**
   * 创建会话
   */
  static async create(data: CreateConversationRequest): Promise<Conversation> {
    const conversation = await openApiRequest('createConversation', { body: data })
    return normalizeConversation(conversation)
  }

  /**
   * 删除会话
   */
  static async delete(conversationId: string): Promise<void> {
    await openApiRequest('deleteConversation', {
      pathParams: { conversationId }
    })
  }

  /**
   * 更新会话标题
   */
  static async updateTitle(conversationId: string, title: string): Promise<Conversation> {
    const conversation = await openApiRequest('updateConversationTitle', {
      pathParams: { conversationId },
      query: { title }
    })
    return normalizeConversation(conversation)
  }
}

/**
 * 消息服务
 */
export class MessageService {
  /**
   * 获取消息列表
   */
  static async getList(conversationId: string): Promise<Message[]> {
    const messages = await openApiRequest('getMessages', {
      pathParams: { conversationId }
    })
    return (messages || []).map(normalizeMessage)
  }

  /**
   * 发送消息
   */
  static async send(conversationId: string, data: SendMessageRequest): Promise<Message> {
    const message = await openApiRequest('sendMessage', {
      pathParams: { conversationId },
      body: data
    })
    return normalizeMessage(message)
  }
}
