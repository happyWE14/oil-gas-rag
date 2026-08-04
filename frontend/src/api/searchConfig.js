import request from '@/utils/request'
import axios from 'axios'


export const getSearchConfigs = () => {
    return axios.get('/api/search-configs')
}

// 获取单个搜索配置
export function getSearchConfig(id) {
    return request({
        url: `/api/search-configs/${id}`,
        method: 'get'
    })
}

// 创建搜索配置
export function createSearchConfig(data) {
    return request({
        url: '/api/search-configs',
        method: 'post',
        data
    })
}


// 立即执行搜索配置
export function runSearchConfig(id) {
    return request({
        url: `/api/search-configs/${id}/run`,
        method: 'post'
    })
}

// 暂停搜索配置
export function pauseSearchConfig(id) {
    return request({
        url: `/api/search-configs/${id}/pause`,
        method: 'post'
    })
}

// 恢复搜索配置
export function resumeSearchConfig(id) {
    return request({
        url: `/api/search-configs/${id}/resume`,
        method: 'post'
    })
}


export const getTasks = (params = { limit: 200 }) => {
    return axios.get('/api/tasks', { params })
}

// 可选：重试和取消任务
export const retryTask = (taskId) => {
    return axios.post(`/api/tasks/${taskId}/retry`)
}

export const cancelTask = (taskId) => {
    return axios.post(`/api/tasks/${taskId}/cancel`)
}
// 获取详情
export const getSearchConfigDetail = (id) => request.get(`/api/search-configs/${id}`)

// 获取统计
export const getSearchConfigStats = (id) => request.get(`/api/search-configs/${id}/stats`)

// 更新配置
export const updateSearchConfig = (id, data) => request.put(`/api/search-configs/${id}`, data)

// 删除配置
export const deleteSearchConfig = (id) => request.delete(`/api/search-configs/${id}`)
