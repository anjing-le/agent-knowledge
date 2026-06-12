<template>
  <div class="chat-layout">
    <!-- 左侧会话列表 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <el-button type="primary" class="new-chat-btn" @click="handleNewChat">
          <el-icon><Plus /></el-icon>
          新建对话
        </el-button>
      </div>

      <!-- 会话列表 -->
      <div class="conversation-list">
        <div class="list-title">历史会话</div>
        <div
          v-for="conv in conversationList"
          :key="conv.conversationId"
          class="conversation-item"
          :class="{ active: currentConversationId === conv.conversationId }"
          @click="selectConversation(conv)"
        >
          <div class="conv-content">
            <span class="conv-title">{{ conv.title }}</span>
            <span class="conv-time">{{ formatDate(conv.updatedAt) }}</span>
          </div>
          <el-button
            class="delete-conv-btn"
            size="small"
            circle
            @click.stop="handleDeleteConversation(conv)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
      </div>

    </div>

    <!-- 右侧聊天区域 -->
    <div class="main-content">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="header-left">
          <h2 class="chat-title">{{ currentConversation?.title || '新对话' }}</h2>
        </div>
        <div class="header-right">
          <el-select
            v-model="selectedKbIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择知识库"
            class="kb-select"
          >
            <el-option
              v-for="kb in knowledgeList"
              :key="kb.kbId"
              :label="kb.name"
              :value="kb.kbId"
            />
          </el-select>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesContainer">
        <div v-if="messageList.length === 0" class="empty-chat">
          <div class="empty-icon">
            <el-icon><ChatLineRound /></el-icon>
          </div>
          <h3 class="empty-title">开始新对话</h3>
          <p class="empty-desc">选择知识库后，输入问题开始智能问答</p>
        </div>

        <div v-else class="messages-wrapper">
          <div
            v-for="msg in messageList"
            :key="msg.messageId"
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <img
                v-if="msg.role === 'user'"
                src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png"
                alt="用户"
              />
              <div v-else class="ai-avatar">
                <el-icon><ChatLineRound /></el-icon>
              </div>
            </div>
            <div class="message-content">
              <div class="message-text">{{ msg.content }}</div>
              <div v-if="msg.references && msg.references.length > 0" class="message-references">
                <div class="ref-title">参考来源：</div>
                <div
                  v-for="(ref, index) in msg.references"
                  :key="index"
                  class="ref-item"
                >
                  <div class="ref-header">
                    <span class="ref-doc">{{ ref.docName || '未知文档' }}</span>
                    <span class="ref-score" v-if="ref.score">相似度: {{ (ref.score * 100).toFixed(1) }}%</span>
                  </div>
                  <div class="ref-meta" v-if="formatReferenceMeta(ref)">
                    {{ formatReferenceMeta(ref) }}
                  </div>
                  <div class="ref-content" v-if="ref.content">{{ ref.content.length > 150 ? ref.content.substring(0, 150) + '...' : ref.content }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <div class="input-wrapper">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="3"
            placeholder="输入您的问题..."
            class="message-input"
            @keydown.enter.ctrl="handleSend"
          />
          <el-button
            type="primary"
            class="send-btn"
            :loading="sending"
            :disabled="!inputMessage.trim()"
            @click="handleSend"
          >
            <el-icon><Position /></el-icon>
            发送
          </el-button>
        </div>
        <div class="input-tips">
          按 Ctrl + Enter 发送消息
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Position } from '@element-plus/icons-vue'
import {
  ConversationService,
  MessageService,
  type Conversation,
  type Message,
  type MessageReference
} from '@/api/chat'
import { KnowledgeService, type KnowledgeBase } from '@/api/knowledge'

// 路由
const router = useRouter()

// 响应式数据
const conversationList = ref<Conversation[]>([])
const currentConversationId = ref<string>('')
const currentConversation = ref<Conversation | null>(null)
const messageList = ref<Message[]>([])
const knowledgeList = ref<KnowledgeBase[]>([])
const selectedKbIds = ref<string[]>([])
const inputMessage = ref('')
const sending = ref(false)
const messagesContainer = ref<HTMLElement | null>(null)

// 格式化日期
const formatDate = (dateStr?: string) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN')
}

const formatReferenceMeta = (ref: MessageReference) => {
  const metadata = ref.metadata || {}
  const parts: string[] = []
  if (Array.isArray(metadata.page_idx) && metadata.page_idx.length > 0) {
    parts.push(`页码 ${metadata.page_idx.join(', ')}`)
  }
  if (typeof metadata.content_type === 'string' && metadata.content_type) {
    parts.push(metadata.content_type)
  }
  return parts.join(' · ')
}

// 获取会话列表
const fetchConversations = async () => {
  try {
    const res = await ConversationService.getList({ page: 1, size: 50 })
    if (res) {
      conversationList.value = res.records || []
    }
  } catch (error) {
    console.error('获取会话列表失败:', error)
    // 使用模拟数据
    conversationList.value = []
  }
}

// 获取知识库列表
const fetchKnowledgeList = async () => {
  try {
    const res = await KnowledgeService.getList({ page: 1, size: 100 })
    if (res) {
      knowledgeList.value = res.records || []
    }
  } catch (error) {
    console.error('获取知识库列表失败:', error)
    // 使用模拟数据
    knowledgeList.value = [
      { kbId: '1', name: '产品知识库', description: '产品相关知识', isEnabled: true }
    ]
  }
}

// 获取消息列表
const fetchMessages = async (conversationId: string) => {
  try {
    const res = await MessageService.getList(conversationId, { page: 1, size: 100 })
    if (res) {
      messageList.value = res
      scrollToBottom()
    }
  } catch (error) {
    console.error('获取消息列表失败:', error)
    messageList.value = []
  }
}

// 选择会话
const selectConversation = async (conv: Conversation) => {
  currentConversationId.value = conv.conversationId
  currentConversation.value = conv
  selectedKbIds.value = conv.kbIds || []
  await fetchMessages(conv.conversationId)
}

// 新建对话
const handleNewChat = async () => {
  try {
    const res = await ConversationService.create({
      title: '新对话',
      kbIds: selectedKbIds.value
    })
    if (res) {
      const newConv = res
      conversationList.value.unshift(newConv)
      await selectConversation(newConv)
    }
  } catch (error) {
    console.error('创建会话失败:', error)
    // 本地模拟创建
    const newConv: Conversation = {
      conversationId: Date.now().toString(),
      title: '新对话',
      kbIds: selectedKbIds.value,
      messageCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    conversationList.value.unshift(newConv)
    await selectConversation(newConv)
  }
}

// 删除会话
const handleDeleteConversation = async (conv: Conversation) => {
  try {
    await ElMessageBox.confirm(`确定要删除对话「${conv.title}」吗？`, '删除确认', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await ConversationService.delete(conv.conversationId)
    
    const index = conversationList.value.findIndex(c => c.conversationId === conv.conversationId)
    if (index > -1) {
      conversationList.value.splice(index, 1)
    }
    
    if (currentConversationId.value === conv.conversationId) {
      currentConversationId.value = ''
      currentConversation.value = null
      messageList.value = []
    }
    
    ElMessage.success('对话删除成功')
  } catch {
    // 用户取消或删除失败
  }
}

// 发送消息
const handleSend = async () => {
  if (!inputMessage.value.trim() || sending.value) return

  // 如果没有当前会话，先创建一个
  if (!currentConversationId.value) {
    await handleNewChat()
  }

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  sending.value = true

  // 添加用户消息到列表
  const userMsg: Message = {
    messageId: Date.now().toString(),
    conversationId: currentConversationId.value,
    role: 'user',
    content: userMessage,
    createdAt: new Date().toISOString()
  }
  messageList.value.push(userMsg)
  scrollToBottom()

  try {
    const res = await MessageService.send(currentConversationId.value, {
      content: userMessage,
      enableRetrieval: true,
      kbIds: selectedKbIds.value
    })

    if (res) {
      messageList.value.push(res)
      scrollToBottom()
      
      // 更新会话标题
      if (currentConversation.value && messageList.value.length <= 2) {
        currentConversation.value.title = userMessage.slice(0, 30) + (userMessage.length > 30 ? '...' : '')
      }
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    // 模拟回复
    const aiMsg: Message = {
      messageId: (Date.now() + 1).toString(),
      conversationId: currentConversationId.value,
      role: 'assistant',
      content: '抱歉，我暂时无法回答您的问题。请稍后再试。',
      createdAt: new Date().toISOString()
    }
    messageList.value.push(aiMsg)
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}


// 组件挂载时初始化
onMounted(() => {
  fetchConversations()
  fetchKnowledgeList()
})
</script>

<style lang="scss" scoped>
.chat-layout {
  display: flex;
  height: 100%;
  background: var(--el-bg-color-page);
}

// 左侧会话列表
.chat-sidebar {
  display: flex;
  flex-direction: column;
  width: 280px;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);

  .sidebar-header {
    padding: 16px;

    .new-chat-btn {
      width: 100%;
      height: 40px;
      font-weight: 600;
    }
  }

  .conversation-list {
    flex: 1;
    padding: 16px 20px;
    overflow-y: auto;

    .list-title {
      margin-bottom: 12px;
      font-size: 12px;
      font-weight: 600;
      color: #999;
      text-transform: uppercase;
    }

    .conversation-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 14px;
      margin-bottom: 8px;
      cursor: pointer;
      background: #fff;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      transition: all 0.3s ease;

      &:hover {
        background: #f9f9f9;
        border-color: #ffc300;

        .delete-conv-btn {
          opacity: 1;
        }
      }

      &.active {
        background: #f1e0a9;
        border-color: #ffc300;
      }

      .conv-content {
        flex: 1;
        min-width: 0;

        .conv-title {
          display: block;
          overflow: hidden;
          font-size: 14px;
          font-weight: 500;
          color: #131921;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .conv-time {
          display: block;
          margin-top: 4px;
          font-size: 12px;
          color: #999;
        }
      }

      .delete-conv-btn {
        flex-shrink: 0;
        margin-left: 8px;
        color: #999;
        opacity: 0;
        transition: opacity 0.3s;

        &:hover {
          color: #f56c6c;
          background: rgb(245 108 108 / 10%);
        }
      }
    }
  }

  .sidebar-footer {
    padding: 20px;
    border-top: 1px solid #f0e68c;

    .user-profile {
      display: flex;
      gap: 12px;
      align-items: center;

      .user-avatar {
        width: 36px;
        height: 36px;
        overflow: hidden;
        border-radius: 50%;

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }
      }

      .user-name {
        font-size: 14px;
        font-weight: 500;
        color: #131921;
      }
    }
  }
}

// 主内容区域
.main-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  background: #f9f7e3;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e0e0e0;

  .header-left {
    .chat-title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      color: #131921;
    }
  }

  .header-right {
    .kb-select {
      width: 300px;
    }
  }
}

.chat-messages {
  flex: 1;
  padding: 24px;
  overflow-y: auto;

  .empty-chat {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    text-align: center;

    .empty-icon {
      margin-bottom: 16px;
      font-size: 64px;
      color: #ffc300;
    }

    .empty-title {
      margin: 0 0 8px;
      font-size: 24px;
      font-weight: 600;
      color: #131921;
    }

    .empty-desc {
      margin: 0;
      font-size: 14px;
      color: #666;
    }
  }

  .messages-wrapper {
    max-width: 900px;
    margin: 0 auto;
  }

  .message-item {
    display: flex;
    gap: 16px;
    margin-bottom: 24px;

    &.user {
      flex-direction: row-reverse;

      .message-content {
        background: #ffc300;
        border-color: #e6b420;

        .message-text {
          color: #131921;
        }
      }
    }

    &.assistant {
      .message-content {
        background: #fff;
        border-color: #e0e0e0;
      }
    }

    .message-avatar {
      flex-shrink: 0;
      width: 40px;
      height: 40px;
      overflow: hidden;
      border-radius: 50%;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .ai-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        font-size: 20px;
        color: #fff;
        background: linear-gradient(135deg, #ffc300 0%, #e6b420 100%);
      }
    }

    .message-content {
      max-width: 70%;
      padding: 16px 20px;
      border: 1px solid;
      border-radius: 12px;

      .message-text {
        margin: 0;
        font-size: 14px;
        line-height: 1.6;
        color: #333;
        white-space: pre-wrap;
      }

      .message-references {
        padding-top: 12px;
        margin-top: 12px;
        border-top: 1px solid #e0e0e0;

        .ref-title {
          margin-bottom: 8px;
          font-size: 12px;
          font-weight: 600;
          color: #999;
        }

        .ref-item {
          padding: 8px 10px;
          margin-bottom: 6px;
          font-size: 12px;
          background: #f9f9f9;
          border-radius: 6px;
          border-left: 3px solid #67c23a;

          .ref-header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 4px;
          }

          .ref-doc {
            color: #333;
            font-weight: 500;
          }

          .ref-score {
            color: #67c23a;
            flex-shrink: 0;
            margin-left: 12px;
          }

          .ref-meta {
            margin-bottom: 4px;
            color: #409eff;
            font-size: 11px;
          }

          .ref-content {
            color: #888;
            line-height: 1.5;
            font-size: 11px;
          }
        }
      }
    }
  }
}

.chat-input-area {
  padding: 16px 24px 24px;
  background: #fff;
  border-top: 1px solid #e0e0e0;

  .input-wrapper {
    display: flex;
    gap: 16px;
    max-width: 900px;
    margin: 0 auto;

    .message-input {
      flex: 1;

      :deep(.el-textarea__inner) {
        padding: 12px 16px;
        font-size: 14px;
        border-radius: 12px;
        resize: none;

        &:focus {
          border-color: #ffc300;
          box-shadow: 0 0 0 2px rgb(255 195 0 / 20%);
        }
      }
    }

    .send-btn {
      align-self: flex-end;
      height: 44px;
      padding: 0 24px;
      font-weight: 600;
      color: #131921;
      background: #ffc300;
      border-color: #ffc300;
      border-radius: 12px;

      &:hover {
        background: #e6b420;
        border-color: #e6b420;
      }

      &:disabled {
        color: #999;
        background: #f0f0f0;
        border-color: #ddd;
      }
    }
  }

  .input-tips {
    max-width: 900px;
    margin: 8px auto 0;
    font-size: 12px;
    color: #999;
    text-align: right;
  }
}

// 按钮样式优化
:deep(.el-button) {
  font-weight: 500;
  border-radius: 6px;

  &.el-button--primary {
    color: #131921;
    background: #ffc300;
    border-color: #ffc300;

    &:hover {
      background: #e6b420;
      border-color: #e6b420;
    }
  }
}

// 选择器样式
:deep(.el-select) {
  .el-input__wrapper {
    border-radius: 8px;

    &:hover {
      border-color: #ffc300;
    }

    &.is-focus {
      border-color: #ffc300;
      box-shadow: 0 0 0 2px rgb(255 195 0 / 20%);
    }
  }
}
</style>
