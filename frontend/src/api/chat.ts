/**
 * @file chat.ts
 * @description 聊天相关API接口
 * @date 2025-01-02
 */

import request from '@/utils/http'
import type { BaseResult, PageResult } from '@/types/common/response'

/**
 * 会话数据接口
 */
export interface Conversation {
  /** 会话ID */
  conversationId: string
  /** 会话标题 */
  title: string
  /** 关联的知识库ID列表 */
  kbIds?: string[]
  /** 消息数量 */
  messageCount?: number
  /** 创建时间 */
  createdAt?: string
  /** 更新时间 */
  updatedAt?: string
  /** 最后一条消息 */
  lastMessage?: string
  /** 最后消息时间 */
  lastMessageAt?: string
}

/**
 * 消息数据接口
 */
export interface Message {
  /** 消息ID */
  messageId: string
  /** 会话ID */
  conversationId: string
  /** 消息角色：user/assistant/system */
  role: 'user' | 'assistant' | 'system'
  /** 消息内容 */
  content: string
  /** 引用来源 */
  references?: MessageReference[]
  /** 创建时间 */
  createdAt?: string
}

/**
 * 消息引用来源
 */
export interface MessageReference {
  /** 分片ID */
  chunkId: string
  /** 文档ID */
  docId: string
  /** 文档名称 */
  docName: string
  /** 分片内容 */
  content: string
  /** 相似度分数 */
  score: number
}

/**
 * 创建会话请求
 */
export interface CreateConversationRequest {
  /** 会话标题 */
  title?: string
  /** 关联的知识库ID列表 */
  kbIds?: string[]
}

/**
 * 发送消息请求
 */
export interface SendMessageRequest {
  /** 用户消息内容 */
  content: string
  /** 是否启用检索 */
  enableRetrieval?: boolean
  /** 检索的知识库ID列表 */
  kbIds?: string[]
}

/**
 * 分页参数
 */
export interface PaginationParams {
  page?: number
  size?: number
}

/**
 * 会话服务
 */
export class ConversationService {
  /**
   * 获取会话列表
   */
  static getList(params: PaginationParams) {
    return request.get<BaseResult<PageResult<Conversation[]>>>({
      url: '/api/chat/conversations',
      params
    })
  }

  /**
   * 获取会话详情
   */
  static getDetail(conversationId: string) {
    return request.get<BaseResult<Conversation>>({
      url: `/api/chat/conversations/${conversationId}`
    })
  }

  /**
   * 创建会话
   */
  static create(data: CreateConversationRequest) {
    return request.post<BaseResult<Conversation>>({
      url: '/api/chat/conversations',
      data
    })
  }

  /**
   * 删除会话
   */
  static delete(conversationId: string) {
    return request.del<BaseResult<void>>({
      url: `/api/chat/conversations/${conversationId}`
    })
  }
}

/**
 * 消息服务
 */
export class MessageService {
  /**
   * 获取消息列表
   */
  static getList(conversationId: string, params: PaginationParams) {
    return request.get<BaseResult<PageResult<Message[]>>>({
      url: `/api/chat/conversations/${conversationId}/messages`,
      params
    })
  }

  /**
   * 发送消息
   */
  static send(conversationId: string, data: SendMessageRequest) {
    return request.post<BaseResult<Message>>({
      url: `/api/chat/conversations/${conversationId}/messages`,
      data
    })
  }

  /**
   * 发送消息（流式）
   */
  static sendStream(conversationId: string, data: SendMessageRequest): EventSource {
    const params = new URLSearchParams({
      content: data.content,
      enableRetrieval: String(data.enableRetrieval ?? true),
      kbIds: (data.kbIds ?? []).join(',')
    })
    
    return new EventSource(`/api/chat/conversations/${conversationId}/messages/stream?${params}`)
  }
}

