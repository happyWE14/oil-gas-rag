<template>
  <div class="paper-management">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4" v-for="stat in stateStats" :key="stat.state">
        <el-card
            shadow="hover"
            :class="['stat-card', stat.state.toLowerCase()]"
            @click="quickFilter(stat.state)"
        >
          <div class="stat-value">{{ stat.count }}</div>
          <div class="stat-label">
            <el-tag :type="getStateType(stat.state)" size="small" effect="plain">
              {{ getStateText(stat.state) }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主操作区 -->
    <el-card shadow="never" class="main-card">
      <template #header>
        <div class="header-compact">
          <div class="filter-group">
            <el-input
                v-model="searchQuery"
                placeholder="搜索标题/作者/DOI..."
                clearable
                style="width: 280px"
                @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <el-select
                v-model="filter.state"
                placeholder="处理状态"
                clearable
                @change="handleFilterChange"
                style="width: 140px"
            >
              <el-option
                  v-for="s in paperStates"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
              >
                <el-tag :type="s.type" size="small" style="margin-right: 6px" />
                {{ s.label }}
              </el-option>
            </el-select>

            <el-select
                v-model="filter.source"
                placeholder="来源平台"
                clearable
                style="width: 130px"
            >
              <el-option
                  v-for="src in sourceOptions"
                  :key="src.value"
                  :label="src.label"
                  :value="src.value"
              />
            </el-select>

            <el-tooltip content="刷新列表">
              <el-button :icon="Refresh" circle @click="refreshList" :loading="loading" />
            </el-tooltip>
          </div>

          <div class="action-group">
            <el-button
                v-if="selectedPapers.length > 0"
                type="danger"
                plain
                @click="handleBatchDelete"
            >
              <el-icon><Delete /></el-icon>
              批量删除 ({{ selectedPapers.length }})
            </el-button>

            <el-dropdown split-button type="success" @click="showBatchDialog = true">
              <el-icon><Upload /></el-icon> 批量导入
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="exportTemplate">下载导入模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>

            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 新增论文
            </el-button>
          </div>
        </div>
      </template>

      <!-- 论文表格 -->
      <el-table
          :data="papers"
          v-loading="loading"
          stripe
          border
          @selection-change="handleSelectionChange"
          row-key="paperId"
          highlight-current-row
      >
        <el-table-column type="selection" width="50" align="center" reserve-selection />

        <el-table-column label="论文信息" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="paper-info">
              <div class="title-line">
                <el-link
                    type="primary"
                    @click="viewDetail(row)"
                    :underline="false"
                    class="paper-title"
                >
                  {{ row.title }}
                </el-link>
                <el-icon v-if="isPolling(row.paperId)" class="is-loading" style="margin-left: 6px; color: #409EFF">
                  <Loading />
                </el-icon>
              </div>
              <div class="meta-line">
                <el-tag size="small" :type="getSourceType(row.paperSource)" effect="plain">
                  {{ formatSource(row.paperSource) }}
                </el-tag>
                <span class="meta-item" v-if="row.yearPublished">
                  <el-icon><Calendar /></el-icon> {{ row.yearPublished }}
                </span>
                <span class="meta-item" v-if="row.doi" @click.stop="openDoi(row.doi)">
                  <el-icon><Link /></el-icon> DOI
                </span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="authors" label="作者" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatAuthors(row.authors) }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tooltip :content="getStateHint(row.state)" placement="top">
              <div :class="['state-badge', row.state.toLowerCase()]">
                <el-icon v-if="isProcessing(row.state)" class="is-loading"><Loading /></el-icon>
                <el-icon v-else-if="row.state === 'FAILED'"><CircleClose /></el-icon>
                <el-icon v-else-if="row.state === 'COMPLETED'"><CircleCheck /></el-icon>
                <span>{{ getStateText(row.state) }}</span>
              </div>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column label="本地文件" width="100" align="center">
          <template #default="{ row }">
            <div v-if="isLocalFileExist(row)" class="file-status" @click="handleLocalFileAction(row)">
              <el-icon class="success-icon"><DocumentChecked /></el-icon>
            </div>
            <el-progress
                v-else-if="isProcessing(row.state)"
                type="circle"
                :percentage="getProgress(row.state)"
                :width="24"
                :stroke-width="3"
            />
            <span v-else class="no-file">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <!-- 查看PDF（文件存在时） -->
              <el-button
                  v-if="isLocalFileExist(row)"
                  type="success"
                  size="small"
                  @click="handleLocalFileAction(row)"
                  title="查看PDF"
              >
                <el-icon><View /></el-icon>
              </el-button>

              <!-- 流程控制按钮 -->
              <el-button
                  v-if="canDownload(row)"
                  type="primary"
                  size="small"
                  @click="manualStartDownload(row.paperId)"
                  :loading="isPolling(row.paperId)"
                  title="开始下载"
              >
                <el-icon><Download /></el-icon>
              </el-button>

              <el-button
                  v-if="canTransform(row)"
                  type="warning"
                  size="small"
                  @click="manualStartTransform(row.paperId)"
                  :loading="isPolling(row.paperId)"
                  title="开始转换"
              >
                <el-icon><RefreshRight /></el-icon>
              </el-button>

              <el-button
                  v-if="canExtract(row)"
                  type="info"
                  size="small"
                  @click="manualStartExtraction(row.paperId)"
                  :loading="isPolling(row.paperId)"
                  title="提取材料"
              >
                <el-icon><DataAnalysis /></el-icon>
              </el-button>

              <!-- 更多操作 -->
              <el-dropdown trigger="click">
                <el-button size="small">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="viewDetail(row)">
                      <el-icon><Document /></el-icon> 详情
                    </el-dropdown-item>
                    <el-dropdown-item @click="viewEvents(row.paperId)">
                      <el-icon><Timer /></el-icon> 事件历史
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-if="row.state === 'FAILED'"
                        @click="retryPaper(row)"
                        divided
                    >
                      <el-icon><RefreshRight /></el-icon> 重新处理
                    </el-dropdown-item>
                    <el-dropdown-item
                        @click="deletePaper(row)"
                        type="danger"
                        :disabled="isProcessing(row.state)"
                    >
                      <el-icon><Delete /></el-icon> 删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @change="handlePageChange"
      />
    </el-card>

    <!-- 论文详情抽屉 -->
    <el-drawer
        v-model="detailDrawer.visible"
        :title="detailDrawer.paper?.title || '论文详情'"
        size="800px"
        destroy-on-close
    >
      <PaperDetail
          v-if="detailDrawer.paper"
          :paper="detailDrawer.paper"
          @refresh="refreshList"
      />
    </el-drawer>

    <!-- 新增/编辑论文对话框 -->
    <el-dialog
        v-model="dialog.visible"
        :title="dialog.isEdit ? '编辑论文' : '新增论文'"
        width="700px"
        destroy-on-close
    >
      <el-form :model="dialog.form" :rules="dialog.rules" ref="formRef" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="dialog.form.title" placeholder="论文标题" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="作者" prop="authors">
              <el-input v-model="dialog.form.authors" placeholder="逗号分隔多个作者" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="年份">
              <el-input-number v-model="dialog.form.yearPublished" :min="1900" :max="2030" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="DOI">
              <el-input v-model="dialog.form.doi" placeholder="10.xxxx/xxxx" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源" prop="paperSource">
              <el-select v-model="dialog.form.paperSource" placeholder="选择来源" style="width: 100%">
                <el-option
                    v-for="src in sourceOptions"
                    :key="src.value"
                    :label="src.label"
                    :value="src.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="PDF链接" prop="downloadUrl">
          <el-input v-model="dialog.form.downloadUrl" placeholder="https://..." />
        </el-form-item>

        <el-form-item label="摘要">
          <el-input
              v-model="dialog.form.abstractContent"
              type="textarea"
              :rows="4"
              placeholder="论文摘要..."
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="dialog.loading">
          {{ dialog.isEdit ? '保存' : '创建并下载' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog v-model="batchDialog.visible" title="批量导入论文" width="800px">
      <el-alert
          title="支持格式：标题|DOI|作者|年份|PDF链接|来源"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
      />
      <el-input
          v-model="batchDialog.content"
          type="textarea"
          :rows="12"
          placeholder="每行一篇论文，例如：&#10;Field Tests of High-Density Oil|10.xxx/xxx|Smith J|2024|https://...|arXiv"
      />
      <template #footer>
        <el-button @click="batchDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleBatchImport" :loading="batchDialog.loading">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 事件历史对话框 -->
    <el-dialog v-model="eventsDialog.visible" title="状态流转历史" width="600px">
      <el-timeline v-if="eventsDialog.events.length">
        <el-timeline-item
            v-for="(event, index) in eventsDialog.events"
            :key="index"
            :type="getEventType(event.toState)"
            :timestamp="formatTime(event.occurredAt)"
            :hollow="event.toState === 'FAILED'"
        >
          <p>
            <el-tag size="small">{{ event.fromState || '初始' }}</el-tag>
            <el-icon style="margin: 0 8px"><Right /></el-icon>
            <el-tag :type="getStateType(event.toState)" size="small">{{ event.toState }}</el-tag>
          </p>
          <p v-if="event.operator" class="event-meta">操作人: {{ event.operator }}</p>
          <p v-if="event.reason" class="event-error">原因: {{ event.reason }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无事件记录" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, View, Download, RefreshRight, DataAnalysis, Timer,
  Refresh, Loading, Upload, Search, Delete, Calendar, Link,
  CircleCheck, CircleClose, MoreFilled, Document, Right,
  DocumentChecked
} from '@element-plus/icons-vue'
import {
  getPapersPage, createPaper, getPaper, getPaperEvents,
  startDownload, startTransform, startExtraction, deletePaper
} from '@/api/paper'
import PaperDetail from './PaperDetail.vue' // 假设存在详情组件
import { API_ORIGIN } from '@/config/runtime'

// ==================== 配置与常量 ====================
const backendBaseUrl = ref(localStorage.getItem('paper_backend_url') || API_ORIGIN)

const paperStates = [
  { value: 'DISCOVERED', label: '已发现', type: 'info', hint: '等待分析' },
  { value: 'PRE_ANALYZING', label: '分析中', type: 'warning', hint: 'AI判定相关性' },
  { value: 'PRE_RELEVANT', label: '相关', type: 'success', hint: '等待下载' },
  { value: 'IRRELEVANT', label: '不相关', type: 'info', hint: '已过滤' },
  { value: 'DOWNLOADING', label: '下载中', type: 'warning', hint: '获取PDF' },
  { value: 'DOWNLOADED', label: '已下载', type: 'success', hint: '等待转换' },
  { value: 'TRANSFORMING', label: '转换中', type: 'warning', hint: 'PDF转文本' },
  { value: 'TRANSFORMED', label: '已转换', type: 'success', hint: '等待向量化' },
  { value: 'EMBEDDING', label: '向量化中', type: 'warning', hint: '生成向量' },
  { value: 'EMBEDDING_COMPLETED', label: '向量完成', type: 'success', hint: '等待提取' },
  { value: 'EXTRACTING', label: '提取中', type: 'warning', hint: '提取材料' },
  { value: 'MATERIAL_EXTRACTED', label: '已提取', type: 'success', hint: '处理完成' },
  { value: 'COMPLETED', label: '已完成', type: 'success', hint: '全流程结束' },
  { value: 'FAILED', label: '失败', type: 'danger', hint: '需要处理' }
]

const sourceOptions = [
  { value: 'ARXIV', label: 'ArXiv' },
  { value: 'CORE', label: 'CORE' },
  { value: 'SEMANTIC_SCHOLAR', label: 'Semantic Scholar' },
  { value: 'CROSSREF', label: 'CrossRef' },
  { value: 'CNKI', label: 'CNKI' },
  { value: 'MANUAL', label: '手动录入' }
]

// ==================== 响应式数据 ====================
const loading = ref(false)
const papers = ref([])
const selectedPapers = ref([])
const searchQuery = ref('')
const formRef = ref()

const filter = reactive({
  state: '',
  source: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
  totalPages: 0
})

// 对话框状态
const dialog = reactive({
  visible: false,
  isEdit: false,
  loading: false,
  form: {
    title: '',
    authors: '',
    doi: '',
    yearPublished: new Date().getFullYear(),
    paperSource: 'MANUAL',
    abstractContent: '',
    downloadUrl: ''
  },
  rules: {
    title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
    authors: [{ required: true, message: '请输入作者', trigger: 'blur' }],
    paperSource: [{ required: true, message: '请选择来源', trigger: 'change' }],
    downloadUrl: [{ required: true, message: '请输入PDF链接', trigger: 'blur' }]
  }
})

const batchDialog = reactive({
  visible: false,
  content: '',
  loading: false
})

const detailDrawer = reactive({
  visible: false,
  paper: null
})

const eventsDialog = reactive({
  visible: false,
  events: []
})

// ==================== 轮询管理（优化版） ====================
const pollingQueue = ref(new Map()) // paperId -> { abortController, timeoutId }
const MAX_CONCURRENT_POLLS = 3

const isPolling = (paperId) => pollingQueue.value.has(paperId)

const startPaperPolling = (paperId, immediate = true) => {
  // 如果已在轮询，先停止旧的
  if (pollingQueue.value.has(paperId)) {
    stopPaperPolling(paperId)
  }

  const controller = new AbortController()
  let retryCount = 0
  const maxRetries = 30
  let interval = immediate ? 2000 : 5000

  const poll = async () => {
    if (retryCount >= maxRetries) {
      ElMessage.warning(`论文 ${paperId.slice(0, 8)}... 轮询超时`)
      stopPaperPolling(paperId)
      return
    }

    try {
      const res = await getPaper(paperId, {
        signal: controller.signal,
        includeChunks: false
      })

      const paper = res.data
      retryCount++

      // 更新本地数据
      const idx = papers.value.findIndex(p => p.paperId === paperId)
      if (idx !== -1) {
        papers.value[idx] = { ...papers.value[idx], ...paper }
      }

      // 检查是否完成
      if (!isProcessing(paper.state)) {
        ElMessage.success(`论文处理完成: ${paper.state}`)
        stopPaperPolling(paperId)
        refreshList() // 刷新获取最新状态
        return
      }

      // 继续轮询，指数退避
      interval = Math.min(interval * 1.2, 30000)
      const timeoutId = setTimeout(poll, interval)
      pollingQueue.value.set(paperId, { controller, timeoutId })

    } catch (error) {
      if (error.name === 'AbortError') return

      console.error(`轮询失败 ${paperId}:`, error)
      if (retryCount < 3) {
        const timeoutId = setTimeout(poll, 5000)
        pollingQueue.value.set(paperId, { controller, timeoutId })
      } else {
        stopPaperPolling(paperId)
      }
    }
  }

  // 控制并发数
  if (pollingQueue.value.size >= MAX_CONCURRENT_POLLS) {
    // 等待其他轮询完成
    setTimeout(() => startPaperPolling(paperId, false), 5000)
    return
  }

  poll()
}

const stopPaperPolling = (paperId) => {
  const item = pollingQueue.value.get(paperId)
  if (item) {
    item.controller?.abort()
    clearTimeout(item.timeoutId)
    pollingQueue.value.delete(paperId)
  }
}

// ==================== 本地文件管理（来自第二个组件） ====================
const isLocalFileExist = (row) => {
  if (row.localFilePath || row.fileKey || row.downloadInfo?.ossUrl) return true

  const hasFileStates = [
    'DOWNLOADED', 'TRANSFORMING', 'TRANSFORMED', 'EMBEDDING',
    'EMBEDDING_COMPLETED', 'EXTRACTING', 'MATERIAL_EXTRACTED', 'COMPLETED'
  ]
  return hasFileStates.includes(row.state) || (row.state === 'FAILED' && row.paperId)
}

const getLocalFileUrl = (paperId) => {
  return `${backendBaseUrl.value}/api/local-file/${paperId}`
}

const handleLocalFileAction = (row) => {
  if (!row.paperId) {
    ElMessage.warning('论文ID无效')
    return
  }
  window.open(getLocalFileUrl(row.paperId), '_blank')
}

const openFolder = async (paperId) => {
  try {
    const res = await fetch(`${backendBaseUrl.value}/api/local-file/${paperId}/open-folder`, {
      method: 'POST'
    })
    const text = await res.text()
    ElMessage[res.ok ? 'success' : 'warning'](text)
  } catch (e) {
    ElMessage.error('打开文件夹失败')
  }
}

// ==================== 业务逻辑 ====================
const loadPapers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      state: filter.state || undefined
    }

    // 如果有来源筛选，需要在前端过滤（因为后端page接口可能不支持source筛选）
    const res = await getPapersPage(params)

    if (res.data) {
      papers.value = res.data.items || []
      pagination.total = res.data.total || 0
      pagination.totalPages = res.data.totalPages || 0

      // 自动恢复处理中论文的轮询
      papers.value.forEach(paper => {
        if (isProcessing(paper.state) && !isPolling(paper.paperId)) {
          startPaperPolling(paper.paperId, false)
        }
      })
    }
  } catch (error) {
    ElMessage.error('加载论文列表失败')
  } finally {
    loading.value = false
  }
}

const refreshList = () => {
  pagination.page = 1
  loadPapers()
}

const handleSearch = () => {
  // 实现前端搜索或调用后端搜索接口
  if (searchQuery.value) {
    // 如果后端支持搜索，调用搜索接口；否则前端过滤
    const query = searchQuery.value.toLowerCase()
    papers.value = papers.value.filter(p =>
        p.title?.toLowerCase().includes(query) ||
        p.authors?.toLowerCase().includes(query) ||
        p.doi?.toLowerCase().includes(query)
    )
  } else {
    loadPapers()
  }
}

const handleFilterChange = () => {
  pagination.page = 1
  loadPapers()
}

const handlePageChange = () => {
  loadPapers()
}

const handleSelectionChange = (val) => {
  selectedPapers.value = val
}

// ==================== 流程控制 ====================
const isProcessing = (state) => {
  return ['DOWNLOADING', 'TRANSFORMING', 'EMBEDDING', 'EXTRACTING', 'PRE_ANALYZING'].includes(state)
}

const canDownload = (row) => {
  return ['PRE_RELEVANT', 'FAILED'].includes(row.state) && !isLocalFileExist(row)
}

const canTransform = (row) => {
  return row.state === 'DOWNLOADED' || (row.state === 'FAILED' && isLocalFileExist(row))
}

const canExtract = (row) => {
  return ['TRANSFORMED', 'EMBEDDING_COMPLETED'].includes(row.state)
}

const manualStartDownload = async (paperId) => {
  try {
    await ElMessageBox.confirm('确定开始下载该论文吗？', '确认')
    await startDownload(paperId)
    ElMessage.success('下载任务已启动')
    startPaperPolling(paperId)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '启动失败')
    }
  }
}

const manualStartTransform = async (paperId) => {
  try {
    await ElMessageBox.confirm('确定开始转换该论文吗？', '确认')
    await startTransform(paperId, 'MINER_U')
    ElMessage.success('转换任务已启动')
    startPaperPolling(paperId)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('启动转换失败')
  }
}

const manualStartExtraction = async (paperId) => {
  try {
    await ElMessageBox.confirm('确定开始材料提取吗？', '确认')
    await startExtraction(paperId)
    ElMessage.success('提取任务已启动')
    startPaperPolling(paperId)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('启动提取失败')
  }
}

const retryPaper = (row) => {
  ElMessageBox.confirm(
      `重新处理论文 "${row.title?.slice(0, 20)}..."？`,
      '确认重新处理',
      { type: 'warning' }
  ).then(() => {
    // 根据当前状态决定从哪一步开始
    if (!isLocalFileExist(row)) {
      manualStartDownload(row.paperId)
    } else if (row.state === 'FAILED' || row.state === 'DOWNLOADED') {
      manualStartTransform(row.paperId)
    } else {
      manualStartExtraction(row.paperId)
    }
  })
}

// ==================== 批量操作 ====================
const handleBatchDelete = async () => {
  try {
    await ElMessageBox.confirm(
        `确定删除选中的 ${selectedPapers.value.length} 篇论文吗？此操作不可恢复。`,
        '确认删除',
        { type: 'danger' }
    )

    const ids = selectedPapers.value.map(p => p.paperId)
    // 并行删除
    await Promise.all(ids.map(id => deletePaper(id)))

    ElMessage.success('批量删除成功')
    selectedPapers.value = []
    loadPapers()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleBatchImport = async () => {
  if (!batchDialog.content.trim()) {
    ElMessage.warning('请输入论文数据')
    return
  }

  batchDialog.loading = true
  try {
    const lines = batchDialog.content.split('\n').filter(l => l.trim())
    const papersData = lines.map(line => {
      const parts = line.split('|').map(p => p.trim())
      return {
        title: parts[0],
        doi: parts[1] || undefined,
        authors: parts[2]?.split(',').map(a => a.trim()) || [],
        yearPublished: parseInt(parts[3]) || new Date().getFullYear(),
        downloadUrl: parts[4],
        paperSource: parts[5]?.toUpperCase() || 'MANUAL'
      }
    }).filter(p => p.title && p.downloadUrl)

    let success = 0
    for (const [index, paper] of papersData.entries()) {
      try {
        await createPaper(paper)
        success++
        if ((index + 1) % 5 === 0) {
          ElMessage.info(`已导入 ${index + 1}/${papersData.length}`)
        }
      } catch (e) {
        console.error(`导入失败: ${paper.title}`, e)
      }
    }

    ElMessage.success(`成功导入 ${success}/${papersData.length} 篇论文`)
    batchDialog.visible = false
    batchDialog.content = ''
    loadPapers()
  } finally {
    batchDialog.loading = false
  }
}

// ==================== 详情与事件 ====================
const viewDetail = async (row) => {
  try {
    const res = await getPaper(row.paperId)
    detailDrawer.paper = res.data
    detailDrawer.visible = true
  } catch (e) {
    ElMessage.error('获取详情失败')
  }
}

const viewEvents = async (paperId) => {
  try {
    eventsDialog.visible = true
    const res = await getPaperEvents(paperId, 50)
    eventsDialog.events = res.data || []
  } catch (e) {
    ElMessage.error('获取事件历史失败')
  }
}

const deletePaperHandler = async (row) => {
  if (isProcessing(row.state)) {
    ElMessage.warning('处理中的论文无法删除')
    return
  }

  try {
    await ElMessageBox.confirm(
        `确定删除 "${row.title?.slice(0, 30)}..." 吗？`,
        '确认删除',
        { type: 'warning' }
    )
    await deletePaper(row.paperId)
    ElMessage.success('删除成功')
    loadPapers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// ==================== 表单操作 ====================
const openCreateDialog = () => {
  dialog.isEdit = false
  dialog.form = {
    title: '',
    authors: '',
    doi: '',
    yearPublished: new Date().getFullYear(),
    paperSource: 'MANUAL',
    abstractContent: '',
    downloadUrl: ''
  }
  dialog.visible = true
}

const submitForm = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    dialog.loading = true

    const payload = {
      ...dialog.form,
      authors: dialog.form.authors.split(',').map(s => s.trim()).filter(Boolean)
    }

    const res = await createPaper(payload)
    ElMessage.success('创建成功')
    dialog.visible = false

    if (res.data?.paperId) {
      startPaperPolling(res.data.paperId)
    }
    loadPapers()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '创建失败')
  } finally {
    dialog.loading = false
  }
}

// ==================== 工具函数 ====================
const formatAuthors = (authors) => {
  if (!authors) return '未知'
  const arr = Array.isArray(authors) ? authors : authors.split(',').map(s => s.trim())
  return arr.length > 2 ? `${arr[0]} 等 ${arr.length} 人` : arr.join(', ')
}

const formatTime = (time) => time ? new Date(time).toLocaleString() : '-'

const formatSource = (source) => {
  const found = sourceOptions.find(s => s.value === source)
  return found?.label || source
}

const getStateText = (state) => {
  const found = paperStates.find(s => s.value === state)
  return found?.label || state
}

const getStateType = (state) => {
  const found = paperStates.find(s => s.value === state)
  return found?.type || 'info'
}

const getStateHint = (state) => {
  const found = paperStates.find(s => s.value === state)
  return found?.hint || ''
}

const getSourceType = (source) => {
  const map = { 'ARXIV': 'primary', 'CORE': 'success', 'MANUAL': 'info' }
  return map[source] || 'info'
}

const getProgress = (state) => {
  const map = { 'DOWNLOADING': 30, 'TRANSFORMING': 60, 'EMBEDDING': 80, 'EXTRACTING': 90 }
  return map[state] || 0
}

const getEventType = (toState) => {
  if (toState === 'FAILED') return 'danger'
  if (toState === 'COMPLETED') return 'success'
  if (['DOWNLOADED', 'TRANSFORMED'].includes(toState)) return 'primary'
  return 'info'
}

const openDoi = (doi) => {
  window.open(`https://doi.org/${doi}`, '_blank')
}

const quickFilter = (state) => {
  filter.state = state
  handleFilterChange()
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadPapers()
})

onUnmounted(() => {
  // 清理所有轮询
  pollingQueue.value.forEach((item, paperId) => {
    stopPaperPolling(paperId)
  })
})
</script>

<style scoped>
.paper-management {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.3s;
  text-align: center;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 12px;
}

.main-card {
  border-radius: 8px;
}

.header-compact {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-group {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.action-group {
  display: flex;
  gap: 8px;
}

.paper-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title-line {
  display: flex;
  align-items: center;
}

.paper-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.meta-line {
  display: flex;
  gap: 12px;
  align-items: center;
  font-size: 12px;
  color: #606266;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #409EFF;
}

.meta-item:hover {
  text-decoration: underline;
}

.state-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.state-badge.downloading,
.state-badge.transforming,
.state-badge.embedding,
.state-badge.extracting,
.state-badge.pre_analyzing {
  background: #ecf5ff;
  color: #409EFF;
  animation: pulse 2s infinite;
}

.state-badge.failed {
  background: #fef0f0;
  color: #f56c6c;
}

.state-badge.completed,
.state-badge.material_extracted {
  background: #f0f9eb;
  color: #67c23a;
}

.file-status {
  cursor: pointer;
  display: flex;
  justify-content: center;
}

.success-icon {
  color: #67c23a;
  font-size: 20px;
}

.no-file {
  color: #c0c4cc;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.event-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.event-error {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

:deep(.el-table__row) {
  transition: background-color 0.2s;
}

:deep(.el-table__row:hover) {
  background-color: #f5f7fa !important;
}
</style>
