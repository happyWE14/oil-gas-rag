import axios from 'axios'

const API_BASE = '/api/tasks'

// 获取任务统计汇总 - GET /api/tasks/summary
// 响应: { waiting: {...}, running: {...}, failed: {...}, succeed: {...}, canceled: {...} }
export const getTaskSummary = () => axios.get(`${API_BASE}/summary`)

// 获取任务列表 - GET /api/tasks?status=&paperId=&limit=20
// 响应: 数组 []
export const getTasks = (params = {}) => axios.get(API_BASE, { params })

// 获取任务详情 - GET /api/tasks/{id}
export const getTask = (id) => axios.get(`${API_BASE}/${id}`)

// 创建任务 - POST /api/tasks
// 请求体: { taskType, taskName, description, parameters, paperId, maxRetries, createdBy }
export const createTask = (data) => axios.post(API_BASE, data)

// 更新任务状态 - PUT /api/tasks/{id}/status
// 请求体: { status, result, errorMessage }
export const updateTaskStatus = (id, data) => axios.put(`${API_BASE}/${id}/status`, data)

// 重试任务 - POST /api/tasks/{id}/retry?extraAttempts=3
export const retryTask = (id, extraAttempts = 3) =>
    axios.post(`${API_BASE}/${id}/retry`, null, { params: { extraAttempts } })

// 取消任务 - POST /api/tasks/{id}/cancel
// 请求体: { status, result, errorMessage }
export const cancelTask = (id, data) => axios.post(`${API_BASE}/${id}/cancel`, data)

// 删除任务 - DELETE /api/tasks/{id}
export const deleteTask = (id) => axios.delete(`${API_BASE}/${id}`)
// api/task.js 中添加
export const getTaskStats = (params) => {
    return request.get('/api/tasks/stats', { params })
}
