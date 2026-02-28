import { AppRouteRecord } from '@/types/router'

export const knowledgeRoutes: AppRouteRecord = {
  name: 'KnowledgeBase',
  path: '/kb',
  component: '/index/index',
  meta: {
    title: 'menus.kb.title',
    icon: 'ri:book-3-line',
    roles: ['R_SUPER', 'R_ADMIN', 'R_USER', 'R_GUEST']
  },
  children: [
    // 知识管理
    {
      path: 'knowledge',
      name: 'KnowledgeList',
      component: '/knowledge/index',
      meta: {
        title: 'menus.kb.knowledge',
        icon: 'ri:folder-3-line',
        keepAlive: true
      }
    },
    // 智能对话
    {
      path: 'chat',
      name: 'ChatIndex',
      component: '/chat/index',
      meta: {
        title: 'menus.kb.chat',
        icon: 'ri:chat-3-line',
        keepAlive: false
      }
    }
  ]
}

// 注意：知识库详情和知识切片页面已移至 staticRoutes.ts
// 这样可以确保它们不会出现在菜单和标签页中
