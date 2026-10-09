import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import KnowledgeBaseView from './views/KnowledgeBaseView.vue'
import ChatView from './views/ChatView.vue'
import ModelSettingsView from './views/ModelSettingsView.vue'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: HomeView }, { path: '/knowledge-bases', component: KnowledgeBaseView },
  { path: '/qa', component: ChatView }, { path: '/settings/models', component: ModelSettingsView },
] })
