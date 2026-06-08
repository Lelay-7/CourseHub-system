import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import viteCompression from 'vite-plugin-compression'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())

  return {
    plugins: [
      vue(),
      // Gzip 压缩构建产物
      viteCompression({
        ext: '.gz',
        algorithm: 'gzip',
        deleteOriginFile: false,
        threshold: 10240 // 10KB 以上才压缩
      })
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    server: {
      port: 3000,
      host: '0.0.0.0',
      open: true,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '/api')
        }
      }
    },
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: false,
      // 分包策略：Element Plus 单独打包
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus'],
            'element-icons': ['@element-plus/icons-vue']
          }
        }
      },
      // 首屏资源 ≤ 500KB (gzip)
      chunkSizeWarningLimit: 500
    }
  }
})
