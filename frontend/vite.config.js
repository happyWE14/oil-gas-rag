import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [
        vue()  // 确保没有 vueJsx() 或其他 TS 插件
    ],
    resolve: {
        alias: {
            '@': resolve(__dirname, './src')
        },
        // 确保后缀包含 .js 而不是 .ts（或者两者都保留但 .js 优先）
        extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json']  // 如果有这行，确保 .js 在 .ts 前面，或删除这行使用默认
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8080',
                changeOrigin: true
            }
        }
    }
})
