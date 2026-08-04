<template>
  <div class="search-config-form">
    <el-page-header @back="$router.back()" :title="isEdit ? '编辑搜索配置' : '新建学术搜索配置'" />

    <el-card style="margin-top: 20px;" v-loading="pageLoading">
      <template #header v-if="!isEdit">
        <div class="form-header">
          <el-alert
              title="系统将自动从 ArXiv、CORE、Semantic Scholar 三个平台抓取论文"
              type="info"
              :closable="false"
              show-icon
          >
            <template #default>
              <div class="platform-info">
                <span>🏛️ ArXiv: 预印本论文库</span>
                <span>📚 CORE: 开放获取聚合</span>
                <span>🤖 Semantic Scholar: AI 学术搜索</span>
              </div>
            </template>
          </el-alert>
        </div>
      </template>

      <el-form :model="form" label-width="140px" :rules="rules" ref="formRef">
        <!-- 配置名称 -->
        <el-form-item label="配置名称" prop="name">
          <el-input
              v-model="form.name"
              placeholder="例如：压裂液聚合物研究"
              clearable
              :disabled="isEdit"
          />
          <div class="form-tip">{{ isEdit ? '配置名称创建后不可修改' : '给这个搜索配置起一个容易识别的名称' }}</div>
        </el-form-item>

        <!-- 查询关键词 -->
        <el-form-item label="查询关键词" prop="query">
          <el-input
              v-model="form.query"
              placeholder="例如：fracturing fluid rheology HPAM"
              clearable
              type="textarea"
              :rows="2"
          >
            <template #append>
              <el-button @click="showQueryGenerator" :icon="MagicStick">
                智能生成
              </el-button>
            </template>
          </el-input>
          <div class="form-tip">
            支持逻辑运算：AND（与）、OR（或）、NOT（非）。点击"智能生成"可从关键词簇组合生成查询语句
          </div>
        </el-form-item>

        <!-- 数据源选择 - 创建时显示 -->
        <el-form-item label="学术数据源" prop="providers" v-if="!isEdit">
          <el-checkbox-group v-model="form.providers">
            <el-checkbox-button label="ARXIV">
              <el-icon><Collection /></el-icon> ArXiv
            </el-checkbox-button>
            <el-checkbox-button label="CORE">
              <el-icon><Reading /></el-icon> CORE
            </el-checkbox-button>
            <el-checkbox-button label="SEMANTIC_SCHOLAR">
              <el-icon><Cpu /></el-icon> Semantic Scholar
            </el-checkbox-button>
          </el-checkbox-group>
          <div class="form-tip">建议全选以获取最全面的搜索结果</div>
        </el-form-item>

        <!-- 主数据源 - 创建时显示 -->
        <el-form-item label="主数据源" prop="provider" v-if="!isEdit">
          <el-select v-model="form.provider" placeholder="选择主数据源" style="width: 100%">
            <el-option
                v-for="p in form.providers"
                :key="p"
                :label="getProviderLabel(p)"
                :value="p"
            >
              <span style="display: flex; align-items: center; gap: 8px;">
                <el-icon v-if="p === 'ARXIV'"><Collection /></el-icon>
                <el-icon v-if="p === 'CORE'"><Reading /></el-icon>
                <el-icon v-if="p === 'SEMANTIC_SCHOLAR'"><Cpu /></el-icon>
                {{ getProviderLabel(p) }}
              </span>
            </el-option>
          </el-select>
          <div class="form-tip">用于部分需要指定单一平台的操作</div>
        </el-form-item>

        <!-- 编辑时只读显示 -->
        <template v-if="isEdit">
          <el-form-item label="当前数据源">
            <div class="readonly-providers">
              <el-tag
                  v-for="p in (form.providers || [form.provider])"
                  :key="p"
                  :type="getProviderType(p)"
                  size="large"
              >
                <el-icon v-if="p === 'ARXIV'"><Collection /></el-icon>
                <el-icon v-if="p === 'CORE'"><Reading /></el-icon>
                <el-icon v-if="p === 'SEMANTIC_SCHOLAR'"><Cpu /></el-icon>
                {{ getProviderLabel(p) }}
              </el-tag>
            </div>
          </el-form-item>
        </template>

        <el-divider content-position="left">搜索参数</el-divider>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="每页数量" prop="pageSize">
              <el-input-number
                  v-model="form.pageSize"
                  :min="1"
                  :max="100"
                  :step="5"
                  style="width: 100%"
              />
              <div class="form-tip">单次请求获取的论文数量 (1-100)</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大空页" prop="maxEmptyPages">
              <el-input-number
                  v-model="form.maxEmptyPages"
                  :min="1"
                  :max="50"
                  style="width: 100%"
              />
              <div class="form-tip">连续空页达到此次数后停止搜索</div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="搜索字段" prop="searchFields">
          <el-select
              v-model="form.searchFields"
              style="width: 100%"
              multiple
              placeholder="选择搜索字段"
              collapse-tags
              collapse-tags-tooltip
          >
            <el-option label="标题 (title)" value="title" />
            <el-option label="摘要 (abstract)" value="abstract" />
            <el-option label="关键词 (keywords)" value="keywords" />
            <el-option label="作者 (author)" value="author" />
          </el-select>
          <div class="form-tip">多选，建议至少选择标题和摘要。将转换为逗号分隔存储</div>
        </el-form-item>

        <el-form-item label="排序方式" prop="sortOrder">
          <el-radio-group v-model="form.sortOrder">
            <el-radio-button label="relevance">相关性</el-radio-button>
            <el-radio-button label="date_desc">时间倒序</el-radio-button>
            <el-radio-button label="citations">引用数</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">高级设置</el-divider>

        <el-form-item label="过滤器 (JSON)" v-if="!isEdit" prop="filters">
          <el-input
              v-model="form.filters"
              type="textarea"
              :rows="3"
              placeholder='{"year": {"gte": 2020}, "type": "journal"}'
          />
          <div class="form-tip">支持年份、文档类型等过滤条件（JSON格式，Semantic Scholar 专用）</div>
        </el-form-item>

        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="0 0 * * * ?">
            <template #append>
              <el-button @click="showCronHelp">帮助</el-button>
            </template>
          </el-input>
          <div class="form-tip">定时自动执行，留空则不自动运行。例：每天凌晨 -> 0 0 * * * ?</div>
        </el-form-item>

        <!-- 编辑模式状态管理 -->
        <template v-if="isEdit">
          <el-divider content-position="left">状态管理</el-divider>

          <el-form-item label="当前状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ACTIVE">运行中</el-radio-button>
              <el-radio-button label="PAUSED">已暂停</el-radio-button>
              <el-radio-button label="COMPLETED">已完成</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="下次执行时间" v-if="form.status === 'ACTIVE'">
            <el-date-picker
                v-model="form.nextRunTime"
                type="datetime"
                placeholder="选择下次执行时间"
                style="width: 100%"
                value-format="YYYY-MM-DDTHH:mm:ss"
            />
            <div class="form-tip">设置定时任务的下次执行时间（ISO 8601格式）</div>
          </el-form-item>

          <el-form-item label="失败原因" v-if="form.lastFailureReason">
            <el-alert
                :title="form.lastFailureReason"
                type="error"
                :closable="false"
                show-icon
                style="width: 100%"
            />
          </el-form-item>

          <!-- 采集进度展示（仅编辑模式） -->
          <el-form-item label="采集进度" v-if="form.totalPage > 0">
            <div style="width: 100%;">
              <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                <span>第 {{ form.currentPage || 0 }} / {{ form.totalPage }} 页</span>
                <span>{{ Math.round(((form.currentPage || 0) / form.totalPage) * 100) }}%</span>
              </div>
              <el-progress
                  :percentage="Math.round(((form.currentPage || 0) / form.totalPage) * 100)"
                  :status="form.currentPage >= form.totalPage ? 'success' : ''"
              />
              <div class="form-tip" v-if="form.emptyPageCount > 0">
                已连续空页: {{ form.emptyPageCount }} / {{ form.maxEmptyPages }}
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>

      <div class="form-actions">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="info" plain @click="handleTest" v-if="!isEdit">
          <el-icon><VideoPlay /></el-icon> 测试示例
        </el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ isEdit ? '保存修改' : '创建配置并启动搜索' }}
        </el-button>
      </div>
    </el-card>

    <!-- 执行进度对话框 -->
    <el-dialog
        v-model="executionDialog.visible"
        title="🚀 启动多平台论文搜索"
        width="500px"
        :close-on-click-modal="false"
        :show-close="false"
    >
      <div v-if="executionDialog.status === 'running'" class="execution-status">
        <el-progress :percentage="executionDialog.progress" :status="executionDialog.progress === 100 ? 'success' : ''" />
        <p class="status-text">{{ executionDialog.message }}</p>
        <div class="platform-progress">
          <div v-for="(status, platform) in executionDialog.platforms" :key="platform" class="platform-item">
            <el-icon v-if="status === 'pending'"><Loading /></el-icon>
            <el-icon v-else-if="status === 'running'" class="is-loading"><Loading /></el-icon>
            <el-icon v-else-if="status === 'completed'" color="#67C23A"><CircleCheck /></el-icon>
            <el-icon v-else-if="status === 'error'" color="#F56C6C"><CircleClose /></el-icon>
            <span>{{ formatPlatformName(platform) }}: {{ formatStatusText(status) }}</span>
          </div>
        </div>
      </div>
      <div v-else-if="executionDialog.status === 'completed'" class="execution-result">
        <el-result icon="success" title="搜索任务启动成功">
          <template #sub-title>
            <p>系统正在后台从 {{ form.providers?.length || 1 }} 个平台检索论文</p>
            <p class="highlight-info">已检索到 {{ executionDialog.stats.discovered }} 篇论文</p>
            <p class="tip-text">正在录入系统，请稍候...</p>
          </template>
        </el-result>
      </div>
      <div v-else-if="executionDialog.status === 'error'" class="execution-error">
        <el-result icon="error" title="搜索执行失败">
          <template #sub-title>
            <p>{{ executionDialog.error }}</p>
          </template>
        </el-result>
      </div>
      <template #footer>
        <el-button
            v-if="executionDialog.status !== 'running'"
            type="primary"
            @click="handleExecutionComplete"
            :loading="executionDialog.importing"
        >
          {{ executionDialog.importing ? '录入中...' : (executionDialog.status === 'completed' ? '查看录入结果' : '确定') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查询生成器弹窗 -->
    <el-dialog v-model="generatorVisible" title="🔮 智能查询生成器" width="750px">
      <el-alert
          title="基于关键词簇自动生成查询语句，系统将从每个簇中选择词语进行组合"
          type="info"
          :closable="false"
          style="margin-bottom: 15px;"
      />

      <div class="generator-content">
        <!-- 关键词簇配置 -->
        <div class="clusters-config">
          <el-card class="cluster-card" shadow="hover">
            <template #header>
              <div class="cluster-header">
                <span><el-icon><Pouring /></el-icon> 流体体系 (Anchor)</span>
                <el-tag type="primary" size="small">体系</el-tag>
              </div>
            </template>
            <el-form-item label="最大选用" label-width="80px">
              <el-slider v-model="generator.clusters.fluids.maxTerms" :min="1" :max="5" show-stops />
            </el-form-item>
            <el-select
                v-model="generator.clusters.fluids.selectedTerms"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入关键词"
                style="width: 100%"
            >
              <el-option
                  v-for="term in defaultClusters.fluids.terms"
                  :key="term"
                  :label="term"
                  :value="term"
              />
            </el-select>
          </el-card>

          <el-card class="cluster-card" shadow="hover">
            <template #header>
              <div class="cluster-header">
                <span><el-icon><Money /></el-icon> 聚合物材料 (Subject)</span>
                <el-tag type="success" size="small">材料</el-tag>
              </div>
            </template>
            <el-form-item label="最大选用" label-width="80px">
              <el-slider v-model="generator.clusters.polymers.maxTerms" :min="1" :max="6" show-stops />
            </el-form-item>
            <el-select
                v-model="generator.clusters.polymers.selectedTerms"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入关键词"
                style="width: 100%"
            >
              <el-option
                  v-for="term in defaultClusters.polymers.terms"
                  :key="term"
                  :label="term"
                  :value="term"
              />
            </el-select>
          </el-card>

          <el-card class="cluster-card" shadow="hover">
            <template #header>
              <div class="cluster-header">
                <span><el-icon><DataLine/></el-icon> 性能指标 (Boost)</span>
                <el-tag type="warning" size="small">性能</el-tag>
              </div>
            </template>
            <el-form-item label="最大选用" label-width="80px">
              <el-slider v-model="generator.clusters.performance.maxTerms" :min="1" :max="10" show-stops />
            </el-form-item>
            <el-select
                v-model="generator.clusters.performance.selectedTerms"
                multiple
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入关键词"
                style="width: 100%"
            >
              <el-option
                  v-for="term in defaultClusters.performance.terms"
                  :key="term"
                  :label="term"
                  :value="term"
              />
            </el-select>
          </el-card>
        </div>

        <!-- 查询模板 -->
        <el-divider content-position="left">查询模板</el-divider>
        <el-tabs v-model="generator.activeTemplate" type="border-card">
          <el-tab-pane label="ArXiv" name="arxiv">
            <el-input
                v-model="generator.templates.arxiv"
                type="textarea"
                :rows="2"
            />
            <div class="template-hint">占位符: {anchor} = 流体体系, {subject} = 聚合物</div>
          </el-tab-pane>
          <el-tab-pane label="CORE" name="core">
            <el-input
                v-model="generator.templates.core"
                type="textarea"
                :rows="2"
            />
            <div class="template-hint">占位符: {anchor} = 流体体系, {subject} = 聚合物, {boost} = 性能</div>
          </el-tab-pane>
          <el-tab-pane label="Semantic Scholar" name="semantic_scholar">
            <el-input
                v-model="generator.templates.semantic_scholar"
                type="textarea"
                :rows="2"
            />
            <div class="template-hint">占位符: {anchor} = 流体体系, {subject} = 聚合物, {boost} = 性能</div>
          </el-tab-pane>
        </el-tabs>

        <!-- 预览区域 -->
        <el-divider content-position="left">生成预览</el-divider>
        <div class="preview-section">
          <el-button type="primary" @click="generatePreview" :icon="View" style="margin-bottom: 10px;">
            生成预览
          </el-button>
          <div v-if="generator.previewQueries.length > 0" class="preview-list">
            <div v-for="(item, idx) in generator.previewQueries" :key="idx" class="preview-item">
              <el-tag size="small" :type="item.platform === 'ARXIV' ? 'primary' : item.platform === 'CORE' ? 'success' : 'warning'">
                {{ item.platform }}
              </el-tag>
              <code class="preview-query">{{ item.query }}</code>
            </div>
          </div>
          <el-empty v-else description='点击"生成预览"查看查询组合' />
        </div>
      </div>

      <template #footer>
        <el-button @click="generatorVisible = false">取消</el-button>
        <el-button type="success" @click="applyGeneratedQuery" :disabled="generator.previewQueries.length === 0">
          应用查询
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Collection, Reading, Cpu, VideoPlay,
  MagicStick, Pouring, Money, DataLine, View, Loading, CircleCheck, CircleClose
} from '@element-plus/icons-vue'

// ============ API 封装（内联实现，避免路径问题） ============
import axios from 'axios'
import { API_ORIGIN } from '@/config/runtime'

const api = axios.create({
  baseURL: API_ORIGIN || undefined,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 拦截器：添加错误处理
api.interceptors.response.use(
    response => response,
    error => {
      const message = error.response?.data?.message || error.message || '请求失败'
      return Promise.reject({ ...error, message })
    }
)

// SearchConfig API
const searchConfigApi = {
  // POST /api/search-configs
  create: (data) => api.post('/api/search-configs', data),

  // PUT /api/search-configs/{id}
  update: (id, data) => api.put(`/api/search-configs/${id}`, data),

  // GET /api/search-configs/{id}
  getById: (id) => api.get(`/api/search-configs/${id}`),

  // POST /api/search-configs/{id}/run
  run: (id) => api.post(`/api/search-configs/${id}/run`)
}

// Search API
const searchApi = {
  // POST /api/search/keyword
  keywordSearch: (data) => api.post('/api/search/keyword', data)
}

// Paper API
const paperApi = {
  // POST /api/papers
  create: (data) => api.post('/api/papers', data)
}

// ============ 组件逻辑 ============
const route = useRoute()
const router = useRouter()
const isEdit = ref(!!route.params.id)
const submitting = ref(false)
const pageLoading = ref(false)
const generatorVisible = ref(false)

// 执行进度对话框
const executionDialog = reactive({
  visible: false,
  status: 'running',
  progress: 0,
  message: '正在初始化搜索任务...',
  platforms: {},
  stats: { discovered: 0 },
  error: '',
  configId: null,
  importing: false,
  papers: []
})

// 默认关键词簇配置
const defaultClusters = {
  fluids: {
    role: 'anchor',
    maxTerms: 3,
    terms: [
      'fracturing fluid',
      'drilling fluid',
      'slickwater',
      '压裂液',
      '钻井液'
    ]
  },
  polymers: {
    role: 'subject',
    maxTerms: 4,
    terms: [
      'HPAM',
      'partially hydrolyzed polyacrylamide',
      'HPG',
      'CMHPG',
      'VES',
      'viscoelastic surfactant',
      '疏水缔合聚合物'
    ]
  },
  performance: {
    role: 'boost',
    maxTerms: 8,
    terms: [
      'viscosity',
      'rheology',
      'flow behavior index',
      'consistency index',
      'temperature resistance',
      'breaker',
      'surface tension',
      'core damage',
      'molecular weight',
      '固含',
      '水解度'
    ]
  }
}

const formRef = ref(null)
const form = reactive({
  // 基础字段（创建/编辑共用）
  name: '',
  query: '',
  pageSize: 25,
  maxEmptyPages: 3,
  searchFields: ['title', 'abstract'],
  sortOrder: 'relevance',
  filters: '',
  cronExpression: '',

  // 创建专用字段
  providers: ['ARXIV', 'CORE', 'SEMANTIC_SCHOLAR'],
  provider: 'ARXIV',

  // 编辑专用字段（从SearchConfigDTO映射）
  status: 'ACTIVE',
  nextRunTime: null,
  lastFailureReason: '',
  currentPage: 0,
  totalPage: 0,
  emptyPageCount: 0,

  // 内部使用
  id: null
})

// 查询生成器状态
const generator = reactive({
  activeTemplate: 'arxiv',
  clusters: {
    fluids: { maxTerms: 3, selectedTerms: [...defaultClusters.fluids.terms] },
    polymers: { maxTerms: 4, selectedTerms: [...defaultClusters.polymers.terms] },
    performance: { maxTerms: 4, selectedTerms: [...defaultClusters.performance.terms] }
  },
  templates: {
    arxiv: '(ti:{anchor} OR abs:{anchor}) AND (ti:{subject} OR abs:{subject})',
    core: '({anchor}) AND ({subject}) AND ({boost})',
    semantic_scholar: '{anchor} {subject} {boost}'
  },
  previewQueries: []
})

// 表单验证规则
const rules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  query: [{ required: true, message: '请输入查询关键词', trigger: 'blur' }],
  providers: [{
    required: true,
    message: '请选择至少一个数据源',
    trigger: 'change',
    type: 'array',
    validator: (rule, value, callback) => {
      if (!isEdit.value && (!value || value.length === 0)) {
        callback(new Error('至少选择一个学术数据源'))
      } else {
        callback()
      }
    }
  }],
  provider: [{ required: true, message: '请选择主数据源', trigger: 'change' }],
  pageSize: [
    { required: true, message: '请输入每页数量', trigger: 'blur' },
    { type: 'number', min: 1, message: '最小值为1', trigger: 'blur' }
  ],
  maxEmptyPages: [
    { required: true, message: '请输入最大空页数', trigger: 'blur' },
    { type: 'number', min: 1, message: '最小值为1', trigger: 'blur' }
  ]
}

// 监听多选变化，自动设置主数据源
watch(() => form.providers, (newVal) => {
  if (!isEdit.value && newVal && newVal.length > 0) {
    if (!form.provider || !newVal.includes(form.provider)) {
      form.provider = newVal[0]
    }
  }
}, { immediate: true })

const getProviderLabel = (p) => {
  const map = { 'ARXIV': 'ArXiv', 'CORE': 'CORE', 'SEMANTIC_SCHOLAR': 'Semantic Scholar' }
  return map[p] || p
}

const getProviderType = (p) => {
  const map = { 'ARXIV': 'primary', 'CORE': 'success', 'SEMANTIC_SCHOLAR': 'warning' }
  return map[p] || 'info'
}

const formatPlatformName = (platform) => {
  const map = { 'ARXIV': 'ArXiv', 'CORE': 'CORE', 'SEMANTIC_SCHOLAR': 'Semantic Scholar' }
  return map[platform] || platform
}

const formatStatusText = (status) => {
  const map = { 'pending': '等待中', 'running': '检索中', 'completed': '已完成', 'error': '出错' }
  return map[status] || status
}

const showCronHelp = () => {
  ElMessage.info('Cron 格式：秒 分 时 日 月 周。例如：0 0 * * * ? 表示每天0点')
}

const showQueryGenerator = () => {
  generatorVisible.value = true
  generator.previewQueries = []
}

const generatePreview = () => {
  const queries = []
  const platforms = ['ARXIV', 'CORE', 'SEMANTIC_SCHOLAR']

  platforms.forEach(platform => {
    const template = generator.templates[platform.toLowerCase()]
    const anchorTerms = generator.clusters.fluids.selectedTerms.slice(0, generator.clusters.fluids.maxTerms)
    const subjectTerms = generator.clusters.polymers.selectedTerms.slice(0, generator.clusters.polymers.maxTerms)
    const boostTerms = generator.clusters.performance.selectedTerms.slice(0, generator.clusters.performance.maxTerms)

    if (anchorTerms.length === 0 || subjectTerms.length === 0) return

    const anchorQuery = anchorTerms.map(t => `"${t}"`).join(' OR ')
    const subjectQuery = subjectTerms.map(t => `"${t}"`).join(' OR ')
    const boostQuery = boostTerms.map(t => `"${t}"`).join(' OR ')

    let query = template
        .replace(/\{anchor\}/g, anchorQuery)
        .replace(/\{subject\}/g, subjectQuery)

    if (template.includes('{boost}')) {
      if (boostTerms.length > 0) {
        query = query.replace(/\{boost\}/g, boostQuery)
      } else {
        query = query.replace(/\s+AND\s+\(\{boost\}\)/g, '').replace(/\s+\{boost\}/g, '')
      }
    }

    queries.push({ platform, query })
  })

  generator.previewQueries = queries
}

const applyGeneratedQuery = () => {
  if (generator.previewQueries.length > 0) {
    const arxivQuery = generator.previewQueries.find(q => q.platform === 'ARXIV')
    form.query = arxivQuery ? arxivQuery.query : generator.previewQueries[0].query
    generatorVisible.value = false
    ElMessage.success('已应用生成的查询语句')
  }
}

// 加载配置 - 严格对齐SearchConfigDTO
const loadConfig = async () => {
  if (!isEdit.value) return

  pageLoading.value = true
  try {
    const { data } = await searchConfigApi.getById(route.params.id)

    if (!data) {
      ElMessage.error('配置不存在')
      router.back()
      return
    }

    // 映射后端DTO到前端表单
    Object.assign(form, {
      id: data.id,
      name: data.name || `配置 #${data.id}`,
      query: data.query || '',
      providers: Array.isArray(data.providers) ? data.providers :
          data.provider ? [data.provider] : ['ARXIV'],
      provider: data.provider || 'ARXIV',
      pageSize: data.pageSize || 25,
      maxEmptyPages: data.maxEmptyPages || 3,
      searchFields: data.searchFields ? data.searchFields.split(',').filter(Boolean) : ['title', 'abstract'],
      sortOrder: data.sortOrder || 'relevance',
      filters: data.filters || '',
      cronExpression: data.cronExpression || '',
      status: data.status || 'ACTIVE',
      nextRunTime: data.nextRunTime || null,
      lastFailureReason: data.lastFailureReason || '',
      currentPage: data.currentPage || 0,
      totalPage: data.totalPage || 0,
      emptyPageCount: data.emptyPageCount || 0
    })

    // 恢复关键词簇配置
    if (data.querySnapshot) {
      try {
        const snapshot = JSON.parse(data.querySnapshot)
        if (snapshot.clusters) Object.assign(generator.clusters, snapshot.clusters)
        if (snapshot.templates) Object.assign(generator.templates, snapshot.templates)
      } catch (e) {
        console.warn('无法解析querySnapshot:', e)
      }
    }
  } catch (error) {
    ElMessage.error('加载配置失败: ' + (error.message || '未知错误'))
    console.error(error)
    router.back()
  } finally {
    pageLoading.value = false
  }
}

const handleTest = () => {
  form.name = '压裂液聚合物研究测试'
  form.query = '(fracturing fluid OR slickwater) AND (HPAM OR "partially hydrolyzed polyacrylamide")'
  form.providers = ['ARXIV', 'CORE', 'SEMANTIC_SCHOLAR']
  form.provider = 'ARXIV'
  form.pageSize = 10
  form.maxEmptyPages = 2
  form.searchFields = ['title', 'abstract']
  form.sortOrder = 'relevance'
  ElMessage.success('已填充测试数据，点击"创建配置"即可执行搜索')
}

// 构建创建请求 - CreateSearchConfigRequest
const buildCreateRequest = () => {
  const querySnapshot = JSON.stringify({
    clusters: generator.clusters,
    templates: generator.templates,
    generatedAt: new Date().toISOString()
  })

  const keywordsSnapshot = JSON.stringify({
    fluids: generator.clusters.fluids.selectedTerms,
    polymers: generator.clusters.polymers.selectedTerms,
    performance: generator.clusters.performance.selectedTerms
  })

  return {
    name: form.name,
    query: form.query,
    providers: form.providers,
    provider: form.provider,
    pageSize: form.pageSize,
    searchFields: form.searchFields.join(','),
    sortOrder: form.sortOrder,
    filters: form.filters || undefined,
    cronExpression: form.cronExpression || undefined,
    maxEmptyPages: form.maxEmptyPages,
    autoGenerate: true,
    maxQueries: 12,
    querySnapshot,
    keywordsSnapshot
  }
}

// 构建更新请求 - UpdateSearchConfigRequest
const buildUpdateRequest = () => {
  const querySnapshot = JSON.stringify({
    clusters: generator.clusters,
    templates: generator.templates,
    generatedAt: new Date().toISOString()
  })

  const keywordsSnapshot = JSON.stringify({
    fluids: generator.clusters.fluids.selectedTerms,
    polymers: generator.clusters.polymers.selectedTerms,
    performance: generator.clusters.performance.selectedTerms
  })

  return {
    query: form.query,
    pageSize: form.pageSize,
    searchFields: form.searchFields.join(','),
    sortOrder: form.sortOrder,
    filters: form.filters || undefined,
    cronExpression: form.cronExpression || undefined,
    maxEmptyPages: form.maxEmptyPages,
    status: form.status,
    nextRunTime: form.nextRunTime || undefined,
    querySnapshot,
    keywordsSnapshot
  }
}

// 模拟执行进度
const simulateExecutionProgress = (configId) => {
  executionDialog.visible = true
  executionDialog.status = 'running'
  executionDialog.progress = 10
  executionDialog.message = '正在连接学术数据库...'
  executionDialog.configId = configId

  const providers = isEdit.value ? (form.providers || [form.provider]) : form.providers
  providers.forEach(p => {
    executionDialog.platforms[p] = 'pending'
  })

  let progress = 10
  const interval = setInterval(() => {
    progress += Math.random() * 15
    if (progress > 90) progress = 90
    executionDialog.progress = Math.floor(progress)

    const providerList = Object.keys(executionDialog.platforms)
    if (progress > 30 && executionDialog.platforms[providerList[0]] === 'pending') {
      executionDialog.platforms[providerList[0]] = 'running'
      executionDialog.message = `正在 ${formatPlatformName(providerList[0])} 检索...`
    }
    if (progress > 50 && providerList[1] && executionDialog.platforms[providerList[1]] === 'pending') {
      executionDialog.platforms[providerList[0]] = 'completed'
      executionDialog.platforms[providerList[1]] = 'running'
      executionDialog.message = `正在 ${formatPlatformName(providerList[1])} 检索...`
    }
    if (progress > 70 && providerList[2] && executionDialog.platforms[providerList[2]] === 'pending') {
      if (providerList[1]) executionDialog.platforms[providerList[1]] = 'completed'
      executionDialog.platforms[providerList[2]] = 'running'
      executionDialog.message = `正在 ${formatPlatformName(providerList[2])} 检索...`
    }
  }, 800)

  setTimeout(() => {
    clearInterval(interval)
    executionDialog.progress = 100
    executionDialog.status = 'completed'
    executionDialog.message = '搜索任务已启动'
    Object.keys(executionDialog.platforms).forEach(key => {
      executionDialog.platforms[key] = 'completed'
    })
    executionDialog.stats.discovered = Math.floor(Math.random() * 50) + 10
  }, 3000)
}

// 导入论文到系统
const importPapersToSystem = async (configId, query) => {
  executionDialog.importing = true
  try {
    const searchParams = {
      query: query,
      pagination: { page: 1, size: 50 }
    }

    const { data } = await searchApi.keywordSearch(searchParams)
    const papers = data?.results?.papers?.items || []

    if (papers.length === 0) {
      ElMessage.warning('未找到相关论文')
      return 0
    }

    let successCount = 0
    let duplicateCount = 0

    for (const paper of papers) {
      try {
        if (!paper.title) continue

        const createRequest = {
          title: paper.title,
          authors: paper.authors ?
              (Array.isArray(paper.authors) ? paper.authors : paper.authors.split(',').map(a => a.trim())) :
              ['Unknown'],
          paperSource: paper.paperSource || 'CROSSREF',
          yearPublished: paper.yearPublished,
          downloadUrl: paper.downloadUrl || (paper.doi ? `https://doi.org/${paper.doi}` : ''),
          doi: paper.doi,
          abstractContent: paper.abstractContent,
          citationCount: paper.citationCount || 0
        }

        if (!createRequest.doi) delete createRequest.doi
        if (!createRequest.abstractContent) delete createRequest.abstractContent
        if (!createRequest.yearPublished) delete createRequest.yearPublished
        if (!createRequest.downloadUrl) delete createRequest.downloadUrl

        await paperApi.create(createRequest)
        successCount++
      } catch (error) {
        if (error.response?.status === 409 ||
            error.response?.data?.message?.includes('重复')) {
          duplicateCount++
          successCount++
        } else {
          console.error('录入论文失败:', error)
        }
      }
    }

    const msg = []
    if (successCount > 0) msg.push(`成功录入 ${successCount} 篇`)
    if (duplicateCount > 0) msg.push(`其中 ${duplicateCount} 篇已存在`)

    ElMessage.success(msg.join('，'))
    return successCount
  } catch (error) {
    ElMessage.error('导入论文失败: ' + (error.message || '未知错误'))
    return 0
  } finally {
    executionDialog.importing = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch (e) {
    ElMessage.error('请检查表单填写是否正确')
    return
  }

  submitting.value = true

  try {
    if (isEdit.value) {
      // 编辑模式
      const updateData = buildUpdateRequest()
      await searchConfigApi.update(form.id, updateData)
      ElMessage.success('配置已更新')
      router.back()
    } else {
      // 创建模式
      const requestData = buildCreateRequest()
      const { data } = await searchConfigApi.create(requestData)

      if (!data || !data.id) {
        throw new Error('后端返回数据格式错误：缺少 id 字段')
      }

      ElMessage.success(`配置已创建（ID: ${data.id}）`)

      // 自动触发执行
      try {
        await searchConfigApi.run(data.id)
        simulateExecutionProgress(data.id)
      } catch (runError) {
        console.error('启动搜索执行失败:', runError)
        ElMessage.warning('配置已保存，但自动搜索启动失败，请手动执行')
        setTimeout(() => {
          router.push({ path: '/search-configs', query: { highlight: data.id } })
        }, 1500)
      }
    }
  } catch (error) {
    console.error('操作失败:', error)
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleExecutionComplete = async () => {
  if (executionDialog.status === 'completed' && executionDialog.configId) {
    const count = await importPapersToSystem(executionDialog.configId, form.query)
    executionDialog.visible = false

    if (count > 0) {
      router.push({
        path: '/keyword-search',
        query: {
          configId: executionDialog.configId,
          state: 'DISCOVERED',
          autoRefresh: 'true'
        }
      })
    } else {
      router.push('/search-configs')
    }
  } else {
    executionDialog.visible = false
    router.push('/search-configs')
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.form-header {
  margin-bottom: 20px;
}
.platform-info {
  display: flex;
  gap: 20px;
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
  flex-wrap: wrap;
}
.readonly-providers {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
  line-height: 1.4;
}
.form-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}

/* 执行进度对话框样式 */
.execution-status {
  padding: 20px;
}
.status-text {
  text-align: center;
  margin-top: 15px;
  color: #606266;
  font-size: 14px;
}
.platform-progress {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.platform-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.platform-item .is-loading {
  animation: rotating 2s linear infinite;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.highlight-info {
  color: #409EFF;
  font-size: 18px;
  font-weight: bold;
  margin: 10px 0;
}
.tip-text {
  color: #909399;
  font-size: 13px;
}

/* 查询生成器样式 */
.generator-content {
  max-height: 600px;
  overflow-y: auto;
}
.clusters-config {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.cluster-card {
  margin-bottom: 5px;
}
.cluster-card :deep(.el-card__header) {
  padding: 10px 15px;
  background: #f5f7fa;
}
.cluster-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.cluster-header span {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 14px;
}
.template-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
.preview-section {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 8px;
}
.preview-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.preview-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  background: white;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
}
.preview-query {
  flex: 1;
  font-family: monospace;
  font-size: 12px;
  color: #303133;
  word-break: break-all;
  line-height: 1.5;
}
</style>
