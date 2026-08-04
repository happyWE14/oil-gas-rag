import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { API_ORIGIN } from '@/config/runtime'

// 创建axios实例
const service = axios.create({
    baseURL: API_ORIGIN || undefined,
    timeout: 60000
})

// request拦截器
service.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token
        }
        return config
    },
    error => {
        console.error('请求错误:', error)
        return Promise.reject(error)
    }
)

// response拦截器 - 关键修复
service.interceptors.response.use(
    response => {
        const res = response.data

        // 🔧 关键修复：如果后端没有返回 code 字段（标准 REST 风格），直接返回数据
        if (res.code === undefined || res.code === null) {
            return res
        }

        // 如果自定义状态码不是200，视为错误
        if (res.code !== 200) {
            ElMessage({
                message: res.message || '请求失败',
                type: 'error',
                duration: 5 * 1000
            })

            if (res.code === 401) {
                ElMessageBox.confirm(
                    '登录状态已过期，请重新登录',
                    '确认退出',
                    {
                        confirmButtonText: '重新登录',
                        cancelButtonText: '取消',
                        type: 'warning'
                    }
                ).then(() => {
                    localStorage.removeItem('token')
                    window.location.href = '/login'
                })
            }

            return Promise.reject(new Error(res.message || '请求失败'))
        } else {
            return res
        }
    },
    error => {
        console.error('响应错误:', error)

        let message = '网络异常，请稍后重试'
        if (error.response) {
            const status = error.response.status
            const data = error.response.data

            // 🔧 对于 201 Created、202 Accepted、204 No Content 等成功状态，不报错
            if (status >= 200 && status < 300) {
                return Promise.resolve(data)
            }

            switch (status) {
                case 400:
                    message = data?.message || '请求参数错误'
                    break
                case 401:
                    message = '登录状态已过期，请重新登录'
                    break
                case 403:
                    message = '没有权限访问该资源'
                    break
                case 404:
                    message = '请求的资源不存在'
                    break
                case 500:
                    message = data?.message || '服务器内部错误'
                    break
                default:
                    message = data?.message || `请求错误 (${status})`
            }
        } else if (error.request) {
            message = '网络请求失败，请检查网络连接'
        }

        ElMessage({
            message: message,
            type: 'error',
            duration: 5 * 1000
        })

        return Promise.reject(error)
    }
)

export default service
