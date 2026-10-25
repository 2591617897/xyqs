import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发环境全部走同源：vite 把 /api 与 /uploads 代理到网关 8888。
// 同源就没有跨域、没有 CORS 预检，能绕开一大类「本地跑得好线上跨域失败」的问题。
// 生产环境由 Nginx 同样把 /api 与 /uploads 反代到网关，保持同源。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8888', changeOrigin: true },
      '/uploads': { target: 'http://127.0.0.1:8888', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    // element-plus + icons 全量引入，首屏 chunk 必然超过默认 500KB 阈值，调高消警
    chunkSizeWarningLimit: 1500
  }
})
