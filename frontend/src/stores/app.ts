import { defineStore } from 'pinia'
export const useAppStore = defineStore('app', { state: () => ({ projectName: '智能知识库管理' }) })
