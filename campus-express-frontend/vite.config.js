import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 配置：Vue 插件 + 路径别名 + 开发代理（解决前后端跨域）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ 指向 src 目录，组件内统一用 @/xxx 导入
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    open: false,
    // 代理：前端所有 /api 请求转发到后端 Spring Boot（8080），避免跨域
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 上传的静态资源（头像等 /uploads/**）同样转发到后端 8080
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket 实时推送端点：握手与数据帧都转发到后端 8080
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true
      }
    }
  }
})
