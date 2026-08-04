import request from '@/utils/request'

const API_BASE = '/api/materials'
const ES_BASE = '/api/materials/es'  // ES 中间件基础路径

// ==================== 核心接口（原有）====================

/**
 * 获取材料列表（聚合）
 * 对应后端: GET /api/materials/page
 */
export const pageMaterials = (params) => {
    return request.get(`${API_BASE}/page`, { params })
        .then(res => ({ data: res }))
}

/**
 * 获取材料在各论文中的出现记录（分页）
 * 对应后端: GET /api/materials/papers/page
 */
export const pageMaterialPapers = (params) => {
    return request.get(`${API_BASE}/papers/page`, { params })
        .then(res => ({ data: res }))
}

// ==================== ES 同步管理接口 ====================

/**
 * 触发材料数据同步到 ES（全量或增量）
 * POST /api/materials/es/sync
 * @param {Object} params - { fullSync: boolean, materialKeys: string[], since: timestamp }
 */
export const syncMaterialsToEs = (data) => {
    return request.post(`${ES_BASE}/sync`, data)
}

/**
 * 获取同步任务状态
 * GET /api/materials/es/sync/status
 */
export const getSyncStatus = () => {
    return request.get(`${ES_BASE}/sync/status`)
}

/**
 * 从 ES 中删除指定材料（软删同步）
 * DELETE /api/materials/es/{materialKey}
 */
export const deleteFromEs = (materialKey) => {
    return request.delete(`${ES_BASE}/${materialKey}`)
}

/**
 * 重建 ES 索引（管理后台使用）
 * POST /api/materials/es/reindex
 */
export const rebuildEsIndex = (data = {}) => {
    return request.post(`${ES_BASE}/reindex`, data)
}

// ==================== ES 增强搜索接口 ====================

/**
 * ES 材料高级搜索（替代原有搜索，支持向量检索）
 * POST /api/materials/es/search
 *
 * 支持能力：
 * - 全文检索（material/property/observations）
 * - 指标范围筛选（如粘度 50-100 mPa·s）
 * - 实验条件筛选（temperature_C, shear_rate_s_1）
 * - 向量相似度搜索（2048维）
 * - 聚合统计（年份分布、指标统计、共现材料）
 */
export const searchMaterialsEs = (params) => {
    return request.post(`${ES_BASE}/search`, params)
        .then(res => ({ data: res }))
}

/**
 * 材料向量相似度搜索（基于 2048 维向量）
 * POST /api/materials/es/similar
 * @param {Object} data - { vector: number[], topK: number, minScore: number, materialKey?: string }
 */
export const searchSimilarMaterials = (data) => {
    return request.post(`${ES_BASE}/similar`, data)
}

/**
 * 材料名自动补全（Suggester）
 * GET /api/materials/es/suggest
 * @param {string} prefix - 输入前缀
 * @param {string} type - 类型: material/property/metric_key
 */
export const suggestMaterials = (prefix, type = 'material', size = 10) => {
    return request.get(`${ES_BASE}/suggest`, {
        params: { prefix, type, size }
    }).then(res => ({ data: res }))
}

/**
 * 智能过滤条件生成（根据当前结果动态建议）
 * POST /api/materials/es/smart-filters
 */
export const getSmartFilters = (currentQuery) => {
    return request.post(`${ES_BASE}/smart-filters`, {
        query: currentQuery,
        suggestFields: ['material', 'property', 'metric_key', 'year_published', 'unit']
    })
}

// ==================== 材料深度分析接口 ====================

/**
 * 材料全景详情（聚合 extraction + metrics + observations）
 * GET /api/materials/es/{materialKey}/panorama
 *
 * 返回：材料统计、指标分布（箱线图数据）、观察分类、相关推荐、研究趋势
 */
export const getMaterialPanorama = (materialKey, options = {}) => {
    return request.get(`${ES_BASE}/${materialKey}/panorama`, {
        params: {
            metricsAggregation: options.metricsAggregation || 'by_paper',
            observationsSort: options.observationsSort || 'confidence_desc',
            includeRelated: options.includeRelated !== false,
            relatedLimit: options.relatedLimit || 5,
            vectorSearch: options.vectorSearch || false // 是否包含相似材料
        }
    }).then(res => ({ data: res }))
}

/**
 * 材料对比分析（跨论文/跨条件对比）
 * POST /api/materials/es/compare
 *
 * 用途：对比同一材料在不同论文中的性能，或不同材料的同指标对比
 */
export const compareMaterials = (data) => {
    return request.post(`${ES_BASE}/compare`, {
        mode: data.mode || 'same_material_cross_papers', // same_material_cross_papers | cross_materials
        targetMaterials: data.targetMaterials || [data.targetMaterial],
        metricKeys: data.metricKeys || [],
        groupBy: data.groupBy || ['year_published'],
        statistics: data.statistics || ['avg', 'min', 'max', 'percentiles'],
        conditionsFilter: data.conditionsFilter || {} // 特定实验条件筛选
    })
}

/**
 * 获取指标分布直方图（用于筛选器 UI）
 * GET /api/materials/es/metrics/{metricKey}/distribution
 * @param {Object} params - { interval: number, material?: string, unit?: string, conditions?: object }
 */
export const getMetricDistribution = (metricKey, params = {}) => {
    return request.get(`${ES_BASE}/metrics/${metricKey}/distribution`, {
        params: {
            interval: params.interval || 10,
            material: params.material,
            unit: params.unit,
            ...params.conditions
        }
    }).then(res => ({ data: res }))
}

/**
 * 多指标相关性分析（散点图数据源）
 * POST /api/materials/es/correlation
 * @param {string[]} metricKeys - 需要分析相关性的指标列表（如 ['apparent_viscosity', 'yield_point']）
 * @param {string} materialKey - 可选：限定特定材料
 */
export const analyzeMetricsCorrelation = (metricKeys, materialKey = null) => {
    return request.post(`${ES_BASE}/correlation`, {
        metricKeys,
        materialKey,
        calculation: 'pearson' // 或 'spearman'
    })
}

// ==================== 材料知识图谱接口 ====================

/**
 * 材料共现图谱数据（力导向图）
 * GET /api/materials/es/graph/cooccurrence
 *
 * 节点：材料；边：共现关系（同一论文中出现）或指标相似度
 */
export const getMaterialCooccurrenceGraph = (params) => {
    return request.get(`${ES_BASE}/graph/cooccurrence`, {
        params: {
            centerMaterial: params.centerMaterial,
            depth: params.depth || 2,
            minCooccurrence: params.minCooccurrence || 2,
            metricCorrelation: params.metricCorrelation || false, // 是否基于指标相似度加权
            limit: params.limit || 50
        }
    }).then(res => ({ data: res }))
}

/**
 * 材料研究趋势分析（时间序列）
 * GET /api/materials/es/trends/{materialKey}
 */
export const getMaterialTrends = (materialKey, params = {}) => {
    return request.get(`${ES_BASE}/trends/${materialKey}`, {
        params: {
            granularity: params.granularity || 'year', // year/month
            metricKeys: params.metricKeys?.join(','),
            includeObservations: params.includeObservations || false
        }
    }).then(res => ({ data: res }))
}

// ==================== 批量与导出接口 ====================

/**
 * 批量材料质量评估（基于 confidence 和 observations 完整性）
 * POST /api/materials/es/batch-quality-report
 */
export const batchQualityReport = (materialKeys) => {
    return request.post(`${ES_BASE}/batch-quality-report`, {
        materialKeys,
        dimensions: ['completeness', 'consistency', 'confidence_distribution', 'source_diversity']
    })
}

/**
 * 材料数据导出（CSV/Excel，基于 ES 查询结果）
 * POST /api/materials/es/export
 * @param {string} format - csv | xlsx
 */
export const exportMaterials = (searchParams, format = 'xlsx') => {
    return request.post(`${ES_BASE}/export`, {
        ...searchParams,
        format
    }, {
        responseType: 'blob',
        headers: { 'Accept': format === 'csv' ? 'text/csv' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }
    })
}

/**
 * 批量获取材料卡片（用于对比工作台）
 * POST /api/materials/es/batch-cards
 */
export const getMaterialCardsBatch = (materialKeys) => {
    return request.post(`${ES_BASE}/batch-cards`, { materialKeys })
        .then(res => ({ data: res }))
}

// ==================== 扩展接口（Mapper 层，如后端已实现）====================

/**
 * 搜索材料指标（数值/范围查询）
 * 对应 Mapper: MaterialMetricSearchMapper.searchMetrics
 */
export const searchMetrics = (params) => {
    return request.get(`${API_BASE}/metrics/search`, { params })
        .then(res => ({ data: res }))
}

/**
 * 获取特定材料的所有结构化指标
 * 对应表: material_metric
 */
export const getMaterialMetrics = (materialKey, params = {}) => {
    return request.get(`${API_BASE}/metrics`, {
        params: { ...params, materialKey }
    }).then(res => ({ data: res }))
}

/**
 * 获取材料的观察记录
 * 对应表: material_observation
 */
export const getMaterialObservations = (materialKey, params = {}) => {
    return request.get(`${API_BASE}/observations`, {
        params: { ...params, materialKey }
    }).then(res => ({ data: res }))
}

/**
 * 全文搜索材料（FTS - PostgreSQL 原生，备用）
 * 对应 Mapper: MaterialSearchMapper.searchFts
 */
export const searchMaterialsFts = (params) => {
    return request.get(`${API_BASE}/search`, { params })
        .then(res => ({ data: res }))
}

/**
 * 获取所有材料Key列表（用于自动补全）
 */
export const getAllMaterialKeys = () => {
    return request.get(`${API_BASE}/keys`)
        .then(res => ({ data: res }))
}

/**
 * 获取材料详情（PG 直连，包含所有维度数据）
 * 聚合 material_extraction + metric + observation
 */
export const getMaterialDetail = (materialKey) => {
    return request.get(`${API_BASE}/detail`, {
        params: { materialKey }
    }).then(res => ({ data: res }))
}

// ==================== 便捷方法（业务封装）====================

/**
 * 加载材料完整数据（用于详情页）
 * 同时加载论文列表、指标、观察记录
 */
export const loadMaterialFullData = async (material, options = {}) => {
    const {
        includeChunks = true,
        chunkLimit = 5,
        minConfidence = null,
        useEsPanorama = true // 新增：优先使用 ES 全景接口
    } = options

    if (useEsPanorama) {
        // 使用 ES 增强接口获取聚合数据
        const panoramaPromise = getMaterialPanorama(material, {
            includeRelated: true,
            vectorSearch: true
        })

        const papersPromise = pageMaterialPapers({
            material,
            page: 1,
            size: 50,
            includeChunks,
            chunkLimit,
            minConfidence
        })

        const [panoramaRes, papersRes] = await Promise.all([panoramaPromise, papersPromise])

        return {
            panorama: panoramaRes.data,
            papers: papersRes.data?.items || [],
            total: papersRes.data?.total || 0,
        }
    }

    // 原有逻辑：并行加载论文列表和相关数据
    const papersPromise = pageMaterialPapers({
        material,
        page: 1,
        size: 50,
        includeChunks,
        chunkLimit,
        minConfidence
    })

    try {
        const papersRes = await papersPromise
        return {
            papers: papersRes.data?.items || [],
            total: papersRes.data?.total || 0,
        }
    } catch (error) {
        console.error('加载材料完整数据失败:', error)
        throw error
    }
}

// ==================== 翻译接口（原有）====================

/**
 * 翻译内容
 * @param {Object} data - 翻译请求参数
 * @param {string} data.text - 需要翻译的文本
 * @param {string} data.type - 翻译类型：MATERIAL_NAME, PAPER_TITLE, EVIDENCE_CHUNK, OBSERVATION
 * @param {string} data.context - 上下文信息，帮助提高翻译准确性
 */
export function translateContent(data) {
    return request({
        url: '/api/materials/translate',
        method: 'post',
        data
    })
}

/**
 * 批量翻译
 * @param {Array} data - 翻译请求数组
 */
export function translateBatch(data) {
    return request({
        url: '/api/materials/translate/batch',
        method: 'post',
        data
    })
}

// ==================== 统一导出 ====================
export default {
    // 基础 CRUD
    pageMaterials,
    pageMaterialPapers,
    getMaterialDetail,
    loadMaterialFullData,

    // ES 同步管理
    syncMaterialsToEs,
    getSyncStatus,
    deleteFromEs,
    rebuildEsIndex,

    // ES 搜索
    searchMaterialsEs,
    searchSimilarMaterials,
    suggestMaterials,
    getSmartFilters,

    // 分析
    getMaterialPanorama,
    compareMaterials,
    getMetricDistribution,
    analyzeMetricsCorrelation,

    // 图谱
    getMaterialCooccurrenceGraph,
    getMaterialTrends,

    // 批量与导出
    batchQualityReport,
    exportMaterials,
    getMaterialCardsBatch,

    // 扩展
    searchMetrics,
    getMaterialMetrics,
    getMaterialObservations,
    searchMaterialsFts,
    getAllMaterialKeys,

    // 翻译
    translateContent,
    translateBatch
}
