import axios from 'axios'

const API_BASE = '/api/search'

// 关键词搜索 - POST /api/search/keyword
export const keywordSearch = (data) => axios.post(`${API_BASE}/keyword`, data)

// 创建深度研究会话 - POST /api/search/deep-research
export const createDeepResearch = (data) => axios.post(`${API_BASE}/deep-research`, data)

// 获取深度研究流 - GET /api/search/deep-research/{researchId}/stream
// 注意：这个返回 SSE 流，不在此封装 axios，直接在 View 中使用 EventSource
export const getDeepResearchStreamUrl = (researchId) =>
    `${API_BASE}/deep-research/${researchId}/stream`
