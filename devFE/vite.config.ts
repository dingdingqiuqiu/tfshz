import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发时的后端地址：/api、/imgs、/types 都从这里取
const BACKEND = 'http://localhost:8081'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // 图片：数据库里存的是 /imgs/blogs/...、/imgs/icons/...，
      // 后端 MvcConfig 已把 /imgs/** 映射到磁盘上的 imgs 目录。
      // 不加这条代理，浏览器会去 5173 取图，Vite 只会返回 404/HTML，页面就是一堆碎图。
      '/imgs': {
        target: BACKEND,
        changeOrigin: true,
      },
      // 分类图标：数据库存的是 /types/ms.png，磁盘上在 imgs/types/ 下，转发时补上 /imgs 前缀。
      '/types': {
        target: BACKEND,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/types/, '/imgs/types'),
      },
    },
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
  },
})
