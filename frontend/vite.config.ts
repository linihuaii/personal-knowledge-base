import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { loadEnv } from 'vite'
export default defineConfig(({ mode }) => ({ plugins: [vue()], server: { port: 5173, proxy: { '/api': loadEnv(mode, process.cwd(), '').VITE_API_PROXY || 'http://127.0.0.1:8080' } } }))
