<template>
  <div class="task-center-pro">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="page-title">任务管理中心</h2>
        <el-tag effect="plain" size="small" class="env-tag">Production</el-tag>
      </div>

      <div class="toolbar-right">
        <el-input
            v-model="searchQuery"
            placeholder="搜索任务ID、名称或Paper ID..."
            class="search-input"
            clearable
            @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-divider direction="vertical" style="height: 24px; margin: 0 16px;" />

        <el-tooltip content="自动刷新（5s）" placement="bottom">
          <el-switch
              v-model="autoRefresh"
              inline-prompt
              :active-icon="Check"
              :inactive-icon="Close"
              @change="toggleAutoRefresh"
          />
        </el-tooltip>

        <el-button type="primary" @click="openCreateDialog" class="create-btn">
          <el-icon><Plus /></el-icon>新建任务
        </el-button>
      </div>
    </div>

    <!-- 筛选面板 -->
    <div class="filter-panel">
      <div class="filter-groups">
        <div class="filter-group">
          <span class="filter-label">状态：</span>
          <el-radio-group v-model="filter.status" size="small" @change="handleFilterChange">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="WAITING">
              <el-icon><Timer /></el-icon>等待
            </el-radio-button>
            <el-radio-button label="RUNNING">
              <el-icon><VideoPlay /></el-icon>运行
            </el-radio-button>
            <el-radio-button label="SUCCEED">
              <el-icon><CircleCheck /></el-icon>成功
            </el-radio-button>
            <el-radio-button label="FAILED">
              <el-icon><CircleClose /></el-icon>失败
            </el-radio-button>
            <el-radio-button label="CANCELED">
              <el-icon><Remove /></el-icon>取消
            </el-radio-button>
          </el-radio-group>
        </div>

        <div class="filter-group">
          <span class="filter-label">类型：</span>
          <el-select
              v-model="filter.taskType"
              placeholder="全部类型"
              clearable
              size="small"
              style="width: 160px"
              @change="handleFilterChange"
          >
            <el-option
                v-for="type in taskTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value"
            />
          </el-select>
        </div>

        <div class="filter-group">
          <span class="filter-label">时间：</span>
          <el-date-picker
              v-model="filter.timeRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              size="small"
              style="width: 240px"
              @change="handleFilterChange"
          />
        </div>
      </div>

      <div class="filter-actions">
        <el-button size="small" @click="resetFilters">
          <el-icon><Refresh /></el-icon>重置
        </el-button>
        <el-button size="small" type="primary" plain @click="exportData">
          <el-icon><Download /></el-icon>导出
        </el-button>
      </div>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedTasks.length > 0" class="batch-bar">
      <span class="batch-info">已选择 <strong>{{ selectedTasks.length }}</strong> 项</span>
      <el-button-group>
        <el-button size="small" @click="batchRetry" :disabled="!canBatchRetry">
          <el-icon><RefreshRight /></el-icon>批量重试
        </el-button>
        <el-button size="small" type="danger" @click="batchDelete">
          <el-icon><Delete /></el-icon>批量删除
        </el-button>
      </el-button-group>
      <el-button size="small" text @click="clearSelection">取消选择</el-button>
    </div>

    <!-- 数据表格 -->
    <div class="table-container" v-loading="loading">
      <el-table
          :data="taskList"
          style="width: 100%"
          @selection-change="handleSelectionChange"
          row-key="id"
          :row-class-name="getRowClassName"
      >
        <el-table-column type="selection" width="50" reserve-selection />

        <el-table-column prop="id" label="任务ID" width="100" sortable>
          <template #default="{ row }">
            <span class="task-id">#{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="taskName" label="任务名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="task-name-cell">
              <el-icon class="task-icon" :size="16"><Document /></el-icon>
              <div class="task-info">
                <div class="name">{{ row.taskName }}</div>
                <div class="meta" v-if="row.paperId">Paper: {{ row.paperId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="taskType" label="类型" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="getTaskTypeType(row.taskType)" effect="light" class="type-tag">
              {{ getTaskTypeText(row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <div class="status-cell">
              <el-tag
                  :type="getStatusType(row.status)"
                  effect="dark"
                  size="small"
                  class="status-tag"
              >
                <el-icon v-if="row.status === 'RUNNING'" class="is-loading"><Loading /></el-icon>
                <span class="status-dot" :class="row.status?.toLowerCase()"></span>
                {{ getStatusText(row.status) }}
              </el-tag>
              <div v-if="row.retryCount > 0" class="retry-badge">
                重试{{ row.retryCount }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="进度/耗时" width="160">
          <template #default="{ row }">
            <div v-if="row.status === 'RUNNING'" class="progress-cell">
              <el-progress :percentage="row.progress || 0" :stroke-width="4" :show-text="false" />
              <span class="progress-text">运行中...</span>
            </div>
            <div v-else-if="row.durationMs" class="duration-cell">
              <el-icon><Timer /></el-icon>
              <span :class="getDurationClass(row.durationMs)">{{ formatDuration(row.durationMs) }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column label="时间" width="200">
          <template #default="{ row }">
            <div class="time-cell">
              <div class="time-row">
                <span class="time-label">创建：</span>
                <span class="time-value">{{ formatTime(row.createdAt) }}</span>
              </div>
              <div class="time-row" v-if="row.startTime">
                <span class="time-label">开始：</span>
                <span class="time-value">{{ formatTime(row.startTime) }}</span>
              </div>
              <div class="time-row" v-if="row.endTime">
                <span class="time-label">结束：</span>
                <span class="time-value">{{ formatTime(row.endTime) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-tooltip content="查看详情" placement="top">
                <el-button link type="primary" @click="viewDetail(row)">
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>

              <el-tooltip content="查看日志" placement="top">
                <el-button link type="info" @click="viewLog(row)">
                  <el-icon><DocumentCopy /></el-icon>
                </el-button>
              </el-tooltip>

              <el-divider direction="vertical" />

              <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, row)">
                <el-button link type="primary">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="retry" :disabled="row.status !== 'FAILED'">
                      <el-icon><RefreshRight /></el-icon>重试任务
                    </el-dropdown-item>
                    <el-dropdown-item command="cancel" :disabled="!['WAITING', 'RUNNING'].includes(row.status)">
                      <el-icon><CircleClose /></el-icon>取消任务
                    </el-dropdown-item>
                    <el-dropdown-item divided command="delete">
                      <el-icon><Delete /></el-icon>删除任务
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :page-sizes="[20, 50, 100, 200]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
        v-model="detailDrawer.visible"
        title="任务详情"
        size="700px"
        :destroy-on-close="true"
        class="detail-drawer"
    >
      <TaskDetail
          v-if="detailDrawer.taskId"
          :task-id="detailDrawer.taskId"
          mode="drawer"
          @refresh="loadData"
          @close="detailDrawer.visible = false"
      />
    </el-drawer>

    <!-- 日志对话框 -->
    <el-dialog
        v-model="logDialog.visible"
        title="任务日志"
        width="800px"
        class="log-dialog"
        destroy-on-close
    >
      <div class="log-viewer" v-loading="logDialog.loading">
        <div v-if="logDialog.content" class="log-content">
          <pre>{{ logDialog.content }}</pre>
        </div>
        <el-empty v-else description="暂无日志" />
      </div>
      <template #footer>
        <el-button @click="logDialog.visible = false">关闭</el-button>
        <el-button type="primary" @click="refreshLog">刷新</el-button>
      </template>
    </el-dialog>

    <!-- 创建任务对话框 -->
    <el-dialog
        v-model="createDialog.visible"
        title="新建任务"
        width="560px"
        destroy-on-close
    >
      <el-form :model="createDialog.form" :rules="createDialog.rules" ref="createFormRef" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="createDialog.form.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="createDialog.form.taskType" placeholder="选择类型" style="width: 100%">
            <el-option v-for="t in taskTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Paper ID">
          <el-input v-model="createDialog.form.paperId" placeholder="可选" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="createDialog.form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="createDialog.loading">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'  // 添加这行
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Refresh, Download, Timer, VideoPlay,
  CircleCheck, CircleClose, Remove, RefreshRight, Delete,
  Check, Close, Document, Loading, View, DocumentCopy,
  ArrowDown
} from '@element-plus/icons-vue'
import { getTasks, createTask, retryTask, cancelTask, deleteTask, getTask } from '@/api/task'
// 修正：路径改为 ./TaskDetail.vue（因为当前文件就在 views 目录下）
import TaskDetail from './TaskDetail.vue'

const router = useRouter()  // 添加这行

// 数据
const loading = ref(false)
const autoRefresh = ref(false)
const refreshTimer = ref(null)
const searchQuery = ref('')
const taskList = ref([])
const selectedTasks = ref([])
const allTasks = ref([])

const filter = reactive({
  status: '',
  taskType: '',
  timeRange: null
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const taskTypes = [
  { value: 'PAPER_SEARCH', label: '论文搜索' },
  { value: 'PAPER_PRE_ANALYSIS', label: '论文预分析' },
  { value: 'PAPER_DOWNLOAD', label: '论文下载' },
  { value: 'PAPER_TRANSFORM', label: 'PDF转换' },
  { value: 'PAPER_RELATION_EXPAND', label: '关系扩展' },
  { value: 'PAPER_EMBEDDING', label: '向量化' },
  { value: 'MATERIAL_EXTRACTION', label: '材料提取' },
  { value: 'CLEANUP', label: '清理任务' }
]

const detailDrawer = reactive({
  visible: false,
  taskId: null
})

const logDialog = reactive({
  visible: false,
  loading: false,
  content: '',
  taskId: null
})

const createDialog = reactive({
  visible: false,
  loading: false,
  form: {
    taskName: '',
    taskType: '',
    paperId: '',
    description: '',
    maxRetries: 3
  },
  rules: {
    taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
    taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }]
  }
})

const createFormRef = ref(null)

// 计算属性
const canBatchRetry = computed(() => {
  return selectedTasks.value.some(t => t.status === 'FAILED')
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const params = { limit: 200 }
    if (filter.status) params.status = filter.status
    if (filter.taskType) params.taskType = filter.taskType
    if (searchQuery.value) params.query = searchQuery.value

    const { data } = await getTasks(params)

    if (Array.isArray(data)) {
      allTasks.value = data.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))

      if (filter.timeRange && filter.timeRange.length === 2) {
        const [start, end] = filter.timeRange
        allTasks.value = allTasks.value.filter(t => {
          const created = new Date(t.createdAt)
          return created >= start && created <= end
        })
      }

      pagination.total = allTasks.value.length
      updatePageData()
    }
  } catch (error) {
    ElMessage.error('加载失败：' + error.message)
  } finally {
    loading.value = false
  }
}

const updatePageData = () => {
  const start = (pagination.page - 1) * pagination.size
  const end = start + pagination.size
  taskList.value = allTasks.value.slice(start, end)
}

const handlePageChange = (page) => {
  pagination.page = page
  updatePageData()
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
  updatePageData()
}

const handleFilterChange = () => {
  pagination.page = 1
  loadData()
}

const handleSearch = () => {
  loadData()
}

const resetFilters = () => {
  filter.status = ''
  filter.taskType = ''
  filter.timeRange = null
  searchQuery.value = ''
  handleFilterChange()
}

const toggleAutoRefresh = (val) => {
  if (val) {
    refreshTimer.value = setInterval(() => loadData(), 5000)
    ElMessage.success('已开启自动刷新')
  } else {
    clearInterval(refreshTimer.value)
  }
}

// 表格选择
const handleSelectionChange = (selection) => {
  selectedTasks.value = selection
}

const clearSelection = () => {
  selectedTasks.value = []
}

const getRowClassName = ({ row }) => {
  if (row.status === 'FAILED') return 'row-failed'
  if (row.status === 'RUNNING') return 'row-running'
  return ''
}

// 操作
const viewDetail = (row) => {
  // 使用抽屉模式打开详情
  detailDrawer.taskId = row.id
  detailDrawer.visible = true
}

const viewLog = async (row) => {
  logDialog.taskId = row.id
  logDialog.visible = true
  logDialog.loading = true
  try {
    const { data } = await getTask(row.id)
    logDialog.content = data.result || data.errorMessage || '暂无日志'
  } catch (error) {
    logDialog.content = '获取日志失败：' + error.message
  } finally {
    logDialog.loading = false
  }
}

const refreshLog = async () => {
  if (!logDialog.taskId) return
  logDialog.loading = true
  try {
    const { data } = await getTask(logDialog.taskId)
    logDialog.content = data.result || data.errorMessage || '暂无日志'
  } finally {
    logDialog.loading = false
  }
}

const handleCommand = async (command, row) => {
  switch (command) {
    case 'retry':
      await handleRetry(row)
      break
    case 'cancel':
      await handleCancel(row)
      break
    case 'delete':
      await handleDelete(row)
      break
  }
}

const handleRetry = async (row) => {
  try {
    await ElMessageBox.confirm(`确定重试任务 "${row.taskName}"？`, '确认')
    await retryTask(row.id)
    ElMessage.success('重试请求已发送')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(`确定取消任务 "${row.taskName}"？`, '警告', { type: 'warning' })
    await cancelTask(row.id, {
      status: 'CANCELED',
      errorMessage: '用户手动取消'
    })
    ElMessage.success('已取消')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除任务 "${row.taskName}"？此操作不可恢复！`, '危险', {
      type: 'error',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteTask(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

// 批量操作
const batchRetry = async () => {
  const failedTasks = selectedTasks.value.filter(t => t.status === 'FAILED')
  if (failedTasks.length === 0) return

  try {
    await ElMessageBox.confirm(`确定批量重试 ${failedTasks.length} 个失败任务？`, '确认')
    await Promise.all(failedTasks.map(t => retryTask(t.id)))
    ElMessage.success('批量重试请求已发送')
    clearSelection()
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedTasks.value.length} 个任务？`, '危险', { type: 'error' })
    await Promise.all(selectedTasks.value.map(t => deleteTask(t.id)))
    ElMessage.success('批量删除成功')
    clearSelection()
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message)
  }
}

const openCreateDialog = () => {
  createDialog.visible = true
  createDialog.form = {
    taskName: '',
    taskType: '',
    paperId: '',
    description: '',
    maxRetries: 3
  }
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
    createDialog.loading = true
    await createTask(createDialog.form)
    ElMessage.success('创建成功')
    createDialog.visible = false
    loadData()
  } catch (e) {
    if (e.message) ElMessage.error(e.message)
  } finally {
    createDialog.loading = false
  }
}

const exportData = () => {
  const headers = ['ID', '任务名称', '类型', '状态', '创建时间']
  const rows = taskList.value.map(t => [
    t.id,
    t.taskName,
    t.taskType,
    t.status,
    t.createdAt
  ])
  const csvContent = [headers, ...rows].map(e => e.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `tasks_${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  ElMessage.success('导出成功')
}

// 工具函数
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

const formatDuration = (ms) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms/1000).toFixed(1)}s`
  if (ms < 3600000) return `${Math.floor(ms/60000)}m`
  return `${Math.floor(ms/3600000)}h`
}

const getDurationClass = (ms) => {
  if (ms > 300000) return 'text-danger'
  if (ms > 60000) return 'text-warning'
  return 'text-success'
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  if (refreshTimer.value) clearInterval(refreshTimer.value)
})
</script>

<style scoped>
.task-center-pro {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

/* 工具栏 */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 16px 24px;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
}

.env-tag {
  font-weight: 600;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 320px;
}

.create-btn {
  margin-left: 8px;
}

/* 筛选面板 */
.filter-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 16px 24px;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.filter-groups {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #6b7280;
  font-weight: 500;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  padding: 12px 24px;
  border-radius: 8px;
  margin-bottom: 16px;
  animation: slideDown 0.3s ease;
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

.batch-info {
  color: #1e40af;
  font-size: 14px;
}

/* 表格容器 */
.table-container {
  background: white;
  padding: 0;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

:deep(.el-table) {
  --el-table-header-bg-color: #f9fafb;
  --el-table-header-text-color: #374151;
  --el-table-row-hover-bg-color: #f3f4f6;
}

:deep(.el-table th) {
  font-weight: 600;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* 表格行样式 */
:deep(.row-failed) {
  background-color: #fef2f2 !important;
}

:deep(.row-running) {
  background-color: #eff6ff !important;
}

/* 单元格内容 */
.task-id {
  font-family: monospace;
  font-weight: 600;
  color: #6b7280;
  font-size: 13px;
}

.task-name-cell {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.task-icon {
  color: #9ca3af;
  margin-top: 2px;
}

.task-info .name {
  font-weight: 500;
  color: #111827;
  margin-bottom: 2px;
}

.task-info .meta {
  font-size: 12px;
  color: #6b7280;
  font-family: monospace;
}

.type-tag {
  font-weight: 500;
}

/* 状态 */
.status-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  width: fit-content;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-dot.waiting { animation: pulse 2s infinite; }
.status-dot.running { animation: pulse 1s infinite; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.retry-badge {
  font-size: 11px;
  color: #dc2626;
  background: #fee2e2;
  padding: 0 6px;
  border-radius: 4px;
  width: fit-content;
}

/* 进度与耗时 */
.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.progress-text {
  font-size: 12px;
  color: #3b82f6;
}

.duration-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.text-success { color: #10b981; }
.text-warning { color: #f59e0b; }
.text-danger { color: #ef4444; }
.text-muted { color: #9ca3af; }

/* 时间 */
.time-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.time-row {
  font-size: 12px;
  display: flex;
  gap: 4px;
}

.time-label {
  color: #9ca3af;
  width: 40px;
  flex-shrink: 0;
}

.time-value {
  color: #4b5563;
  font-family: monospace;
}

/* 操作 */
.action-group {
  display: flex;
  align-items: center;
}

/* 分页 */
.pagination-wrapper {
  padding: 16px 24px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #e5e7eb;
}

/* 日志查看器 */
.log-viewer {
  background: #1e1e1e;
  border-radius: 8px;
  max-height: 500px;
  overflow: auto;
}

.log-content pre {
  margin: 0;
  padding: 16px;
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 响应式 */
@media (max-width: 1024px) {
  .filter-panel {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .search-input {
    width: 240px;
  }
}
</style>
