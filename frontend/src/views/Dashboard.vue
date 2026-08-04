<template>
  <div class="modern-dashboard">
    <!-- 动态背景增强 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="grid-pattern"></div>
    </div>

    <!-- 页面标题区 -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="greeting">
          <div class="time-badge-wrapper">
            <span class="time-badge">{{ currentTime }}</span>
            <span class="live-pulse">
              <span class="pulse-ring"></span>
              <span class="pulse-dot"></span>
            </span>
          </div>
          <h1 class="main-title">
            <span class="gradient-text">欢迎回来</span>，管理员
          </h1>
          <p class="subtitle">实时监控论文收集与任务处理状态</p>
        </div>
        <div class="hero-stats">
          <div class="quick-stat">
            <div class="quick-stat-value">{{ formatNumber(totalProcessedTasks) }}</div>
            <div class="quick-stat-label">总处理量</div>
          </div>
          <div class="quick-stat-divider"></div>
          <div class="quick-stat">
            <div class="quick-stat-value text-success">{{ formattedStats.find(s => s.key === 'succeed')?.displayValue || 0 }}</div>
            <div class="quick-stat-label">成功任务</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实时状态统计卡片 -->
    <div class="section-header">
      <div class="section-title-group">
        <div class="section-icon">
          <el-icon><Monitor /></el-icon>
        </div>
        <div>
          <h2 class="section-title">实时状态监控</h2>
          <p class="section-desc">当前任务队列与各状态分布</p>
        </div>
      </div>
      <el-button text type="primary" @click="refreshAllData" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新数据
      </el-button>
    </div>

    <div class="stats-grid">
      <div
          v-for="(stat, index) in formattedStats"
          :key="stat.key"
          class="stat-card"
          :class="[`stat-${stat.key}`, { clickable: stat.key !== 'backlogThreshold' }]"
          :style="{ animationDelay: `${index * 0.1}s` }"
          @click="handleCardClick(stat.key)"
      >
        <div class="card-glass">
          <div class="stat-visual">
            <div class="icon-container" :class="stat.key">
              <el-icon size="24">
                <component :is="stat.icon" />
              </el-icon>
              <div class="icon-ring"></div>
            </div>
            <div class="trend-indicator" v-if="stat.trend !== undefined && stat.trend !== 0">
              <el-icon><ArrowUp v-if="stat.trend > 0" /><ArrowDown v-else /></el-icon>
              <span>{{ Math.abs(stat.trend) }}%</span>
            </div>
          </div>
          <div class="stat-body">
            <div class="stat-value-wrapper">
              <span class="stat-value">{{ stat.displayValue }}</span>
              <span class="stat-unit" v-if="stat.unit">{{ stat.unit }}</span>
            </div>
            <div class="stat-label">{{ stat.label }}</div>
            <div class="stat-detail" v-if="stat.detail">
              <el-tag size="small" effect="plain" :type="stat.detailType">
                {{ stat.detail }}
              </el-tag>
            </div>
          </div>
          <div class="card-shine"></div>
        </div>
      </div>
    </div>

    <!-- 处理能力总览（横向滚动布局） -->
    <div class="section-header">
      <div class="section-title-group">
        <div class="section-icon purple">
          <el-icon><Cpu /></el-icon>
        </div>
        <div>
          <h2 class="section-title">处理能力总览</h2>
          <p class="section-desc">系统累计处理任务类型分布 · 左右滑动查看更多</p>
        </div>
      </div>
      <div class="scroll-hint">
        <el-icon class="scroll-icon"><ArrowRight /></el-icon>
      </div>
    </div>

    <div class="capability-wrapper">
      <div class="capability-scroll-container">
        <div
            v-for="(item, index) in taskTypeStats"
            :key="item.type"
            class="capability-card"
            :style="{ animationDelay: `${index * 0.08}s` }"
        >
          <div class="capability-inner">
            <div class="capability-main">
              <div class="capability-icon-wrapper" :class="item.colorClass">
                <el-icon size="20">
                  <component :is="item.icon" />
                </el-icon>
                <div class="icon-glow"></div>
              </div>
              <div class="capability-content">
                <div class="capability-label">{{ item.label }}</div>
                <div class="capability-value-wrapper">
                  <span class="capability-value">{{ formatNumber(item.count) }}</span>
                  <span class="capability-percent" v-if="item.count > 0">
                    {{ ((item.count / totalProcessedTasks) * 100).toFixed(1) }}%
                  </span>
                </div>
              </div>
            </div>
            <div class="capability-progress-bg">
              <div
                  class="capability-progress-fill"
                  :class="item.colorClass"
                  :style="{ width: `${Math.min((item.count / maxTaskTypeCount) * 100, 100)}%` }"
              ></div>
            </div>
          </div>
        </div>

        <!-- 添加一个"查看更多"卡片 -->
        <div class="capability-card more-card" @click="router.push('/tasks')">
          <div class="capability-inner center">
            <div class="more-icon">
              <el-icon size="24"><ArrowRight /></el-icon>
            </div>
            <div class="more-text">查看全部</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 主内容网格 -->
    <div class="content-grid">
      <!-- 左侧列 -->
      <div class="content-column main-column">
        <!-- 任务趋势图表 -->
        <el-card class="glass-card chart-container" shadow="never" v-loading="trendLoading">
          <template #header>
            <div class="card-header-modern">
              <div class="header-title-group">
                <div class="title-icon purple">
                  <el-icon><TrendCharts /></el-icon>
                </div>
                <div>
                  <h3 class="card-title">任务趋势分析</h3>
                  <span class="card-subtitle">
                    最近{{ chartPeriod === 'week' ? '7天' : '30天' }}任务处理情况
                    <el-tag v-if="trendDataStats.total > 0" size="small" type="info" effect="light" style="margin-left: 8px">
                      共 {{ trendDataStats.total }} 条
                    </el-tag>
                    <el-tag v-if="trendDataStats.isPartial" size="small" type="warning" effect="light" style="margin-left: 8px">
                      数据可能不完整
                    </el-tag>
                  </span>
                </div>
              </div>
              <div class="header-actions-group">
                <el-tooltip content="清理缓存并重新加载" placement="top">
                  <el-button circle size="small" @click="clearTrendCache" :loading="trendLoading">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-radio-group v-model="chartPeriod" size="small">
                  <el-radio-button label="week">本周</el-radio-button>
                  <el-radio-button label="month">本月</el-radio-button>
                </el-radio-group>
                <el-tooltip content="下载报表" placement="top">
                  <el-button circle size="small" @click="downloadReport">
                    <el-icon><Download /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>
          </template>

          <!-- 数据提示 -->
          <div v-if="trendDataStats.isPartial" class="data-warning">
            <el-alert
                title="检测到数据不完整"
                type="warning"
                :closable="false"
                show-icon
                :description="`仅加载了最近 ${trendDataStats.loaded} 条/${trendDataStats.total} 条记录，历史数据可能缺失。建议定期清理缓存重新加载。`"
            />
          </div>

          <div ref="trendChartRef" class="trend-chart"></div>
        </el-card>

        <!-- 最近任务 -->
        <el-card class="glass-card task-card" shadow="never">
          <template #header>
            <div class="card-header-modern">
              <div class="header-title-group">
                <div class="title-icon blue">
                  <el-icon><List /></el-icon>
                </div>
                <div>
                  <h3 class="card-title">最近任务</h3>
                  <span class="card-subtitle" v-if="!taskLoading">
                    {{ recentTasks.length }} 个活跃任务
                    <span v-if="runningTaskCount > 0" class="live-indicator">
                      <span class="pulse-dot"></span>
                      {{ runningTaskCount }} 个运行中
                    </span>
                  </span>
                </div>
              </div>
              <div class="header-actions">
                <el-radio-group v-model="taskFilter" size="small" @change="fetchRecentTasks">
                  <el-radio-button label="all">全部</el-radio-button>
                  <el-radio-button label="running">运行中</el-radio-button>
                  <el-radio-button label="failed">失败</el-radio-button>
                </el-radio-group>
                <el-button
                    text
                    type="primary"
                    @click="$router.push('/tasks')"
                    class="view-all"
                >
                  查看全部 <el-icon class="arrow-icon"><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>
          </template>

          <!-- 加载状态 -->
          <div v-if="taskLoading" class="task-skeleton">
            <div v-for="i in 4" :key="i" class="skeleton-item">
              <div class="skeleton-status"></div>
              <div class="skeleton-content">
                <div class="skeleton-line short"></div>
                <div class="skeleton-line"></div>
              </div>
            </div>
          </div>

          <!-- 任务列表 -->
          <div v-else-if="recentTasks.length" class="modern-task-list">
            <div
                v-for="(task, idx) in recentTasks"
                :key="task.id"
                class="task-row"
                :class="{ 'has-error': task.status === 'FAILED' }"
                :style="{ animationDelay: `${idx * 0.05}s` }"
            >
              <div class="task-status-indicator" :class="getStatusClass(task.status)">
                <div class="pulse-ring" v-if="task.status === 'RUNNING'"></div>
                <div class="status-dot"></div>
                <el-icon v-if="task.status === 'SUCCEED'" class="status-icon"><CircleCheck /></el-icon>
                <el-icon v-else-if="task.status === 'FAILED'" class="status-icon"><Warning /></el-icon>
              </div>

              <div class="task-content" @click="viewTaskDetail(task.id)">
                <div class="task-header">
                  <span class="task-name">{{ task.taskName || '未命名任务' }}</span>
                  <div class="task-tags">
                    <el-tag size="small" effect="light" class="task-type">
                      {{ formatTaskType(task.taskType) }}
                    </el-tag>
                    <el-tag
                        v-if="task.retryCount > 0"
                        size="small"
                        type="warning"
                        effect="light"
                        class="retry-tag"
                    >
                      重试 {{ task.retryCount }}/{{ task.maxRetries }}
                    </el-tag>
                  </div>
                </div>

                <div class="task-meta">
                  <span class="meta-item" v-if="task.status === 'RUNNING' && task.startTime">
                    <el-icon><Timer /></el-icon>
                    已运行 {{ calculateDuration(task.startTime) }}
                  </span>
                  <span class="meta-item" v-else-if="task.durationMs">
                    <el-icon><Timer /></el-icon>
                    耗时 {{ formatDuration(task.durationMs) }}
                  </span>
                  <span class="meta-item" v-else>
                    <el-icon><Clock /></el-icon>
                    {{ formatTime(task.startTime || task.createdAt) }}
                  </span>

                  <span class="meta-divider">·</span>

                  <span class="meta-item task-id">
                    ID: {{ task.id }}
                  </span>

                  <span v-if="task.paperId" class="meta-divider">·</span>
                  <span v-if="task.paperId" class="meta-item paper-ref">
                    <el-icon><Document /></el-icon>
                    关联论文
                  </span>
                </div>

                <div v-if="task.status === 'FAILED' && task.errorMessage" class="error-hint">
                  <el-icon><InfoFilled /></el-icon>
                  <span class="error-text">{{ truncateError(task.errorMessage) }}</span>
                </div>
              </div>

              <div class="task-operations">
                <div v-if="task.status === 'RUNNING'" class="task-progress">
                  <el-progress
                      :percentage="calculateProgress(task)"
                      :show-text="false"
                      :stroke-width="3"
                      :color="getProgressColor(task)"
                      class="mini-progress"
                  />
                </div>

                <el-dropdown trigger="click" @command="handleTaskCommand($event, task)">
                  <el-button text class="action-menu-btn">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="view">
                        <el-icon><View /></el-icon>查看详情
                      </el-dropdown-item>
                      <el-dropdown-item
                          v-if="task.status === 'FAILED'"
                          command="retry"
                          :disabled="task.retryCount >= task.maxRetries"
                      >
                        <el-icon><RefreshRight /></el-icon>
                        {{ task.retryCount >= task.maxRetries ? '重试次数耗尽' : '重新执行' }}
                      </el-dropdown-item>
                      <el-dropdown-item
                          v-if="['WAITING', 'RUNNING'].includes(task.status)"
                          command="cancel"
                      >
                        <el-icon><CircleClose /></el-icon>取消任务
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" divided class="danger-item">
                        <el-icon><Delete /></el-icon>删除记录
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <div v-else class="empty-state modern-empty">
            <div class="empty-illustration">
              <div class="floating-icons">
                <el-icon size="48" color="#cbd5e1"><Document /></el-icon>
                <el-icon size="32" color="#e2e8f0" class="float-icon"><Plus /></el-icon>
              </div>
            </div>
            <h4>暂无{{ taskFilter === 'all' ? '' : statusTextMap[taskFilter] }}任务</h4>
            <p>{{ emptyStateText }}</p>
            <el-button type="primary" @click="openCreateDialog" round size="large" class="create-btn">
              <el-icon><Plus /></el-icon> 创建首个任务
            </el-button>
          </div>

          <!-- 底部刷新栏 -->
          <div v-if="recentTasks.length" class="task-list-footer">
            <span class="last-update">上次更新: {{ lastUpdateTime }}</span>
            <el-button
                link
                type="primary"
                :loading="taskLoading"
                @click="fetchRecentTasks"
                size="small"
            >
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 右侧列 -->
      <div class="content-column side-column">
        <!-- 今日速览 - 真实数据 -->
        <el-card class="glass-card today-card" shadow="never">
          <template #header>
            <div class="card-header-modern">
              <div class="header-title-group">
                <div class="title-icon green">
                  <el-icon><Calendar /></el-icon>
                </div>
                <div>
                  <h3 class="card-title">今日速览</h3>
                  <span class="card-subtitle">今日任务处理统计</span>
                </div>
              </div>
              <el-tag v-if="!todayLoading" type="success" size="small" effect="light">
                <el-icon class="live-dot"><CircleCheck /></el-icon>
                实时
              </el-tag>
              <el-tag v-else type="info" size="small">加载中...</el-tag>
            </div>
          </template>

          <div v-if="todayLoading" class="today-loading">
            <div v-for="i in 3" :key="i" class="today-skeleton">
              <div class="skeleton-icon"></div>
              <div class="skeleton-text"></div>
            </div>
          </div>

          <div v-else class="today-stats">
            <div class="today-item">
              <div class="today-icon blue">
                <el-icon><Check /></el-icon>
              </div>
              <div class="today-info">
                <div class="today-value">{{ todayStats.completed }}</div>
                <div class="today-label">已完成</div>
                <div class="today-trend" :class="{ 'up': todayStats.completed > 0 }">
                  <span v-if="todayStats.completed > 5" class="trend-badge">+{{ todayStats.growth }}%</span>
                </div>
              </div>
            </div>
            <div class="today-item">
              <div class="today-icon orange">
                <el-icon><Timer /></el-icon>
              </div>
              <div class="today-info">
                <div class="today-value">{{ todayStats.avgTime }}<span class="unit">m</span></div>
                <div class="today-label">平均耗时</div>
                <div class="today-sub">较昨日 {{ todayStats.avgTimeDiff > 0 ? '+' : '' }}{{ todayStats.avgTimeDiff }}m</div>
              </div>
            </div>
            <div class="today-item">
              <div class="today-icon purple">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div class="today-info">
                <div class="today-value">{{ todayStats.successRate }}<span class="unit">%</span></div>
                <div class="today-label">成功率</div>
                <div class="today-sub">{{ todayStats.total }} 个任务</div>
              </div>
            </div>
          </div>

          <div class="mini-chart-container">
            <div ref="miniChartRef" class="mini-chart"></div>
          </div>

          <div class="today-footer">
            <div class="today-summary">
              <span class="summary-label">今日总任务</span>
              <span class="summary-value">{{ todayStats.total }}</span>
            </div>
            <div class="today-actions">
              <el-button text type="primary" size="small" @click="router.push('/tasks')">
                查看详情 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </el-card>

        <!-- 快捷操作 -->
        <el-card class="glass-card actions-card" shadow="never">
          <template #header>
            <div class="card-header-modern">
              <div class="header-title-group">
                <div class="title-icon orange">
                  <el-icon><Operation /></el-icon>
                </div>
                <h3 class="card-title">快捷操作</h3>
              </div>
            </div>
          </template>

          <div class="actions-grid">
            <div
                v-for="action in quickActions"
                :key="action.key"
                class="action-item"
                :class="action.type"
                @click="action.handler"
            >
              <div class="action-icon-wrapper">
                <el-icon size="20"><component :is="action.icon" /></el-icon>
              </div>
              <div class="action-info">
                <div class="action-title">{{ action.title }}</div>
                <div class="action-desc">{{ action.desc }}</div>
              </div>
              <el-icon class="action-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>

        <!-- 系统动态 -->
        <el-card class="glass-card notice-card" shadow="never">
          <template #header>
            <div class="card-header-modern">
              <div class="header-title-group">
                <div class="title-icon cyan">
                  <el-icon><Bell /></el-icon>
                </div>
                <div>
                  <h3 class="card-title">系统动态</h3>
                  <span class="card-subtitle" v-if="notices.length">{{ notices.length }} 条更新</span>
                </div>
              </div>
            </div>
          </template>

          <div v-if="notices.length" class="timeline-modern">
            <div
                v-for="(notice, idx) in notices.slice(0, 5)"
                :key="idx"
                class="timeline-item"
                :class="{ clickable: notice.clickable }"
                @click="notice.clickable && handleNoticeClick(notice)"
            >
              <div class="timeline-marker" :class="notice.type">
                <div class="marker-dot"></div>
                <div class="marker-line" v-if="idx !== notices.slice(0, 5).length - 1"></div>
              </div>
              <div class="timeline-content">
                <div class="timeline-header">
                  <span class="timeline-title">{{ notice.title }}</span>
                  <span class="timeline-time">{{ notice.time }}</span>
                </div>
                <p class="timeline-desc">{{ notice.content }}</p>
                <el-tag v-if="notice.tag" size="small" :type="notice.tagType" effect="light">
                  {{ notice.tag }}
                </el-tag>
              </div>
            </div>
          </div>

          <div v-else class="empty-state small">
            <el-icon size="40" color="#cbd5e1"><Bell /></el-icon>
            <p>暂无系统动态</p>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 悬浮操作按钮（FAB） -->
    <div class="fab-container" :class="{ 'fab-visible': showFab }">
      <el-button
          circle
          size="large"
          type="primary"
          class="fab-btn"
          @click="openCreateDialog"
      >
        <el-icon size="24"><Plus /></el-icon>
      </el-button>
      <div class="fab-tooltip">新建任务</div>
    </div>

    <!-- 创建任务对话框 -->
    <el-dialog
        v-model="createDialog.visible"
        title="新建任务"
        width="600px"
        :close-on-click-modal="false"
        destroy-on-close
        class="modern-dialog"
        align-center
    >
      <div class="dialog-body">
        <el-steps :active="currentStep" finish-status="success" simple class="form-steps">
          <el-step title="基础配置" />
          <el-step title="参数设置" />
        </el-steps>

        <el-form
            v-if="currentStep === 0"
            ref="formRef"
            :model="createDialog.form"
            :rules="rules"
            label-position="top"
            class="modern-form"
        >
          <el-form-item label="任务类型" prop="taskType">
            <div class="task-type-grid">
              <div
                  v-for="type in taskTypes"
                  :key="type.value"
                  class="type-option"
                  :class="{ active: createDialog.form.taskType === type.value }"
                  @click="createDialog.form.taskType = type.value"
              >
                <div class="type-icon" :class="type.color">
                  <el-icon size="24"><component :is="type.icon" /></el-icon>
                </div>
                <div class="type-label">{{ type.label }}</div>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="任务名称" prop="taskName">
            <el-input
                v-model="createDialog.form.taskName"
                placeholder="例如：石油工程论文抓取任务"
                maxlength="50"
                show-word-limit
                size="large"
            />
          </el-form-item>
        </el-form>

        <el-form
            v-else
            :model="createDialog.form"
            label-position="top"
            class="modern-form"
        >
          <el-form-item label="任务描述">
            <el-input
                v-model="createDialog.form.description"
                type="textarea"
                :rows="4"
                placeholder="描述任务的具体内容和目标..."
                maxlength="200"
                show-word-limit
            />
          </el-form-item>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="最大重试次数">
                <el-slider v-model="createDialog.form.maxRetries" :max="10" show-stops />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="优先级">
                <el-segmented
                    v-model="createDialog.form.priority"
                    :options="[
                    { label: '普通', value: 0 },
                    { label: '紧急', value: 1 },
                    { label: '立即', value: 2 }
                  ]"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <template #footer>
        <div class="dialog-footer-modern">
          <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
          <el-button v-if="currentStep < 1" type="primary" @click="nextStep">下一步</el-button>
          <el-button
              v-else
              type="primary"
              @click="handleCreate"
              :loading="createDialog.loading"
              size="large"
          >
            创建任务
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import {
  Refresh, Plus, List, ArrowRight, ArrowUp, ArrowDown, Timer, Clock,
  TrendCharts, Operation, Bell, Monitor, Document, Search,
  Download, Brush, Cpu, CircleCheck, Warning, CircleClose,
  MoreFilled, View, RefreshRight, Delete, InfoFilled, DataAnalysis, Files, Reading,
  Calendar, Check
} from '@element-plus/icons-vue'
import { API_BASE_URL } from '@/config/runtime'

const router = useRouter()

// === 缓存管理工具（升级版本v3，修复数据丢失问题）===
const TREND_CACHE_KEY = 'task_trend_cache_v3' // 升级版本号，强制清理旧缓存
const CACHE_MAX_AGE = 7 * 24 * 60 * 60 * 1000 // 缓存有效期7天（延长）
const MAX_CACHE_TASKS = 5000 // 最大缓存任务数，881条数据可以轻松容纳

const getTrendCache = () => {
  try {
    const cache = localStorage.getItem(TREND_CACHE_KEY)
    if (!cache) return null
    const parsed = JSON.parse(cache)
    // 检查缓存数据结构完整性
    if (!parsed.rawTasks || !Array.isArray(parsed.rawTasks) || !parsed.trendData) {
      console.warn('缓存数据损坏，清理缓存')
      localStorage.removeItem(TREND_CACHE_KEY)
      return null
    }
    if (Date.now() - parsed.timestamp > CACHE_MAX_AGE) {
      console.log('缓存已过期')
      return null
    }
    console.log(`读取缓存成功: ${parsed.rawTasks.length} 条任务`)
    return parsed
  } catch (e) {
    console.error('读取缓存失败:', e)
    return null
  }
}

const setTrendCache = (trendData, rawTasks) => {
  try {
    // 保留完整字段，不精简，避免数据丢失
    // 按ID去重，保留最新
    const taskMap = new Map()
    rawTasks.forEach(t => {
      if (t && t.id) {
        // 如果已存在，比较时间保留更新的
        const existing = taskMap.get(t.id)
        if (!existing || new Date(t.updatedAt || t.endTime || 0) > new Date(existing.updatedAt || existing.endTime || 0)) {
          taskMap.set(t.id, t)
        }
      }
    })

    const uniqueTasks = Array.from(taskMap.values())

    // 如果数据过多，按时间倒序保留最新的（而不是前面的）
    if (uniqueTasks.length > MAX_CACHE_TASKS) {
      uniqueTasks.sort((a, b) => {
        const timeA = new Date(a.endTime || a.startTime || a.createdAt || 0)
        const timeB = new Date(b.endTime || b.startTime || b.createdAt || 0)
        return timeB - timeA // 倒序，保留最新的
      })
      uniqueTasks.length = MAX_CACHE_TASKS
    }

    const cacheData = {
      timestamp: Date.now(),
      trendData,
      rawTasks: uniqueTasks,
      totalCount: uniqueTasks.length
    }

    localStorage.setItem(TREND_CACHE_KEY, JSON.stringify(cacheData))
    console.log(`缓存已更新: ${uniqueTasks.length} 条任务`)
    return uniqueTasks.length
  } catch (e) {
    console.error('缓存存储失败:', e)
    // 如果存储失败，可能是数据太大，尝试压缩存储（只存关键字段）
    try {
      const minimalTasks = rawTasks.slice(0, 2000).map(t => ({
        id: t.id,
        status: t.status,
        endTime: t.endTime,
        startTime: t.startTime,
        createdAt: t.createdAt,
        taskType: t.taskType,
        taskName: t.taskName // 保留名称用于调试
      }))
      localStorage.setItem(TREND_CACHE_KEY, JSON.stringify({
        timestamp: Date.now(),
        trendData,
        rawTasks: minimalTasks,
        totalCount: minimalTasks.length,
        isCompressed: true
      }))
      console.log(`已切换到压缩模式存储: ${minimalTasks.length} 条`)
      return minimalTasks.length
    } catch (e2) {
      console.error('压缩存储也失败:', e2)
      return 0
    }
  }
}

const clearTrendCache = () => {
  localStorage.removeItem(TREND_CACHE_KEY)
  // 同时清理旧版本缓存
  localStorage.removeItem('task_trend_cache_v2')
  localStorage.removeItem('task_trend_cache')
  ElMessage.success('历史缓存已清理，正在重新加载完整数据...')
  loadTrendData(true)
}

// === 全局状态 ===
const loading = ref(false)
const statsData = ref({})
const trendChartRef = ref(null)
let trendChart = null
const miniChartRef = ref(null)
let miniChart = null
const chartPeriod = ref('week')
const showFab = ref(false)
const trendLoading = ref(false)

// === 趋势数据统计信息 ===
const trendDataStats = ref({
  total: 0,
  loaded: 0,
  isPartial: false
})

// === 任务类型统计 ===
const taskTypeStats = ref([])

// === 今日统计状态（真实数据）===
const todayLoading = ref(false)
const todayStats = ref({
  completed: 0,
  avgTime: 0,
  avgTimeDiff: 0,
  growth: 0,
  successRate: 0,
  total: 0
})

// === 趋势数据状态 ===
const trendData = ref({
  dates: [],
  succeed: [],
  running: [],
  waiting: [],
  failed: []
})

// === 任务相关状态 ===
const taskLoading = ref(false)
const recentTasks = ref([])
const taskFilter = ref('all')
const lastUpdateTime = ref('-')
const notices = ref([])

// 任务类型配置
const taskTypeConfig = {
  'PAPER_SEARCH': { label: '论文搜索', icon: Search, colorClass: 'blue', color: '#3b82f6' },
  'PAPER_DOWNLOAD': { label: '论文下载', icon: Download, colorClass: 'cyan', color: '#06b6d4' },
  'PAPER_TRANSFORM': { label: 'PDF转化', icon: Files, colorClass: 'purple', color: '#8b5cf6' },
  'PAPER_EMBEDDING': { label: '向量化处理', icon: Cpu, colorClass: 'indigo', color: '#6366f1' },
  'MATERIAL_EXTRACTION': { label: '材料提取', icon: DataAnalysis, colorClass: 'orange', color: '#f97316' },
  'DEEP_ANALYSIS': { label: '深度分析', icon: Reading, colorClass: 'pink', color: '#ec4899' },
  'BATCH_DOWNLOAD': { label: '批量下载', icon: Download, colorClass: 'teal', color: '#14b8a6' },
  'DATA_CLEAN': { label: '数据清洗', icon: Brush, colorClass: 'lime', color: '#84cc16' }
}

// 状态映射配置
const statusClassMap = {
  'WAITING': 'waiting',
  'RUNNING': 'running',
  'SUCCEED': 'succeed',
  'FAILED': 'failed',
  'CANCELED': 'canceled'
}

const statusTextMap = {
  'WAITING': '等待中',
  'RUNNING': '运行中',
  'SUCCEED': '成功',
  'FAILED': '失败',
  'CANCELED': '已取消',
  'all': '',
  'running': '运行中',
  'failed': '失败'
}

const taskTypeMap = {
  'PAPER_SEARCH': '论文搜索',
  'BATCH_DOWNLOAD': '批量下载',
  'DATA_CLEAN': '数据清洗',
  'DEEP_ANALYSIS': '深度分析',
  'MATERIAL_EXTRACTION': '材料提取',
  'PAPER_EMBEDDING': '向量化任务',
  'PAPER_TRANSFORM': 'PDF转换任务',
  'PAPER_DOWNLOAD': '论文下载'
}

// === 计算属性 ===
const currentTime = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const formattedStats = computed(() => {
  const stats = [
    { key: 'waiting', label: '等待中', icon: 'Timer', color: '#3b82f6', trend: 0 },
    { key: 'running', label: '运行中', icon: 'Loading', color: '#10b981', trend: 0 },
    { key: 'succeed', label: '成功', icon: 'CircleCheck', color: '#22c55e', trend: 0 },
    { key: 'failed', label: '失败', icon: 'Warning', color: '#ef4444', trend: 0 },
    { key: 'canceled', label: '已取消', icon: 'CircleClose', color: '#6b7280', trend: 0 },
    { key: 'backlogThreshold', label: '积压阈值', icon: ArrowUp, color: '#f59e0b' }
  ]

  return stats.map(stat => {
    const value = statsData.value[stat.key] || 0
    const total = getTotal(value)
    return {
      ...stat,
      value: total,
      displayValue: formatNumber(total),
      unit: total >= 1000 ? 'k' : '',
      detail: typeof value === 'object' && value !== null ? `${Object.keys(value).length} 个分类` : null,
      detailType: stat.key === 'failed' ? 'danger' : 'info'
    }
  })
})

const runningTaskCount = computed(() =>
    recentTasks.value.filter(t => t.status === 'RUNNING').length
)

const emptyStateText = computed(() => {
  const map = {
    'all': '创建新任务开始收集论文数据',
    'running': '当前没有正在执行的任务',
    'failed': '近期没有失败的任务，太棒了！'
  }
  return map[taskFilter.value]
})

const maxTaskTypeCount = computed(() => {
  if (taskTypeStats.value.length === 0) return 1
  return Math.max(...taskTypeStats.value.map(item => item.count))
})

const totalProcessedTasks = computed(() => {
  return taskTypeStats.value.reduce((sum, item) => sum + item.count, 0)
})

// === 快捷操作 ===
const quickActions = [
  {
    key: 'create',
    title: '新建任务',
    desc: '创建论文处理任务',
    icon: Plus,
    type: 'primary',
    handler: () => openCreateDialog()
  },
  {
    key: 'search',
    title: '搜索论文',
    desc: '从网络检索论文',
    icon: Search,
    type: 'success',
    handler: () => router.push('/keyword-search')
  },
  {
    key: 'library',
    title: '论文库',
    desc: '管理已收集论文',
    icon: Document,
    type: 'warning',
    handler: () => router.push('/papers')
  }
]

// === 任务类型 ===
const taskTypes = [
  { value: 'PAPER_SEARCH', label: '论文搜索', icon: Search, color: 'blue' },
  { value: 'BATCH_DOWNLOAD', label: '批量下载', icon: Download, color: 'purple' },
  { value: 'DATA_CLEAN', label: '数据清洗', icon: Brush, color: 'orange' },
  { value: 'DEEP_ANALYSIS', label: '深度分析', icon: Cpu, color: 'cyan' }
]

// === 创建任务对话框 ===
const createDialog = reactive({
  visible: false,
  loading: false,
  form: {
    taskType: '',
    taskName: '',
    description: '',
    maxRetries: 3,
    priority: 0
  }
})
const currentStep = ref(0)
const formRef = ref()

const rules = {
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur', min: 2, max: 50 }]
}

// === 新增：获取今日统计数据（真实数据）===
const loadTodayStats = async () => {
  todayLoading.value = true
  try {
    // 获取今日日期范围（使用本地时间）
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const tomorrow = new Date(today)
    tomorrow.setDate(tomorrow.getDate() + 1)

    // 获取昨日日期范围用于对比
    const yesterday = new Date(today)
    yesterday.setDate(yesterday.getDate() - 1)

    // 使用趋势图的缓存数据来计算今日统计（避免重复请求）
    const cache = getTrendCache()
    let allTasks = []

    if (cache && cache.rawTasks) {
      allTasks = cache.rawTasks
    } else {
      // 如果没有缓存，获取最近数据
      const statuses = ['SUCCEED', 'FAILED', 'RUNNING', 'WAITING', 'CANCELED']
      const promises = statuses.map(status =>
          fetch(`${API_BASE_URL}/tasks?status=${status}&limit=200`)
              .then(r => r.ok ? r.json() : [])
              .catch(() => [])
      )
      const results = await Promise.all(promises)
      allTasks = results.flat()
    }

    // 过滤今日任务（使用本地时间比较）
    const todayTasks = allTasks.filter(task => {
      const taskTime = getTaskTimeForFilter(task)
      if (!taskTime) return false
      return taskTime >= today && taskTime < tomorrow
    })

    const yesterdayTasks = allTasks.filter(task => {
      const taskTime = getTaskTimeForFilter(task)
      if (!taskTime) return false
      return taskTime >= yesterday && taskTime < today
    })

    // 计算今日统计
    const todayCompleted = todayTasks.filter(t => t.status === 'SUCCEED').length
    const todayTotal = todayTasks.length

    // 计算平均耗时
    const completedTasksWithDuration = todayTasks.filter(t =>
        (t.status === 'SUCCEED' || t.status === 'FAILED') && t.durationMs
    )
    const avgTimeMs = completedTasksWithDuration.length > 0
        ? completedTasksWithDuration.reduce((sum, t) => sum + (t.durationMs || 0), 0) / completedTasksWithDuration.length
        : 0
    const avgTimeMinutes = Math.round(avgTimeMs / 60000 * 10) / 10

    // 计算昨日平均耗时
    const yesterdayCompleted = yesterdayTasks.filter(t => t.status === 'SUCCEED')
    const yesterdayAvgTimeMs = yesterdayCompleted.length > 0
        ? yesterdayCompleted.reduce((sum, t) => sum + (t.durationMs || 0), 0) / yesterdayCompleted.length
        : 0
    const yesterdayAvgTimeMinutes = Math.round(yesterdayAvgTimeMs / 60000 * 10) / 10

    const growth = yesterdayCompleted.length > 0
        ? Math.round(((todayCompleted - yesterdayCompleted.length) / yesterdayCompleted.length) * 100)
        : 0

    const successRate = todayTotal > 0
        ? Math.round((todayCompleted / todayTotal) * 100)
        : 0

    // 更新图表
    updateTodayHourlyChart(todayTasks)

    todayStats.value = {
      completed: todayCompleted,
      avgTime: avgTimeMinutes || 0,
      avgTimeDiff: (avgTimeMinutes - yesterdayAvgTimeMinutes).toFixed(1),
      growth: growth,
      successRate: successRate,
      total: todayTotal
    }

  } catch (error) {
    console.error('获取今日统计失败:', error)
    todayStats.value = { completed: 0, avgTime: 0, avgTimeDiff: 0, growth: 0, successRate: 0, total: 0 }
  } finally {
    todayLoading.value = false
  }
}

// 辅助函数：获取任务时间用于过滤（优先使用endTime对于已完成任务）
const getTaskTimeForFilter = (task) => {
  if (!task) return null
  let dateStr = null
  if (task.status === 'SUCCEED' || task.status === 'FAILED') {
    dateStr = task.endTime || task.startTime || task.createdAt
  } else if (task.status === 'RUNNING') {
    dateStr = task.startTime || task.createdAt
  } else {
    dateStr = task.createdAt
  }
  if (!dateStr) return null
  const date = new Date(dateStr)
  return isNaN(date.getTime()) ? null : date
}

// 辅助函数：获取指定日期范围的任务
const fetchTasksByDateRange = async (startTime, endTime) => {
  try {
    const statuses = ['SUCCEED', 'FAILED', 'RUNNING', 'WAITING', 'CANCELED']
    const promises = statuses.map(status =>
        fetch(`${API_BASE_URL}/tasks?status=${status}&limit=200`)
            .then(r => r.ok ? r.json() : [])
            .catch(() => [])
    )

    const results = await Promise.all(promises)
    const allTasks = results.flat()

    // 过滤指定日期范围（使用本地时间）
    const start = new Date(startTime)
    const end = new Date(endTime)

    return allTasks.filter(task => {
      const taskTime = getTaskTimeForFilter(task)
      if (!taskTime) return false
      return taskTime >= start && taskTime < end
    })
  } catch (error) {
    console.error('获取任务数据失败:', error)
    return []
  }
}

// 更新今日分时图表
const updateTodayHourlyChart = (tasks) => {
  if (!miniChart) return

  const hours = Array.from({length: 12}, (_, i) => `${i * 2}:00`)
  const hourData = new Array(12).fill(0)

  tasks.filter(t => t.status === 'SUCCEED').forEach(task => {
    const taskTime = getTaskTimeForFilter(task)
    if (!taskTime) return
    const hour = taskTime.getHours()
    const slotIndex = Math.floor(hour / 2)
    if (slotIndex >= 0 && slotIndex < 12) {
      hourData[slotIndex]++
    }
  })

  const option = {
    grid: { top: 5, right: 5, bottom: 5, left: 5 },
    xAxis: {
      type: 'category',
      data: hours,
      show: false
    },
    yAxis: {
      type: 'value',
      show: false
    },
    series: [{
      data: hourData,
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#3b82f6' },
          { offset: 1, color: '#10b981' }
        ]),
        width: 3
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(59, 130, 246, 0.4)' },
          { offset: 1, color: 'rgba(16, 185, 129, 0.05)' }
        ])
      }
    }],
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const hourLabel = params[0].name
        const value = params[0].value
        return `<div style="font-size:12px;font-weight:600">${hourLabel}</div>
                <div style="font-size:11px;color:#64748b">完成任务: ${value}</div>`
      }
    }
  }

  miniChart.setOption(option)
}

// === API 方法 ===

const loadStats = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/tasks/summary`)
    if (response.ok) {
      statsData.value = await response.json()
    } else {
      throw new Error('获取统计失败')
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
    ElMessage.error('获取统计数据失败')
    statsData.value = {}
  }
}

const loadTaskTypeStats = async () => {
  try {
    const statuses = ['SUCCEED', 'RUNNING', 'WAITING', 'FAILED', 'CANCELED']
    const promises = statuses.map(status =>
        fetch(`${API_BASE_URL}/tasks?status=${status}&limit=200`)
            .then(r => r.ok ? r.json() : [])
            .catch(() => [])
    )

    const results = await Promise.all(promises)
    const taskMap = new Map()
    results.flat().forEach(task => {
      if (task && task.id && !taskMap.has(task.id)) {
        taskMap.set(task.id, task)
      }
    })

    const typeCount = {}
    taskMap.forEach(task => {
      const type = task.taskType || 'UNKNOWN'
      typeCount[type] = (typeCount[type] || 0) + 1
    })

    const stats = Object.entries(typeCount).map(([type, count]) => {
      const config = taskTypeConfig[type] || {
        label: type,
        icon: Document,
        colorClass: 'gray',
        color: '#6b7280'
      }
      return {
        type,
        count,
        label: config.label,
        icon: config.icon,
        colorClass: config.colorClass,
        color: config.color
      }
    })

    stats.sort((a, b) => b.count - a.count)
    taskTypeStats.value = stats

  } catch (error) {
    console.error('获取任务类型统计失败:', error)
    taskTypeStats.value = []
  }
}

// === 修复后的趋势数据加载（关键修复版本）===
const loadTrendData = async (forceRefresh = false) => {
  trendLoading.value = true

  // 1. 优先从缓存加载（瞬时响应）
  const cache = !forceRefresh ? getTrendCache() : null
  if (cache && cache.rawTasks && cache.rawTasks.length > 0) {
    console.log(`从缓存加载: ${cache.rawTasks.length} 条任务`)
    // 直接使用缓存数据计算趋势
    processTrendData(cache.rawTasks, false)
    trendLoading.value = false
  }

  try {
    // 2. 并行获取所有状态数据（最大limit=200/状态，共800条）
    // 注意：如果某个状态超过200条，后端会截断，这是无法避免的
    const statuses = ['SUCCEED', 'RUNNING', 'WAITING', 'FAILED']
    const promises = statuses.map(status =>
        fetch(`${API_BASE_URL}/tasks?status=${status}&limit=200`)
            .then(r => r.ok ? r.json() : [])
            .catch(() => [])
    )

    const results = await Promise.all(promises)
    const newTasks = results.flat()

    console.log(`API返回新数据: ${newTasks.length} 条`)

    // 3. 与缓存数据智能合并（基于ID去重，保留所有数据）
    let allTasks = newTasks
    if (cache && cache.rawTasks && cache.rawTasks.length > 0) {
      const taskMap = new Map()

      // 先添加新数据（优先，因为可能包含状态更新）
      newTasks.forEach(t => {
        if (t && t.id) taskMap.set(t.id, t)
      })

      // 再添加缓存中的旧数据（补充被分页截断的历史数据）
      cache.rawTasks.forEach(t => {
        if (t && t.id && !taskMap.has(t.id)) {
          taskMap.set(t.id, t)
        }
      })

      allTasks = Array.from(taskMap.values())
      console.log(`合并后总数据: ${allTasks.length} 条 (新: ${newTasks.length}, 缓存: ${cache.rawTasks.length})`)
    }

    // 4. 处理趋势数据
    const isPartial = newTasks.length >= 800 // 如果每次都满800条，说明可能有数据被截断
    processTrendData(allTasks, true, isPartial)

    // 5. 更新缓存（异步，不阻塞UI）
    if (allTasks.length > 0) {
      setTimeout(() => {
        const cachedCount = setTrendCache(trendData.value, allTasks)
        console.log(`已缓存 ${cachedCount} 条任务`)
      }, 100)
    }

  } catch (error) {
    console.error('加载趋势数据失败:', error)
    if (!cache || !cache.rawTasks) {
      ElMessage.warning('趋势数据加载失败，请稍后重试')
    }
  } finally {
    trendLoading.value = false
  }
}

// 提取趋势数据处理逻辑
const processTrendData = (tasks, isFresh = false, isPartial = false) => {
  const targetDays = chartPeriod.value === 'month' ? 30 : 7

  // 本地时区日期格式化（关键修复：使用本地时间）
  const formatLocalDateKey = (date) => {
    if (!date || isNaN(date.getTime())) return null
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  // 生成日期范围（本地时间）
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const startDate = new Date(today)
  startDate.setDate(startDate.getDate() - targetDays + 1)

  const dates = []
  const dateMap = {}

  for (let i = targetDays - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const dateKey = formatLocalDateKey(d)
    const label = formatTrendDate(d, chartPeriod.value === 'month')

    dates.push({ key: dateKey, label, fullDate: d })
    dateMap[dateKey] = { SUCCEED: 0, RUNNING: 0, WAITING: 0, FAILED: 0 }
  }

  // 统计任务（修复时间字段逻辑）
  let processedCount = 0
  const validTasks = tasks.filter(t => t && t.id)

  validTasks.forEach(task => {
    // 根据状态选择对应的时间字段（关键修复）
    let taskDate = null

    if (task.status === 'SUCCEED' || task.status === 'FAILED') {
      // 已完成：优先使用完成时间（endTime）
      if (task.endTime) {
        taskDate = new Date(task.endTime)
      } else if (task.startTime) {
        taskDate = new Date(task.startTime)
      }
    } else if (task.status === 'RUNNING') {
      // 运行中：使用开始时间
      if (task.startTime) {
        taskDate = new Date(task.startTime)
      }
    } else if (task.status === 'WAITING') {
      // 等待中：使用创建时间
      if (task.createdAt) {
        taskDate = new Date(task.createdAt)
      }
    }

    if (!taskDate || isNaN(taskDate.getTime())) return

    // 只统计在目标日期范围内的任务
    if (taskDate < startDate || taskDate >= new Date(today.getTime() + 86400000)) {
      return
    }

    const dateKey = formatLocalDateKey(taskDate)
    if (dateMap[dateKey] && task.status) {
      const status = task.status.toUpperCase()
      if (dateMap[dateKey][status] !== undefined) {
        dateMap[dateKey][status]++
        processedCount++
      }
    }
  })

  // 更新趋势数据
  trendData.value = {
    dates: dates.map(d => d.label),
    succeed: dates.map(d => dateMap[d.key].SUCCEED),
    running: dates.map(d => dateMap[d.key].RUNNING),
    waiting: dates.map(d => dateMap[d.key].WAITING),
    failed: dates.map(d => dateMap[d.key].FAILED)
  }

  // 更新统计信息
  trendDataStats.value = {
    total: validTasks.length,
    loaded: processedCount,
    isPartial: isPartial || validTasks.length >= 800
  }

  console.log(`处理完成: ${processedCount} 条任务分布在趋势图中`)
  updateTrendChart()
}

// === 图表渲染优化 ===
const updateTrendChart = () => {
  if (!trendChart) return

  const isMonth = chartPeriod.value === 'month'
  const data = trendData.value

  if (!data.dates || data.dates.length === 0) {
    trendChart.clear()
    return
  }

  const option = {
    grid: {
      left: '2%',
      right: '3%',
      bottom: isMonth ? '15%' : '10%',
      top: '18%',
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      textStyle: { color: '#1e293b' },
      padding: [12, 16],
      formatter: function(params) {
        let result = `<div style="font-weight:700;margin-bottom:8px;font-size:14px">${params[0].name}</div>`
        let total = 0
        const colors = {
          '成功': '#10b981',
          '运行中': '#3b82f6',
          '等待中': '#f59e0b',
          '失败': '#ef4444'
        }

        params.forEach(item => {
          total += item.value || 0
          result += `<div style="display:flex;align-items:center;gap:8px;margin:6px 0;font-size:13px">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${item.color}"></span>
            <span style="flex:1;color:#64748b">${item.seriesName}:</span>
            <span style="font-weight:700;color:${colors[item.seriesName] || '#0f172a'};font-size:14px">${item.value || 0}</span>
          </div>`
        })

        const successRate = total > 0 ? ((params[0].value || 0) / total * 100).toFixed(1) : 0
        result += `<div style="margin-top:8px;padding-top:8px;border-top:1px solid #f1f5f9;font-size:12px;color:#94a3b8;display:flex;justify-content:space-between">
          <span>总计 ${total} 个任务</span>
          <span style="color:#10b981">成功率 ${successRate}%</span>
        </div>`
        return result
      }
    },
    legend: {
      data: ['成功', '运行中', '等待中', '失败'],
      top: '2%',
      right: '2%',
      icon: 'circle',
      itemGap: 15,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    xAxis: {
      type: 'category',
      data: data.dates,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: {
        color: '#64748b',
        fontSize: 11,
        interval: isMonth ? 4 : 0,
        rotate: isMonth ? 30 : 0,
        margin: 12
      },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
      axisLabel: { color: '#64748b', fontSize: 11 }
    },
    series: [
      {
        name: '成功',
        type: 'bar',
        stack: 'total',
        data: data.succeed,
        barWidth: '60%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#34d399' },
            { offset: 1, color: '#10b981' }
          ]),
          borderRadius: [0, 0, 0, 0]
        },
        emphasis: { focus: 'series' },
        animationDelay: (idx) => idx * 50
      },
      {
        name: '运行中',
        type: 'bar',
        stack: 'total',
        data: data.running,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#60a5fa' },
            { offset: 1, color: '#3b82f6' }
          ])
        },
        emphasis: { focus: 'series' },
        animationDelay: (idx) => idx * 50 + 100
      },
      {
        name: '等待中',
        type: 'bar',
        stack: 'total',
        data: data.waiting,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#fbbf24' },
            { offset: 1, color: '#f59e0b' }
          ])
        },
        emphasis: { focus: 'series' },
        animationDelay: (idx) => idx * 50 + 200
      },
      {
        name: '失败',
        type: 'bar',
        stack: 'total',
        data: data.failed,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#f87171' },
            { offset: 1, color: '#ef4444' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        emphasis: { focus: 'series' },
        animationDelay: (idx) => idx * 50 + 300
      }
    ],
    animationEasing: 'elasticOut',
    animationDelayUpdate: (idx) => idx * 5
  }

  trendChart.setOption(option, { notMerge: false, lazyUpdate: true })
}

// 初始化迷你图表
const initMiniChart = () => {
  if (!miniChartRef.value) return
  miniChart = echarts.init(miniChartRef.value)
  miniChart.setOption({
    grid: { top: 5, right: 5, bottom: 5, left: 5 },
    xAxis: { type: 'category', data: Array.from({length: 12}, (_, i) => `${i * 2}:00`), show: false },
    yAxis: { type: 'value', show: false },
    series: [{
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#3b82f6' },
          { offset: 1, color: '#10b981' }
        ]),
        width: 3
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(59, 130, 246, 0.4)' },
          { offset: 1, color: 'rgba(16, 185, 129, 0.05)' }
        ])
      }
    }]
  })
}

const fetchRecentTasks = async () => {
  taskLoading.value = true
  try {
    const params = new URLSearchParams()
    params.append('limit', '6')
    if (taskFilter.value !== 'all') {
      params.append('status', taskFilter.value.toUpperCase())
    }

    const response = await fetch(`${API_BASE_URL}/tasks?${params.toString()}`)

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }

    const data = await response.json()
    recentTasks.value = data || []
    lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN')

    updateNoticesFromTasks()
  } catch (error) {
    console.error('获取任务列表失败:', error)
    ElMessage.error('获取任务列表失败: ' + error.message)
    recentTasks.value = []
  } finally {
    taskLoading.value = false
  }
}

const retryTask = async (taskId) => {
  try {
    const response = await fetch(`${API_BASE_URL}/tasks/${taskId}/retry`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    })

    if (!response.ok) throw new Error('重试失败')

    const result = await response.json()
    ElMessage.success(`任务已重新提交，调度状态: ${result.dispatchStatus}`)
    fetchRecentTasks()
  } catch (error) {
    ElMessage.error('重试任务失败: ' + error.message)
  }
}

const cancelTask = async (task) => {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '确认取消', {
      confirmButtonText: '确定',
      cancelButtonText: '保留',
      type: 'warning'
    })

    const response = await fetch(`${API_BASE_URL}/tasks/${task.id}/cancel`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: 'CANCELED',
        reason: '用户手动取消'
      })
    })

    if (!response.ok) throw new Error('取消失败')

    ElMessage.success('任务已取消')
    fetchRecentTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消任务失败: ' + error.message)
    }
  }
}

const deleteTask = async (taskId) => {
  try {
    await ElMessageBox.confirm('删除后将无法恢复，确定继续吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'danger'
    })

    const response = await fetch(`${API_BASE_URL}/tasks/${taskId}`, {
      method: 'DELETE'
    })

    if (!response.ok) throw new Error('删除失败')

    ElMessage.success('任务已删除')
    fetchRecentTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除任务失败: ' + error.message)
    }
  }
}

// === 事件处理 ===

const handleTaskCommand = (command, task) => {
  switch(command) {
    case 'view':
      viewTaskDetail(task.id)
      break
    case 'retry':
      retryTask(task.id)
      break
    case 'cancel':
      cancelTask(task)
      break
    case 'delete':
      deleteTask(task.id)
      break
  }
}

const refreshAllData = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadStats(),
      fetchRecentTasks(),
      loadTrendData(true), // 强制刷新，忽略缓存
      loadTaskTypeStats(),
      loadTodayStats()
    ])
  } catch (error) {
    console.error('刷新数据失败:', error)
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  createDialog.visible = true
  currentStep.value = 0
  Object.assign(createDialog.form, {
    taskType: '',
    taskName: '',
    description: '',
    maxRetries: 3,
    priority: 0
  })
}

const nextStep = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    currentStep.value++
  } catch (e) {
    // validation failed
  }
}

const handleCreate = async () => {
  createDialog.loading = true
  try {
    const response = await fetch(`${API_BASE_URL}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(createDialog.form)
    })

    if (!response.ok) throw new Error('创建失败')

    ElMessage.success('任务创建成功')
    createDialog.visible = false
    fetchRecentTasks()
    loadTaskTypeStats()
    loadTodayStats()
  } catch (error) {
    ElMessage.error('创建失败: ' + error.message)
  } finally {
    createDialog.loading = false
  }
}

const handleCardClick = (key) => {
  if (key === 'backlogThreshold') return
  const statusMap = {
    waiting: 'WAITING',
    running: 'RUNNING',
    failed: 'FAILED',
    succeed: 'SUCCEED',
    canceled: 'CANCELED'
  }
  router.push({
    path: '/tasks',
    query: { status: statusMap[key], from: 'dashboard', t: Date.now() }
  })
}

const viewTaskDetail = (id) => {
  router.push(`/tasks/${id}`)
}

const handleNoticeClick = (notice) => {
  if (notice.route) {
    router.push(notice.route)
  }
}

const downloadReport = () => {
  ElMessage.success('报表下载中...')
}

// === 工具函数 ===

const getStatusClass = (status) => statusClassMap[status] || 'waiting'
const formatTaskType = (type) => taskTypeMap[type] || type

const calculateProgress = (task) => {
  if (task.status === 'SUCCEED') return 100
  if (task.status === 'FAILED') return 100
  if (!task.startTime) return 0
  const elapsed = Date.now() - new Date(task.startTime).getTime()
  const estimated = 60000
  return Math.min(Math.floor((elapsed / estimated) * 100), 95)
}

const getProgressColor = (task) => {
  const colors = {
    'RUNNING': '#3b82f6',
    'WAITING': '#f59e0b'
  }
  return colors[task.status] || '#10b981'
}

const calculateDuration = (startTime) => {
  const diff = Date.now() - new Date(startTime).getTime()
  const minutes = Math.floor(diff / 60000)
  const seconds = Math.floor((diff % 60000) / 1000)
  if (minutes > 0) return `${minutes}分${seconds}秒`
  return `${seconds}秒`
}

const formatDuration = (ms) => {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${Math.floor(ms/1000)}秒`
  return `${Math.floor(ms/60000)}分${Math.floor((ms%60000)/1000)}秒`
}

const truncateError = (msg) => {
  if (!msg) return ''
  return msg.length > 50 ? msg.substring(0, 50) + '...' : msg
}

const getTotal = (val) => {
  if (typeof val === 'object' && val !== null) {
    return Object.values(val).reduce((a, b) => a + (Number(b) || 0), 0)
  }
  return Number(val) || 0
}

const formatNumber = (num) => {
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return num.toString()
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  const now = new Date()
  const diff = (now - date) / 1000 / 60
  if (diff < 1) return '刚刚'
  if (diff < 60) return `${Math.floor(diff)}分钟前`
  if (diff < 1440) return `${Math.floor(diff / 60)}小时前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const formatTrendDate = (date, showMonth = false) => {
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)

  const d = new Date(date)
  d.setHours(0, 0, 0, 0)

  if (d.getTime() === today.getTime()) return '今天'
  if (d.getTime() === yesterday.getTime()) return '昨天'

  if (showMonth) {
    return `${d.getMonth() + 1}/${d.getDate()}`
  }

  return days[d.getDay()]
}

const updateNoticesFromTasks = () => {
  const newNotices = []

  const failedTasks = recentTasks.value.filter(t => t.status === 'FAILED')
  if (failedTasks.length > 0) {
    const latest = failedTasks[0]
    newNotices.push({
      title: '任务执行失败',
      content: `"${latest.taskName}" 失败: ${truncateError(latest.errorMessage)}`,
      time: '刚刚',
      type: 'danger',
      tagType: 'danger',
      tag: '需处理',
      clickable: true,
      route: `/tasks/${latest.id}`
    })
  }

  const runningCount = recentTasks.value.filter(t => t.status === 'RUNNING').length
  if (runningCount > 0) {
    newNotices.push({
      title: '任务正在运行',
      content: `当前有 ${runningCount} 个任务正在执行中`,
      time: '刚刚',
      type: 'primary',
      tagType: 'primary',
      tag: '运行中'
    })
  }

  const waitingCount = recentTasks.value.filter(t => t.status === 'WAITING').length
  if (waitingCount > 0) {
    newNotices.push({
      title: '任务排队中',
      content: `${waitingCount} 个任务正在等待调度`,
      time: '刚刚',
      type: 'warning',
      tagType: 'warning',
      tag: '队列'
    })
  }

  notices.value = newNotices
}

const initTrendChart = () => {
  if (!trendChartRef.value) return

  if (trendChart) {
    trendChart.dispose()
  }

  trendChart = echarts.init(trendChartRef.value)
  loadTrendData()
}

const handleResize = () => {
  if (trendChart) trendChart.resize()
  if (miniChart) miniChart.resize()
}

const handleScroll = () => {
  showFab.value = window.scrollY > 200
}

onMounted(async () => {
  // 检查是否有缓存
  const cache = getTrendCache()
  if (cache) {
    console.log(`启动时加载缓存: ${cache.rawTasks?.length || 0} 条任务`)
  }

  await refreshAllData()
  await nextTick()
  initTrendChart()
  initMiniChart()

  window.addEventListener('resize', handleResize)
  window.addEventListener('scroll', handleScroll)

  watch(chartPeriod, () => {
    loadTrendData()
  })

  // 降低自动刷新频率到5分钟
  const interval = setInterval(() => {
    fetchRecentTasks()
    loadTodayStats()
    loadTrendData(false) // 增量更新
  }, 300000)

  onUnmounted(() => {
    clearInterval(interval)
    if (trendChart) {
      trendChart.dispose()
      trendChart = null
    }
    if (miniChart) {
      miniChart.dispose()
      miniChart = null
    }
    window.removeEventListener('resize', handleResize)
    window.removeEventListener('scroll', handleScroll)
  })
})
</script>

<style scoped>
.modern-dashboard {
  padding: 24px;
  min-height: 100vh;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  position: relative;
  overflow-x: hidden;
}

/* 动态背景增强 */
.ambient-bg {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
  animation: float 20s infinite ease-in-out;
}

.orb-1 {
  width: 600px;
  height: 600px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  top: -200px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  bottom: -100px;
  left: -100px;
  animation-delay: -5s;
}

.orb-3 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  top: 40%;
  left: 30%;
  animation-delay: -10s;
}

.grid-pattern {
  position: absolute;
  inset: 0;
  background-image:
      linear-gradient(rgba(99, 102, 241, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(99, 102, 241, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: linear-gradient(to bottom, transparent, black 10%, black 90%, transparent);
}

@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
}

/* 章节标题优化 */
.section-header {
  position: relative;
  z-index: 1;
  margin: 32px 0 20px 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.section-icon.purple {
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  box-shadow: 0 4px 12px rgba(139, 92, 246, 0.3);
}

.section-title {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 4px 0;
  letter-spacing: -0.02em;
}

.section-desc {
  font-size: 13px;
  color: #64748b;
  margin: 0;
  font-weight: 500;
}

.scroll-hint {
  display: flex;
  align-items: center;
  color: #94a3b8;
  font-size: 13px;
  gap: 6px;
  animation: bounce 2s infinite;
}

.scroll-icon {
  font-size: 16px;
}

@keyframes bounce {
  0%, 100% { transform: translateX(0); }
  50% { transform: translateX(5px); }
}

/* Hero Section优化 */
.hero-section {
  position: relative;
  z-index: 1;
  margin-bottom: 32px;
  animation: fadeInDown 0.6s ease-out;
}

.hero-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 24px;
  padding: 32px;
  box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.05);
  position: relative;
  overflow: hidden;
}

.hero-content::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6, #ec4899);
}

.greeting {
  flex: 1;
}

.time-badge-wrapper {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.time-badge {
  display: inline-block;
  padding: 6px 14px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.live-pulse {
  position: relative;
  width: 8px;
  height: 8px;
}

.live-pulse .pulse-dot {
  position: absolute;
  inset: 0;
  background: #10b981;
  border-radius: 50%;
}

.live-pulse .pulse-ring {
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  border: 2px solid #10b981;
  animation: ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;
  opacity: 0;
}

@keyframes ping {
  75%, 100% { transform: scale(2); opacity: 0; }
}

.main-title {
  font-size: 36px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 8px 0;
  letter-spacing: -0.02em;
}

.gradient-text {
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 50%, #ec4899 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  font-size: 16px;
  color: #64748b;
  margin: 0;
  font-weight: 500;
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-left: 32px;
  border-left: 1px solid #e2e8f0;
}

.quick-stat {
  text-align: center;
}

.quick-stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
  margin-bottom: 4px;
}

.quick-stat-value.text-success {
  color: #10b981;
}

.quick-stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
}

.quick-stat-divider {
  width: 1px;
  height: 40px;
  background: #e2e8f0;
}

/* 统计卡片网格优化 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 0;
  position: relative;
  z-index: 1;
}

.stat-card {
  animation: fadeInUp 0.6s ease-out backwards;
  cursor: default;
}

.stat-card.clickable {
  cursor: pointer;
}

.card-glass {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  padding: 20px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.stat-card.clickable:hover .card-glass {
  transform: translateY(-4px);
  box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.95);
}

.card-shine {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent);
  transition: left 0.5s;
}

.stat-card:hover .card-shine {
  left: 100%;
}

.stat-visual {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.icon-container {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  position: relative;
  box-shadow: 0 8px 16px -4px rgba(0, 0, 0, 0.15);
}

.icon-container.waiting { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.icon-container.running { background: linear-gradient(135deg, #10b981, #059669); }
.icon-container.succeed { background: linear-gradient(135deg, #22c55e, #16a34a); }
.icon-container.failed { background: linear-gradient(135deg, #ef4444, #dc2626); }
.icon-container.canceled { background: linear-gradient(135deg, #6b7280, #4b5563); }
.icon-container.backlogThreshold { background: linear-gradient(135deg, #f59e0b, #d97706); }

.icon-ring {
  position: absolute;
  inset: -4px;
  border-radius: 16px;
  border: 2px solid transparent;
  opacity: 0.3;
}

.icon-container.running .icon-ring {
  animation: pulse-ring 2s infinite;
  border-color: #10b981;
}

@keyframes pulse-ring {
  0% { transform: scale(1); opacity: 0.3; }
  100% { transform: scale(1.2); opacity: 0; }
}

.trend-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 700;
  color: #10b981;
  background: rgba(16, 185, 129, 0.1);
  padding: 4px 8px;
  border-radius: 6px;
}

.stat-body {
  margin-top: auto;
}

.stat-value-wrapper {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 2px;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
  font-family: 'Inter', system-ui, sans-serif;
}

.stat-unit {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 6px;
}

.stat-detail {
  font-size: 11px;
}

/* 处理能力总览 - 横向滚动布局 */
.capability-wrapper {
  position: relative;
  z-index: 1;
  margin-bottom: 32px;
}

.capability-scroll-container {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 8px 4px;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.capability-scroll-container::-webkit-scrollbar {
  display: none;
}

.capability-card {
  flex: 0 0 200px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 16px;
  padding: 20px;
  animation: fadeInUp 0.6s ease-out backwards;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02);
  cursor: pointer;
}

.capability-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.1);
  background: rgba(255, 255, 255, 0.95);
}

.capability-card.more-card {
  flex: 0 0 120px;
  background: rgba(248, 250, 252, 0.6);
  border: 2px dashed #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.capability-card.more-card:hover {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
}

.capability-inner {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.capability-inner.center {
  align-items: center;
  justify-content: center;
  text-align: center;
}

.capability-main {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.capability-icon-wrapper {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  position: relative;
  flex-shrink: 0;
}

.icon-glow {
  position: absolute;
  inset: -2px;
  border-radius: 12px;
  opacity: 0.4;
  filter: blur(8px);
  z-index: -1;
}

.capability-icon-wrapper.blue { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.capability-icon-wrapper.blue .icon-glow { background: #3b82f6; }
.capability-icon-wrapper.cyan { background: linear-gradient(135deg, #06b6d4, #0891b2); }
.capability-icon-wrapper.cyan .icon-glow { background: #06b6d4; }
.capability-icon-wrapper.purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
.capability-icon-wrapper.purple .icon-glow { background: #8b5cf6; }
.capability-icon-wrapper.indigo { background: linear-gradient(135deg, #6366f1, #4f46e5); }
.capability-icon-wrapper.indigo .icon-glow { background: #6366f1; }
.capability-icon-wrapper.orange { background: linear-gradient(135deg, #f97316, #ea580c); }
.capability-icon-wrapper.orange .icon-glow { background: #f97316; }
.capability-icon-wrapper.pink { background: linear-gradient(135deg, #ec4899, #db2777); }
.capability-icon-wrapper.pink .icon-glow { background: #ec4899; }
.capability-icon-wrapper.teal { background: linear-gradient(135deg, #14b8a6, #0d9488); }
.capability-icon-wrapper.teal .icon-glow { background: #14b8a6; }
.capability-icon-wrapper.lime { background: linear-gradient(135deg, #84cc16, #65a30d); }
.capability-icon-wrapper.lime .icon-glow { background: #84cc16; }
.capability-icon-wrapper.gray { background: linear-gradient(135deg, #6b7280, #4b5563); }
.capability-icon-wrapper.gray .icon-glow { background: #6b7280; }

.capability-content {
  flex: 1;
  min-width: 0;
}

.capability-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.capability-value-wrapper {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.capability-value {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
  font-family: 'Inter', system-ui, sans-serif;
}

.capability-percent {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.capability-progress-bg {
  width: 100%;
  height: 4px;
  background: #e2e8f0;
  border-radius: 2px;
  overflow: hidden;
  margin-top: auto;
}

.capability-progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.capability-progress-fill.blue { background: linear-gradient(90deg, #3b82f6, #2563eb); }
.capability-progress-fill.cyan { background: linear-gradient(90deg, #06b6d4, #0891b2); }
.capability-progress-fill.purple { background: linear-gradient(90deg, #8b5cf6, #7c3aed); }
.capability-progress-fill.indigo { background: linear-gradient(90deg, #6366f1, #4f46e5); }
.capability-progress-fill.orange { background: linear-gradient(90deg, #f97316, #ea580c); }
.capability-progress-fill.pink { background: linear-gradient(90deg, #ec4899, #db2777); }
.capability-progress-fill.teal { background: linear-gradient(90deg, #14b8a6, #0d9488); }
.capability-progress-fill.lime { background: linear-gradient(90deg, #84cc16, #65a30d); }
.capability-progress-fill.gray { background: linear-gradient(90deg, #6b7280, #4b5563); }

.more-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(203, 213, 225, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  margin-bottom: 8px;
  transition: all 0.3s;
}

.capability-card.more-card:hover .more-icon {
  background: #3b82f6;
  color: white;
  transform: scale(1.1);
}

.more-text {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
}

/* 内容网格 */
.content-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
  position: relative;
  z-index: 1;
}

.content-column {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 玻璃卡片通用样式 */
.glass-card {
  background: rgba(255, 255, 255, 0.7) !important;
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.8) !important;
  border-radius: 20px !important;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02), 0 2px 4px -1px rgba(0, 0, 0, 0.02) !important;
  transition: box-shadow 0.3s ease;
  overflow: hidden;
}

.glass-card:hover {
  box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.08) !important;
}

.card-header-modern {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.header-title-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.title-icon.blue { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.title-icon.purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
.title-icon.orange { background: linear-gradient(135deg, #f97316, #ea580c); }
.title-icon.green { background: linear-gradient(135deg, #10b981, #059669); }
.title-icon.cyan { background: linear-gradient(135deg, #06b6d4, #0891b2); }

.card-title {
  font-size: 17px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 2px 0;
  letter-spacing: -0.01em;
}

.card-subtitle {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.header-actions-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.view-all {
  font-weight: 600;
  font-size: 13px;
}

.arrow-icon {
  transition: transform 0.3s ease;
}

.view-all:hover .arrow-icon {
  transform: translateX(4px);
}

/* 图表容器 */
.chart-container {
  min-height: 420px;
}

.data-warning {
  margin: 0 0 16px 0;
}

.trend-chart {
  height: 340px;
  width: 100%;
}

/* 今日速览卡片 - 优化样式 */
.today-card {
  background: linear-gradient(135deg, rgba(255,255,255,0.8) 0%, rgba(236, 253, 245, 0.4) 100%) !important;
}

.today-loading {
  padding: 20px;
}

.today-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 12px;
  background: rgba(241, 245, 249, 0.8);
  border-radius: 12px;
  animation: pulse 1.5s infinite;
}

.today-skeleton .skeleton-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #e2e8f0;
  flex-shrink: 0;
}

.today-skeleton .skeleton-text {
  flex: 1;
  height: 40px;
  border-radius: 6px;
  background: #e2e8f0;
}

.today-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.today-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.today-item:hover {
  background: rgba(255, 255, 255, 0.9);
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.today-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
  position: relative;
}

.today-icon::after {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 14px;
  opacity: 0.3;
  filter: blur(8px);
  z-index: -1;
}

.today-icon.blue { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.today-icon.blue::after { background: #3b82f6; }
.today-icon.orange { background: linear-gradient(135deg, #f97316, #ea580c); }
.today-icon.orange::after { background: #f97316; }
.today-icon.purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
.today-icon.purple::after { background: #8b5cf6; }

.today-info {
  flex: 1;
  min-width: 0;
}

.today-value {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
  margin-bottom: 2px;
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.today-value .unit {
  font-size: 14px;
  font-weight: 600;
  color: #64748b;
}

.today-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  margin-bottom: 4px;
}

.today-sub {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 500;
}

.today-trend {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
}

.trend-badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
}

.today-trend.up .trend-badge {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.today-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid rgba(226, 232, 240, 0.6);
}

.today-summary {
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.summary-value {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.today-actions {
  display: flex;
  gap: 8px;
}

.live-dot {
  margin-right: 4px;
  animation: pulse 2s infinite;
}

.mini-chart-container {
  height: 60px;
  margin: 8px 0;
}

.mini-chart {
  width: 100%;
  height: 100%;
}

/* 快捷操作 */
.actions-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: rgba(248, 250, 252, 0.5);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
  position: relative;
  overflow: hidden;
}

.action-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, transparent 0%, rgba(255,255,255,0.4) 100%);
  opacity: 0;
  transition: opacity 0.3s;
}

.action-item:hover::before {
  opacity: 1;
}

.action-item.primary:hover {
  background: rgba(59, 130, 246, 0.08);
  border-color: rgba(59, 130, 246, 0.2);
  transform: translateX(4px);
}

.action-item.success:hover {
  background: rgba(16, 185, 129, 0.08);
  border-color: rgba(16, 185, 129, 0.2);
  transform: translateX(4px);
}

.action-item.warning:hover {
  background: rgba(245, 158, 11, 0.08);
  border-color: rgba(245, 158, 11, 0.2);
  transform: translateX(4px);
}

.action-icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  position: relative;
  z-index: 1;
}

.action-item.primary .action-icon-wrapper { color: #3b82f6; }
.action-item.success .action-icon-wrapper { color: #10b981; }
.action-item.warning .action-icon-wrapper { color: #f59e0b; }

.action-info {
  flex: 1;
  position: relative;
  z-index: 1;
}

.action-title {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 2px;
  font-size: 14px;
}

.action-desc {
  font-size: 12px;
  color: #64748b;
  font-weight: 500;
}

.action-arrow {
  color: #cbd5e1;
  transition: all 0.3s;
  position: relative;
  z-index: 1;
}

.action-item:hover .action-arrow {
  transform: translateX(4px);
  color: #94a3b8;
}

/* 时间线 */
.timeline-modern {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.timeline-item {
  display: flex;
  gap: 16px;
  position: relative;
}

.timeline-item.clickable {
  cursor: pointer;
}

.timeline-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
  flex-shrink: 0;
}

.marker-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid white;
  box-shadow: 0 0 0 2px currentColor;
  margin-top: 4px;
}

.timeline-marker.primary { color: #3b82f6; }
.timeline-marker.success { color: #10b981; }
.timeline-marker.danger { color: #ef4444; }
.timeline-marker.warning { color: #f59e0b; }

.marker-line {
  width: 2px;
  flex: 1;
  background: #e2e8f0;
  margin-top: 8px;
}

.timeline-content {
  flex: 1;
  padding-bottom: 20px;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.timeline-title {
  font-weight: 700;
  color: #0f172a;
  font-size: 14px;
}

.timeline-time {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}

.timeline-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  margin: 0 0 8px 0;
}

/* 悬浮按钮（FAB） */
.fab-container {
  position: fixed;
  bottom: 32px;
  right: 32px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  opacity: 0;
  transform: translateY(20px);
  pointer-events: none;
  transition: all 0.3s ease;
}

.fab-container.fab-visible {
  opacity: 1;
  transform: translateY(0);
  pointer-events: all;
}

.fab-btn {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #3b82f6, #2563eb) !important;
  border: none !important;
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.4);
  transition: all 0.3s ease;
}

.fab-btn:hover {
  transform: scale(1.1) rotate(90deg);
  box-shadow: 0 8px 24px rgba(59, 130, 246, 0.5);
}

.fab-tooltip {
  background: rgba(15, 23, 42, 0.9);
  color: white;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.3s ease;
  pointer-events: none;
  white-space: nowrap;
}

.fab-container:hover .fab-tooltip {
  opacity: 1;
  transform: translateY(0);
}

/* 任务列表样式 */
.task-skeleton {
  padding: 16px;
}

.skeleton-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  margin-bottom: 12px;
  background: rgba(241, 245, 249, 0.5);
  border-radius: 12px;
  animation: pulse 2s infinite;
}

.skeleton-status {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #e2e8f0;
  flex-shrink: 0;
}

.skeleton-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-line {
  height: 12px;
  background: #e2e8f0;
  border-radius: 4px;
  width: 60%;
}

.skeleton-line.short {
  width: 30%;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.modern-task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: rgba(248, 250, 252, 0.5);
  border-radius: 16px;
  border: 1px solid transparent;
  border-left: 3px solid transparent;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  animation: fadeInLeft 0.5s ease-out backwards;
  position: relative;
}

.task-row.has-error {
  border-left-color: #ef4444;
  background: rgba(254, 226, 226, 0.3);
}

.task-row:hover {
  background: white;
  border-color: #e2e8f0;
  box-shadow: 0 4px 12px -2px rgba(0, 0, 0, 0.05);
  transform: translateX(4px);
}

.task-status-indicator {
  position: relative;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  position: relative;
  z-index: 2;
}

.task-status-indicator.waiting .status-dot { background: #94a3b8; }
.task-status-indicator.running .status-dot { background: #3b82f6; }
.task-status-indicator.succeed .status-dot { background: #10b981; }
.task-status-indicator.failed .status-dot { background: #ef4444; }
.task-status-indicator.canceled .status-dot { background: #6b7280; }

.status-icon {
  position: absolute;
  z-index: 3;
  color: white;
  font-size: 16px;
}

.pulse-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid transparent;
  opacity: 0;
}

.task-status-indicator.running .pulse-ring {
  border-color: #3b82f6;
  animation: ping 1.5s cubic-bezier(0, 0, 0.2, 1) infinite;
  opacity: 1;
}

@keyframes ping {
  75%, 100% { transform: scale(2); opacity: 0; }
}

.task-content {
  flex: 1;
  min-width: 0;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.task-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-tags {
  display: flex;
  gap: 8px;
  align-items: center;
}

.task-type {
  flex-shrink: 0;
  font-weight: 500;
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
  border: none;
}

.retry-tag {
  font-size: 11px;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.meta-divider {
  color: #cbd5e1;
}

.task-id {
  font-family: monospace;
  font-size: 12px;
  color: #94a3b8;
}

.paper-ref {
  color: #3b82f6;
  font-weight: 500;
}

.error-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: #ef4444;
  background: rgba(254, 226, 226, 0.5);
  padding: 6px 10px;
  border-radius: 6px;
  max-width: fit-content;
}

.error-text {
  font-weight: 500;
}

.task-progress {
  width: 60px;
  margin-right: 8px;
}

.mini-progress :deep(.el-progress-bar__outer) {
  border-radius: 3px;
  background-color: #e2e8f0;
}

.task-operations {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-menu-btn {
  padding: 8px;
  color: #94a3b8;
}

.action-menu-btn:hover {
  color: #64748b;
  background: rgba(241, 245, 249, 0.8);
}

.task-list-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-top: 1px solid #e2e8f0;
  background: rgba(248, 250, 252, 0.5);
  margin: 0 -20px -20px;
  border-radius: 0 0 16px 16px;
}

.last-update {
  font-size: 12px;
  color: #94a3b8;
}

/* 空状态 */
.modern-empty {
  padding: 60px 20px;
}

.floating-icons {
  position: relative;
  display: inline-block;
  margin-bottom: 20px;
}

.float-icon {
  position: absolute;
  right: -10px;
  bottom: -5px;
  animation: float-icon 3s ease-in-out infinite;
}

@keyframes float-icon {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}

.create-btn {
  margin-top: 8px;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

:deep(.danger-item) {
  color: #ef4444;
}
:deep(.danger-item:hover) {
  color: #dc2626;
  background: rgba(254, 226, 226, 0.3);
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #64748b;
}

.empty-state.small {
  padding: 40px 20px;
}

.empty-illustration {
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-state h4 {
  margin: 0 0 8px 0;
  color: #334155;
  font-size: 18px;
}

.empty-state p {
  margin: 0 0 24px 0;
  font-size: 14px;
}

/* 对话框样式 */
.modern-dialog :deep(.el-dialog__header) {
  padding: 24px 24px 16px;
  margin-right: 0;
  border-bottom: 1px solid #e2e8f0;
}

.modern-dialog :deep(.el-dialog__title) {
  font-weight: 700;
  font-size: 20px;
  color: #0f172a;
}

.modern-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.dialog-body {
  padding: 24px;
}

.form-steps {
  margin-bottom: 24px;
}

.task-type-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.type-option {
  padding: 20px;
  border: 2px solid #e2e8f0;
  border-radius: 16px;
  cursor: pointer;
  text-align: center;
  transition: all 0.3s;
  background: white;
}

.type-option:hover {
  border-color: #cbd5e1;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.type-option.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.05);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.type-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
  color: white;
}

.type-icon.blue { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.type-icon.purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
.type-icon.orange { background: linear-gradient(135deg, #f97316, #ea580c); }
.type-icon.cyan { background: linear-gradient(135deg, #06b6d4, #0891b2); }

.type-label {
  font-weight: 600;
  color: #0f172a;
  font-size: 14px;
}

.dialog-footer-modern {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 0 0 8px 8px;
}

/* 动画 */
@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeInLeft {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* 响应式优化 */
@media (max-width: 1400px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 20px;
  }

  .side-column > * {
    margin-bottom: 0;
  }
}

@media (max-width: 768px) {
  .modern-dashboard {
    padding: 16px;
  }

  .hero-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 24px;
  }

  .hero-stats {
    padding-left: 0;
    border-left: none;
    width: 100%;
    justify-content: space-around;
    padding-top: 20px;
    border-top: 1px solid #e2e8f0;
  }

  .main-title {
    font-size: 28px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .capability-card {
    flex: 0 0 180px;
  }

  .side-column {
    grid-template-columns: 1fr;
  }

  .header-actions {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .task-row {
    flex-wrap: wrap;
  }

  .task-operations {
    width: 100%;
    justify-content: space-between;
    margin-top: 8px;
    padding-top: 12px;
    border-top: 1px solid #e2e8f0;
  }

  .task-progress {
    flex: 1;
    margin-right: 16px;
  }

  .fab-container {
    bottom: 20px;
    right: 20px;
  }
}
</style>
