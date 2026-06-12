/**
 * @file system.ts
 * @description System health APIs used by the RAG workspace shell.
 */

import request from '@/utils/http'
import { ApiPaths } from '@/api/paths'

export interface DownstreamHealth {
  serviceId: string
  status: 'UP' | 'DOWN' | string
  required: boolean
}

export interface SystemHealth {
  status: 'UP' | 'DOWN' | string
  application: string
  timestamp: string
  uptime: string
  javaVersion: string
  activeProfiles: string[]
  downstreams?: {
    docParser?: DownstreamHealth
  }
}

export class SystemService {
  static getHealth() {
    return request.get<SystemHealth>({
      url: ApiPaths.test.health,
      showErrorMessage: false
    })
  }
}
