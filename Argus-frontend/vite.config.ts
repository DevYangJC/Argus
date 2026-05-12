import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
const apiProxyTarget = process.env.VITE_DEV_PROXY_TARGET ?? 'http://localhost:10001'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: apiProxyTarget,
        changeOrigin: true,
        // SSE 关键：禁用代理缓冲；Vite 底层 http-proxy 默认会缓冲响应流，
        // 会导致 text/event-stream 里的 delta 堆到最后才一并送达浏览器，
        // 从而破坏打字机效果。设置为 false 后 http-proxy 直接把字节透传。
        selfHandleResponse: false,
        ws: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            const contentType = proxyRes.headers['content-type'] ?? ''
            if (typeof contentType === 'string' && contentType.includes('text/event-stream')) {
              // 告诉下游（Vite dev server / 浏览器）这条连接保持活跃、禁用任何压缩/缓冲。
              proxyRes.headers['cache-control'] = 'no-cache, no-transform'
              proxyRes.headers['x-accel-buffering'] = 'no'
              delete proxyRes.headers['content-length']
            }
          })
        },
      },
    },
  },
})
