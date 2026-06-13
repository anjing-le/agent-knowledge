/**
 * 快速入口配置
 * 包含：应用列表、快速链接等配置
 */
import { WEB_LINKS } from '@/utils/constants'
import type { FastEnterConfig } from '@/types/config'

const fastEnterConfig: FastEnterConfig = {
  // 显示条件（屏幕宽度）
  minWidth: 1200,
  // 应用列表
  applications: [
    {
      name: 'RAG Pipeline',
      description: '查看脚手架如何生长出 RAG 全链路',
      icon: 'ri:route-line',
      iconColor: '#1f8a70',
      enabled: true,
      order: 1,
      routeName: 'RagPipeline'
    },
    {
      name: '知识库',
      description: '管理文档、切片和处理任务',
      icon: 'ri:folder-3-line',
      iconColor: '#2f80ed',
      enabled: true,
      order: 2,
      routeName: 'KnowledgeList'
    },
    {
      name: '知识问答',
      description: '基于知识库检索并生成引用回答',
      icon: 'ri:chat-3-line',
      iconColor: '#11a683',
      enabled: true,
      order: 3,
      routeName: 'ChatIndex'
    },
    {
      name: '架构说明',
      description: '查看脚手架生长 RAG agent 的设计',
      icon: 'ri:git-branch-line',
      iconColor: '#8e5cf7',
      enabled: true,
      order: 4,
      link: WEB_LINKS.GITHUB
    },
    {
      name: '启动指南',
      description: '查看三服务本地启动和验证方式',
      icon: 'ri:terminal-box-line',
      iconColor: '#f2994a',
      enabled: true,
      order: 5,
      link: `${WEB_LINKS.GITHUB}/blob/main/project_document/LOCAL_STARTUP_GUIDE.md`
    }
  ],
  // 快速链接
  quickLinks: [
    {
      name: 'RAG Pipeline',
      enabled: true,
      order: 1,
      routeName: 'RagPipeline'
    },
    {
      name: '知识库',
      enabled: true,
      order: 2,
      routeName: 'KnowledgeList'
    },
    {
      name: '知识问答',
      enabled: true,
      order: 3,
      routeName: 'ChatIndex'
    },
    {
      name: '路线图',
      enabled: true,
      order: 4,
      link: `${WEB_LINKS.GITHUB}/blob/main/project_document/ROADMAP.md`
    }
  ]
}

export default Object.freeze(fastEnterConfig)
