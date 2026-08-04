<template>
  <div class="task-list-container">
    <!-- 顶部操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">论文处理流程</h2>
        <div class="subtitle-wrapper">
          <span class="subtitle">
            共 {{ groupedTasks.length }} 篇论文，{{ allTasks.length }} 个任务
            <el-tooltip v-if="isDataTruncated" content="后端 API 限制，仅展示最近 200 个任务" placement="top">
              <el-icon class="warning-icon"><Warning /></el-icon>
            </el-tooltip>
          </span>
          <el-tag v-if="isDataTruncated" type="warning" size="small" effect="plain" class="truncated-tag">
            已截断显示 (API Limit: 200)
          </el-tag>
        </div>
      </div>
      <el-button type="primary" size="large" @click="openCreateDialog" class="create-btn">
        <el-icon><Plus /></el-icon>
        <span>新建任务</span>
      </el-button>
    </div>

    <!-- 筛选卡片 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-content">
        <div class="filter-item">
          <span class="filter-label">论文状态</span>
          <el-select
              v-model="filter.status"
              placeholder="全部状态"
              clearable
              style="width: 140px"
              @change="handleFilter"
          >
            <el-option label="处理中" value="RUNNING" />
            <el-option label="有失败" value="FAILED" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </div>

        <div class="filter-item filter-search">
          <span class="filter-label">论文ID/标题</span>
          <el-input
              v-model="filter.searchKey"
              placeholder="搜索 Paper ID 或标题..."
              clearable
              style="width: 280px"
              @keyup.enter="handleFilter"
              @clear="handleFilter"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="filter-actions">
          <el-button text type="primary" @click="handleRefresh" :loading="loading">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="timeline">
              <el-icon><Timer /></el-icon> 时间线
            </el-radio-button>
            <el-radio-button label="list">
              <el-icon><List /></el-icon> 列表
            </el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </el-card>

    <!-- 时间线视图 -->
    <div v-if="viewMode === 'timeline'" class="timeline-view" v-loading="loading" element-loading-text="加载任务数据中...">
      <div v-if="paginatedGroups.length === 0" class="empty-state">
        <el-empty description="暂无任务数据" :image-size="120">
          <template #description>
            <p>暂无符合条件的论文</p>
            <p class="empty-hint">点击右上角"新建任务"创建</p>
          </template>
          <el-button type="primary" @click="openCreateDialog">创建任务</el-button>
        </el-empty>
      </div>

      <div v-else class="paper-flow-list">
        <el-card
            v-for="group in paginatedGroups"
            :key="group.paperId"
            shadow="hover"
            class="paper-flow-card"
            :class="{ 'has-failed': group.hasFailed, 'is-running': group.isRunning }"
        >
          <!-- 论文头部信息 -->
          <div class="paper-header" @click="toggleExpand(group.paperId)">
            <div class="paper-main-info">
              <div class="paper-title-section">
                <el-icon class="paper-icon" :size="20"><Document /></el-icon>
                <div class="paper-title-wrapper">
                  <h3 class="paper-title" :title="group.paperTitle">
                    {{ group.paperTitle || '未命名论文' }}
                    <el-tag v-if="paperLoadingSet.has(group.paperId)" size="small" type="info" effect="light" class="loading-tag">
                      <el-icon class="is-loading"><Loading /></el-icon> 加载中
                    </el-tag>
                    <el-tag v-else-if="!group.paperTitle && group.paperId !== 'unassigned'" size="small" type="warning" effect="light" class="title-missing-tag">
                      未获取到标题
                    </el-tag>
                  </h3>
                  <div class="paper-meta">
                    <el-tag size="small" type="info" effect="plain" class="paper-id-tag">ID: {{ group.paperId }}</el-tag>
                    <span class="meta-divider">|</span>
                    <span class="task-count">{{ group.tasks.length }} 个任务</span>
                    <span class="meta-divider">|</span>
                    <span class="time-range">{{ formatTimeRange(group.tasks) }}</span>
                  </div>
                </div>
              </div>

              <div class="paper-status-summary">
                <el-tag
                    v-if="group.isRunning"
                    type="primary"
                    effect="dark"
                    class="summary-tag"
                >
                  <el-icon class="is-loading"><Loading /></el-icon>
                  处理中
                </el-tag>
                <el-tag
                    v-else-if="group.hasFailed"
                    type="danger"
                    effect="dark"
                    class="summary-tag"
                >
                  <el-icon><CircleClose /></el-icon>
                  存在失败
                </el-tag>
                <el-tag
                    v-else
                    type="success"
                    effect="dark"
                    class="summary-tag"
                >
                  <el-icon><Check /></el-icon>
                  已完成
                </el-tag>
                <el-icon class="expand-icon" :class="{ 'is-expanded': expandedPapers.has(group.paperId) }">
                  <ArrowDown />
                </el-icon>
              </div>
            </div>

            <!-- 流程进度条 -->
            <div class="flow-progress">
              <div
                  v-for="(step, index) in getFlowSteps(group.tasks)"
                  :key="index"
                  class="flow-step"
                  :class="{
                  'completed': step.status === 'completed',
                  'failed': step.status === 'failed',
                  'running': step.status === 'running',
                  'pending': step.status === 'pending'
                }"
              >
                <div class="step-node">
                  <el-icon v-if="step.status === 'completed'" class="step-icon"><Check /></el-icon>
                  <el-icon v-else-if="step.status === 'failed'" class="step-icon"><Close /></el-icon>
                  <el-icon v-else-if="step.status === 'running'" class="step-icon is-loading"><Loading /></el-icon>
                  <span v-else class="step-number">{{ index + 1 }}</span>
                </div>
                <div class="step-info">
                  <span class="step-name">{{ step.name }}</span>
                  <span class="step-time" v-if="step.time">{{ step.time }}</span>
                </div>
                <div v-if="index < getFlowSteps(group.tasks).length - 1" class="step-connector"></div>
              </div>
            </div>
          </div>

          <!-- 展开的任务详情 - 按时间先后顺序展示 -->
          <el-collapse-transition>
            <div v-show="expandedPapers.has(group.paperId)" class="paper-tasks-detail">
              <el-divider />
              <div class="timeline-header">
                <div class="timeline-title">
                  <el-icon><Timer /></el-icon>
                  <span>执行时间线</span>
                  <el-tag size="small" type="info" effect="plain">按时间先后排序</el-tag>
                </div>
                <div class="timeline-legend">
                  <span class="legend-item"><span class="dot running"></span>运行中</span>
                  <span class="legend-item"><span class="dot success"></span>成功</span>
                  <span class="legend-item"><span class="dot failed"></span>失败</span>
                  <span class="legend-item"><span class="dot pending"></span>等待</span>
                </div>
              </div>

              <div class="tasks-timeline">
                <div
                    v-for="(task, index) in sortedTasksByTime(group.tasks)"
                    :key="task.id"
                    class="task-timeline-item"
                    :class="[task.status?.toLowerCase(), { 'first-item': index === 0, 'last-item': index === sortedTasksByTime(group.tasks).length - 1 }]"
                >
                  <div class="timeline-left">
                    <div class="timeline-dot" :class="getStatusType(task.status)">
                      <el-icon v-if="task.status === 'RUNNING'" class="is-loading"><Loading /></el-icon>
                      <el-icon v-else-if="task.status === 'SUCCEED'"><Check /></el-icon>
                      <el-icon v-else-if="task.status === 'FAILED'"><Close /></el-icon>
                      <el-icon v-else-if="task.status === 'CANCELED'"><CircleClose /></el-icon>
                      <span v-else class="dot-inner"></span>
                    </div>
                    <div class="timeline-line"></div>
                  </div>

                  <div class="timeline-content">
                    <div class="task-card-mini">
                      <div class="task-header-mini">
                        <div class="task-title-row">
                          <el-tag :type="getTaskTypeColor(task.taskType)" size="small" effect="light">
                            {{ getTaskTypeText(task.taskType) }}
                          </el-tag>
                          <span class="task-name">{{ task.taskName }}</span>
                          <el-tag
                              :type="getStatusType(task.status)"
                              size="small"
                              effect="dark"
                              class="status-mini"
                          >
                            {{ getStatusText(task.status) }}
                          </el-tag>
                        </div>
                        <div class="task-actions-mini">
                          <el-tooltip content="查看详情" placement="top">
                            <el-button link type="primary" @click.stop="viewDetail(task.id)">
                              <el-icon><View /></el-icon>
                            </el-button>
                          </el-tooltip>
                          <el-dropdown trigger="hover" @command="(cmd) => handleAction(cmd, task)">
                            <el-button link type="primary">
                              <el-icon><MoreFilled /></el-icon>
                            </el-button>
                            <template #dropdown>
                              <el-dropdown-menu>
                                <el-dropdown-item
                                    command="retry"
                                    :disabled="task.status !== 'FAILED'"
                                    :icon="RefreshRight"
                                >
                                  重试任务
                                </el-dropdown-item>
                                <el-dropdown-item
                                    command="cancel"
                                    :disabled="!['WAITING', 'RUNNING'].includes(task.status)"
                                    :icon="CircleClose"
                                >
                                  取消任务
                                </el-dropdown-item>
                                <el-dropdown-item divided command="delete" :icon="Delete" class="danger-item">
                                  <span style="color: #f56c6c;">删除任务</span>
                                </el-dropdown-item>
                              </el-dropdown-menu>
                            </template>
                          </el-dropdown>
                        </div>
                      </div>

                      <div class="task-meta-row">
                        <div class="meta-time-group">
                          <span class="meta-item" :class="{ 'time-highlight': index === 0 }">
                            <el-icon><Clock /></el-icon>
                            <span class="time-label">创建：</span>
                            {{ formatTime(task.createdAt) }}
                          </span>
                          <span v-if="task.startTime" class="meta-item">
                            <el-icon><Right /></el-icon>
                            <span class="time-label">开始：</span>
                            {{ formatTime(task.startTime) }}
                          </span>
                          <span v-if="task.endTime" class="meta-item" :class="{ 'time-latest': index === sortedTasksByTime(group.tasks).length - 1 }">
                            <el-icon><Flag /></el-icon>
                            <span class="time-label">结束：</span>
                            {{ formatTime(task.endTime) }}
                          </span>
                        </div>

                        <div class="meta-stats-group">
                          <span class="meta-item" v-if="task.durationMs">
                            <el-icon><Timer /></el-icon>
                            耗时 {{ formatDuration(task.durationMs) }}
                          </span>
                          <span class="meta-item">
                            <el-icon><RefreshLeft /></el-icon>
                            重试 {{ task.retryCount }}/{{ task.maxRetries }}
                          </span>
                          <el-tag
                              v-if="task.errorMessage"
                              type="danger"
                              size="small"
                              effect="plain"
                              class="error-hint"
                          >
                            <el-icon><Warning /></el-icon>
                            执行失败
                          </el-tag>
                        </div>
                      </div>

                      <div v-if="task.errorMessage" class="error-detail">
                        <el-alert
                            :title="task.errorMessage"
                            type="error"
                            :closable="false"
                            show-icon
                            size="small"
                        />
                      </div>

                      <!-- 时间连接线指示器 -->
                      <div v-if="index < sortedTasksByTime(group.tasks).length - 1" class="time-connector">
                        <el-divider direction="vertical" />
                        <span class="time-gap">{{ calculateTimeGap(task, sortedTasksByTime(group.tasks)[index + 1]) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-collapse-transition>
        </el-card>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :page-sizes="[5, 10, 20, 50]"
            :total="filteredGroups.length"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
            background
        />
      </div>
    </div>

    <!-- 列表视图 -->
    <el-card v-else shadow="never" class="table-card" v-loading="loading" element-loading-text="加载任务数据中...">
      <el-table
          :data="taskListComputed"
          stripe
          highlight-current-row
          :default-sort="{ prop: 'createdAt', order: 'descending' }"
          @sort-change="handleSortChange"
          :row-class-name="tableRowClassName"
          style="width: 100%"
      >
        <el-table-column type="index" width="60" :index="calculateIndex" align="center">
          <template #header>序号</template>
        </el-table-column>

        <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="task-name-wrapper">
              <el-icon class="task-icon"><Document /></el-icon>
              <div class="task-info">
                <div class="task-title">{{ row.taskName }}</div>
                <div class="task-id" v-if="row.paperId">
                  论文: {{ getPaperTitle(row.paperId) }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="taskType" label="任务类型" width="140">
          <template #default="{ row }">
            <el-tag
                :type="getTaskTypeColor(row.taskType)"
                size="small"
                effect="light"
                class="type-tag"
            >
              {{ getTaskTypeText(row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="执行状态" width="120" align="center">
          <template #default="{ row }">
            <div class="status-wrapper">
              <el-tag
                  :type="getStatusType(row.status)"
                  size="small"
                  effect="dark"
                  round
                  class="status-tag"
              >
                <el-icon v-if="row.status === 'RUNNING'" class="is-loading"><Loading /></el-icon>
                <span v-else class="status-dot" :class="row.status?.toLowerCase()"></span>
                {{ getStatusText(row.status) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="retryCount" label="重试" width="90" align="center">
          <template #default="{ row }">
            <el-tooltip
                :content="row.retryCount >= row.maxRetries ? '已达最大重试次数' : '可重试'"
                placement="top"
            >
              <el-tag
                  size="small"
                  :type="row.retryCount >= row.maxRetries ? 'danger' : 'info'"
                  effect="plain"
                  class="retry-tag"
              >
                {{ row.retryCount }}/{{ row.maxRetries }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="170" sortable>
          <template #default="{ row }">
            <div class="time-wrapper">
              <el-icon><Clock /></el-icon>
              <span>{{ formatTime(row.createdAt) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-wrapper">
              <el-tooltip content="查看详情" placement="top">
                <el-button
                    link
                    type="primary"
                    @click="viewDetail(row.id)"
                    class="action-btn"
                >
                  <el-icon><View /></el-icon>
                </el-button>
              </el-tooltip>

              <el-divider direction="vertical" />

              <el-dropdown trigger="hover" @command="(cmd) => handleAction(cmd, row)">
                <el-button link type="primary" class="action-btn">
                  操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                        command="retry"
                        :disabled="row.status !== 'FAILED'"
                        :icon="RefreshRight"
                    >
                      重试任务
                    </el-dropdown-item>
                    <el-dropdown-item
                        command="cancel"
                        :disabled="!['WAITING', 'RUNNING'].includes(row.status)"
                        :icon="CircleClose"
                    >
                      取消任务
                    </el-dropdown-item>
                    <el-dropdown-item divided command="delete" :icon="Delete" class="danger-item">
                      <span style="color: #f56c6c;">删除任务</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无任务数据" :image-size="120">
            <template #description>
              <p>暂无符合条件的任务</p>
              <p class="empty-hint">点击右上角"新建任务"创建</p>
            </template>
            <el-button type="primary" @click="openCreateDialog">创建任务</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
            background
        />
      </div>
    </el-card>

    <!-- 创建任务对话框 -->
    <el-dialog
        v-model="createDialog.visible"
        title="新建任务"
        width="640px"
        :close-on-click-modal="false"
        destroy-on-close
        class="create-dialog enhanced"
    >
      <!-- ... 对话框内容保持不变 ... -->
      <template #footer>
        <div class="dialog-footer enhanced-footer">
          <el-button @click="createDialog.visible = false" size="large">取消</el-button>
          <el-button type="primary" @click="handleCreate" :loading="createDialog.loading" size="large" class="submit-btn">
            <el-icon><Plus /></el-icon>
            确认创建
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTasks, createTask, retryTask, cancelTask, deleteTask
} from '@/api/task'
import { getPaper } from '@/api/paper'
import {
  Plus, Search, Refresh, Document, Loading, Clock,
  View, ArrowDown, RefreshRight, CircleClose, Delete,
  Check, Close, Timer, List, MoreFilled, Warning, RefreshLeft,
  Connection, DocumentChecked, CopyDocument, Setting, Right, Flag
} from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const createFormRef = ref(null)
const allTasks = ref([])
const viewMode = ref('timeline')
const expandedPapers = ref(new Set())
const paperCache = ref(new Map())
const paperLoadingSet = ref(new Set())

// 论文查询相关状态
const paperQueryLoading = ref(false)
const selectedPaperInfo = ref(null)
const paperQueryError = ref('')

const filter = reactive({ status: '', searchKey: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const sortConfig = reactive({ prop: 'createdAt', order: 'descending' })

const createDialog = reactive({
  visible: false,
  loading: false,
  form: {
    taskName: '',
    taskType: '',
    paperId: '',
    description: '',
    maxRetries: 3,
    createdBy: 'admin'
  },
  rules: {
    taskName: [
      { required: true, message: '请输入任务名称', trigger: 'blur' },
      { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
    ],
    taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }]
  }
})

// 按时间先后顺序排序任务（核心修改）
const sortedTasksByTime = (tasks) => {
  return [...tasks].sort((a, b) => {
    // 优先按创建时间排序（从早到晚）
    const timeA = new Date(a.createdAt).getTime()
    const timeB = new Date(b.createdAt).getTime()

    if (timeA !== timeB) {
      return timeA - timeB // 升序：从早到晚
    }

    // 如果创建时间相同，按开始时间排序
    if (a.startTime && b.startTime) {
      return new Date(a.startTime) - new Date(b.startTime)
    }

    // 最后按任务类型排序作为兜底
    const typeOrder = [
      'PAPER_SEARCH',
      'PAPER_PRE_ANALYSIS',
      'PAPER_DOWNLOAD',
      'PAPER_TRANSFORM',
      'PAPER_EMBEDDING',
      'MATERIAL_EXTRACTION',
      'PAPER_RELATION_EXPAND',
      'CLEANUP'
    ]
    return typeOrder.indexOf(a.taskType) - typeOrder.indexOf(b.taskType)
  })
}

// 计算两个任务之间的时间间隔
const calculateTimeGap = (currentTask, nextTask) => {
  if (!currentTask.endTime || !nextTask.createdAt) return ''

  const end = new Date(currentTask.endTime).getTime()
  const start = new Date(nextTask.createdAt).getTime()
  const gap = start - end

  if (gap < 0) return '并行执行'
  if (gap < 60000) return '间隔 < 1分钟'
  if (gap < 3600000) return `间隔 ${Math.floor(gap / 60000)}分钟`
  return `间隔 ${Math.floor(gap / 3600000)}小时`
}

// 获取论文标题（优先从缓存）
const getPaperTitle = (paperId) => {
  if (!paperId || paperId === 'unassigned') return '未关联论文'
  const cached = paperCache.value.get(paperId)
  return cached?.title || cached?.paperTitle || paperId
}

// 按论文ID分组任务
const groupedTasks = computed(() => {
  const groups = new Map()

  allTasks.value.forEach(task => {
    const paperId = task.paperId || 'unassigned'
    if (!groups.has(paperId)) {
      groups.set(paperId, {
        paperId,
        paperTitle: getPaperTitle(paperId),
        tasks: [],
        hasFailed: false,
        isRunning: false,
        latestTask: null,
        earliestTask: null
      })
    }
    const group = groups.get(paperId)
    group.tasks.push(task)

    if (task.status === 'FAILED') group.hasFailed = true
    if (task.status === 'RUNNING') group.isRunning = true

    if (!group.latestTask || new Date(task.createdAt) > new Date(group.latestTask.createdAt)) {
      group.latestTask = task
    }
    if (!group.earliestTask || new Date(task.createdAt) < new Date(group.earliestTask.createdAt)) {
      group.earliestTask = task
    }
  })

  return Array.from(groups.values()).sort((a, b) => {
    // 按最新任务时间倒序排列（最新的论文在前面）
    if (a.isRunning !== b.isRunning) return a.isRunning ? -1 : 1
    if (a.hasFailed !== b.hasFailed) return a.hasFailed ? -1 : 1
    return new Date(b.latestTask.createdAt) - new Date(a.latestTask.createdAt)
  })
})

// 筛选后的分组
const filteredGroups = computed(() => {
  let result = groupedTasks.value

  if (filter.status) {
    result = result.filter(g => {
      if (filter.status === 'RUNNING') return g.isRunning
      if (filter.status === 'FAILED') return g.hasFailed
      if (filter.status === 'COMPLETED') return !g.isRunning && !g.hasFailed
      return true
    })
  }

  if (filter.searchKey) {
    const key = filter.searchKey.toLowerCase()
    result = result.filter(g =>
        g.paperId.toLowerCase().includes(key) ||
        (g.paperTitle && g.paperTitle.toLowerCase().includes(key))
    )
  }

  return result
})

// 分页后的分组
const paginatedGroups = computed(() => {
  const start = (pagination.page - 1) * pagination.size
  const end = start + pagination.size
  return filteredGroups.value.slice(start, end)
})

// 列表视图数据 - 支持排序
const taskListComputed = computed(() => {
  if (viewMode.value !== 'list') return []

  let tasks = [...allTasks.value]

  // 应用排序
  if (sortConfig.prop === 'createdAt') {
    tasks.sort((a, b) => {
      const timeA = new Date(a.createdAt).getTime()
      const timeB = new Date(b.createdAt).getTime()
      return sortConfig.order === 'ascending' ? timeA - timeB : timeB - timeA
    })
  }

  const start = (pagination.page - 1) * pagination.size
  const end = start + pagination.size
  return tasks.slice(start, end)
})

const isDataTruncated = computed(() => allTasks.value.length >= 200)

// 智能加载当前页论文标题
const loadVisiblePaperTitles = async () => {
  if (viewMode.value !== 'timeline') return

  const needLoadIds = paginatedGroups.value
      .filter(g => g.paperId !== 'unassigned' && !paperCache.value.has(g.paperId))
      .map(g => g.paperId)

  if (needLoadIds.length === 0) return

  needLoadIds.forEach(id => paperLoadingSet.value.add(id))

  const batchSize = 3
  for (let i = 0; i < needLoadIds.length; i += batchSize) {
    const batch = needLoadIds.slice(i, i + batchSize)
    await Promise.all(batch.map(async (paperId) => {
      try {
        const { data } = await getPaper(paperId)
        if (data) {
          paperCache.value.set(paperId, data)
        }
      } catch (error) {
        console.warn(`获取论文 ${paperId} 信息失败`, error)
      } finally {
        paperLoadingSet.value.delete(paperId)
      }
    }))
    await nextTick()
  }
}

// 切换展开状态
const toggleExpand = async (paperId) => {
  if (expandedPapers.value.has(paperId)) {
    expandedPapers.value.delete(paperId)
  } else {
    expandedPapers.value.add(paperId)

    if (paperId !== 'unassigned' && !paperCache.value.has(paperId) && !paperLoadingSet.value.has(paperId)) {
      paperLoadingSet.value.add(paperId)
      try {
        const { data } = await getPaper(paperId)
        if (data) {
          paperCache.value.set(paperId, data)
        }
      } catch (e) {
        console.warn(`获取论文 ${paperId} 失败`, e)
      } finally {
        paperLoadingSet.value.delete(paperId)
      }
    }
  }
}

// 查询论文信息
const queryPaperInfo = async () => {
  const paperId = createDialog.form.paperId?.trim()
  if (!paperId) {
    ElMessage.warning('请输入 Paper ID')
    return
  }

  paperQueryLoading.value = true
  paperQueryError.value = ''

  try {
    const { data } = await getPaper(paperId)
    if (data) {
      selectedPaperInfo.value = data
      if (!createDialog.form.taskName && createDialog.form.taskType) {
        generateTaskName()
      }
      ElMessage.success('论文信息获取成功')
    } else {
      paperQueryError.value = '未找到该论文信息'
      selectedPaperInfo.value = null
    }
  } catch (error) {
    console.error('获取论文信息失败:', error)
    paperQueryError.value = error.response?.data?.message || '获取论文信息失败，请检查ID是否正确'
    selectedPaperInfo.value = null
  } finally {
    paperQueryLoading.value = false
  }
}

// 处理 Paper ID 输入（防抖查询）
let queryTimeout = null
const handlePaperIdInput = (val) => {
  if (queryTimeout) clearTimeout(queryTimeout)
  if (!val) {
    clearPaperInfo()
    return
  }
  queryTimeout = setTimeout(() => {
    if (val.length >= 5) {
      queryPaperInfo()
    }
  }, 800)
}

// 清除论文信息
const clearPaperInfo = () => {
  createDialog.form.paperId = ''
  selectedPaperInfo.value = null
  paperQueryError.value = ''
  paperQueryLoading.value = false
  if (queryTimeout) clearTimeout(queryTimeout)
}

// 获取论文状态样式
const getPaperStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'PROCESSING': 'primary',
    'COMPLETED': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const getPaperStatusText = (status) => {
  const map = {
    'PENDING': '待处理',
    'PROCESSING': '处理中',
    'COMPLETED': '已完成',
    'FAILED': '失败'
  }
  return map[status] || status
}

// 复制到剪贴板
const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (err) {
    ElMessage.error('复制失败')
  }
}

const getFlowSteps = (tasks) => {
  const steps = [
    { name: '搜索', type: 'PAPER_SEARCH' },
    { name: '预分析', type: 'PAPER_PRE_ANALYSIS' },
    { name: '下载', type: 'PAPER_DOWNLOAD' },
    { name: '转换', type: 'PAPER_TRANSFORM' },
    { name: '向量化', type: 'PAPER_EMBEDDING' },
    { name: '材料提取', type: 'MATERIAL_EXTRACTION' }
  ]

  return steps.map(step => {
    const task = tasks.find(t => t.taskType === step.type)
    let status = 'pending'
    let time = ''

    if (task) {
      if (task.status === 'SUCCEED') {
        status = 'completed'
        time = formatTime(task.endTime || task.createdAt, 'short')
      } else if (task.status === 'FAILED') {
        status = 'failed'
      } else if (task.status === 'RUNNING') {
        status = 'running'
      }
    }

    return { ...step, status, time }
  })
}

const formatTimeRange = (tasks) => {
  if (tasks.length === 0) return '-'

  // 按时间排序后取最早和最晚
  const sorted = [...tasks].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
  const earliest = new Date(sorted[0].createdAt)
  const latest = new Date(sorted[sorted.length - 1].createdAt)

  if (earliest.toDateString() === latest.toDateString()) {
    return earliest.toLocaleDateString('zh-CN')
  }
  return `${earliest.toLocaleDateString('zh-CN')} ~ ${latest.toLocaleDateString('zh-CN')}`
}

const formatDuration = (ms) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${Math.floor(ms/1000)}s`
  if (ms < 3600000) return `${Math.floor(ms/60000)}m ${Math.floor((ms%60000)/1000)}s`
  return `${Math.floor(ms/3600000)}h ${Math.floor((ms%3600000)/60000)}m`
}

const calculateIndex = (index) => (pagination.page - 1) * pagination.size + index + 1

const tableRowClassName = ({ row }) => {
  if (row.status === 'FAILED') return 'row-failed'
  if (row.status === 'RUNNING') return 'row-running'
  return ''
}

const handleSortChange = ({ prop, order }) => {
  sortConfig.prop = prop
  sortConfig.order = order
}

const loadTasks = async () => {
  loading.value = true
  try {
    const { data } = await getTasks({
      limit: 200,
      ...(filter.status && viewMode.value === 'list' ? { status: filter.status } : {}),
      ...(filter.searchKey && viewMode.value === 'list' ? { paperId: filter.searchKey } : {})
    })

    allTasks.value = Array.isArray(data) ? data : (data?.items || [])

    if (viewMode.value === 'timeline') {
      pagination.total = filteredGroups.value.length
      nextTick(() => {
        loadVisiblePaperTitles()
      })
    } else {
      pagination.total = allTasks.value.length
    }
  } catch (error) {
    console.error('加载任务失败:', error)
    ElMessage.error(error.response?.data?.message || '加载任务失败')
  } finally {
    loading.value = false
  }
}

watch(() => pagination.page, () => {
  if (viewMode.value === 'timeline') {
    loadVisiblePaperTitles()
  }
})

watch(viewMode, (newMode) => {
  pagination.page = 1
  if (newMode === 'timeline') {
    pagination.total = filteredGroups.value.length
    pagination.size = 10
    nextTick(() => loadVisiblePaperTitles())
  } else {
    pagination.total = allTasks.value.length
    pagination.size = 20
  }
})

const handlePageChange = (page) => {
  pagination.page = page
}

const handleSizeChange = (size) => {
  pagination.size = size
  pagination.page = 1
}

const handleFilter = () => {
  pagination.page = 1
  loadTasks()
}

const handleRefresh = () => {
  loadTasks()
}

const viewDetail = (id) => router.push(`/tasks/${id}`)

const generateTaskName = () => {
  const typeMap = {
    'PAPER_SEARCH': '搜索',
    'PAPER_PRE_ANALYSIS': '预分析',
    'PAPER_DOWNLOAD': '下载',
    'PAPER_TRANSFORM': '转换',
    'PAPER_RELATION_EXPAND': '关系扩展',
    'PAPER_EMBEDDING': '向量化',
    'MATERIAL_EXTRACTION': '材料提取',
    'CLEANUP': '清理'
  }

  const typeText = typeMap[createDialog.form.taskType] || '处理'

  if (selectedPaperInfo.value?.title) {
    const title = selectedPaperInfo.value.title.slice(0, 20) + (selectedPaperInfo.value.title.length > 20 ? '...' : '')
    createDialog.form.taskName = `${typeText} - ${title}`
  } else if (createDialog.form.paperId) {
    createDialog.form.taskName = `论文${typeText} - ${createDialog.form.paperId}`
  }
}

const openCreateDialog = () => {
  clearPaperInfo()
  createDialog.form = {
    taskName: '',
    taskType: '',
    paperId: '',
    description: '',
    maxRetries: 3,
    createdBy: 'admin'
  }
  createDialog.visible = true
  setTimeout(() => createFormRef.value?.resetFields(), 0)
}

const handleCreate = async () => {
  if (!createFormRef.value) return

  try {
    await createFormRef.value.validate()
    createDialog.loading = true

    const payload = {
      ...createDialog.form,
      paperId: createDialog.form.paperId || undefined,
      description: createDialog.form.description || undefined,
      ...(selectedPaperInfo.value && {
        paperTitle: selectedPaperInfo.value.title
      })
    }

    await createTask(payload)
    ElMessage.success('任务创建成功')
    createDialog.visible = false
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '创建失败')
    }
  } finally {
    createDialog.loading = false
  }
}

const handleAction = async (command, row) => {
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
    await ElMessageBox.confirm(`确定要重试任务 "${row.taskName}" 吗？`, '提示', { type: 'warning' })
    await retryTask(row.id, { extraAttempts: 3 })
    ElMessage.success('重试请求已发送')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '重试失败')
    }
  }
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要取消任务 "${row.taskName}" 吗？`, '提示', { type: 'warning' })
    await cancelTask(row.id, {
      status: 'CANCELED',
      result: '',
      errorMessage: '用户手动取消'
    })
    ElMessage.success('取消成功')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '取消失败')
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？删除后不可恢复', '警告', { type: 'danger' })
    await deleteTask(row.id)
    ElMessage.success('删除成功')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || error.message || '删除失败')
    }
  }
}

const getStatusType = (status) => ({
  'WAITING': 'info',
  'RUNNING': 'primary',
  'FAILED': 'danger',
  'SUCCEED': 'success',
  'CANCELED': 'warning'
}[status] || 'info')

const getStatusText = (status) => ({
  'WAITING': '等待中',
  'RUNNING': '运行中',
  'FAILED': '失败',
  'SUCCEED': '成功',
  'CANCELED': '已取消'
}[status] || status)

const getTaskTypeColor = (type) => ({
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

const formatTime = (time, mode = 'full') => {
  if (!time) return '-'
  const date = new Date(time)
  if (mode === 'short') {
    return date.toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    }).replace(/\//g, '-')
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.task-list-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
}

.subtitle-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.subtitle {
  color: #6b7280;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.warning-icon {
  color: #e6a23c;
  cursor: help;
  font-size: 16px;
}

.truncated-tag {
  font-size: 11px;
  height: 20px;
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 20px;
  height: 40px;
  border-radius: 8px;
  font-weight: 500;
  flex-shrink: 0;
}

.filter-card {
  margin-bottom: 20px;
  border-radius: 8px;
  border: none;
}

.filter-content {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

.filter-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.timeline-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.paper-flow-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.paper-flow-card {
  border-radius: 12px;
  border: none;
  transition: all 0.3s ease;
}

.paper-flow-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.paper-flow-card.has-failed {
  border-left: 4px solid #f56c6c;
}

.paper-flow-card.is-running {
  border-left: 4px solid #409eff;
  animation: pulse-border 2s infinite;
}

@keyframes pulse-border {
  0%, 100% { border-left-color: #409eff; }
  50% { border-left-color: #a0cfff; }
}

.paper-header {
  cursor: pointer;
  padding: 4px;
}

.paper-main-info {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.paper-title-section {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  flex: 1;
}

.paper-icon {
  color: #409eff;
  margin-top: 2px;
}

.paper-title-wrapper {
  flex: 1;
  min-width: 0;
}

.paper-title {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  word-break: break-all;
}

.loading-tag, .title-missing-tag {
  font-size: 11px;
  height: 20px;
  font-weight: normal;
  flex-shrink: 0;
}

.paper-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #6b7280;
  flex-wrap: wrap;
}

.paper-id-tag {
  font-family: monospace;
  font-size: 12px;
}

.meta-divider {
  color: #d1d5db;
}

.paper-status-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.summary-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 28px;
  font-weight: 500;
  white-space: nowrap;
}

.expand-icon {
  color: #9ca3af;
  transition: transform 0.3s;
  font-size: 16px;
}

.expand-icon.is-expanded {
  transform: rotate(180deg);
}

.flow-progress {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
  overflow-x: auto;
}

.flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  flex: 1;
  min-width: 80px;
}

.step-node {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  transition: all 0.3s;
  z-index: 2;
  background: #fff;
  border: 2px solid #e5e7eb;
  color: #9ca3af;
}

.flow-step.completed .step-node {
  background: #67c23a;
  border-color: #67c23a;
  color: #fff;
}

.flow-step.failed .step-node {
  background: #f56c6c;
  border-color: #f56c6c;
  color: #fff;
}

.flow-step.running .step-node {
  background: #409eff;
  border-color: #409eff;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.2);
}

.step-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-align: center;
}

.step-name {
  font-size: 12px;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
}

.step-time {
  font-size: 11px;
  color: #9ca3af;
}

.step-connector {
  position: absolute;
  top: 16px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: #e5e7eb;
  z-index: 1;
}

.flow-step.completed .step-connector,
.flow-step.failed .step-connector {
  background: #67c23a;
}

.flow-step.failed ~ .flow-step .step-connector {
  background: #e5e7eb;
}

.paper-tasks-detail {
  padding-top: 8px;
}

/* 时间线头部样式 */
.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 8px;
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.timeline-title .el-icon {
  color: #409eff;
  font-size: 18px;
}

.timeline-legend {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.legend-item .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-item .dot.running { background: #409eff; }
.legend-item .dot.success { background: #67c23a; }
.legend-item .dot.failed { background: #f56c6c; }
.legend-item .dot.pending { background: #909399; }

.tasks-timeline {
  padding: 16px 8px 8px 8px;
  position: relative;
}

.task-timeline-item {
  display: flex;
  gap: 16px;
  position: relative;
  padding-bottom: 24px;
}

.task-timeline-item:last-child {
  padding-bottom: 0;
}

.task-timeline-item.last-item .timeline-line {
  display: none;
}

.timeline-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 32px;
  flex-shrink: 0;
}

.timeline-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  z-index: 2;
  background: #fff;
  border: 2px solid;
  transition: all 0.3s;
}

.timeline-dot.info { border-color: #909399; color: #909399; }
.timeline-dot.primary {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.1);
}
.timeline-dot.success { border-color: #67c23a; color: #67c23a; }
.timeline-dot.danger {
  border-color: #f56c6c;
  color: #f56c6c;
  background: #fef0f0;
}
.timeline-dot.warning { border-color: #e6a23c; color: #e6a23c; }

.dot-inner {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.timeline-line {
  width: 2px;
  flex: 1;
  background: linear-gradient(to bottom, #e5e7eb 0%, #e5e7eb 100%);
  margin-top: 4px;
  position: relative;
}

/* 第一个和最后一个项目的特殊样式 */
.task-timeline-item.first-item .timeline-dot {
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.15);
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.task-card-mini {
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px 16px;
  border: 1px solid #e5e7eb;
  transition: all 0.3s;
}

.task-card-mini:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
  transform: translateX(4px);
}

.task-header-mini {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.task-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex: 1;
}

.task-name {
  font-weight: 500;
  color: #1f2937;
  font-size: 14px;
}

.status-mini {
  font-size: 11px;
  height: 20px;
  padding: 0 6px;
}

.task-actions-mini {
  display: flex;
  gap: 4px;
}

.task-meta-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  color: #6b7280;
}

.meta-time-group,
.meta-stats-group {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.time-label {
  color: #9ca3af;
  font-size: 11px;
}

.time-highlight {
  color: #409eff;
  font-weight: 600;
}

.time-latest {
  color: #67c23a;
  font-weight: 600;
}

.error-hint {
  font-size: 11px;
  height: 20px;
}

.error-detail {
  margin-top: 8px;
}

/* 时间间隔指示器 */
.time-connector {
  margin-top: 8px;
  padding-left: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #9ca3af;
  font-size: 11px;
}

.time-connector .el-divider--vertical {
  height: 20px;
  border-left: 2px dashed #dcdfe6;
}

.table-card {
  border-radius: 8px;
  border: none;
  overflow: hidden;
}

:deep(.el-table) {
  --el-table-header-bg-color: #f9fafb;
  --el-table-header-text-color: #374151;
  --el-table-row-hover-bg-color: #f3f4f6;
}

:deep(.el-table th) {
  font-weight: 600;
  font-size: 13px;
  height: 48px;
}

:deep(.el-table td) {
  padding: 12px 0;
}

:deep(.row-failed) {
  background-color: #fef2f2 !important;
}

:deep(.row-running) {
  background-color: #eff6ff !important;
}

.task-name-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
}

.task-icon {
  color: #9ca3af;
  font-size: 18px;
  flex-shrink: 0;
}

.task-info {
  min-width: 0;
}

.task-title {
  font-weight: 500;
  color: #1f2937;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-id {
  font-size: 12px;
  color: #6b7280;
  font-family: monospace;
}

.type-tag {
  font-weight: 500;
}

.status-wrapper {
  display: flex;
  justify-content: center;
}

.status-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 24px;
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

.retry-tag {
  font-family: monospace;
  font-weight: 600;
}

.time-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #6b7280;
  font-size: 13px;
}

.action-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.action-btn {
  padding: 4px 8px;
}

:deep(.danger-item) {
  color: #f56c6c;
}

.pagination-wrapper {
  padding: 16px 24px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 0 0 8px 8px;
}

.empty-state {
  padding: 60px 0;
  background: #fff;
  border-radius: 8px;
}

.empty-hint {
  font-size: 13px;
  color: #9ca3af;
  margin-top: 8px;
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .filter-content {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-actions {
    margin-left: 0;
    display: flex;
    justify-content: space-between;
  }

  .paper-main-info {
    flex-direction: column;
    gap: 12px;
  }

  .paper-status-summary {
    width: 100%;
    justify-content: flex-end;
  }

  .flow-progress {
    padding: 12px 8px;
  }

  .step-name {
    font-size: 11px;
  }

  .paper-title {
    font-size: 14px;
  }

  .subtitle-wrapper {
    width: 100%;
  }

  .timeline-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .meta-time-group,
  .meta-stats-group {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
