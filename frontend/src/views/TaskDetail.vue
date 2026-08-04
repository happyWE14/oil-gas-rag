<template>
  <div class="task-detail-pro" v-if="task">
    <!-- 模式1：独立页面头部 -->
    <template v-if="mode === 'page'">
      <el-page-header @back="goBack" title="任务详情" class="page-header" />
      <div class="page-title-section">
        <h2>{{ task.taskName }}</h2>
        <el-tag :type="getStatusType(task.status)" size="large" effect="dark">
          <el-icon v-if="task.status === 'RUNNING'" class="is-loading"><Loading /></el-icon>
          {{ getStatusText(task.status) }}
        </el-tag>
      </div>
    </template>

    <!-- 模式2：抽屉头部 -->
    <template v-else>
      <div class="drawer-header">
        <div class="drawer-title">
          <h3>{{ task.taskName }}</h3>
          <el-tag :type="getStatusType(task.status)" effect="dark" size="small">
            <el-icon v-if="task.status === 'RUNNING'" class="is-loading"><Loading /></el-icon>
            {{ getStatusText(task.status) }}
          </el-tag>
        </div>
        <div class="drawer-actions">
          <el-button circle :icon="Refresh" @click="loadTask" :loading="loading" title="刷新" />
          <el-dropdown trigger="click">
            <el-button text type="primary">
              更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="copyTaskInfo">复制任务信息</el-dropdown-item>
                <el-dropdown-item @click="copyTaskId">复制任务ID</el-dropdown-item>
                <el-dropdown-item divided type="danger" @click="handleDelete">删除任务</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </template>

    <!-- 关键指标卡片 -->
    <div class="metrics-row" :class="mode">
      <div class="metric-card" :class="task.status?.toLowerCase()">
        <div class="metric-icon">
          <el-icon :size="24"><Clock /></el-icon>
        </div>
        <div class="metric-content">
          <div class="metric-value">{{ formatDuration(task.durationMs) }}</div>
          <div class="metric-label">执行耗时</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon blue">
          <el-icon :size="24"><Refresh /></el-icon>
        </div>
        <div class="metric-content">
          <div class="metric-value">{{ task.retryCount }}/{{ task.maxRetries }}</div>
          <div class="metric-label">重试次数</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon purple">
          <el-icon :size="24"><Timer /></el-icon>
        </div>
        <div class="metric-content">
          <div class="metric-value">{{ calculateQueueTime() }}</div>
          <div class="metric-label">排队时长</div>
        </div>
      </div>
    </div>

    <!-- 时间线 -->
    <div class="timeline-section">
      <h4 class="section-title">执行时间线</h4>
      <el-timeline>
        <el-timeline-item
            v-if="task.createdAt"
            type="primary"
            :timestamp="formatTime(task.createdAt)"
            placement="top"
        >
          <div class="timeline-node">
            <div class="node-title">任务创建</div>
            <div class="node-desc">由 {{ task.createdBy }} 创建</div>
          </div>
        </el-timeline-item>

        <el-timeline-item
            v-if="task.startTime"
            type="success"
            :timestamp="formatTime(task.startTime)"
            placement="top"
        >
          <div class="timeline-node">
            <div class="node-title">开始执行</div>
            <div class="node-desc">Worker 节点开始处理</div>
          </div>
        </el-timeline-item>

        <el-timeline-item
            v-if="task.endTime"
            :type="task.status === 'SUCCEED' ? 'success' : 'danger'"
            :timestamp="formatTime(task.endTime)"
            placement="top"
        >
          <div class="timeline-node">
            <div class="node-title">{{ task.status === 'SUCCEED' ? '执行完成' : '执行结束' }}</div>
            <div class="node-desc" v-if="task.durationMs">耗时 {{ formatDuration(task.durationMs) }}</div>
          </div>
        </el-timeline-item>

        <el-timeline-item
            v-if="task.nextRetryAt && task.status === 'FAILED'"
            type="warning"
            :timestamp="formatTime(task.nextRetryAt)"
            placement="top"
        >
          <div class="timeline-node">
            <div class="node-title">下次重试</div>
            <div class="node-desc">将在 {{ formatRelativeTime(task.nextRetryAt) }} 后重试</div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <el-divider />

    <!-- 详细信息 -->
    <div class="info-section">
      <h4 class="section-title">基本信息</h4>
      <el-descriptions :column="mode === 'drawer' ? 1 : 2" border>
        <el-descriptions-item label="任务ID" label-class-name="label-bold">
          <div class="id-cell">
            <span>{{ task.id }}</span>
            <el-button link type="primary" :icon="CopyDocument" @click="copy(task.id)" />
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="任务类型" label-class-name="label-bold">
          <el-tag size="small" :type="getTaskTypeType(task.taskType)">
            {{ getTaskTypeText(task.taskType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联论文" label-class-name="label-bold">
          <span v-if="task.paperId" class="paper-link" @click="goToPaper(task.paperId)">
            {{ task.paperId }} <el-icon><Link /></el-icon>
          </span>
          <span v-else class="text-muted">无关联</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" label-class-name="label-bold">
          {{ formatTime(task.createdAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 参数与结果 -->
    <div class="data-sections">
      <div class="data-block" v-if="task.parameters">
        <div class="block-header">
          <h4 class="section-title">输入参数</h4>
          <el-button link type="primary" :icon="CopyDocument" @click="copy(task.parameters)">
            复制
          </el-button>
        </div>
        <div class="code-block">
          <pre>{{ formatJSON(task.parameters) }}</pre>
        </div>
      </div>

      <div class="data-block" v-if="task.result">
        <div class="block-header">
          <h4 class="section-title">执行结果</h4>
          <el-button link type="primary" :icon="CopyDocument" @click="copy(task.result)">
            复制
          </el-button>
        </div>
        <div class="code-block success">
          <pre>{{ formatJSON(task.result) }}</pre>
        </div>
      </div>

      <div class="data-block" v-if="task.errorMessage">
        <div class="block-header">
          <h4 class="section-title">错误信息</h4>
          <el-button link type="danger" :icon="CopyDocument" @click="copy(task.errorMessage)">
            复制
          </el-button>
        </div>
        <div class="code-block error">
          <div class="error-header">
            <el-icon><CircleClose /></el-icon>
            <span>{{ task.errorCategory || '执行错误' }}</span>
          </div>
          <pre>{{ task.errorMessage }}</pre>
          <div v-if="task.errorReason" class="error-reason">
            <strong>原因：</strong>{{ task.errorReason }}
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <div class="footer-actions" :class="mode">
      <el-button
          v-if="task.status === 'FAILED'"
          type="warning"
          size="large"
          @click="handleRetry"
          :loading="actionLoading.retry"
      >
        <el-icon><RefreshRight /></el-icon> 重试任务
      </el-button>

      <el-button
          v-if="['WAITING', 'RUNNING'].includes(task.status)"
          type="danger"
          size="large"
          @click="handleCancel"
          :loading="actionLoading.cancel"
      >
        <el-icon><CircleClose /></el-icon> 取消任务
      </el-button>

      <!-- 根据模式显示不同按钮 -->
      <el-button v-if="mode === 'drawer'" @click="$emit('close')" size="large">
        关闭
      </el-button>
      <el-button v-else @click="goBack" size="large">
        返回列表
      </el-button>
    </div>
  </div>

  <div v-else class="loading-container">
    <el-skeleton :rows="10" animated />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown, Clock, Timer, Loading, Refresh,
  CopyDocument, Link, CircleClose, RefreshRight
} from '@element-plus/icons-vue'
import { getTask, retryTask, cancelTask, deleteTask } from '@/api/task'

const props = defineProps({
  // 任务ID：外部传入（抽屉模式）或从路由获取（页面模式）
  taskId: {
    type: [String, Number],
    default: null
  },
  // 显示模式：'page' 独立页面 | 'drawer' 抽屉内嵌
  mode: {
    type: String,
    default: 'page',
    validator: (val) => ['page', 'drawer'].includes(val)
  }
})

const emit = defineEmits(['refresh', 'close', 'delete'])

const route = useRoute()
const router = useRouter()

const task = ref(null)
const loading = ref(false)
const actionLoading = reactive({
  retry: false,
  cancel: false
})

// 计算实际使用的 taskId：props 优先，否则从路由获取
const actualTaskId = computed(() => {
  return props.taskId || route.params.id
})

// 加载任务数据
const loadTask = async () => {
  if (!actualTaskId.value) {
    ElMessage.error('任务ID不能为空')
    return
  }

  loading.value = true
  try {
    const { data } = await getTask(actualTaskId.value)
    task.value = data
  } catch (error) {
    ElMessage.error('加载任务详情失败：' + (error.message || '未知错误'))
    // 加载失败时关闭抽屉或返回列表
    if (props.mode === 'drawer') {
      emit('close')
    } else {
      goBack()
    }
  } finally {
    loading.value = false
  }
}

// 重试任务
const handleRetry = async () => {
  try {
    await ElMessageBox.confirm('确定要重试该任务吗？', '确认重试', {
      type: 'warning',
      confirmButtonText: '确认重试'
    })
    actionLoading.retry = true
    const { data } = await retryTask(actualTaskId.value, 3)
    ElMessage.success(`重试成功，当前状态：${data.currentStatus}`)
    await loadTask()
    emit('refresh')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '重试失败')
    }
  } finally {
    actionLoading.retry = false
  }
}

// 取消任务
const handleCancel = async () => {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '确认取消', {
      type: 'danger',
      confirmButtonText: '确认取消'
    })
    actionLoading.cancel = true
    await cancelTask(actualTaskId.value, {
      status: 'CANCELED',
      result: '',
      errorMessage: '用户手动取消'
    })
    ElMessage.success('任务已取消')
    await loadTask()
    emit('refresh')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '取消失败')
    }
  } finally {
    actionLoading.cancel = false
  }
}

// 删除任务
const handleDelete = async () => {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？此操作不可恢复！', '危险操作', {
      type: 'error',
      confirmButtonClass: 'el-button--danger',
      confirmButtonText: '确认删除'
    })
    await deleteTask(actualTaskId.value)
    ElMessage.success('删除成功')
    emit('delete')
    if (props.mode === 'page') {
      router.push('/tasks')
    } else {
      emit('close')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '删除失败')
    }
  }
}

// 返回列表
const goBack = () => {
  router.push('/tasks')
}

// 跳转到论文详情
const goToPaper = (id) => {
  router.push(`/papers/${id}`)
}

// 复制功能
const copy = (text) => {
  if (!text) return
  navigator.clipboard.writeText(String(text))
  ElMessage.success('已复制到剪贴板')
}

const copyTaskId = () => copy(task.value?.id)

const copyTaskInfo = () => {
  const info = `[Task #${task.value.id}] ${task.value.taskName}\n状态：${getStatusText(task.value.status)}\n类型：${task.value.taskType}`
  copy(info)
}

// 格式化工具
const formatJSON = (str) => {
  if (!str) return '无数据'
  try {
    const obj = JSON.parse(str)
    return JSON.stringify(obj, null, 2)
  } catch {
    return str
  }
}

const formatDuration = (ms) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  if (ms < 3600000) return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`
  return `${Math.floor(ms / 3600000)}h ${Math.floor((ms % 3600000) / 60000)}m`
}

const calculateQueueTime = () => {
  if (!task.value?.createdAt || !task.value?.startTime) return '-'
  const queue = new Date(task.value.startTime) - new Date(task.value.createdAt)
  return formatDuration(queue)
}

const formatTime = (time) => time ? new Date(time).toLocaleString('zh-CN') : '-'

const formatRelativeTime = (time) => {
  if (!time) return ''
  const diff = new Date(time) - new Date()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '不到1分钟'
  if (minutes < 60) return `${minutes}分钟`
  return `${Math.floor(minutes / 60)}小时${minutes % 60}分钟`
}

// 状态映射
const getStatusType = (status) => ({
  WAITING: 'info',
  RUNNING: 'primary',
  FAILED: 'danger',
  SUCCEED: 'success',
  CANCELED: 'warning'
}[status] || 'info')

const getStatusText = (status) => ({
  WAITING: '等待中',
  RUNNING: '运行中',
  FAILED: '失败',
  SUCCEED: '成功',
  CANCELED: '已取消'
}[status] || status)

const getTaskTypeType = (type) => ({
  'PAPER_SEARCH': 'primary',
  'PAPER_PRE_ANALYSIS': 'success',
  'PAPER_DOWNLOAD': 'warning',
  'PAPER_TRANSFORM': 'info',
  'PAPER_RELATION_EXPAND': 'danger',
  'PAPER_EMBEDDING': 'success',
  'MATERIAL_EXTRACTION': 'warning',
  'CLEANUP': 'info'
}[type] || '')

const getTaskTypeText = (type) => ({
  'PAPER_SEARCH': '论文搜索',
  'PAPER_PRE_ANALYSIS': '预分析',
  'PAPER_DOWNLOAD': '下载',
  'PAPER_TRANSFORM': 'PDF转换',
  'PAPER_RELATION_EXPAND': '关系扩展',
  'PAPER_EMBEDDING': '向量化',
  'MATERIAL_EXTRACTION': '材料提取',
  'CLEANUP': '清理'
}[type] || type)

onMounted(() => {
  loadTask()
})
</script>

<style scoped>
.task-detail-pro {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 页面模式样式 */
.page-header {
  margin-bottom: 10px;
}

.page-title-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e5e7eb;
}

.page-title-section h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #111827;
}

/* 抽屉模式样式 */
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
  margin-bottom: 20px;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.drawer-actions {
  display: flex;
  gap: 8px;
}

/* 指标卡片 */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.metrics-row.drawer {
  gap: 12px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #f9fafb;
  border-radius: 12px;
  border-left: 4px solid #e5e7eb;
  transition: all 0.3s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.metric-card.running {
  border-left-color: #3b82f6;
  background: #eff6ff;
}
.metric-card.succeed {
  border-left-color: #10b981;
  background: #f0fdf4;
}
.metric-card.failed {
  border-left-color: #ef4444;
  background: #fef2f2;
}

.metrics-row.drawer .metric-card {
  padding: 16px;
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: white;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.metric-icon.blue { color: #3b82f6; }
.metric-icon.purple { color: #8b5cf6; }

.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
  margin-bottom: 4px;
  font-family: 'Inter', system-ui, sans-serif;
}

.metric-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

/* 时间线 */
.timeline-section {
  background: #f9fafb;
  padding: 20px;
  border-radius: 12px;
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 8px;
}

.timeline-node {
  background: white;
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.node-title {
  font-weight: 600;
  color: #111827;
  margin-bottom: 4px;
}

.node-desc {
  font-size: 13px;
  color: #6b7280;
}

/* 信息区域 */
:deep(.label-bold) {
  font-weight: 600 !important;
  color: #374151 !important;
  width: 100px;
}

.id-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: monospace;
}

.paper-link {
  color: #3b82f6;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.paper-link:hover {
  text-decoration: underline;
}

.text-muted {
  color: #9ca3af;
}

/* 代码块 */
.data-sections {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.data-block {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}

.block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.block-header .section-title {
  margin: 0;
}

.code-block {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
}

.code-block pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.code-block.success {
  background: #f0fdf4;
  color: #166534;
}

.code-block.error {
  background: #fef2f2;
  color: #991b1b;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #fee2e2;
}

.error-reason {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #fee2e2;
  font-size: 13px;
  color: #7f1d1d;
}

/* 底部操作 */
.footer-actions {
  display: flex;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
  margin-top: auto;
}

.footer-actions.drawer {
  position: sticky;
  bottom: 0;
  background: white;
  padding: 16px 0;
  margin-top: 20px;
}

.loading-container {
  padding: 40px;
}

/* 响应式 */
@media (max-width: 768px) {
  .metrics-row {
    grid-template-columns: 1fr;
  }

  .page-title-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>
