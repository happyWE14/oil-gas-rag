<template>
  <div class="paper-detail-container" v-loading="loading" element-loading-text="加载论文详情...">
    <!-- 404 错误状态 -->
    <el-result
        v-if="error"
        icon="error"
        title="论文不存在或已删除"
        sub-title="请检查链接是否正确，或返回列表查看"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/papers')">返回论文列表</el-button>
      </template>
    </el-result>

    <!-- 主内容区 -->
    <template v-else-if="paper">
      <!-- 页面头部 -->
      <div class="detail-header" :class="{ 'drawer-mode': isDrawer }">
        <div class="header-left">
          <el-button v-if="!isDrawer" link @click="router.back()" :icon="ArrowLeft">
            返回
          </el-button>
          <el-divider v-if="!isDrawer" direction="vertical" />
          <div class="title-section">
            <h1 class="paper-title">{{ paper.title || '未命名论文' }}</h1>
            <div class="title-meta">
              <el-tag :type="getStateType(paper.state)" size="small" effect="dark">
                <el-icon v-if="isProcessing(paper.state)" class="is-loading"><Loading /></el-icon>
                {{ getStateText(paper.state) }}
              </el-tag>
              <el-tag size="small" :type="getSourceType(paper.paperSource)" effect="plain">
                {{ formatSource(paper.paperSource) }}
              </el-tag>
              <span v-if="paper.yearPublished" class="year-badge">
                <el-icon><Calendar /></el-icon> {{ paper.yearPublished }}
              </span>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <el-button
              v-if="isLocalFileExist(paper)"
              type="success"
              @click="viewPdf"
              :icon="View"
          >
            查看PDF
          </el-button>
          <el-button
              v-if="canProcess(paper)"
              type="primary"
              @click="handleProcess"
              :loading="processing"
              :icon="VideoPlay"
          >
            {{ getProcessButtonText(paper) }}
          </el-button>
          <el-button @click="refreshDetail" :icon="Refresh" circle title="刷新" />
          <el-button v-if="isDrawer" @click="$emit('close')" :icon="Close" circle />
        </div>
      </div>

      <!-- 状态进度条（仅在处理中时显示） -->
      <div v-if="isProcessing(paper.state)" class="process-banner">
        <el-alert
            :title="`正在处理：${getProcessHint(paper.state)}`"
            type="info"
            :closable="false"
            show-icon
        >
          <template #default>
            <el-progress
                :percentage="getProgress(paper.state)"
                :status="getProgressStatus(paper.state)"
                striped
                striped-flow
                :duration="3"
                style="margin-top: 8px;"
            />
          </template>
        </el-alert>
      </div>

      <!-- 失败状态提示 -->
      <el-alert
          v-if="paper.state === 'FAILED'"
          :title="`处理失败：${paper.errorMessage || '未知错误'}`"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 16px;"
      >
        <template #default>
          <div style="margin-top: 8px;">
            <el-button
                v-if="paper.errorReason"
                link
                type="danger"
                @click="showErrorDetail = !showErrorDetail"
            >
              {{ showErrorDetail ? '隐藏详情' : '查看详情' }}
            </el-button>
            <div v-if="showErrorDetail" class="error-detail">
              <p><strong>错误类型：</strong>{{ paper.errorCategory || 'N/A' }}</p>
              <p><strong>原因：</strong>{{ paper.errorReason || 'N/A' }}</p>
              <p><strong>时间：</strong>{{ formatTime(paper.updatedAt) }}</p>
            </div>
            <el-button
                type="warning"
                size="small"
                @click="retryProcess"
                style="margin-top: 8px;"
            >
              <el-icon><RefreshRight /></el-icon> 重新处理
            </el-button>
          </div>
        </template>
      </el-alert>

      <!-- 内容网格 -->
      <el-row :gutter="24" class="content-grid">
        <el-col :xs="24" :lg="16">
          <!-- 基本信息 -->
          <el-card shadow="never" class="info-card">
            <template #header>
              <div class="card-header">
                <span>基本信息</span>
                <el-button link type="primary" @click="copyPaperInfo">
                  <el-icon><CopyDocument /></el-icon>复制信息
                </el-button>
              </div>
            </template>

            <el-descriptions :column="isDrawer ? 1 : 2" border>
              <el-descriptions-item label="论文ID" label-class-name="label-bold">
                <div class="id-cell">
                  <code>{{ paper.paperId }}</code>
                  <el-button link :icon="CopyDocument" @click="copy(paper.paperId)" />
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="DOI" label-class-name="label-bold">
                <div v-if="paper.doi" class="doi-cell">
                  <el-link type="primary" :href="`https://doi.org/${paper.doi}`" target="_blank">
                    {{ paper.doi }} <el-icon><Link /></el-icon>
                  </el-link>
                </div>
                <span v-else class="text-muted">未提供</span>
              </el-descriptions-item>

              <el-descriptions-item label="作者" label-class-name="label-bold" :span="2">
                <div class="authors-list">
                  <el-tag
                      v-for="(author, idx) in parseAuthors(paper.authors)"
                      :key="idx"
                      size="small"
                      effect="plain"
                      class="author-tag"
                  >
                    {{ author }}
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="来源链接" label-class-name="label-bold" :span="2">
                <el-link v-if="paper.downloadUrl" :href="paper.downloadUrl" target="_blank" type="info">
                  <el-icon><Link /></el-icon> {{ paper.downloadUrl }}
                </el-link>
                <span v-else class="text-muted">未提供</span>
              </el-descriptions-item>

              <el-descriptions-item label="创建时间" label-class-name="label-bold">
                {{ formatTime(paper.createdAt) }}
              </el-descriptions-item>

              <el-descriptions-item label="更新时间" label-class-name="label-bold">
                {{ formatTime(paper.updatedAt) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 摘要 -->
          <el-card shadow="never" class="abstract-card" style="margin-top: 16px;">
            <template #header>
              <span>论文摘要</span>
            </template>
            <div class="abstract-content" v-html="formatAbstract(paper.abstractContent)" />
          </el-card>

          <!-- 文本块（Chunks）- 仅当已转换完成时显示 -->
          <el-card
              v-if="paper.chunks && paper.chunks.length > 0"
              shadow="never"
              class="chunks-card"
              style="margin-top: 16px;"
          >
            <template #header>
              <div class="card-header">
                <span>文本分块 ({{ paper.chunks.length }})</span>
                <el-input
                    v-model="chunkSearch"
                    placeholder="搜索文本块..."
                    size="small"
                    style="width: 200px"
                    clearable
                >
                  <template #prefix>
                    <el-icon><Search /></el-icon>
                  </template>
                </el-input>
              </div>
            </template>

            <el-collapse>
              <el-collapse-item
                  v-for="(chunk, idx) in filteredChunks"
                  :key="chunk.chunkId"
                  :title="`块 ${chunk.orderNo || idx + 1} ${chunk.embeddingModel ? '[已向量化]' : ''}`"
              >
                <div class="chunk-content">{{ chunk.content }}</div>
              </el-collapse-item>
            </el-collapse>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="8">
          <!-- 状态时间线 -->
          <el-card shadow="never" class="timeline-card">
            <template #header>
              <span>处理流程</span>
            </template>
            <el-timeline>
              <el-timeline-item
                  v-for="(step, index) in processSteps"
                  :key="step.value"
                  :type="getStepType(step.value)"
                  :icon="getStepIcon(step.value)"
                  :timestamp="getStepTime(step.value)"
              >
                {{ step.label }}
              </el-timeline-item>
            </el-timeline>
          </el-card>

          <!-- 本地文件信息 -->
          <el-card
              v-if="isLocalFileExist(paper)"
              shadow="never"
              class="file-card"
              style="margin-top: 16px;"
          >
            <template #header>
              <span>本地文件</span>
            </template>
            <div class="file-info">
              <div class="file-icon">
                <el-icon :size="40" color="#67C23A"><DocumentChecked /></el-icon>
              </div>
              <div class="file-meta">
                <div class="file-name">PDF 文件</div>
                <div class="file-size" v-if="paper.downloadInfo?.fileSize">
                  {{ formatFileSize(paper.downloadInfo.fileSize) }}
                </div>
                <div class="file-path" :title="getLocalFilePath(paper)">
                  {{ getLocalFilePath(paper) }}
                </div>
              </div>
            </div>
            <div class="file-actions">
              <el-button type="primary" @click="viewPdf" style="width: 100%;">
                <el-icon><View /></el-icon> 查看 PDF
              </el-button>
              <el-button @click="openFolder" style="width: 100%; margin-top: 8px; margin-left: 0;">
                <el-icon><FolderOpened /></el-icon> 打开所在文件夹
              </el-button>
            </div>
          </el-card>

          <!-- 提取的材料 -->
          <el-card
              v-if="paper.materials && paper.materials.length > 0"
              shadow="never"
              class="materials-card"
              style="margin-top: 16px;"
          >
            <template #header>
              <span>提取材料 ({{ paper.materials.length }})</span>
            </template>
            <div class="materials-list">
              <div
                  v-for="m in paper.materials"
                  :key="m.material"
                  class="material-item"
              >
                <div class="material-name">
                  <el-icon><Collection /></el-icon>
                  {{ m.material }}
                </div>
                <div v-if="m.property" class="material-prop">{{ m.property }}</div>
                <div v-if="m.method" class="material-method">方法: {{ m.method }}</div>
                <el-progress
                    v-if="m.confidence"
                    :percentage="Math.round(m.confidence * 100)"
                    :color="getConfidenceColor(m.confidence)"
                    size="small"
                />
              </div>
            </div>
          </el-card>

          <!-- 操作记录 -->
          <el-card shadow="never" style="margin-top: 16px;">
            <template #header>
              <span>最近事件</span>
              <el-button link type="primary" @click="loadEvents">查看全部</el-button>
            </template>
            <el-timeline v-if="recentEvents.length > 0">
              <el-timeline-item
                  v-for="event in recentEvents.slice(0, 3)"
                  :key="event.occurredAt"
                  :type="getEventType(event.toState)"
                  :timestamp="formatTime(event.occurredAt)"
              >
                <div class="event-title">
                  {{ event.fromState || '初始' }} → {{ event.toState }}
                </div>
                <div v-if="event.operator" class="event-operator">
                  操作: {{ event.operator }}
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无事件记录" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- 事件历史弹窗 -->
    <el-dialog v-model="eventsDialog.visible" title="完整事件历史" width="600px">
      <el-timeline v-if="eventsDialog.events.length > 0">
        <el-timeline-item
            v-for="(event, index) in eventsDialog.events"
            :key="index"
            :type="getEventType(event.toState)"
            :timestamp="formatTime(event.occurredAt)"
        >
          <p><strong>{{ event.fromState || '初始' }} → {{ event.toState }}</strong></p>
          <p v-if="event.operator">操作人: {{ event.operator }}</p>
          <p v-if="event.reason" style="color: #f56c6c;">原因: {{ event.reason }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无事件记录" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Loading, Calendar, Link, CopyDocument, Refresh,
  View, VideoPlay, RefreshRight, Close, Search, DocumentChecked,
  FolderOpened, Collection, Check, Warning, Download, CircleCheck
} from '@element-plus/icons-vue'
import { getPaper, getPaperEvents, startDownload, startTransform, startExtraction } from '@/api/paper'
import { API_ORIGIN } from '@/config/runtime'

const props = defineProps({
  // 抽屉模式传入，页面模式从路由获取
  paperId: {
    type: String,
    default: null
  },
  // 是否为抽屉模式
  isDrawer: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'refresh'])

const route = useRoute()
const router = useRouter()

// 状态
const loading = ref(false)
const error = ref(false)
const paper = ref(null)
const processing = ref(false)
const showErrorDetail = ref(false)
const chunkSearch = ref('')
const recentEvents = ref([])

const eventsDialog = reactive({
  visible: false,
  events: []
})

// 后端基础URL（从localStorage获取或默认）
const backendBaseUrl = ref(localStorage.getItem('paper_backend_url') || API_ORIGIN)

// 计算实际使用的 paperId
const actualPaperId = computed(() => {
  return props.paperId || route.params.id
})

// 过滤后的文本块
const filteredChunks = computed(() => {
  if (!paper.value?.chunks) return []
  if (!chunkSearch.value) return paper.value.chunks
  const query = chunkSearch.value.toLowerCase()
  return paper.value.chunks.filter(c =>
      c.content?.toLowerCase().includes(query)
  )
})

// 处理步骤定义
const processSteps = [
  { value: 'DISCOVERED', label: '已发现' },
  { value: 'PRE_ANALYZING', label: 'AI分析' },
  { value: 'PRE_RELEVANT', label: '判定相关' },
  { value: 'DOWNLOADING', label: '下载PDF' },
  { value: 'DOWNLOADED', label: '已下载' },
  { value: 'TRANSFORMING', label: '转换文本' },
  { value: 'TRANSFORMED', label: '已转换' },
  { value: 'EMBEDDING', label: '向量化' },
  { value: 'EMBEDDING_COMPLETED', label: '向量完成' },
  { value: 'EXTRACTING', label: '提取材料' },
  { value: 'MATERIAL_EXTRACTED', label: '已提取' },
  { value: 'COMPLETED', label: '已完成' }
]

// 加载论文详情
const loadDetail = async () => {
  if (!actualPaperId.value) {
    error.value = true
    return
  }

  loading.value = true
  error.value = false

  try {
    // 获取基本信息 + chunks
    const res = await getPaper(actualPaperId.value, { includeChunks: true })
    paper.value = res.data

    // 加载最近事件
    await loadEvents()

    // 如果处于处理中状态，启动轮询
    if (isProcessing(paper.value.state)) {
      startPolling()
    }
  } catch (err) {
    console.error('加载论文详情失败:', err)
    if (err.response?.status === 404) {
      error.value = true
    } else {
      ElMessage.error('加载失败: ' + (err.message || '未知错误'))
    }
  } finally {
    loading.value = false
  }
}

// 加载事件历史
const loadEvents = async () => {
  try {
    const res = await getPaperEvents(actualPaperId.value, 50)
    recentEvents.value = res.data || []
  } catch (e) {
    console.error('加载事件失败:', e)
  }
}

// 轮询状态（当论文处理中时）
let pollTimer = null
const startPolling = () => {
  if (pollTimer) clearInterval(pollTimer)

  pollTimer = setInterval(async () => {
    try {
      const res = await getPaper(actualPaperId.value, { includeChunks: false })
      const newData = res.data
      paper.value.state = newData.state

      // 如果处理完成，停止轮询并刷新完整数据
      if (!isProcessing(newData.state)) {
        clearInterval(pollTimer)
        pollTimer = null
        ElMessage.success('论文处理完成')
        await loadDetail() // 刷新获取完整数据（包括chunks等）
        emit('refresh')
      }
    } catch (e) {
      console.error('轮询失败:', e)
    }
  }, 3000)
}

// 停止轮询
const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 刷新详情
const refreshDetail = () => {
  loadDetail()
}

// 判断是否为处理中状态
const isProcessing = (state) => {
  return ['DOWNLOADING', 'TRANSFORMING', 'EMBEDDING', 'EXTRACTING', 'PRE_ANALYZING'].includes(state)
}

// 判断本地文件是否存在
const isLocalFileExist = (paper) => {
  if (!paper) return false
  const hasFileStates = [
    'DOWNLOADED', 'TRANSFORMING', 'TRANSFORMED', 'EMBEDDING',
    'EMBEDDING_COMPLETED', 'EXTRACTING', 'MATERIAL_EXTRACTED', 'COMPLETED'
  ]
  return hasFileStates.includes(paper.state) || paper.localFilePath || paper.fileKey
}

// 获取本地文件访问URL（后端代理）
const getLocalFileUrl = (paperId) => {
  return `${backendBaseUrl.value}/api/local-file/${paperId}`
}

// 获取本地文件路径（用于显示）
const getLocalFilePath = (paper) => {
  if (paper.localFilePath) return paper.localFilePath
  // 根据ID构造默认路径（与后端约定一致）
  return `paper_${paper.paperId}.pdf`
}

// 查看PDF
const viewPdf = () => {
  window.open(getLocalFileUrl(actualPaperId.value), '_blank')
}

// 打开文件夹
const openFolder = async () => {
  try {
    const res = await fetch(`${backendBaseUrl.value}/api/local-file/${actualPaperId.value}/open-folder`, {
      method: 'POST'
    })
    const text = await res.text()
    ElMessage[res.ok ? 'success' : 'warning'](text)
  } catch (e) {
    ElMessage.error('打开文件夹失败')
  }
}

// 判断是否可以处理（流程控制）
const canProcess = (paper) => {
  if (!paper) return false
  return ['PRE_RELEVANT', 'DOWNLOADED', 'TRANSFORMED', 'EMBEDDING_COMPLETED', 'FAILED'].includes(paper.state)
}

// 获取处理按钮文本
const getProcessButtonText = (paper) => {
  if (paper.state === 'PRE_RELEVANT') return '开始下载'
  if (paper.state === 'DOWNLOADED') return '开始转换'
  if (paper.state === 'TRANSFORMED' || paper.state === 'EMBEDDING_COMPLETED') return '提取材料'
  if (paper.state === 'FAILED') return '重新处理'
  return '处理'
}

// 处理流程控制
const handleProcess = async () => {
  if (!paper.value) return

  processing.value = true
  try {
    if (paper.value.state === 'PRE_RELEVANT' || (paper.value.state === 'FAILED' && !isLocalFileExist(paper.value))) {
      await startDownload(actualPaperId.value)
      ElMessage.success('开始下载')
    } else if (paper.value.state === 'DOWNLOADED' || (paper.value.state === 'FAILED' && isLocalFileExist(paper.value))) {
      await startTransform(actualPaperId.value, 'MINER_U')
      ElMessage.success('开始转换')
    } else if (['TRANSFORMED', 'EMBEDDING_COMPLETED'].includes(paper.value.state)) {
      await startExtraction(actualPaperId.value)
      ElMessage.success('开始提取')
    }
    startPolling()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.message || '未知错误'))
  } finally {
    processing.value = false
  }
}

// 重新处理
const retryProcess = () => {
  handleProcess()
}

// 复制功能
const copy = (text) => {
  if (!text) return
  navigator.clipboard.writeText(String(text))
  ElMessage.success('已复制到剪贴板')
}

const copyPaperInfo = () => {
  const info = `[${paper.value.paperId}] ${paper.value.title}\n作者: ${paper.value.authors}\n状态: ${getStateText(paper.value.state)}`
  copy(info)
}

// 格式化函数
const formatTime = (time) => time ? new Date(time).toLocaleString('zh-CN') : '-'

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const parseAuthors = (authors) => {
  if (!authors) return []
  if (Array.isArray(authors)) return authors
  return String(authors).split(/[,，;；]/).map(s => s.trim()).filter(Boolean)
}

const formatAbstract = (content) => {
  if (!content) return '<p style="color: #999;">暂无摘要</p>'
  // 简单的格式化：将换行符转为 <br>
  return content.replace(/\n/g, '<br>')
}

const formatSource = (source) => {
  const map = {
    'ARXIV': 'ArXiv',
    'CORE': 'CORE',
    'SEMANTIC_SCHOLAR': 'Semantic Scholar',
    'MANUAL': '手动录入'
  }
  return map[source] || source
}

// 状态相关
const getStateType = (state) => {
  const map = {
    DISCOVERED: 'info',
    PRE_ANALYZING: 'warning',
    PRE_RELEVANT: 'success',
    IRRELEVANT: 'info',
    DOWNLOADING: 'warning',
    DOWNLOADED: 'success',
    TRANSFORMING: 'warning',
    TRANSFORMED: 'success',
    EMBEDDING: 'warning',
    EMBEDDING_COMPLETED: 'success',
    EXTRACTING: 'warning',
    MATERIAL_EXTRACTED: 'success',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return map[state] || 'info'
}

const getStateText = (state) => {
  const found = processSteps.find(s => s.value === state)
  return found ? found.label : state
}

const getProcessHint = (state) => {
  const hints = {
    DOWNLOADING: '正在下载PDF文件到本地...',
    TRANSFORMING: '正在将PDF转换为Markdown文本...',
    EMBEDDING: '正在生成向量嵌入...',
    EXTRACTING: '正在提取材料信息...',
    PRE_ANALYZING: 'AI正在分析论文相关性...'
  }
  return hints[state] || '处理中...'
}

const getProgress = (state) => {
  const map = {
    PRE_ANALYZING: 20,
    DOWNLOADING: 40,
    DOWNLOADED: 50,
    TRANSFORMING: 60,
    TRANSFORMED: 70,
    EMBEDDING: 80,
    EMBEDDING_COMPLETED: 90,
    EXTRACTING: 95
  }
  return map[state] || 0
}

const getProgressStatus = (state) => {
  if (state === 'FAILED') return 'exception'
  return ''
}

const getSourceType = (source) => {
  const map = { ARXIV: 'primary', CORE: 'success', MANUAL: 'info' }
  return map[source] || 'info'
}

// 时间线相关
const getStepType = (stepValue) => {
  if (!paper.value) return ''
  const currentIdx = processSteps.findIndex(s => s.value === paper.value.state)
  const stepIdx = processSteps.findIndex(s => s.value === stepValue)

  if (stepIdx < currentIdx) return 'success'
  if (stepIdx === currentIdx) return 'primary'
  return ''
}

const getStepIcon = (stepValue) => {
  if (!paper.value) return ''
  const currentIdx = processSteps.findIndex(s => s.value === paper.value.state)
  const stepIdx = processSteps.findIndex(s => s.value === stepValue)

  if (stepIdx < currentIdx) return Check
  if (stepIdx === currentIdx && isProcessing(paper.value.state)) return Loading
  return ''
}

const getStepTime = (stepValue) => {
  // 这里可以根据实际情况从 events 中提取时间
  return ''
}

const getEventType = (toState) => {
  if (toState === 'FAILED') return 'danger'
  if (toState === 'COMPLETED') return 'success'
  return 'primary'
}

const getConfidenceColor = (confidence) => {
  if (confidence > 0.8) return '#67C23A'
  if (confidence > 0.5) return '#E6A23C'
  return '#F56C6C'
}

// 监听 paperId 变化（抽屉模式切换论文时）
watch(() => actualPaperId.value, (newId) => {
  if (newId) {
    stopPolling()
    loadDetail()
  }
})

onMounted(() => {
  loadDetail()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.paper-detail-container {
  padding: 24px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 100%;
}

.drawer-mode {
  padding: 0;
}

/* 头部样式 */
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
}

.title-section {
  flex: 1;
}

.paper-title {
  margin: 0 0 12px 0;
  font-size: 24px;
  line-height: 1.4;
  color: #303133;
  font-weight: 600;
}

.title-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.year-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 14px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 处理中横幅 */
.process-banner {
  margin-bottom: 20px;
}

/* 卡片样式 */
.info-card, .abstract-card, .chunks-card, .timeline-card, .file-card, .materials-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

/* ID单元格 */
.id-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: monospace;
  font-size: 13px;
}

/* 作者列表 */
.authors-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.author-tag {
  margin-right: 0;
}

/* 摘要内容 */
.abstract-content {
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
}

/* 错误详情 */
.error-detail {
  margin-top: 12px;
  padding: 12px;
  background: #fef0f0;
  border-radius: 4px;
  font-size: 13px;
  color: #f56c6c;
}

/* 文件信息 */
.file-info {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.file-meta {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.file-size {
  font-size: 13px;
  color: #67c23a;
  margin-bottom: 4px;
}

.file-path {
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

/* 材料列表 */
.materials-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.material-item {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  border-left: 3px solid #67c23a;
}

.material-name {
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.material-prop {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
}

.material-method {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

/* 文本块 */
.chunk-content {
  white-space: pre-wrap;
  line-height: 1.6;
  color: #606266;
  max-height: 200px;
  overflow-y: auto;
}

/* 响应式 */
@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .paper-title {
    font-size: 18px;
  }
}
</style>
