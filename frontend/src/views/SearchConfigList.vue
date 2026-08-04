<template>
  <div class="search-config-modern">
    <!-- 顶部统计栏 - 可点击筛选 -->
    <div class="stats-header">
      <div
          class="stat-card total"
          :class="{ active: currentFilterType === 'all' }"
          @click="handleStatCardClick('all')"
      >
        <div class="stat-icon-wrapper">
          <div class="stat-icon-bg"></div>
          <el-icon class="stat-icon"><Collection /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ totalCount }}</div>
          <div class="stat-label">总配置数</div>
        </div>
        <div class="stat-active-indicator" v-if="currentFilterType === 'all'">
          <el-icon><Check /></el-icon>
        </div>
      </div>

      <div
          class="stat-card active"
          :class="{ active: currentFilterType === 'active' }"
          @click="handleStatCardClick('active')"
      >
        <div class="stat-icon-wrapper">
          <div class="stat-icon-bg"></div>
          <el-icon class="stat-icon"><VideoPlay /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ activeCount }}</div>
          <div class="stat-label">运行中</div>
        </div>
        <div class="stat-active-indicator" v-if="currentFilterType === 'active'">
          <el-icon><Check /></el-icon>
        </div>
      </div>

      <div
          class="stat-card paused"
          :class="{ active: currentFilterType === 'paused' }"
          @click="handleStatCardClick('paused')"
      >
        <div class="stat-icon-wrapper">
          <div class="stat-icon-bg"></div>
          <el-icon class="stat-icon"><VideoPause /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ pausedCount }}</div>
          <div class="stat-label">已停用</div>
        </div>
        <div class="stat-active-indicator" v-if="currentFilterType === 'paused'">
          <el-icon><Check /></el-icon>
        </div>
      </div>

      <div
          class="stat-card today"
          :class="{ active: currentFilterType === 'today' }"
          @click="handleStatCardClick('today')"
      >
        <div class="stat-icon-wrapper">
          <div class="stat-icon-bg"></div>
          <el-icon class="stat-icon"><Clock /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ todayRunCount }}</div>
          <div class="stat-label">今日执行</div>
        </div>
        <div class="stat-active-indicator" v-if="currentFilterType === 'today'">
          <el-icon><Check /></el-icon>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <el-card class="main-card" shadow="never" v-loading="loading">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-radio-group v-model="filterStatus" size="default" @change="handleFilterChange">
            <el-radio-button label="">全部配置</el-radio-button>
            <el-radio-button label="ACTIVE">
              <el-icon class="btn-icon"><CircleCheck /></el-icon>运行中
            </el-radio-button>
            <el-radio-button label="PAUSED">
              <el-icon class="btn-icon"><CircleClose /></el-icon>已停用
            </el-radio-button>
          </el-radio-group>

          <el-divider direction="vertical" />

          <el-input
              v-model="searchKeyword"
              placeholder="搜索配置名称、关键词或ID..."
              clearable
              prefix-icon="Search"
              @input="handleSearchInput"
              style="width: 300px"
          >
            <template #suffix>
              <span class="search-count" v-if="searchKeyword">
                {{ filteredList.length }} 个结果
              </span>
            </template>
          </el-input>

          <div v-if="currentFilterType !== 'all'" class="filter-tag">
            <el-tag closable @close="handleStatCardClick('all')" type="primary" effect="plain">
              {{ getFilterLabel }}
            </el-tag>
          </div>
        </div>

        <div class="toolbar-right">
          <el-tooltip content="刷新数据">
            <el-button :icon="Refresh" circle plain @click="loadData" :loading="loading" />
          </el-tooltip>
          <el-button type="primary" :icon="Plus" @click="showCreateDialog">
            新建配置
          </el-button>
        </div>
      </div>

      <!-- 批量操作栏 -->
      <div class="batch-operation-bar" v-if="selectedRows.length > 0">
        <div class="batch-info">
          <el-icon class="batch-icon"><Check /></el-icon>
          <span class="batch-text">已选择 <strong>{{ selectedRows.length }}</strong> 项</span>
          <el-button link type="primary" size="small" @click="clearSelection">取消选择</el-button>
        </div>
        <div class="batch-actions">
          <el-button
              size="small"
              type="success"
              :icon="VideoPlay"
              @click="handleBatchResume"
              :loading="batchLoading.resume"
              :disabled="!hasSelectedPaused"
          >
            批量启用
          </el-button>
          <el-button
              size="small"
              type="warning"
              :icon="VideoPause"
              @click="handleBatchPause"
              :loading="batchLoading.pause"
              :disabled="!hasSelectedActive"
          >
            批量停用
          </el-button>
          <el-button
              size="small"
              type="primary"
              :icon="Promotion"
              @click="handleBatchRun"
              :loading="batchLoading.run"
          >
            批量执行
          </el-button>
          <el-divider direction="vertical" />
          <el-button
              size="small"
              type="danger"
              :icon="Delete"
              plain
              @click="handleBatchDelete"
              :loading="batchLoading.delete"
          >
            批量删除
          </el-button>
        </div>
      </div>

      <!-- 数据表格 -->
      <div class="table-container" :class="{ 'has-batch-bar': selectedRows.length > 0 }">
        <el-table
            ref="tableRef"
            :data="paginatedList"
            stripe
            style="width: 100%"
            height="calc(100vh - 340px)"
            v-loading="tableLoading"
            row-key="id"
            @selection-change="handleSelectionChange"
            :header-cell-style="{ background: '#f8fafc', fontWeight: 600, color: '#475569' }"
        >
          <el-table-column type="selection" width="50" align="center" reserve-selection />

          <el-table-column prop="id" label="ID" width="80" align="center" sortable>
            <template #default="{ row }">
              <span class="id-badge">#{{ row.id }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="name" label="配置信息" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="config-info" @click="showDetail(row)">
                <div class="config-name">{{ row.name || `未命名配置 #${row.id}` }}</div>
                <div class="config-query" v-if="row.query">
                  <el-icon><Search /></el-icon>
                  {{ row.query }}
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="数据源" width="140" align="center">
            <template #default="{ row }">
              <div class="source-tags">
                <el-tag
                    v-for="(p, idx) in formatProviders(row.providers || [row.provider]).slice(0, 2)"
                    :key="idx"
                    :type="p.type"
                    size="small"
                    effect="plain"
                >
                  {{ p.label }}
                </el-tag>
                <el-tag v-if="(row.providers || []).length > 2" size="small" type="info" effect="plain">
                  +{{ (row.providers || []).length - 2 }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-switch
                  v-model="row.status"
                  :active-value="'ACTIVE'"
                  :inactive-value="'PAUSED'"
                  @change="(val) => handleStatusChange(row, val)"
                  :loading="row.statusLoading"
                  inline-prompt
                  active-text="运行"
                  inactive-text="停用"
                  style="--el-switch-on-color: #10b981; --el-switch-off-color: #94a3b8"
              />
            </template>
          </el-table-column>

          <el-table-column label="采集进度" width="160">
            <template #default="{ row }">
              <div class="progress-cell" v-if="row.totalPage > 0">
                <div class="progress-text">
                  <span class="page-num">{{ row.currentPage || 0 }}/{{ row.totalPage }}</span>
                  <span class="percent">{{ calculateProgress(row) }}%</span>
                </div>
                <el-progress
                    :percentage="calculateProgress(row)"
                    :stroke-width="4"
                    :show-text="false"
                    :color="progressColors"
                />
              </div>
              <div v-else class="no-progress">未开始</div>
            </template>
          </el-table-column>

          <el-table-column prop="cronExpression" label="执行计划" width="130">
            <template #default="{ row }">
              <div class="schedule-info">
                <el-icon><Timer /></el-icon>
                <span>{{ simplifyCron(row.cronExpression) }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="下次执行" width="150">
            <template #default="{ row }">
              <div class="next-run" :class="{ 'is-overdue': isOverdue(row.nextRunTime) }">
                <el-icon><Calendar /></el-icon>
                <span>{{ formatTime(row.nextRunTime) || '未设置' }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button-group>
                <el-button
                    link
                    type="primary"
                    size="small"
                    @click="handleEdit(row)"
                    title="编辑"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button
                    link
                    type="success"
                    size="small"
                    @click="handleRun(row)"
                    :loading="row.running"
                    title="立即执行"
                >
                  <el-icon><VideoPlay /></el-icon>
                </el-button>
                <el-button
                    link
                    type="danger"
                    size="small"
                    @click="handleDelete(row)"
                    title="删除"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-button-group>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-bar" v-if="filteredList.length > 0">
        <div class="pagination-info">
          <span v-if="selectedRows.length > 0" class="selected-hint">
            已选 {{ selectedRows.length }} 条，
          </span>
          显示 {{ (currentPage - 1) * pageSize + 1 }} - {{ Math.min(currentPage * pageSize, filteredList.length) }} 条，
          共 <strong>{{ filteredList.length }}</strong> 条记录
          <span v-if="filteredList.length !== totalCount && currentFilterType !== 'all'" class="filter-tip">
            （从总计 {{ totalCount }} 条中筛选）
          </span>
        </div>
        <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[20, 50, 100, 200]"
            :total="filteredList.length"
            layout="sizes, prev, pager, next, jumper"
            background
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>

      <!-- 空状态 -->
      <el-empty
          v-if="!loading && filteredList.length === 0"
          :description="getEmptyDescription"
          :image-size="120"
      >
        <el-button type="primary" @click="showCreateDialog" v-if="!searchKeyword && currentFilterType === 'all'">
          创建第一个配置
        </el-button>
        <el-button @click="handleStatCardClick('all')" v-else>
          查看全部配置
        </el-button>
      </el-empty>
    </el-card>

    <!-- 新增/编辑对话框 - 使用完整表单 -->
    <el-dialog
        v-model="dialog.visible"
        :title="dialog.isEdit ? '编辑检索配置' : '新建学术搜索配置'"
        width="750px"
        :close-on-click-modal="false"
        destroy-on-close
        class="config-dialog"
        top="5vh"
    >
      <div v-if="!dialog.isEdit" class="form-header-alert" style="margin-bottom: 20px;">
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

      <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="140px"
          label-position="right"
      >
        <!-- 配置名称 -->
        <el-form-item label="配置名称" prop="name">
          <el-input
              v-model="form.name"
              placeholder="例如：压裂液聚合物研究"
              clearable
              :disabled="dialog.isEdit"
          />
          <div class="form-tip">{{ dialog.isEdit ? '配置名称创建后不可修改' : '给这个搜索配置起一个容易识别的名称' }}</div>
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
        <el-form-item label="学术数据源" prop="providers" v-if="!dialog.isEdit">
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
        <el-form-item label="主数据源" prop="provider" v-if="!dialog.isEdit">
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
        <template v-if="dialog.isEdit">
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

        <el-form-item label="过滤器 (JSON)" v-if="!dialog.isEdit" prop="filters">
          <el-input
              v-model="form.filters"
              type="textarea"
              :rows="2"
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
        <template v-if="dialog.isEdit">
          <el-divider content-position="left">状态管理</el-divider>

          <el-form-item label="当前状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio-button label="ACTIVE">运行中</el-radio-button>
              <el-radio-button label="PAUSED">已暂停</el-radio-button>
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

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialog.visible = false" size="large">取消</el-button>
          <el-button type="info" plain @click="handleTest" v-if="!dialog.isEdit">
            <el-icon><VideoPlay /></el-icon> 测试示例
          </el-button>
          <el-button type="primary" @click="submitForm" :loading="dialog.loading" size="large">
            {{ dialog.isEdit ? '保存修改' : '创建配置并启动搜索' }}
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查询生成器弹窗 -->
    <el-dialog v-model="generatorVisible" title="🔮 智能查询生成器" width="750px" destroy-on-close>
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
                <span><el-icon><DataLine /></el-icon> 性能指标 (Boost)</span>
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

    <!-- 详情抽屉 -->
    <el-drawer
        v-model="drawer.visible"
        :title="drawer.data?.name || `配置详情 #${drawer.data?.id}`"
        size="550px"
        destroy-on-close
        class="detail-drawer"
    >
      <template #header="{ titleId, titleClass }">
        <div :id="titleId" :class="titleClass" style="display: flex; align-items: center; gap: 8px;">
          <el-tag :type="drawer.data?.status === 'ACTIVE' ? 'success' : 'info'">
            {{ drawer.data?.status === 'ACTIVE' ? '运行中' : '已停用' }}
          </el-tag>
          <span>{{ drawer.data?.name || `配置 #${drawer.data?.id}` }}</span>
        </div>
      </template>

      <div v-if="drawer.data" class="detail-content">
        <div class="detail-stats">
          <div class="detail-stat-item">
            <div class="detail-stat-value">{{ drawer.data.totalCount || 0 }}</div>
            <div class="detail-stat-label">已收集论文</div>
          </div>
          <div class="detail-stat-item">
            <div class="detail-stat-value">{{ calculateProgress(drawer.data) }}%</div>
            <div class="detail-stat-label">完成进度</div>
          </div>
          <div class="detail-stat-item">
            <div class="detail-stat-value">{{ drawer.stats?.emptyPageCount || 0 }}</div>
            <div class="detail-stat-label">空页计数</div>
          </div>
        </div>

        <el-divider />

        <el-descriptions :column="1" border size="large">
          <el-descriptions-item label="查询关键词">
            <code class="keyword-code">{{ drawer.data.query }}</code>
          </el-descriptions-item>

          <el-descriptions-item label="数据源">
            <el-tag
                v-for="p in (drawer.data.providers || [drawer.data.provider])"
                :key="p"
                size="small"
                effect="plain"
                style="margin-right: 8px;"
            >
              {{ p }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="采集进度">
            <div style="display: flex; align-items: center; gap: 12px;">
              <span>第 {{ drawer.data.currentPage || 0 }} / {{ drawer.data.totalPage || 0 }} 页</span>
              <el-progress
                  :percentage="calculateProgress(drawer.data)"
                  style="width: 150px;"
                  :status="calculateProgress(drawer.data) === 100 ? 'success' : ''"
              />
            </div>
          </el-descriptions-item>

          <el-descriptions-item label="执行计划">
            {{ simplifyCron(drawer.data.cronExpression) }}
            <el-tag size="small" type="info" style="margin-left: 8px;">
              {{ drawer.data.cronExpression }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="最后运行">
            {{ formatTime(drawer.data.lastRunTime) || '从未执行' }}
          </el-descriptions-item>

          <el-descriptions-item label="下次执行">
            <span :class="{ 'overdue-text': isOverdue(drawer.data.nextRunTime) }">
              {{ formatTime(drawer.data.nextRunTime) || '未设置' }}
            </span>
          </el-descriptions-item>

          <el-descriptions-item label="创建时间" v-if="drawer.data.createdAt">
            {{ formatTime(drawer.data.createdAt) }}
          </el-descriptions-item>

          <el-descriptions-item label="最后失败" v-if="drawer.data.lastFailureReason">
            <el-alert :title="drawer.data.lastFailureReason" type="error" :closable="false" show-icon />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-button type="primary" @click="handleEdit(drawer.data); drawer.visible = false">
            <el-icon><Edit /></el-icon> 编辑配置
          </el-button>
          <el-button @click="handleRun(drawer.data)" :loading="drawer.data.running">
            <el-icon><VideoPlay /></el-icon> 立即执行
          </el-button>
          <el-button
              :type="drawer.data.status === 'ACTIVE' ? 'warning' : 'success'"
              @click="handleStatusChange(drawer.data, drawer.data.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE')"
          >
            <el-icon><component :is="drawer.data.status === 'ACTIVE' ? 'VideoPause' : 'VideoPlay'" /></el-icon>
            {{ drawer.data.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Plus,
  VideoPlay,
  VideoPause,
  Edit,
  Delete,
  Search,
  Collection,
  Clock,
  Timer,
  Calendar,
  Document,
  Reading,
  CircleCheck,
  CircleClose,
  QuestionFilled,
  Check,
  Promotion,
  MagicStick,
  Pouring,
  Money,
  DataLine,
  View,
  Loading,
  Cpu
} from '@element-plus/icons-vue'
import {
  getSearchConfigs,
  createSearchConfig,
  updateSearchConfig,
  deleteSearchConfig,
  getSearchConfigDetail,
  getSearchConfigStats,
  resumeSearchConfig,
  pauseSearchConfig,
  runSearchConfig
} from '@/api/searchConfig'

// 状态
const loading = ref(false)
const tableLoading = ref(false)
const configList = ref([])
const searchKeyword = ref('')
const filterStatus = ref('')
const currentPage = ref(1)
const pageSize = ref(50)
const tableRef = ref()
const currentFilterType = ref('all')

// 批量操作相关
const selectedRows = ref([])
const batchLoading = ref({
  resume: false,
  pause: false,
  delete: false,
  run: false
})

let searchTimer = null

// 进度条颜色
const progressColors = [
  { color: '#f56c6c', percentage: 20 },
  { color: '#e6a23c', percentage: 40 },
  { color: '#5b8ff9', percentage: 60 },
  { color: '#67c23a', percentage: 80 },
  { color: '#10b981', percentage: 100 }
]

// 对话框
const dialog = ref({
  visible: false,
  loading: false,
  isEdit: false,
  editId: null
})

const formRef = ref()
const form = reactive({
  name: '',
  query: '',
  providers: ['ARXIV', 'CORE', 'SEMANTIC_SCHOLAR'],
  provider: 'ARXIV',
  pageSize: 25,
  maxEmptyPages: 3,
  searchFields: ['title', 'abstract'],
  sortOrder: 'relevance',
  filters: '',
  cronExpression: '',
  status: 'ACTIVE',
  nextRunTime: null,
  lastFailureReason: '',
  currentPage: 0,
  totalPage: 0,
  emptyPageCount: 0,
  id: null
})

// 查询生成器
const generatorVisible = ref(false)
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

// 详情抽屉
const drawer = ref({
  visible: false,
  data: null,
  stats: null
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
      if (!dialog.isEdit && (!value || value.length === 0)) {
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

// 计算属性
const totalCount = computed(() => configList.value.length)
const activeCount = computed(() => configList.value.filter(item => item.status === 'ACTIVE').length)
const pausedCount = computed(() => configList.value.filter(item => item.status === 'PAUSED').length)
const todayRunCount = computed(() => {
  const today = new Date().toDateString()
  return configList.value.filter(item => {
    if (!item.lastRunTime) return false
    return new Date(item.lastRunTime).toDateString() === today
  }).length
})

const hasSelectedActive = computed(() => selectedRows.value.some(row => row.status === 'ACTIVE'))
const hasSelectedPaused = computed(() => selectedRows.value.some(row => row.status === 'PAUSED'))

const getFilterLabel = computed(() => {
  const labels = {
    'all': '全部配置',
    'active': '运行中',
    'paused': '已停用',
    'today': '今日执行'
  }
  return labels[currentFilterType.value] || ''
})

const getEmptyDescription = computed(() => {
  if (searchKeyword.value) return '未找到匹配的配置'
  const descriptions = {
    'all': '暂无配置数据',
    'active': '暂无运行中的配置',
    'paused': '暂无已停用的配置',
    'today': '今日暂无执行记录'
  }
  return descriptions[currentFilterType.value] || '暂无数据'
})

const filteredList = computed(() => {
  let list = [...configList.value]

  if (currentFilterType.value === 'active') {
    list = list.filter(item => item.status === 'ACTIVE')
  } else if (currentFilterType.value === 'paused') {
    list = list.filter(item => item.status === 'PAUSED')
  } else if (currentFilterType.value === 'today') {
    const today = new Date().toDateString()
    list = list.filter(item => {
      if (!item.lastRunTime) return false
      return new Date(item.lastRunTime).toDateString() === today
    })
  }

  if (filterStatus.value && currentFilterType.value !== 'active' && currentFilterType.value !== 'paused') {
    list = list.filter(item => item.status === filterStatus.value)
  }

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase().trim()
    if (keyword) {
      list = list.filter(item =>
          (item.name && item.name.toLowerCase().includes(keyword)) ||
          (item.query && item.query.toLowerCase().includes(keyword)) ||
          (item.id && item.id.toString().includes(keyword))
      )
    }
  }

  return list
})

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredList.value.slice(start, end)
})

// 工具函数
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

// 查询生成器方法
const showQueryGenerator = () => {
  generatorVisible.value = true
  generator.previewQueries = []
}

const showCronHelp = () => {
  ElMessage.info('Cron 格式：秒 分 时 日 月 周。例如：0 0 * * * ? 表示每天0点')
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

// 模拟执行进度
const simulateExecutionProgress = (configId) => {
  executionDialog.visible = true
  executionDialog.status = 'running'
  executionDialog.progress = 10
  executionDialog.message = '正在连接学术数据库...'
  executionDialog.configId = configId

  const providers = dialog.isEdit ? (form.providers || [form.provider]) : form.providers
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

const handleExecutionComplete = () => {
  executionDialog.visible = false
  dialog.visible = false
  loadData()
  if (executionDialog.status === 'completed') {
    ElMessage.success('配置已创建并启动搜索')
  }
}

// 构建请求数据
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

// 表单提交
const submitForm = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch (e) {
    ElMessage.error('请检查表单填写是否正确')
    return
  }

  dialog.value.loading = true

  try {
    if (dialog.value.isEdit) {
      const updateData = buildUpdateRequest()
      await updateSearchConfig(dialog.value.editId, updateData)
      ElMessage.success('配置已更新')
      dialog.value.visible = false
      loadData()
    } else {
      const requestData = buildCreateRequest()
      const { data } = await createSearchConfig(requestData)

      if (!data || !data.id) {
        throw new Error('后端返回数据格式错误：缺少 id 字段')
      }

      ElMessage.success(`配置已创建（ID: ${data.id}）`)

      // 自动触发执行
      try {
        await runSearchConfig(data.id)
        simulateExecutionProgress(data.id)
      } catch (runError) {
        console.error('启动搜索执行失败:', runError)
        ElMessage.warning('配置已保存，但自动搜索启动失败，请手动执行')
        setTimeout(() => {
          dialog.value.visible = false
          loadData()
        }, 1500)
      }
    }
  } catch (error) {
    console.error('操作失败:', error)
    ElMessage.error(error.message || '操作失败')
  } finally {
    dialog.value.loading = false
  }
}

// 其他方法（保持原有逻辑）
const handleStatCardClick = (type) => {
  currentFilterType.value = type
  currentPage.value = 1
  clearSelection()

  if (type === 'active') {
    filterStatus.value = 'ACTIVE'
  } else if (type === 'paused') {
    filterStatus.value = 'PAUSED'
  } else {
    filterStatus.value = ''
  }

  const messages = {
    'all': '显示全部配置',
    'active': '筛选：运行中的配置',
    'paused': '筛选：已停用的配置',
    'today': '筛选：今日执行的配置'
  }
  ElMessage.info(messages[type])
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

const clearSelection = () => {
  tableRef.value?.clearSelection()
  selectedRows.value = []
}

const handleSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    clearSelection()
  }, 300)
}

const handleFilterChange = () => {
  currentPage.value = 1
  clearSelection()
  if (filterStatus.value === 'ACTIVE') {
    currentFilterType.value = 'active'
  } else if (filterStatus.value === 'PAUSED') {
    currentFilterType.value = 'paused'
  } else if (currentFilterType.value !== 'today') {
    currentFilterType.value = 'all'
  }
}

// 批量操作方法
const handleBatchResume = async () => {
  const targets = selectedRows.value.filter(row => row.status === 'PAUSED')
  if (targets.length === 0) {
    ElMessage.warning('选中的配置中没有已停用的项目')
    return
  }

  batchLoading.value.resume = true
  let successCount = 0
  let failCount = 0

  try {
    await ElMessageBox.confirm(
        `确定要启用选中的 ${targets.length} 个配置吗？`,
        '批量启用',
        { confirmButtonText: '确定启用', cancelButtonText: '取消', type: 'success' }
    )

    for (const row of targets) {
      try {
        await resumeSearchConfig(row.id)
        row.status = 'ACTIVE'
        successCount++
      } catch (e) {
        failCount++
        console.error(`启用配置 ${row.id} 失败:`, e)
      }
    }

    if (successCount > 0) ElMessage.success(`成功启用 ${successCount} 个配置`)
    if (failCount > 0) ElMessage.error(`${failCount} 个配置启用失败`)

    clearSelection()
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('批量启用失败')
  } finally {
    batchLoading.value.resume = false
  }
}

const handleBatchPause = async () => {
  const targets = selectedRows.value.filter(row => row.status === 'ACTIVE')
  if (targets.length === 0) {
    ElMessage.warning('选中的配置中没有运行中的项目')
    return
  }

  batchLoading.value.pause = true
  let successCount = 0
  let failCount = 0

  try {
    await ElMessageBox.confirm(
        `确定要停用选中的 ${targets.length} 个配置吗？`,
        '批量停用',
        { confirmButtonText: '确定停用', cancelButtonText: '取消', type: 'warning' }
    )

    for (const row of targets) {
      try {
        await pauseSearchConfig(row.id)
        row.status = 'PAUSED'
        successCount++
      } catch (e) {
        failCount++
        console.error(`停用配置 ${row.id} 失败:`, e)
      }
    }

    if (successCount > 0) ElMessage.success(`成功停用 ${successCount} 个配置`)
    if (failCount > 0) ElMessage.error(`${failCount} 个配置停用失败`)

    clearSelection()
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('批量停用失败')
  } finally {
    batchLoading.value.pause = false
  }
}

const handleBatchDelete = async () => {
  batchLoading.value.delete = true

  try {
    await ElMessageBox.confirm(
        `确定要删除选中的 ${selectedRows.value.length} 个配置吗？此操作不可恢复！`,
        '批量删除',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' }
    )

    let successCount = 0
    let failCount = 0

    for (const row of selectedRows.value) {
      try {
        await deleteSearchConfig(row.id)
        successCount++
      } catch (e) {
        failCount++
        console.error(`删除配置 ${row.id} 失败:`, e)
      }
    }

    if (successCount > 0) ElMessage.success(`成功删除 ${successCount} 个配置`)
    if (failCount > 0) ElMessage.error(`${failCount} 个配置删除失败`)

    clearSelection()
    loadData()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('批量删除失败')
  } finally {
    batchLoading.value.delete = false
  }
}

const handleBatchRun = async () => {
  batchLoading.value.run = true

  try {
    await ElMessageBox.confirm(
        `确定要立即执行选中的 ${selectedRows.value.length} 个配置吗？`,
        '批量执行',
        { confirmButtonText: '确定执行', cancelButtonText: '取消', type: 'primary' }
    )

    let successCount = 0
    let failCount = 0

    for (const row of selectedRows.value) {
      try {
        await runSearchConfig(row.id)
        successCount++
      } catch (e) {
        failCount++
        console.error(`执行配置 ${row.id} 失败:`, e)
      }
    }

    if (successCount > 0) ElMessage.success(`成功启动 ${successCount} 个任务`)
    if (failCount > 0) ElMessage.error(`${failCount} 个任务启动失败`)

    clearSelection()
    setTimeout(loadData, 2000)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('批量执行失败')
  } finally {
    batchLoading.value.run = false
  }
}

const loadData = async () => {
  loading.value = true
  tableLoading.value = true
  try {
    const { data } = await getSearchConfigs(filterStatus.value || undefined)

    let rawData = []
    if (Array.isArray(data)) {
      rawData = data
    } else if (data && Array.isArray(data.data)) {
      rawData = data.data
    } else if (data && Array.isArray(data.records)) {
      rawData = data.records
    }

    configList.value = rawData.map(item => ({
      ...item,
      statusLoading: false,
      running: false
    }))

    currentPage.value = 1
    clearSelection()
    console.log(`已加载 ${rawData.length} 条配置数据`)
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载数据失败：' + (error.message || '未知错误'))
    configList.value = []
  } finally {
    loading.value = false
    tableLoading.value = false
  }
}

const handleStatusChange = async (row, newStatus) => {
  row.statusLoading = true
  try {
    if (newStatus === 'ACTIVE') {
      await resumeSearchConfig(row.id)
      ElMessage.success(`已启用配置 #${row.id}`)
    } else {
      await pauseSearchConfig(row.id)
      ElMessage.success(`已停用配置 #${row.id}`)
    }
    row.status = newStatus
    if (drawer.value.visible && drawer.value.data?.id === row.id) {
      drawer.value.data.status = newStatus
    }
  } catch (error) {
    ElMessage.error('操作失败：' + (error.message || '未知错误'))
    row.status = newStatus === 'ACTIVE' ? 'PAUSED' : 'ACTIVE'
  } finally {
    row.statusLoading = false
  }
}

const handleRun = async (row) => {
  row.running = true
  try {
    await runSearchConfig(row.id)
    ElMessage.success('检索任务已启动')
    setTimeout(() => refreshSingleConfig(row.id), 2000)
  } catch (error) {
    ElMessage.error('启动失败：' + (error.message || '未知错误'))
  } finally {
    row.running = false
  }
}

const refreshSingleConfig = async (id) => {
  try {
    const { data } = await getSearchConfigDetail(id)
    const index = configList.value.findIndex(item => item.id === id)
    if (index !== -1) {
      configList.value[index] = {
        ...configList.value[index],
        ...data,
        statusLoading: false,
        running: false
      }
    }
    if (drawer.value.visible && drawer.value.data?.id === id) {
      drawer.value.data = { ...drawer.value.data, ...data }
      loadStats(id)
    }
  } catch (e) {
    console.error('刷新配置失败', e)
  }
}

const showDetail = async (row) => {
  drawer.value.visible = true
  drawer.value.data = { ...row }
  drawer.value.stats = null
  try {
    const { data } = await getSearchConfigDetail(row.id)
    drawer.value.data = { ...drawer.value.data, ...data }
    loadStats(row.id)
  } catch (error) {
    ElMessage.error('加载详情失败：' + error.message)
  }
}

const loadStats = async (id) => {
  try {
    const { data } = await getSearchConfigStats(id)
    drawer.value.stats = data
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

const handleEdit = (row) => {
  dialog.value.isEdit = true
  dialog.value.editId = row.id
  dialog.value.visible = true

  // 重置表单数据
  Object.assign(form, {
    name: row.name || '',
    query: row.query || '',
    providers: row.providers || [row.provider] || ['ARXIV'],
    provider: row.provider || 'ARXIV',
    pageSize: row.pageSize || 25,
    maxEmptyPages: row.maxEmptyPages || 3,
    searchFields: row.searchFields ? row.searchFields.split(',').filter(Boolean) : ['title', 'abstract'],
    sortOrder: row.sortOrder || 'relevance',
    filters: row.filters || '',
    cronExpression: row.cronExpression || '',
    status: row.status || 'PAUSED',
    nextRunTime: row.nextRunTime || null,
    lastFailureReason: row.lastFailureReason || '',
    currentPage: row.currentPage || 0,
    totalPage: row.totalPage || 0,
    emptyPageCount: row.emptyPageCount || 0,
    id: row.id
  })

  // 恢复关键词簇配置
  if (row.querySnapshot) {
    try {
      const snapshot = JSON.parse(row.querySnapshot)
      if (snapshot.clusters) {
        Object.assign(generator.clusters, snapshot.clusters)
      }
      if (snapshot.templates) {
        Object.assign(generator.templates, snapshot.templates)
      }
    } catch (e) {
      console.warn('无法解析querySnapshot:', e)
    }
  }

  if (drawer.value.visible) drawer.value.visible = false
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确定要删除配置 "${row.name || '#' + row.id}" 吗？此操作不可恢复。`,
        '确认删除',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    await deleteSearchConfig(row.id)
    ElMessage.success('删除成功')
    const index = configList.value.findIndex(item => item.id === row.id)
    if (index !== -1) configList.value.splice(index, 1)
    if (drawer.value.visible && drawer.value.data?.id === row.id) {
      drawer.value.visible = false
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }
}

const showCreateDialog = () => {
  dialog.value.isEdit = false
  dialog.value.editId = null
  dialog.value.visible = true

  // 重置表单
  Object.assign(form, {
    name: '',
    query: '',
    providers: ['ARXIV', 'CORE', 'SEMANTIC_SCHOLAR'],
    provider: 'ARXIV',
    pageSize: 25,
    maxEmptyPages: 3,
    searchFields: ['title', 'abstract'],
    sortOrder: 'relevance',
    filters: '',
    cronExpression: '',
    status: 'ACTIVE',
    nextRunTime: null,
    lastFailureReason: '',
    currentPage: 0,
    totalPage: 0,
    emptyPageCount: 0,
    id: null
  })

  // 重置查询生成器
  generator.clusters.fluids = { maxTerms: 3, selectedTerms: [...defaultClusters.fluids.terms] }
  generator.clusters.polymers = { maxTerms: 4, selectedTerms: [...defaultClusters.polymers.terms] }
  generator.clusters.performance = { maxTerms: 4, selectedTerms: [...defaultClusters.performance.terms] }

  nextTick(() => formRef.value?.clearValidate())
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  clearSelection()
}

const handleCurrentChange = (val) => {
  currentPage.value = val
  clearSelection()
  const tableWrapper = document.querySelector('.el-table__body-wrapper')
  if (tableWrapper) tableWrapper.scrollTop = 0
}

// 工具函数
const formatProviders = (providers) => {
  if (!providers || !Array.isArray(providers)) return []
  const map = {
    'ARXIV': { label: 'ArXiv', type: 'danger' },
    'CORE': { label: 'CORE', type: 'success' },
    'SEMANTIC_SCHOLAR': { label: 'Semantic', type: 'warning' }
  }
  return providers.map(p => map[p] || { label: p, type: 'info' })
}

const calculateProgress = (row) => {
  if (!row || !row.totalPage || row.totalPage === 0) return 0
  return Math.min(100, Math.round(((row.currentPage || 0) / row.totalPage) * 100))
}

const simplifyCron = (cron) => {
  if (!cron) return '-'
  const map = {
    '0 0 * * * ?': '每小时',
    '0 0 0 * * ?': '每天 00:00',
    '0 0 0 ? * 1': '每周一',
    '0 0 0 1 * ?': '每月1日'
  }
  return map[cron] || '自定义'
}

const formatTime = (time) => {
  if (!time) return null
  const date = new Date(time)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  const isYesterday = new Date(now - 86400000).toDateString() === date.toDateString()

  if (isToday) return `今天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  if (isYesterday) return `昨天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`

  return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

const isOverdue = (time) => {
  if (!time) return false
  return new Date(time) < new Date()
}

// 监听搜索
watch(searchKeyword, () => handleSearchInput())

// 监听多选变化，自动设置主数据源
watch(() => form.providers, (newVal) => {
  if (!dialog.isEdit && newVal && newVal.length > 0) {
    if (!form.provider || !newVal.includes(form.provider)) {
      form.provider = newVal[0]
    }
  }
}, { immediate: true })

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.search-config-modern {
  padding: 20px;
  background: #f1f5f9;
  min-height: 100vh;
}

/* 顶部统计栏 - 可点击 */
.stats-header {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: white;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transition: all 0.3s ease;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  border: 2px solid transparent;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0,0,0,0.12);
}

.stat-card.active {
  border-color: currentColor;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.stat-card.total {
  border-left: 4px solid #3b82f6;
  color: #3b82f6;
}
.stat-card.total.active {
  background: #eff6ff;
  border-color: #3b82f6;
}

.stat-card.active {
  border-left: 4px solid #10b981;
  color: #10b981;
}
.stat-card.active.active {
  background: #ecfdf5;
  border-color: #10b981;
}

.stat-card.paused {
  border-left: 4px solid #64748b;
  color: #64748b;
}
.stat-card.paused.active {
  background: #f1f5f9;
  border-color: #64748b;
}

.stat-card.today {
  border-left: 4px solid #f59e0b;
  color: #f59e0b;
}
.stat-card.today.active {
  background: #fffbeb;
  border-color: #f59e0b;
}

.stat-icon-wrapper {
  position: relative;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-bg {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 12px;
  opacity: 0.15;
  transition: all 0.3s ease;
}

.stat-card.total .stat-icon-bg { background: linear-gradient(135deg, #3b82f6 0%, #60a5fa 100%); }
.stat-card.active .stat-icon-bg { background: linear-gradient(135deg, #10b981 0%, #34d399 100%); }
.stat-card.paused .stat-icon-bg { background: linear-gradient(135deg, #64748b 0%, #94a3b8 100%); }
.stat-card.today .stat-icon-bg { background: linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%); }

.stat-icon {
  font-size: 28px;
  z-index: 1;
  transition: transform 0.3s ease;
}

.stat-card:hover .stat-icon {
  transform: scale(1.1) rotate(5deg);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.stat-active-indicator {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: currentColor;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
  animation: scaleIn 0.3s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

@keyframes scaleIn {
  from { transform: scale(0); }
  to { transform: scale(1); }
}

/* 主卡片 */
.main-card {
  border-radius: 12px;
  border: none;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-tag {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateX(-10px); }
  to { opacity: 1; transform: translateX(0); }
}

.btn-icon {
  margin-right: 4px;
  font-size: 14px;
}

.search-count {
  font-size: 12px;
  color: #64748b;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 10px;
}

/* 批量操作栏 */
.batch-operation-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 12px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
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

.batch-info {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.batch-icon {
  font-size: 20px;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  padding: 4px;
}

.batch-text strong {
  font-size: 18px;
  margin: 0 4px;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.batch-actions .el-button {
  border: none;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.batch-actions .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.15);
}

/* 表格容器 */
.table-container {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  transition: margin-top 0.3s;
}

.id-badge {
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: #475569;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 13px;
}

.config-info {
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.2s;
}

.config-info:hover {
  background: #f8fafc;
}

.config-name {
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
  font-size: 14px;
}

.config-query {
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-tags {
  display: flex;
  gap: 4px;
  justify-content: center;
  flex-wrap: wrap;
}

.progress-cell {
  padding: 4px 0;
}

.progress-text {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 12px;
}

.page-num {
  color: #64748b;
}

.percent {
  font-weight: 600;
  color: #3b82f6;
}

.no-progress {
  color: #94a3b8;
  font-size: 13px;
}

.schedule-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #475569;
  font-size: 13px;
}

.next-run {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

.next-run.is-overdue {
  color: #ef4444;
}

/* 分页栏 */
.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.pagination-info {
  color: #64748b;
  font-size: 14px;
}

.selected-hint {
  color: #3b82f6;
  font-weight: 500;
}

.filter-tip {
  color: #94a3b8;
  margin-left: 8px;
  font-size: 13px;
}

/* 表单相关样式 */
.form-header-alert {
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
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

/* 详情抽屉 */
.detail-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.detail-stat-item {
  background: #f8fafc;
  border-radius: 12px;
  padding: 20px;
  text-align: center;
  border: 1px solid #e2e8f0;
}

.detail-stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #3b82f6;
  margin-bottom: 4px;
}

.detail-stat-label {
  font-size: 14px;
  color: #64748b;
}

.keyword-code {
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 4px;
  font-family: monospace;
  color: #ef4444;
}

.overdue-text {
  color: #ef4444;
  font-weight: 500;
}

.detail-actions {
  margin-top: 32px;
  display: flex;
  gap: 12px;
}

/* 响应式 */
@media (max-width: 1200px) {
  .stats-header {
    grid-template-columns: repeat(2, 1fr);
  }

  .batch-operation-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }

  .batch-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .stats-header {
    grid-template-columns: 1fr;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .toolbar-left {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-right {
    justify-content: flex-end;
  }

  .batch-actions {
    flex-wrap: wrap;
  }

  .pagination-bar {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
