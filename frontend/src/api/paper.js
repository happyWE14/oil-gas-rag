import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DEFAULT_PAPER_LOCAL_PATH, FILE_API_BASE_URL } from '@/config/runtime'

const API_BASE = '/api/papers'

// ==================== 智能错误解析 ====================

/**
 * 解析后端错误，返回结构化错误信息
 */
const parseError = (error) => {
    if (!error.response) {
        return { type: 'network', message: '网络连接失败', retryable: true }
    }

    const status = error.response.status
    const data = error.response.data

    // AI 服务认证失败 (401)
    if (status === 401 ||
        (data?.message?.includes('401') && data?.message?.includes('AI')) ||
        (data?.error?.includes('Unauthorized'))) {
        return {
            type: 'ai_auth_failed',
            message: 'AI 预分析服务认证失败 (401)，请联系管理员检查 API Key 配置',
            detail: '后端配置的 DeepSeek/OpenAI API Key 可能已过期或无效',
            retryable: false,
            workaround: 'bypass_analysis'
        }
    }

    // AI 服务超时或不可用 (503/504)
    if (status === 503 || status === 504 || data?.message?.includes('timeout')) {
        return {
            type: 'ai_timeout',
            message: 'AI 分析服务暂时不可用',
            retryable: true,
            workaround: 'bypass_analysis'
        }
    }

    // DOI 重复 (409 或 500 包含约束名)
    if (status === 409 ||
        (data?.message?.includes('重复键') && data?.message?.includes('doi'))) {
        return {
            type: 'duplicate_doi',
            message: '该 DOI 的论文已存在',
            doi: extractDoiFromError(data?.message),
            retryable: false,
            workaround: 'use_existing'
        }
    }

    // 下载失败（PDF 链接无效）
    if (data?.message?.includes('download') || data?.message?.includes('PDF')) {
        return {
            type: 'download_failed',
            message: 'PDF 下载失败：链接可能已失效',
            retryable: true
        }
    }

    return {
        type: 'unknown',
        message: data?.message || `请求失败 (${status})`,
        retryable: status >= 500
    }
}

const extractDoiFromError = (msg) => {
    if (!msg) return null
    const match = msg.match(/\(([^)]+)\)/)
    return match ? match[1] : null
}

// ==================== 文件 URL 构造（根据后端 dromara 配置）====================

/**
 * 获取本地文件访问 URL
 * 文件域名由 VITE_API_ORIGIN 派生，默认与前端同源。
 * 访问规则: domain + fileKey (存储在数据库中的相对路径)
 *
 * @param {string} fileKey - 文件在存储中的 key/相对路径 (如: papers/xxxx/xxx.pdf)
 * @param {string} filename - 可选文件名
 * @returns {string} 完整的访问 URL
 */
export const getLocalFileUrl = (fileKey, filename = null) => {
    // 基础域名（与后端配置一致）
    const baseUrl = FILE_API_BASE_URL

    if (!fileKey) return null

    // 如果已经是完整 URL，直接返回
    if (fileKey.startsWith('http://') || fileKey.startsWith('https://')) {
        return fileKey
    }

    // 拼接 URL: baseUrl + fileKey
    const cleanKey = fileKey.startsWith('/') ? fileKey.substring(1) : fileKey

    let url = `${baseUrl}/${cleanKey}`

    // 如果提供了文件名，添加下载名称参数（部分浏览器支持）
    if (filename) {
        url += `?filename=${encodeURIComponent(filename)}`
    }

    return url
}

/**
 * 检查论文是否已下载到本地并可访问
 * 根据后端状态判断：DOWNLOADED 及后续状态都表示文件已存储
 */
export const isDownloaded = (paper) => {
    const downloadedStates = [
        'DOWNLOADED', 'TRANSFORMING', 'TRANSFORMED',
        'EMBEDDING', 'EMBEDDING_COMPLETED', 'EXTRACTING',
        'MATERIAL_EXTRACTED', 'COMPLETED'
    ]

    return downloadedStates.includes(paper?.state) &&
        (paper?.downloadInfo?.ossUrl || paper?.localFilePath || paper?.fileKey)
}

/**
 * 获取存储路径信息（调试用）
 */
export const getStoragePath = () => {
    return {
        basePath: DEFAULT_PAPER_LOCAL_PATH,
        domain: FILE_API_BASE_URL
    }
}

// ==================== 基础查询接口 ====================

export const getPapersByState = (state, limit = 20, config = {}) =>
    axios.get(API_BASE, { params: { state, limit }, ...config })

export const getPapersPage = (params = {}, config = {}) =>
    axios.get(`${API_BASE}/page`, { params, ...config })

// ⚠️ 关键修复：添加 SearchConfigList.vue 需要的别名导出
export const listPapersByPage = getPapersPage

/**
 * 获取论文详情
 * @param {string} paperId - 论文ID
 * @param {boolean|object} includeChunksOrConfig - 是否包含文本块，或传入 axios config 对象（含 signal）
 * @param {object} config - axios 配置（如 signal 用于取消请求）
 *
 * 使用示例：
 * getPaper('123', true) - 包含 chunks
 * getPaper('123', { signal: controller.signal }) - 支持请求取消
 * getPaper('123', false, { signal: controller.signal }) - 不包含 chunks 但支持取消
 */
export const getPaper = (paperId, includeChunksOrConfig = false, config = {}) => {
    // 兼容调用方式：如果第二个参数是对象，则视为 config（如 { signal: ... }）
    let includeChunks = includeChunksOrConfig
    if (typeof includeChunksOrConfig === 'object' && includeChunksOrConfig !== null) {
        config = includeChunksOrConfig
        includeChunks = false
    }

    return axios.get(`${API_BASE}/${paperId}`, {
        params: { includeChunks },
        ...config
    })
}

export const getPaperById = getPaper

export const getPaperByDoi = (doi, includeChunks = false, config = {}) =>
    axios.get(`${API_BASE}/by-doi`, { params: { doi, includeChunks }, ...config })

export const getPapers = (params = {}, config = {}) =>
    axios.get(`${API_BASE}/page`, { params, ...config })

// ==================== 论文创建与处理 ====================

export const createPaper = (data, config = {}) => axios.post(API_BASE, data, config)

/**
 * 创建论文并自动触发下载 - 智能处理 AI 失败降级
 */
export const createAndProcessPaper = async (paperData, options = {}) => {
    const {
        bypassAnalysis = false,
        autoStartDownload = true,
        onAIError,
        onDuplicate,
        onSuccess,
        onError
    } = options

    try {
        if (bypassAnalysis) {
            const { data } = await createPaper(paperData)
            let downloadStarted = false
            if (autoStartDownload && paperData.downloadUrl && data.paperId) {
                try {
                    await startDownload(data.paperId)
                    downloadStarted = true
                } catch (e) {
                    console.warn('下载触发失败:', e)
                }
            }
            if (onSuccess) onSuccess(data, { downloadStarted, bypassedAnalysis: true })
            return {
                success: true,
                paper: data,
                downloadStarted,
                bypassedAnalysis: true
            }
        }

        const { data: paper } = await createPaper(paperData)

        if (paper.state === 'IRRELEVANT') {
            const action = await ElMessageBox.confirm(
                'AI 判定该论文与石油工程领域不相关，是否仍继续处理？',
                '相关性提示',
                {
                    confirmButtonText: '仍要处理',
                    cancelButtonText: '跳过',
                    type: 'warning'
                }
            ).catch(() => 'cancel')

            if (action === 'cancel') {
                return { success: false, cancelled: true, reason: 'irrelevant' }
            }
        }

        let downloadStarted = false
        if (autoStartDownload && paperData.downloadUrl && paper.paperId) {
            try {
                await startDownload(paper.paperId)
                downloadStarted = true
            } catch (e) {
                console.warn('下载触发失败:', e)
            }
        }

        if (onSuccess) onSuccess(paper, { downloadStarted, bypassedAnalysis: false })
        return {
            success: true,
            paper,
            downloadStarted,
            bypassedAnalysis: false
        }

    } catch (error) {
        const parsed = parseError(error)

        if (parsed.type === 'duplicate_doi') {
            if (onDuplicate) {
                const action = await onDuplicate(parsed.doi, error.response?.data)
                if (action === 'reprocess') {
                    const { data: existing } = await getPaperByDoi(paperData.doi)
                    await startDownload(existing.paperId)
                    return { success: true, paper: existing, isReprocess: true }
                } else if (action === 'view') {
                    const { data: existing } = await getPaperByDoi(paperData.doi)
                    return { success: false, isDuplicate: true, paper: existing, action: 'view' }
                }
                return { success: false, cancelled: true }
            }
            throw { ...parsed, isDuplicate: true }
        }

        if (parsed.type === 'ai_auth_failed' || parsed.type === 'ai_timeout') {
            if (onAIError) {
                const decision = await onAIError(parsed)
                if (decision === 'bypass') {
                    return createAndProcessPaper(paperData, {
                        ...options,
                        bypassAnalysis: true
                    })
                } else if (decision === 'retry') {
                    await new Promise(r => setTimeout(r, 2000))
                    return createAndProcessPaper(paperData, options)
                }
                return { success: false, cancelled: true, reason: parsed.type }
            }
            throw parsed
        }

        if (onError) onError(parsed)
        throw error
    }
}

// ==================== 论文管理接口 ====================

export const deletePaper = (paperId, config = {}) =>
    axios.delete(`${API_BASE}/${paperId}`, config)

export const updatePaper = (paperId, data, config = {}) =>
    axios.put(`${API_BASE}/${paperId}`, data, config)

export const getPaperEvents = (paperId, limit = 50, config = {}) =>
    axios.get(`${API_BASE}/${paperId}/events`, { params: { limit }, ...config })

// ==================== 流程控制接口 ====================

export const startPreAnalysis = (paperId, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/pre-analysis/start`, null, config)

export const completePreAnalysis = (paperId, data, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/pre-analysis/complete`, data, config)

export const startDownload = (paperId, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/download/start`, null, config)

export const startPaperDownload = startDownload

export const completeDownload = (paperId, data, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/download/complete`, data, config)

export const startTransform = (paperId, provider = 'MINER_U', config = {}) =>
    axios.post(`${API_BASE}/${paperId}/transform/start`, null, { params: { provider }, ...config })

export const completeTransform = (paperId, data, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/transform/complete`, data, config)

export const startEmbedding = (paperId, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/embedding/start`, null, config)

export const completeEmbedding = (paperId, data, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/embedding/complete`, data, config)

export const startExtraction = (paperId, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/extraction/start`, null, config)

export const completeExtraction = (paperId, data, config = {}) =>
    axios.post(`${API_BASE}/${paperId}/extraction/complete`, data, config)

// ==================== 状态检查与轮询（支持 AbortController）====================

/**
 * 检查论文状态
 * @param {string} paperId - 论文ID
 * @param {object} config - axios 配置（可包含 signal 用于取消）
 */
export const checkPaperStatus = async (paperId, config = {}) => {
    const { data } = await getPaper(paperId, false, config)
    return {
        state: data.state,
        downloadInfo: data.downloadInfo,
        isCompleted: data.state === 'COMPLETED',
        isFailed: data.state === 'FAILED',
        isDownloaded: isDownloaded(data),
        progress: getProgressFromState(data.state),
        localFilePath: data.localFilePath || data.fileKey || data.downloadInfo?.ossUrl
    }
}

/**
 * 启动状态轮询（支持 AbortController 取消）
 * @param {string} paperId - 论文ID
 * @param {object} callbacks - 回调函数
 *   @param {function} onProgress - 状态更新回调 (status) => void
 *   @param {function} onCompleted - 完成回调 (status) => void
 *   @param {function} onFailed - 失败回调 ({ reason, paperId, status/error }) => void
 *   @param {function} onDownloaded - 已下载回调 (status) => void
 *   @param {number} interval - 轮询间隔（毫秒），默认 2000
 *   @param {number} maxAttempts - 最大尝试次数，默认 60
 *   @param {AbortSignal} signal - AbortController 的信号，用于取消轮询
 */
export const startStatusPolling = (paperId, callbacks = {}) => {
    const {
        onProgress,
        onCompleted,
        onFailed,
        onDownloaded,
        interval = 2000,
        maxAttempts = 60,
        signal // 支持 AbortSignal
    } = callbacks

    let attempts = 0
    let lastState = null
    let timeoutId = null
    let isStopped = false

    // 监听取消信号
    const cleanup = () => {
        if (timeoutId) {
            clearTimeout(timeoutId)
            timeoutId = null
        }
        isStopped = true
    }

    if (signal) {
        signal.addEventListener('abort', () => {
            cleanup()
            console.log(`[Paper ${paperId}] 轮询已取消`)
        }, { once: true })
    }

    const poll = async () => {
        if (isStopped || signal?.aborted) return

        if (attempts >= maxAttempts) {
            if (onFailed) onFailed({ reason: 'timeout', paperId, attempts })
            return
        }

        attempts++
        try {
            // 传递 signal 以便可以取消正在进行的请求
            const status = await checkPaperStatus(paperId, { signal })

            if (isStopped || signal?.aborted) return

            if (status.state !== lastState) {
                lastState = status.state
                if (onProgress) onProgress(status)
            }

            if (status.isDownloaded && onDownloaded) {
                onDownloaded(status)
                // 如果已完成，不需要继续轮询；否则继续
                if (status.isCompleted) {
                    if (onCompleted) onCompleted(status)
                    return
                }
            }

            if (status.isCompleted) {
                if (onCompleted) onCompleted(status)
            } else if (status.isFailed) {
                if (onFailed) onFailed({ reason: 'failed', paperId, status })
            } else if (!isStopped && !signal?.aborted) {
                // 继续轮询
                timeoutId = setTimeout(poll, interval)
            }
        } catch (error) {
            if (signal?.aborted) return

            // 如果是取消错误，静默处理
            if (error.name === 'AbortError' || error.message?.includes('aborted')) {
                return
            }

            console.warn(`[Paper ${paperId}] 轮询出错 (尝试 ${attempts}):`, error.message)

            if (attempts >= 3) {
                if (onFailed) onFailed({ reason: 'error', paperId, error, attempts })
            } else if (!isStopped && !signal?.aborted) {
                // 出错后延长间隔重试（指数退避）
                timeoutId = setTimeout(poll, interval * 2)
            }
        }
    }

    poll()
    return {
        stop: cleanup
    }
}

// ==================== 批量操作 ====================

export const batchProcessPapers = async (papersArray, options = {}) => {
    const results = {
        success: [],
        failed: [],
        aiFailed: [],
        duplicate: []
    }

    for (let i = 0; i < papersArray.length; i++) {
        const paper = papersArray[i]
        try {
            const result = await createAndProcessPaper(paper, {
                ...options,
                bypassAnalysis: options.bypassAnalysis
            })
            if (result.success) {
                results.success.push({ paper: result.paper, downloaded: result.downloadStarted })
            }
        } catch (error) {
            if (error.type === 'ai_auth_failed') {
                results.aiFailed.push({ paper, error })
            } else if (error.type === 'duplicate_doi') {
                results.duplicate.push({ paper, doi: error.doi })
            } else {
                results.failed.push({ paper, error: error.message })
            }
        }

        if (options.onProgress) {
            options.onProgress(i + 1, papersArray.length)
        }
    }

    return results
}

// ==================== 工具函数 ====================

const getProgressFromState = (state) => {
    const map = {
        'DISCOVERED': 10,
        'PRE_ANALYZING': 20,
        'PRE_RELEVANT': 30,
        'DOWNLOADING': 40,
        'DOWNLOADED': 50,
        'TRANSFORMING': 60,
        'TRANSFORMED': 70,
        'EMBEDDING': 80,
        'EMBEDDING_COMPLETED': 90,
        'EXTRACTING': 95,
        'MATERIAL_EXTRACTED': 98,
        'COMPLETED': 100
    }
    return map[state] || 0
}

// ==================== 统一导出 ====================
export default {
    getPapersByState,
    getPapersPage,
    listPapersByPage,  // 添加别名到默认导出
    getPaper,
    getPaperById,
    getPaperByDoi,
    createPaper,
    createAndProcessPaper,
    deletePaper,
    updatePaper,
    getPaperEvents,
    startPreAnalysis,
    completePreAnalysis,
    startDownload,
    startPaperDownload,
    completeDownload,
    startTransform,
    completeTransform,
    startEmbedding,
    completeEmbedding,
    startExtraction,
    completeExtraction,
    checkPaperStatus,
    startStatusPolling,
    batchProcessPapers,
    getLocalFileUrl,
    isDownloaded,
    getStoragePath,
    parseError,
}
export { parseError }
