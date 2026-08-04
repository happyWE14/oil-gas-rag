<template>
  <div class="material-search-container">
    <!-- 搜索区域（保持原样） -->
    <el-card class="search-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="title-section">
            <span class="main-title">材料智能检索</span>
            <el-tag type="primary" effect="dark" class="engine-tag">
              <el-icon><Search /></el-icon>
              ES + PgVector 混合引擎
            </el-tag>
          </div>
          <el-radio-group v-model="searchMode" size="small" @change="handleModeChange">
            <el-radio-button label="text">🔤 文本搜索</el-radio-button>
            <el-radio-button label="vector">🧠 语义搜索</el-radio-button>
            <el-radio-button label="hybrid">⚡ 混合搜索</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-form :model="searchForm" label-position="top" class="search-form">
        <el-row :gutter="20">
          <el-col :xs="24" :sm="24" :md="8">
            <el-form-item label="材料名称">
              <el-autocomplete
                  v-model="searchForm.materialName"
                  :fetch-suggestions="querySearch"
                  placeholder="输入材料名称，如：Lithium Iron Phosphate"
                  clearable
                  style="width: 100%"
                  @select="handleSelect"
                  :disabled="searchMode === 'vector' && !searchForm.queryText"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-autocomplete>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6">
            <el-form-item label="性能指标">
              <el-select
                  v-model="searchForm.metricKey"
                  placeholder="选择指标类型"
                  clearable
                  style="width: 100%"
              >
                <el-option label="电导率 (Conductivity)" value="conductivity" />
                <el-option label="粘度 (Viscosity)" value="viscosity" />
                <el-option label="密度 (Density)" value="density" />
                <el-option label="熔点 (Melting Point)" value="melting_point" />
                <el-option label="热导率 (Thermal Conductivity)" value="thermal_conductivity" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :sm="12" :md="6" v-if="searchForm.metricKey">
            <el-form-item label="数值范围">
              <div class="range-inputs">
                <el-input-number
                    v-model="searchForm.metricRange.min"
                    placeholder="最小值"
                    :precision="2"
                    style="width: 45%"
                />
                <span class="range-separator">-</span>
                <el-input-number
                    v-model="searchForm.metricRange.max"
                    placeholder="最大值"
                    :precision="2"
                    style="width: 45%"
                />
              </div>
            </el-form-item>
          </el-col>

          <el-col :xs="24" :sm="12" :md="4">
            <el-form-item label="发表年份">
              <el-date-picker
                  v-model="yearRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始"
                  end-placeholder="结束"
                  value-format="YYYY"
                  style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="searchMode === 'vector' || searchMode === 'hybrid'">
          <el-col :span="24">
            <el-form-item label="语义描述（将转换为向量进行相似度搜索）">
              <el-input
                  v-model="searchForm.queryText"
                  type="textarea"
                  :rows="3"
                  placeholder="输入描述性文本，例如：'具有高电导率的锂离子电池正极材料，适用于高温环境'。系统将转换为2048维向量进行语义匹配。"
                  maxlength="500"
                  show-word-limit
              />
              <div class="vector-tip">
                <el-icon><InfoFilled /></el-icon>
                <span>语义搜索会匹配概念相似的材料，即使关键词不完全相同</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" class="action-row">
          <el-col :span="24" class="button-group">
            <el-button type="primary" size="large" @click="handleSearch" :loading="loading" class="search-btn">
              <el-icon><Search /></el-icon>
              开始检索
            </el-button>
            <el-button size="large" @click="resetForm">
              <el-icon><RefreshRight /></el-icon>
              重置条件
            </el-button>
            <el-divider direction="vertical" />
            <el-tooltip content="将当前PG中的所有材料数据同步到Elasticsearch" placement="top">
              <el-button type="warning" size="large" plain @click="handleSyncAll" :loading="syncLoading">
                <el-icon><Refresh /></el-icon>
                全量同步
              </el-button>
            </el-tooltip>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 结果区域 - 抽屉式卡片 -->
    <el-card class="result-card" v-loading="loading">
      <template #header v-if="total > 0 || loading">
        <div class="result-header">
          <div class="result-stats">
            <span class="result-title">检索结果</span>
            <el-tag type="success" effect="light" class="count-tag">
              共 {{ total }} 条记录
            </el-tag>
            <el-tag v-if="searchMode === 'hybrid'" type="warning" effect="light" size="small">
              RRF融合排序
            </el-tag>
          </div>
          <div class="view-controls">
            <el-tooltip content="全部展开" placement="top">
              <el-button size="small" circle @click="expandAll">
                <el-icon><ArrowDown /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="全部收起" placement="top">
              <el-button size="small" circle @click="collapseAll">
                <el-icon><ArrowUp /></el-icon>
              </el-button>
            </el-tooltip>
            <el-radio-group v-model="sortBy" size="small" @change="handleSortChange">
              <el-radio-button label="relevance">相关度</el-radio-button>
              <el-radio-button label="confidence">置信度</el-radio-button>
              <el-radio-button label="year">发表年份</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && results.length === 0" description="请输入搜索条件开始检索" class="custom-empty">
        <template #image>
          <el-icon :size="60" color="#dcdfe6"><Search /></el-icon>
        </template>
      </el-empty>

      <div v-else class="drawer-list">
        <div
            v-for="(item, index) in results"
            :key="item.docId"
            class="drawer-item"
            :class="{ 'is-expanded': expandedItems.has(item.docId), 'top-result': index < 3 && searchMode === 'hybrid' }"
        >
          <!-- 头部摘要区 - 始终显示 -->
          <div class="drawer-header" @click="toggleExpand(item.docId)">
            <div class="header-main">
              <div class="rank-section">
                <div class="rank-badge" v-if="index < 3">{{ index + 1 }}</div>
                <div class="rank-number" v-else>{{ index + 1 }}</div>
              </div>

              <div class="content-preview">
                <div class="title-row">
                  <h3 class="material-name" :class="{ 'translated-text': item.showTranslation }">
                    {{ item.showTranslation && item.translatedName ? item.translatedName : item.material?.materialName }}
                  </h3>

                  <!-- 快捷翻译按钮 -->
                  <div class="header-actions" @click.stop>
                    <template v-if="!item.translatedName">
                      <el-button
                          size="small"
                          type="primary"
                          text
                          :loading="item.translating"
                          @click="translateMaterialName(item)"
                      >
                        <el-icon><Reading /></el-icon>
                        翻译
                      </el-button>
                    </template>
                    <template v-else>
                      <el-tag size="small" :type="item.translationSource === 'cache' ? 'success' : 'warning'" effect="light" class="source-tag">
                        {{ item.translationSource === 'cache' ? '缓存' : 'API' }}
                      </el-tag>
                      <el-button
                          size="small"
                          :type="item.showTranslation ? 'primary' : 'default'"
                          @click="toggleMaterialTranslation(item)"
                      >
                        {{ item.showTranslation ? '原文' : '译文' }}
                      </el-button>
                    </template>
                  </div>
                </div>

                <div class="preview-metrics">
                  <el-tag size="small" type="warning" effect="dark" v-if="item.material?.confidence" class="confidence-tag">
                    <el-icon><StarFilled /></el-icon>
                    置信度 {{ (item.material.confidence * 100).toFixed(1) }}%
                  </el-tag>
                  <el-tag size="small" type="info" v-if="item.paper?.year">
                    <el-icon><Calendar /></el-icon>
                    {{ item.paper.year }}年
                  </el-tag>
                  <el-tag size="small" type="success" v-if="item.metrics?.length">
                    <el-icon><TrendCharts /></el-icon>
                    {{ item.metrics.length }} 项指标
                  </el-tag>
                  <el-tag size="small" type="primary" v-if="item.observations?.length">
                    <el-icon><View /></el-icon>
                    {{ item.observations.length }} 条观察记录
                  </el-tag>
                </div>

                <!-- 关键指标快速预览（前3个） -->
                <div class="key-metrics-preview" v-if="item.metrics && item.metrics.length > 0">
                  <div
                      v-for="(metric, idx) in item.metrics.slice(0, 3)"
                      :key="metric.metricKey"
                      class="metric-chip"
                  >
                    <span class="metric-name">{{ metric.metricName || metric.metricKey }}:</span>
                    <span class="metric-val">{{ metric.valueNum }}</span>
                    <span class="metric-unit">{{ metric.unit }}</span>
                  </div>
                  <span v-if="item.metrics.length > 3" class="more-metrics">+{{ item.metrics.length - 3 }} 更多</span>
                </div>
              </div>
            </div>

            <div class="expand-indicator">
              <el-icon :class="{ 'is-rotate': expandedItems.has(item.docId) }">
                <ArrowDownBold />
              </el-icon>
            </div>
          </div>

          <!-- 抽屉内容区 - 可折叠 -->
          <div
              class="drawer-content"
              v-show="expandedItems.has(item.docId)"
              :style="{ height: expandedItems.has(item.docId) ? 'auto' : '0' }"
          >
            <div class="drawer-body">
              <!-- 论文信息卡片 -->
              <div class="detail-section paper-section" v-if="item.paper">
                <div class="section-header">
                  <div class="section-title">
                    <el-icon><Document /></el-icon>
                    <span>论文详情</span>
                  </div>
                  <el-button type="primary" text size="small" @click="viewPaper(item.paper?.paperId)">
                    查看完整论文 <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>

                <div class="paper-card">
                  <div class="paper-title-wrapper">
                    <h4 class="paper-title" :class="{ 'translated-text': item.showPaperTranslation }">
                      {{ item.showPaperTranslation && item.translatedPaperTitle ? item.translatedPaperTitle : item.paper.title }}
                    </h4>
                    <div class="paper-actions">
                      <template v-if="!item.translatedPaperTitle">
                        <el-button size="small" type="primary" text :loading="item.paperTranslating" @click="translatePaperTitle(item)">
                          <el-icon><Reading /></el-icon>
                          翻译标题
                        </el-button>
                      </template>
                      <template v-else>
                        <el-tag size="small" :type="item.paperTranslationSource === 'cache' ? 'success' : 'warning'" effect="light">
                          {{ item.paperTranslationSource === 'cache' ? '缓存' : 'API' }}
                        </el-tag>
                        <el-button size="small" :type="item.showPaperTranslation ? 'primary' : 'default'" @click="togglePaperTranslation(item)">
                          {{ item.showPaperTranslation ? '原文' : '译文' }}
                        </el-button>
                      </template>
                    </div>
                  </div>

                  <div class="paper-meta-grid">
                    <div class="meta-cell">
                      <span class="meta-label">作者</span>
                      <span class="meta-value">{{ item.paper.authors || '未知作者' }}</span>
                    </div>
                    <div class="meta-cell" v-if="item.paper.year">
                      <span class="meta-label">发表年份</span>
                      <span class="meta-value">{{ item.paper.year }}</span>
                    </div>
                    <div class="meta-cell" v-if="item.paper.doi">
                      <span class="meta-label">DOI</span>
                      <span class="meta-value mono">{{ item.paper.doi }}</span>
                    </div>
                  </div>

                  <div class="abstract-box" v-if="item.paper.abstractContent">
                    <div class="abstract-label">摘要</div>
                    <p class="abstract-text">{{ item.paper.abstractContent }}</p>
                  </div>
                </div>
              </div>

              <!-- 完整性能指标 -->
              <div class="detail-section metrics-section" v-if="item.metrics && item.metrics.length > 0">
                <div class="section-title">
                  <el-icon><TrendCharts /></el-icon>
                  <span>完整性能指标</span>
                </div>
                <div class="metrics-grid">
                  <div
                      v-for="metric in item.metrics"
                      :key="metric.metricKey"
                      class="metric-card"
                  >
                    <div class="metric-header">
                      <span class="metric-label-full">{{ metric.metricName || metric.metricKey }}</span>
                      <el-tag v-if="metric.conditions" size="small" type="info" effect="light" class="condition-tag">
                        {{ formatConditions(metric.conditions) }}
                      </el-tag>
                    </div>
                    <div class="metric-body">
                      <span class="metric-number">{{ metric.valueNum }}</span>
                      <span class="metric-unit">{{ metric.unit }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 观察记录时间线 -->
              <div class="detail-section observations-section" v-if="item.observations && item.observations.length > 0">
                <div class="section-title">
                  <el-icon><View /></el-icon>
                  <span>实验观察记录 ({{ item.observations.length }}条)</span>
                </div>
                <div class="observations-list">
                  <div
                      v-for="(obs, idx) in item.observations"
                      :key="obs.id"
                      class="observation-card"
                      :class="'obs-type-' + (obs.type || 'default')"
                  >
                    <div class="obs-icon">
                      <el-icon v-if="obs.type === 'result_summary'"><StarFilled /></el-icon>
                      <el-icon v-else-if="obs.type === 'formulation'"><Edit /></el-icon>
                      <el-icon v-else-if="obs.type === 'measurement_method'"><Tools /></el-icon>
                      <el-icon v-else><InfoFilled /></el-icon>
                    </div>
                    <div class="obs-content-wrapper">
                      <div class="obs-header-row">
                        <el-tag size="small" :type="getObsType(obs.type)" effect="light">
                          {{ obs.type || '观察记录' }}
                        </el-tag>
                        <span v-if="obs.confidence" class="obs-confidence">
                          可信度: {{ (obs.confidence * 100).toFixed(0) }}%
                        </span>
                      </div>
                      <p class="obs-text" :class="{ 'translated-text': obs.showTranslation }">
                        {{ obs.showTranslation && obs.translatedContent ? obs.translatedContent : obs.content }}
                      </p>
                      <div class="obs-actions-row">
                        <template v-if="!obs.translatedContent">
                          <el-button size="small" type="primary" text :loading="obs.translating" @click="translateObservation(item, obs)">
                            <el-icon><Reading /></el-icon>
                            翻译内容
                          </el-button>
                        </template>
                        <template v-else>
                          <div class="translation-status">
                            <el-tag size="small" :type="obs.translationSource === 'cache' ? 'success' : 'warning'" effect="plain">
                              {{ obs.translationSource === 'cache' ? '缓存' : 'API' }}
                            </el-tag>
                            <span v-if="obs.translationCostTime" class="cost-hint">{{ obs.translationCostTime }}ms</span>
                          </div>
                          <el-button size="small" :type="obs.showTranslation ? 'primary' : 'default'" @click="toggleObservationTranslation(obs)">
                            {{ obs.showTranslation ? '显示原文' : '显示译文' }}
                          </el-button>
                        </template>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 技术调试信息（可选） -->
              <div class="detail-section debug-section" v-if="showDebug">
                <el-descriptions :column="3" size="small" border>
                  <el-descriptions-item label="Doc ID">{{ item.docId }}</el-descriptions-item>
                  <el-descriptions-item label="向量维度">{{ item.embeddingVector?.length || '无' }}</el-descriptions-item>
                  <el-descriptions-item label="匹配分数">{{ item.score?.toFixed(4) || 'N/A' }}</el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <el-pagination
            v-if="total > 0"
            v-model:current-page="currentPage"
            v-model:page-size="searchForm.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
            class="pagination"
            background
        />
      </div>
    </el-card>

    <!-- 调试开关 -->
    <div class="debug-toggle" v-if="results.length > 0">
      <el-checkbox v-model="showDebug" size="small">显示调试信息</el-checkbox>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, RefreshRight, InfoFilled, StarFilled, ArrowRight,
  UserFilled, Calendar, Document, TrendCharts, View, Refresh,
  Reading, ArrowDown, ArrowUp, ArrowDownBold, Edit, Tools,
  MagicStick, CircleCheck
} from '@element-plus/icons-vue'
import axios from 'axios'

const router = useRouter()
const loading = ref(false)
const syncLoading = ref(false)
const total = ref(0)
const results = ref([])
const currentPage = ref(1)
const yearRange = ref([])
const searchMode = ref('text')
const sortBy = ref('relevance')
const expandedItems = ref(new Set()) // 记录展开的项
const showDebug = ref(false)

const searchForm = reactive({
  materialName: '',
  metricKey: '',
  metricRange: { min: null, max: null },
  queryText: '',
  page: 0,
  size: 10,
  vectorWeight: 0.7,
  textWeight: 0.3
})

// 监听搜索模式变化
watch(searchMode, (newMode) => {
  if (newMode === 'text') {
    searchForm.queryText = ''
  }
})

// 展开/收起控制
const toggleExpand = (docId) => {
  if (expandedItems.value.has(docId)) {
    expandedItems.value.delete(docId)
  } else {
    // 手风琴模式：先关闭其他的（可选，如果需要同时展开多个，注释掉下面这行）
    expandedItems.value.clear()
    expandedItems.value.add(docId)
  }
}

const expandAll = () => {
  results.value.forEach(item => expandedItems.value.add(item.docId))
}

const collapseAll = () => {
  expandedItems.value.clear()
}

// 搜索模式切换处理
const handleModeChange = () => {
  results.value = []
  total.value = 0
  expandedItems.value.clear()
  if (searchMode.value === 'vector') {
    ElMessage.info('语义搜索模式：请输入描述性文本，系统将转换为向量进行匹配')
  } else if (searchMode.value === 'hybrid') {
    ElMessage.info('混合搜索模式：结合关键词和语义进行RRF融合排序')
  }
}

// 自动补全建议
const querySearch = async (queryString, cb) => {
  if (!queryString) {
    cb([])
    return
  }
  try {
    const res = await axios.get(`/api/search/materials/suggest`, {
      params: { keyword: queryString, size: 10 }
    })
    cb(res.data.map(item => ({ value: item })))
  } catch (error) {
    cb([])
  }
}

const handleSelect = (item) => {
  console.log('选中:', item)
}

const buildSearchParams = () => {
  const params = {
    page: searchForm.page,
    size: searchForm.size,
    sortBy: sortBy.value,
    vectorWeight: searchForm.vectorWeight,
    textWeight: searchForm.textWeight
  }

  if (searchMode.value === 'text' || searchMode.value === 'hybrid') {
    params.materialName = searchForm.materialName
  }

  if (searchMode.value === 'vector' || searchMode.value === 'hybrid') {
    params.queryText = searchForm.queryText
  }

  if (searchForm.metricKey) {
    params.metricKey = searchForm.metricKey
    if (searchForm.metricRange.min !== null || searchForm.metricRange.max !== null) {
      params.metricRange = {}
      if (searchForm.metricRange.min !== null) params.metricRange.min = searchForm.metricRange.min
      if (searchForm.metricRange.max !== null) params.metricRange.max = searchForm.metricRange.max
    }
  }

  if (yearRange.value?.length === 2) {
    params.yearFrom = parseInt(yearRange.value[0])
    params.yearTo = parseInt(yearRange.value[1])
  }

  return params
}

// 翻译API调用
const translateText = async (text, type, context = '') => {
  try {
    const response = await axios.post('/api/materials/translate', {
      text,
      type,
      context
    })
    return response.data
  } catch (error) {
    console.error('翻译请求失败:', error)
    throw error
  }
}

// 翻译材料名称
const translateMaterialName = async (item) => {
  if (item.translating || !item.material?.materialName) return

  item.translating = true
  try {
    const result = await translateText(
        item.material.materialName,
        'MATERIAL_NAME',
        'petroleum engineering material'
    )

    item.translatedName = result.translatedText
    item.translationSource = result.source
    item.translationCostTime = result.costTime
    item.showTranslation = true

    ElMessage.success(`翻译完成（${result.source === 'cache' ? 'Redis缓存' : 'DeepSeek API'}，耗时 ${result.costTime}ms）`)
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    item.translating = false
  }
}

const toggleMaterialTranslation = (item) => {
  if (!item.translatedName) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  item.showTranslation = !item.showTranslation
}

// 翻译论文标题
const translatePaperTitle = async (item) => {
  if (item.paperTranslating || !item.paper?.title) return

  item.paperTranslating = true
  try {
    const result = await translateText(
        item.paper.title,
        'PAPER_TITLE',
        'academic paper title'
    )

    item.translatedPaperTitle = result.translatedText
    item.paperTranslationSource = result.source
    item.paperTranslationCostTime = result.costTime
    item.showPaperTranslation = true

    if (result.source === 'api') {
      ElMessage.success(`实时翻译完成（${result.costTime}ms）`)
    }
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    item.paperTranslating = false
  }
}

const togglePaperTranslation = (item) => {
  if (!item.translatedPaperTitle) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  item.showPaperTranslation = !item.showPaperTranslation
}

// 翻译观察记录
const translateObservation = async (item, obs) => {
  if (obs.translating || !obs.content) return

  obs.translating = true
  try {
    const result = await translateText(
        obs.content,
        'OBSERVATION',
        `material: ${item.material?.materialName || ''}`
    )

    obs.translatedContent = result.translatedText
    obs.translationSource = result.source
    obs.translationCostTime = result.costTime
    obs.showTranslation = true

    if (result.source === 'api') {
      ElMessage.success(`实时翻译完成（${result.costTime}ms）`)
    }
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    obs.translating = false
  }
}

const toggleObservationTranslation = (obs) => {
  if (!obs.translatedContent) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  obs.showTranslation = !obs.showTranslation
}

// 搜索
const handleSearch = async () => {
  if (searchMode.value === 'vector' && !searchForm.queryText) {
    ElMessage.warning('语义搜索模式请输入描述文本')
    return
  }
  if (searchMode.value === 'text' && !searchForm.materialName) {
    ElMessage.warning('请输入材料名称或切换搜索模式')
    return
  }

  loading.value = true
  expandedItems.value.clear() // 重置展开状态

  try {
    const params = buildSearchParams()
    console.log('搜索参数:', params)

    const res = await axios.post('/api/search/materials', params)

    results.value = (res.data.content || []).map((item, index) => ({
      ...item,
      translating: false,
      translatedName: null,
      translationSource: null,
      translationCostTime: null,
      showTranslation: false,
      paperTranslating: false,
      translatedPaperTitle: null,
      paperTranslationSource: null,
      paperTranslationCostTime: null,
      showPaperTranslation: false,
      observations: (item.observations || []).map((obs, idx) => ({
        ...obs,
        id: `${item.docId}_obs_${idx}`,
        translating: false,
        translatedContent: null,
        translationSource: null,
        translationCostTime: null,
        showTranslation: false
      }))
    }))

    total.value = res.data.totalElements || 0
    currentPage.value = searchForm.page + 1

    if (results.value.length === 0) {
      ElMessage.info('未找到匹配结果，请尝试调整搜索条件')
    } else {
      ElMessage.success(`找到 ${total.value} 条相关记录`)
      // 默认展开前3条（可选）
      await nextTick()
      results.value.slice(0, 3).forEach(item => expandedItems.value.add(item.docId))
    }
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('检索失败: ' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

// 全量同步
const handleSyncAll = async () => {
  try {
    await ElMessageBox.confirm(
        '这将同步所有PG中的材料数据到Elasticsearch，可能需要几分钟，是否继续？',
        '全量同步确认',
        { confirmButtonText: '确认同步', cancelButtonText: '取消', type: 'warning' }
    )

    syncLoading.value = true
    await axios.post('/api/search/materials/sync-all')
    ElMessage.success('全量同步任务已启动，请稍后刷新查看结果')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('同步失败: ' + error.message)
    }
  } finally {
    syncLoading.value = false
  }
}

// 重置
const resetForm = () => {
  searchForm.materialName = ''
  searchForm.metricKey = ''
  searchForm.metricRange.min = null
  searchForm.metricRange.max = null
  searchForm.queryText = ''
  searchForm.page = 0
  yearRange.value = []
  results.value = []
  total.value = 0
  currentPage.value = 1
  sortBy.value = 'relevance'
  expandedItems.value.clear()
}

// 分页处理
const handleSizeChange = (val) => {
  searchForm.size = val
  searchForm.page = 0
  handleSearch()
}

const handlePageChange = (val) => {
  searchForm.page = val - 1
  handleSearch()
}

// 排序变化
const handleSortChange = () => {
  searchForm.page = 0
  handleSearch()
}

// 查看论文
const viewPaper = (paperId) => {
  if (paperId) {
    router.push(`/papers/${paperId}`)
  } else {
    ElMessage.warning('论文ID缺失')
  }
}

// 格式化条件
const formatConditions = (conditions) => {
  if (!conditions) return ''
  return Object.entries(conditions)
      .map(([k, v]) => `${k}:${v}`)
      .join(', ')
}

// 观察记录类型映射
const getObsType = (type) => {
  const map = {
    'result_summary': 'success',
    'formulation': 'primary',
    'measurement_method': 'warning',
    'limitation': 'info',
    'comparison': 'danger'
  }
  return map[type] || 'info'
}
</script>

<style scoped>
.material-search-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 搜索卡片样式 */
.search-card {
  margin-bottom: 20px;
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.title-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.main-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.engine-tag {
  font-size: 12px;
}

.search-form {
  margin-top: 10px;
}

.range-inputs {
  display: flex;
  align-items: center;
}

.range-separator {
  margin: 0 8px;
  color: #909399;
}

.vector-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.action-row {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #dcdfe6;
}

.button-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.search-btn {
  min-width: 120px;
}

/* 结果区域 - 抽屉式列表 */
.result-card {
  border-radius: 12px;
  min-height: 400px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.result-stats {
  display: flex;
  align-items: center;
  gap: 10px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.count-tag {
  font-size: 14px;
  padding: 0 12px;
  height: 28px;
  line-height: 26px;
}

.view-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 抽屉列表样式 */
.drawer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.drawer-item {
  background: white;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
}

.drawer-item:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  border-color: #c0c4cc;
}

.drawer-item.is-expanded {
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
  border-color: #409eff;
}

.drawer-item.top-result {
  border-left: 4px solid #ffd700;
  background: linear-gradient(to right, #fffbeb, #ffffff);
}

/* 抽屉头部 */
.drawer-header {
  padding: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  transition: background-color 0.2s;
}

.drawer-header:hover {
  background-color: #f5f7fa;
}

.header-main {
  display: flex;
  gap: 16px;
  flex: 1;
  align-items: flex-start;
}

/* 排名标识 */
.rank-section {
  flex-shrink: 0;
}

.rank-badge {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b6b, #ff8e8e);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(255,107,107,0.3);
}

.rank-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
}

/* 内容预览区 */
.content-preview {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 10px;
}

.material-name {
  margin: 0;
  font-size: 18px;
  color: #303133;
  font-weight: 600;
  line-height: 1.4;
  flex: 1;
  transition: all 0.3s;
}

.material-name.translated-text {
  color: #059669;
  font-weight: 700;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.source-tag {
  font-size: 11px;
  height: 22px;
  padding: 0 6px;
}

/* 预览指标标签 */
.preview-metrics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.confidence-tag {
  font-weight: 600;
}

/* 关键指标预览 */
.key-metrics-preview {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}

.metric-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #f0f9ff;
  border-radius: 6px;
  font-size: 13px;
  border: 1px solid #e0f2fe;
}

.metric-name {
  color: #606266;
  font-weight: 500;
}

.metric-val {
  color: #0284c7;
  font-weight: 700;
  font-size: 14px;
}

.metric-unit {
  color: #909399;
  font-size: 11px;
}

.more-metrics {
  color: #909399;
  font-size: 12px;
  font-style: italic;
}

/* 展开指示器 */
.expand-indicator {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f0f2f5;
  transition: all 0.3s;
}

.expand-indicator .el-icon {
  font-size: 16px;
  color: #909399;
  transition: transform 0.3s;
}

.expand-indicator .el-icon.is-rotate {
  transform: rotate(180deg);
}

.drawer-item.is-expanded .expand-indicator {
  background: #ecf5ff;
}

.drawer-item.is-expanded .expand-indicator .el-icon {
  color: #409eff;
}

/* 抽屉内容区 */
.drawer-content {
  overflow: hidden;
  transition: all 0.3s ease-in-out;
}

.drawer-body {
  padding: 0 20px 20px;
  border-top: 1px solid #ebeef5;
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 详情区块 */
.detail-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #e4e7ed;
}

.detail-section:first-child {
  margin-top: 0;
  padding-top: 20px;
  border-top: none;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* 论文卡片 */
.paper-section {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
  margin-top: 0;
  border: 1px solid #e4e7ed;
}

.paper-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.paper-title-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.paper-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
  font-weight: 600;
  flex: 1;
  line-height: 1.5;
  transition: all 0.3s;
}

.paper-title.translated-text {
  color: #059669;
}

.paper-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.paper-meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
}

.meta-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.meta-value {
  font-size: 13px;
  color: #303133;
  font-weight: 600;
  line-height: 1.4;
}

.meta-value.mono {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}

.abstract-box {
  margin-top: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border-left: 3px solid #409eff;
}

.abstract-label {
  font-size: 12px;
  color: #409eff;
  font-weight: 600;
  margin-bottom: 6px;
}

.abstract-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
  text-align: justify;
}

/* 指标网格 */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 12px;
}

.metric-card {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  transition: all 0.2s;
}

.metric-card:hover {
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  border-color: #c0c4cc;
}

.metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.metric-label-full {
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}

.condition-tag {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-body {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.metric-number {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}

.metric-unit {
  font-size: 13px;
  color: #909399;
}

/* 观察记录列表 */
.observations-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.observation-card {
  display: flex;
  gap: 12px;
  padding: 14px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.2s;
}

.observation-card:hover {
  border-color: #d0d7de;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.observation-card.obs-type-result_summary {
  border-left: 3px solid #67c23a;
}

.observation-card.obs-type-formulation {
  border-left: 3px solid #409eff;
}

.observation-card.obs-type-measurement_method {
  border-left: 3px solid #e6a23c;
}

.obs-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #909399;
}

.obs-content-wrapper {
  flex: 1;
  min-width: 0;
}

.obs-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.obs-confidence {
  font-size: 12px;
  color: #909399;
}

.obs-text {
  margin: 0 0 10px 0;
  line-height: 1.6;
  color: #303133;
  font-size: 14px;
  transition: all 0.3s;
}

.obs-text.translated-text {
  color: #059669;
  font-weight: 500;
}

.obs-actions-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.translation-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cost-hint {
  font-size: 11px;
  color: #10b981;
}

/* 分页 */
.pagination {
  margin-top: 30px;
  justify-content: center;
  padding: 20px 0;
}

/* 调试开关 */
.debug-toggle {
  position: fixed;
  bottom: 20px;
  right: 20px;
  background: white;
  padding: 8px 16px;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  z-index: 100;
}

/* 响应式 */
@media (max-width: 768px) {
  .button-group {
    flex-direction: column;
    width: 100%;
  }

  .button-group .el-button {
    width: 100%;
  }

  .view-controls {
    width: 100%;
    justify-content: flex-end;
  }

  .drawer-header {
    padding: 16px;
  }

  .title-row {
    flex-direction: column;
    width: 100%;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .key-metrics-preview {
    gap: 8px;
  }

  .metric-chip {
    font-size: 12px;
    padding: 3px 8px;
  }

  .paper-meta-grid {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .observation-card {
    flex-direction: column;
  }

  .obs-icon {
    width: 28px;
    height: 28px;
    font-size: 12px;
  }
}
</style>
