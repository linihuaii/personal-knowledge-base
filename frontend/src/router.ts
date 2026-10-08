import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import KnowledgeBaseView from './views/KnowledgeBaseView.vue'
import PlaceholderView from './views/PlaceholderView.vue'
export const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: HomeView }, { path: '/knowledge-bases', component: KnowledgeBaseView },
  { path: '/qa', component: PlaceholderView, props: { title: '问答工作台', stage: 'S05' } },
] })
