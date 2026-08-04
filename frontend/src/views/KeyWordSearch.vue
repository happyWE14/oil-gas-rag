<template>
  <div class="keyword-search-container">
    <!-- 搜索筛选卡片 -->
    <el-card class="search-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-title-section">
            <span class="header-title">
              {{ isLibraryMode ? '📚 库内论文管理' : '🔍 跨平台论文检索中心' }}
            </span>
            <el-tag v-if="isFromSearchConfig" type="success" effect="dark" size="small">
              搜索配置: #{{ route.query.configId }}
            </el-tag>
            <el-tag v-else type="info" effect="plain" size="small" class="platform-tag">
              {{ isLibraryMode ? '查看已录入系统的论文' : '覆盖 ArXiv、CORE、Semantic Scholar' }}
            </el-tag>
          </div>
          <div class="header-actions">
            <el-radio-group v-model="viewMode" size="small" @change="handleViewModeChange">
              <el-radio-button label="search">实时搜索</el-radio-button>
              <el-radio-button label="library">库内论文</el-radio-button>
            </el-radio-group>
            <el-divider direction="vertical" />
            <el-tooltip content="设置本地存储路径" placement="bottom">
              <el-button size="small" :icon="Setting" circle @click="showPathConfig" />
            </el-tooltip>
            <el-tooltip content="刷新当前列表" placement="left">
              <el-button size="small" :icon="Refresh" circle @click="refreshSearch" />
            </el-tooltip>
            <el-button size="small" :icon="Plus" @click="openCreateDialog" type="primary">
              新增论文
            </el-button>
          </div>
        </div>
      </template>

      <el-form :model="searchForm" :inline="true" @submit.prevent="handleSearch" class="search-form">
        <el-form-item label="关键词" v-if="viewMode === 'search'">
          <el-input
              v-model="searchForm.query"
              placeholder="输入标题/作者/摘要关键词..."
              clearable
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
              style="width: 340px"
          >
            <template #append>
              <el-button @click="handleSearch" :loading="loading" :icon="Search">
                搜索
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="处理状态">
          <el-select v-model="searchForm.state" placeholder="全部状态" clearable style="width: 150px" @change="handleStateChange">
            <el-option
                v-for="state in paperStates"
                :key="state.value"
                :label="state.label"
                :value="state.value"
            >
              <div class="state-option">
                <el-tag :type="state.type" size="small" effect="light">{{ state.label }}</el-tag>
                <span class="state-desc">{{ state.desc }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="来源平台">
          <el-select v-model="searchForm.paperSource" placeholder="全部来源" clearable style="width: 150px">
            <el-option
                v-for="source in sourceOptions"
                :key="source.value"
                :label="source.label"
                :value="source.value"
            >
              <div class="source-option">
                <span class="platform-dot" :class="source.value.toLowerCase()"></span>
                {{ source.label }}
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button @click="resetSearch" :icon="RefreshRight">重置</el-button>
          <el-button
              v-if="selectedRows.length > 0 && viewMode === 'library'"
              type="success"
              @click="batchProcess"
              :loading="batchLoading"
              :icon="Download"
          >
            批量下载 ({{ selectedRows.length }})
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 本地路径配置提示 -->
      <el-alert
          v-if="localBasePath"
          :title="`本地存储路径: ${localBasePath} | 后端服务: ${backendBaseUrl}`"
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 10px;"
      />
    </el-card>

    <!-- 搜索结果列表 -->
    <el-card class="result-card" shadow="hover" v-loading="loading">
      <template #header>
        <div class="card-header">
          <div class="result-title-section">
            <span class="result-title">
              {{ viewMode === 'library' ? '📋 库内论文列表' : '📄 检索结果' }}
            </span>
            <el-tag type="info" size="small" effect="light" class="count-tag">
              {{ results?.papers?.total || pagination.total || 0 }} 条
            </el-tag>
          </div>
          <div class="header-filters" v-if="selectedRows.length > 0">
            <el-tag type="warning" effect="plain" size="small" closable @close="clearSelection">
              已选择 {{ selectedRows.length }} 篇
            </el-tag>
          </div>
        </div>
      </template>

      <!-- 常规表格 -->
      <el-table
          v-if="searchResults.length > 0"
          :data="searchResults"
          border
          stripe
          class="paper-table"
          @selection-change="handleSelectionChange"
          :row-key="(row) => row.paperId || row.id"
          highlight-current-row
          ref="tableRef"
      >
        <el-table-column v-if="viewMode === 'library'" type="selection" width="55" align="center" reserve-selection />

        <el-table-column label="论文信息" min-width="400" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="paper-info-cell">
              <div class="title-section">
                <el-link
                    @click="viewDetail(row)"
                    type="primary"
                    :underline="false"
                    class="paper-title"
                >
                  {{ row.title || '无标题' }}
                </el-link>
                <!-- 紧凑的状态标签 - 库内模式下不显示动态处理中 -->
                <div class="title-badges" v-if="!isLibraryMode">
                  <el-tag
                      v-if="isProcessing(row.state)"
                      size="small"
                      effect="dark"
                      type="primary"
                      class="compact-tag processing-tag"
                  >
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>处理中</span>
                  </el-tag>
                  <el-tag
                      v-else-if="row.state === 'FAILED' && isLocalFileExist(row)"
                      size="small"
                      effect="dark"
                      type="warning"
                      class="compact-tag"
                  >
                    <el-icon><Warning /></el-icon>
                    <span>文件存在</span>
                  </el-tag>
                </div>
              </div>

              <div class="meta-section">
                <div class="meta-badges">
                  <el-tag
                      size="small"
                      :type="getSourceType(row.paperSource)"
                      effect="plain"
                      class="source-badge"
                  >
                    {{ formatSource(row.paperSource) }}
                  </el-tag>

                  <span v-if="row.doi" class="meta-item doi" @click="openDoi(row.doi)">
                    <el-icon><Link /></el-icon> DOI
                  </span>

                  <span v-if="row.downloadUrl" class="meta-item pdf" title="PDF链接可用">
                    <el-icon><Document /></el-icon> PDF
                  </span>

                  <span v-if="row.yearPublished" class="meta-item year">
                    <el-icon><Calendar /></el-icon> {{ row.yearPublished }}
                  </span>
                </div>

                <!-- 仅在实时搜索模式下显示进度条 -->
                <div v-if="!isLibraryMode && isProcessing(row.state)" class="progress-mini">
                  <el-progress
                      :percentage="getProgress(row.state)"
                      :stroke-width="2"
                      :show-text="false"
                      :status="getProgressStatus(row.state)"
                  />
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="authors" label="作者" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="authors-cell">
              <el-icon><User /></el-icon>
              {{ formatAuthors(row.authors) }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="处理状态" width="130" align="center">
          <template #default="{ row }">
            <div class="status-cell">
              <!-- 实时搜索模式：正常显示处理中动画 -->
              <template v-if="!isLibraryMode">
                <el-tooltip
                    v-if="isProcessing(row.state)"
                    :content="getProcessHint(row.state)"
                    placement="top"
                >
                  <div class="status-badge processing">
                    <el-icon class="is-loading"><Loading /></el-icon>
                    <span>{{ formatState(row.state) }}</span>
                  </div>
                </el-tooltip>
                <el-tooltip
                    v-else-if="row.state === 'FAILED'"
                    :content="isLocalFileExist(row) ? '处理失败，但本地文件可能存在' : '处理失败，无本地文件'"
                    placement="top"
                >
                  <div :class="['status-badge', isLocalFileExist(row) ? 'warning' : 'error']">
                    <el-icon v-if="isLocalFileExist(row)"><Warning /></el-icon>
                    <el-icon v-else><CircleClose /></el-icon>
                    <span>{{ formatState(row.state) }}</span>
                  </div>
                </el-tooltip>
                <div v-else :class="['status-badge', getStateType(row.state)]">
                  <span>{{ formatState(row.state) }}</span>
                </div>
              </template>

              <!-- 库内模式：处理中状态显示为静态失败文字 -->
              <template v-else>
                <div :class="['status-badge', getStateType(row.state)]">
                  <span>{{ formatState(row.state) }}</span>
                </div>
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="本地存储" width="100" align="center">
          <template #default="{ row }">
            <div class="storage-cell">
              <template v-if="isLocalFileExist(row)">
                <el-tooltip content="点击通过后端服务查看PDF" placement="top">
                  <div class="storage-icon success" @click="handleLocalFileAction(row)">
                    <el-icon><CircleCheck /></el-icon>
                  </div>
                </el-tooltip>
              </template>
              <template v-else-if="!isLibraryMode && isProcessing(row.state)">
                <el-progress
                    type="circle"
                    :percentage="getProgress(row.state)"
                    :width="24"
                    :stroke-width="3"
                    class="mini-progress"
                />
              </template>
              <template v-else>
                <span class="not-stored">-</span>
              </template>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <!-- 库内模式下的处理中状态显示为可重新处理 -->
              <template v-if="isLibraryMode && isProcessing(row.state)">
                <el-button
                    size="small"
                    type="warning"
                    @click="retryPaper(row)"
                    :icon="RefreshRight"
                >
                  重新处理
                </el-button>
                <el-button size="small" @click="viewDetail(row)" :icon="View">
                  详情
                </el-button>
              </template>

              <!-- 正常模式或库内模式非处理中状态 -->
              <template v-else-if="!isProcessing(row.state)">
                <el-button
                    size="small"
                    :type="getProcessButtonType(row)"
                    @click="handleProcess(row)"
                    :loading="row.processing"
                    :icon="getProcessButtonIcon(row)"
                    :disabled="!isLocalFileExist(row) && !row.downloadUrl"
                >
                  {{ getProcessButtonText(row) }}
                </el-button>

                <el-button size="small" @click="viewDetail(row)" :icon="View">
                  详情
                </el-button>

                <el-dropdown trigger="click">
                  <el-button size="small" :icon="MoreFilled" style="padding: 5px 8px;" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="row.state === 'FAILED'" @click="retryPaper(row)">
                        <el-icon><RefreshRight /></el-icon> 重新处理
                      </el-dropdown-item>
                      <el-dropdown-item v-if="row.state === 'FAILED' && isLocalFileExist(row)" @click="forceRedownload(row)">
                        <el-icon><Download /></el-icon> 强制重新下载
                      </el-dropdown-item>
                      <el-dropdown-item @click="openFolder(row)" v-if="isLocalFileExist(row)">
                        <el-icon><FolderOpened /></el-icon> 打开所在文件夹
                      </el-dropdown-item>
                      <el-dropdown-item @click="copyPath(row)" v-if="isLocalFileExist(row)">
                        <el-icon><CopyDocument /></el-icon> 复制本地路径
                      </el-dropdown-item>
                      <el-dropdown-item @click="deletePaper(row)" type="danger" divided>
                        <el-icon><Delete /></el-icon> 删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>

              <!-- 实时搜索模式下的处理中状态 -->
              <div v-else class="processing-actions">
                <el-button
                    size="small"
                    type="warning"
                    plain
                    @click="cancelProcessing(row)"
                    :icon="VideoPause"
                >
                  停止
                </el-button>
                <el-button size="small" @click="viewDetail(row)" :icon="View">
                  查看
                </el-button>
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="(results?.papers?.total > 0 || pagination.total > 0)" class="pagination-wrapper">
        <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="results?.papers?.total || pagination.total || 0"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            background
        />
      </div>

      <el-empty
          v-if="!loading && searchResults.length === 0 && (searchForm.query || viewMode === 'library')"
          :description="viewMode === 'library' ? '暂无符合条件的论文' : '未找到相关论文，请尝试其他关键词'"
      >
        <el-button v-if="viewMode === 'search'" type="primary" @click="openCreateDialog" :icon="Plus">
          手动添加论文
        </el-button>
      </el-empty>

      <el-empty
          v-else-if="!loading && searchResults.length === 0 && viewMode === 'search'"
          description="请输入关键词开始检索，系统将搜索 ArXiv、CORE 和 Semantic Scholar"
      >
        <el-button @click="showQuickSearch" type="primary" :icon="Search">
          快速搜索示例
        </el-button>
      </el-empty>
    </el-card>

    <!-- 论文详情弹窗 -->
    <el-dialog
        v-model="detailVisible"
        :title="selectedPaper?.title || '论文详情'"
        width="900px"
        append-to-body
        destroy-on-close
        class="detail-dialog"
        :close-on-click-modal="false"
    >
      <!-- ... 弹窗内容保持不变 ... -->
      <div v-if="selectedPaper" class="paper-detail">
        <!-- 处理进度面板 - 仅在实时搜索模式且处理中显示 -->
        <div
            v-if="!isLibraryMode && isProcessing(selectedPaper.state)"
            class="process-panel active"
        >
          <div class="panel-header">
            <div class="header-left">
              <el-icon class="is-loading processing-icon"><Loading /></el-icon>
              <span class="panel-title">处理进度</span>
              <el-tag :type="getStateType(selectedPaper.state)" size="small" effect="dark">
                {{ formatState(selectedPaper.state) }}
              </el-tag>
            </div>
            <span class="progress-percent">{{ getProgress(selectedPaper.state) }}%</span>
          </div>

          <el-progress
              :percentage="getProgress(selectedPaper.state)"
              :status="getProgressStatus(selectedPaper.state)"
              :stroke-width="10"
              striped
              striped-flow
              :duration="3"
          />

          <div class="process-steps">
            <div
                v-for="(step, index) in processSteps"
                :key="step.value"
                class="step-item"
                :class="{
                'is-active': getCurrentStepIndex(selectedPaper.state) === index,
                'is-completed': getCurrentStepIndex(selectedPaper.state) > index,
                'is-waiting': getCurrentStepIndex(selectedPaper.state) < index
              }"
            >
              <div class="step-dot">
                <el-icon v-if="getCurrentStepIndex(selectedPaper.state) > index"><Check /></el-icon>
                <span v-else>{{ index + 1 }}</span>
              </div>
              <div class="step-label">{{ step.label }}</div>
            </div>
          </div>

          <div class="process-hint">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ getProcessHint(selectedPaper.state) }}</span>
          </div>
        </div>

        <!-- 库内模式下的处理中状态提示（静态） -->
        <div
            v-if="isLibraryMode && isRawProcessingState(selectedPaper.state)"
            class="process-panel"
            style="background: #fef0f0; border-color: #f56c6c;"
        >
          <div class="panel-header">
            <div class="header-left">
              <el-icon style="color: #f56c6c; font-size: 18px;"><CircleClose /></el-icon>
              <span class="panel-title" style="color: #f56c6c;">处理中断</span>
              <el-tag type="danger" size="small" effect="dark">
                {{ formatState(selectedPaper.state) }}
              </el-tag>
            </div>
          </div>
          <div style="margin-top: 12px; padding: 12px; background: rgba(255,255,255,0.6); border-radius: 6px;">
            <p style="margin: 0; color: #606266; font-size: 13px;">
              该论文处理过程中断，可能原因：下载失败、转换超时或向量化错误。建议点击"重新处理"按钮重试。
            </p>
          </div>
        </div>

        <!-- FAILED 状态但文件存在警告面板 -->
        <div v-if="selectedPaper.state === 'FAILED' && isLocalFileExist(selectedPaper)" class="failed-file-panel">
          <div class="file-warning">
            <el-icon class="warning-icon"><Warning /></el-icon>
            <div class="warning-content">
              <div class="warning-title">处理失败，但本地文件存在</div>
              <div class="warning-desc">论文下载可能已完成，但在后续处理（PDF转文本/向量化）阶段失败。</div>
            </div>
          </div>
        </div>

        <!-- 本地文件信息面板（后端代理版） -->
        <div v-if="isLocalFileExist(selectedPaper)" class="local-file-panel">
          <div class="file-info">
            <div class="file-status">
              <el-icon class="success-icon"><CircleCheck /></el-icon>
              <div class="file-meta">
                <div class="file-title">PDF 已存储在本地</div>
                <!-- 显示后端访问链接（蓝色高亮） -->
                <div class="file-path backend-url" @click="handleLocalFileAction(selectedPaper)" style="cursor: pointer;">
                  <el-icon><Link /></el-icon>
                  访问链接: {{ getLocalFileUrl(selectedPaper) }}
                </div>
                <!-- 显示实际本地路径（灰色小字） -->
                <div class="file-path local-path-display">
                  <el-icon><Folder /></el-icon>
                  本地路径: {{ getLocalFilePath(selectedPaper) }}
                </div>
                <div v-if="selectedPaper.downloadInfo?.fileSize" class="file-size">
                  文件大小: {{ formatFileSize(selectedPaper.downloadInfo.fileSize) }}
                </div>
              </div>
            </div>
          </div>
          <div class="file-actions">
            <el-button type="primary" @click="handleLocalFileAction(selectedPaper)">
              <el-icon><View /></el-icon>
              查看 PDF
            </el-button>
            <el-button @click="copyPath(selectedPaper)">
              <el-icon><CopyDocument /></el-icon>
              复制路径
            </el-button>
            <el-button @click="openFolder(selectedPaper)">
              <el-icon><FolderOpened /></el-icon>
              打开文件夹
            </el-button>
          </div>
        </div>

        <!-- 论文元信息 -->
        <div class="detail-content">
          <div class="detail-section">
            <h3 class="section-title">基本信息</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="论文ID" width="120">
                <code class="id-code">{{ selectedPaper.paperId }}</code>
              </el-descriptions-item>
              <el-descriptions-item label="来源平台">
                <el-tag :type="getSourceType(selectedPaper.paperSource)" size="small">
                  {{ formatSource(selectedPaper.paperSource) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="发表年份">
                <el-icon><Calendar /></el-icon> {{ selectedPaper.yearPublished || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="DOI">
                <el-link
                    v-if="selectedPaper.doi"
                    :href="`https://doi.org/${selectedPaper.doi}`"
                    target="_blank"
                    type="primary"
                >
                  {{ selectedPaper.doi }}
                </el-link>
                <span v-else>-</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <div class="detail-section">
            <h3 class="section-title">作者信息</h3>
            <div class="authors-list">
              <el-tag
                  v-for="(author, idx) in parseAuthors(selectedPaper.authors)"
                  :key="idx"
                  size="small"
                  effect="plain"
                  class="author-tag"
              >
                {{ author }}
              </el-tag>
            </div>
          </div>

          <div class="detail-section" v-if="selectedPaper.materials?.length > 0">
            <h3 class="section-title">提取材料</h3>
            <div class="materials-list">
              <el-tag
                  v-for="m in selectedPaper.materials"
                  :key="m.material"
                  type="success"
                  effect="light"
                  class="material-tag"
              >
                {{ m.material }}
                <span v-if="m.property" class="property-text">({{ m.property }})</span>
              </el-tag>
            </div>
          </div>

          <div class="detail-section">
            <h3 class="section-title">摘要</h3>
            <div class="abstract-content">
              {{ selectedPaper.abstractContent || '暂无摘要' }}
            </div>
          </div>

          <div class="detail-section" v-if="selectedPaper.downloadUrl">
            <h3 class="section-title">外部链接</h3>
            <el-link :href="selectedPaper.downloadUrl" target="_blank" type="info">
              <el-icon><Link /></el-icon> {{ selectedPaper.downloadUrl }}
            </el-link>
          </div>
        </div>

        <div class="detail-actions">
          <el-button @click="detailVisible = false">关闭</el-button>
          <!-- 库内模式下处理中状态显示重新处理按钮 -->
          <el-button
              v-if="isLibraryMode && isRawProcessingState(selectedPaper.state)"
              type="primary"
              @click="retryPaper(selectedPaper)"
              :icon="RefreshRight"
          >
            重新处理
          </el-button>
          <el-button
              v-else-if="selectedPaper.state === 'FAILED'"
              type="warning"
              @click="retryPaper(selectedPaper)"
              :icon="RefreshRight"
          >
            重新处理
          </el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 路径配置弹窗 -->
    <el-dialog
        v-model="pathConfigVisible"
        title="配置本地存储路径"
        width="500px"
    >
      <el-alert
          title="配置本地论文存储路径和后端服务地址"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 20px"
      />
      <el-form :model="pathConfigForm" label-width="120px">
        <el-form-item label="本地基础路径">
          <el-input
              v-model="pathConfigForm.basePath"
              placeholder="例如：D:\papers 或 /data/papers"
          />
          <div class="form-hint">请填写后端能够访问的论文目录</div>
        </el-form-item>
        <el-form-item label="后端服务地址">
          <el-input
              v-model="pathConfigForm.backendUrl"
              placeholder="留空表示与前端同源"
          />
          <div class="form-hint">用于代理访问本地文件的后端API地址</div>
        </el-form-item>
        <el-form-item label="文件命名格式">
          <el-radio-group v-model="pathConfigForm.namingPattern">
            <el-radio label="id">paper{ID}.pdf</el-radio>
            <el-radio label="id_underscore">paper_{ID}.pdf</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pathConfigVisible = false">取消</el-button>
        <el-button type="primary" @click="savePathConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增论文弹窗 -->
    <el-dialog
        v-model="createVisible"
        title="新增论文"
        width="750px"
        destroy-on-close
        :close-on-click-modal="false"
    >
      <el-alert
          title="系统将自动下载 PDF 并进行 AI 分析"
          :description="`存储路径: ${localBasePath || '请先在设置中配置本地路径'}`"
          type="info"
          show-icon
          :closable="false"
          style="margin-bottom: 20px"
      />

      <div class="example-section">
        <div class="section-header">
          <span class="section-title">📚 快速填充示例</span>
          <el-button link type="primary" size="small" @click="fillRandomExample">
            随机填充
          </el-button>
        </div>
        <div class="example-grid">
          <el-card
              v-for="(paper, index) in examplePapers"
              :key="index"
              shadow="hover"
              class="example-card"
              @click="fillExample(paper)"
          >
            <div class="example-title">{{ paper.title }}</div>
            <div class="example-meta">
              <el-tag :type="getSourceType(paper.paperSource)" size="small">
                {{ formatSource(paper.paperSource) }}
              </el-tag>
              <span>{{ paper.yearPublished }}</span>
            </div>
          </el-card>
        </div>
      </div>

      <el-divider content-position="left">或手动录入</el-divider>

      <el-form
          :model="createForm"
          label-width="100px"
          :rules="createRules"
          ref="createFormRef"
          label-position="top"
      >
        <el-form-item label="论文标题" prop="title">
          <el-input
              v-model="createForm.title"
              placeholder="请输入完整标题"
              maxlength="500"
              show-word-limit
              type="textarea"
              :rows="2"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="DOI" prop="doi">
              <el-input v-model="createForm.doi" placeholder="10.xxxx/xxxxx" />
              <div class="form-hint">
                仅部分平台提供，ArXiv论文请留空
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源平台" prop="paperSource">
              <el-select v-model="createForm.paperSource" placeholder="选择" style="width: 100%">
                <el-option
                    v-for="source in sourceOptions"
                    :key="source.value"
                    :label="source.label"
                    :value="source.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="PDF直链" prop="downloadUrl">
          <el-input v-model="createForm.downloadUrl" placeholder="https://...">
            <template #append>
              <el-button
                  @click="testPdfLink(createForm.downloadUrl)"
                  :disabled="!createForm.downloadUrl"
                  :icon="Link"
              >
                测试
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="16">
            <el-form-item label="作者" prop="authors">
              <el-input v-model="createForm.authors" placeholder="Smith J, Zhang W" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="发表年份" prop="yearPublished">
              <el-input-number
                  v-model="createForm.yearPublished"
                  :min="1900"
                  :max="2030"
                  style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="摘要" prop="abstractContent">
          <el-input
              v-model="createForm.abstractContent"
              type="textarea"
              :rows="4"
              maxlength="5000"
              show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="createVisible = false">取消</el-button>
          <el-button @click="clearForm" :icon="Delete">清空</el-button>
          <el-button type="primary" @click="submitCreate" :loading="creating" :icon="Check">
            创建并下载
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Plus, RefreshRight, View, Download, Link, Delete, Document,
  CircleCheck, CircleClose, Loading, CopyDocument, Calendar,
  MoreFilled, InfoFilled, Check, User, FolderOpened, VideoPause, Refresh, Warning, Setting, Folder
} from '@element-plus/icons-vue'
import { keywordSearch } from '@/api/search'
import { getPapersPage, createPaper, deletePaper as deletePaperApi } from '@/api/paper'
import {
  createAndProcessPaper,
  startStatusPolling,
  getStoragePath
} from '@/api/paper'
import { API_ORIGIN, DEFAULT_PAPER_LOCAL_PATH } from '@/config/runtime'

const route = useRoute()
const router = useRouter()

// ==================== 本地路径与后端配置（修改版） ====================

// 本地基础路径配置（从localStorage读取，用于显示）
const localBasePath = ref(localStorage.getItem('paper_local_base_path') || DEFAULT_PAPER_LOCAL_PATH)
// 后端服务地址（新增）
const backendBaseUrl = ref(localStorage.getItem('paper_backend_url') || API_ORIGIN)
const namingPattern = ref(localStorage.getItem('paper_naming_pattern') || 'id')

// 路径配置弹窗
const pathConfigVisible = ref(false)
const pathConfigForm = reactive({
  basePath: localBasePath.value,
  backendUrl: backendBaseUrl.value,
  namingPattern: namingPattern.value
})

// 显示路径配置
const showPathConfig = () => {
  pathConfigForm.basePath = localBasePath.value
  pathConfigForm.backendUrl = backendBaseUrl.value
  pathConfigForm.namingPattern = namingPattern.value
  pathConfigVisible.value = true
}

// 保存路径配置
const savePathConfig = () => {
  localBasePath.value = pathConfigForm.basePath
  backendBaseUrl.value = pathConfigForm.backendUrl
  namingPattern.value = pathConfigForm.namingPattern

  localStorage.setItem('paper_local_base_path', pathConfigForm.basePath)
  localStorage.setItem('paper_backend_url', pathConfigForm.backendUrl)
  localStorage.setItem('paper_naming_pattern', pathConfigForm.namingPattern)

  pathConfigVisible.value = false
  ElMessage.success('配置已保存')
}

// 基础状态
const loading = ref(false)
const creating = ref(false)
const detailVisible = ref(false)
const createVisible = ref(false)
const selectedPaper = ref(null)
const selectedRows = ref([])
const batchLoading = ref(false)
const createFormRef = ref(null)
const tableRef = ref(null)
const activePolls = ref(new Map())
const storagePath = ref(getStoragePath())

// 视图模式：search（实时搜索） / library（库内论文）
const viewMode = ref('search')

// 是否来自搜索配置页面
const isFromSearchConfig = computed(() => !!route.query.configId)
const isLibraryMode = computed(() => viewMode.value === 'library')

// 搜索结果存储 - 关键修复：确保初始结构正确，防止undefined错误
const results = ref({
  papers: {
    items: [],
    total: 0
  }
})

// 分页信息（库内模式使用）
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 搜索表单 - 带本地缓存
const searchForm = reactive({
  query: localStorage.getItem('paper_search_query') || '',
  state: route.query.state || '',
  paperSource: '',
  pagination: {
    page: 1,
    size: parseInt(localStorage.getItem('paper_search_size')) || 20
  }
})

// 监听搜索条件变化，保存到本地
watch(() => searchForm.query, (val) => {
  localStorage.setItem('paper_search_query', val)
})
watch(() => searchForm.pagination.size, (val) => {
  localStorage.setItem('paper_search_size', val)
})

// 关键修复：添加空值保护，防止results为undefined时报错
const searchResults = computed(() => {
  if (!results.value || !results.value.papers) {
    return []
  }
  return results.value.papers.items || []
})

// 常量配置
const paperStates = [
  { value: 'DISCOVERED', label: '已发现', desc: '等待分析', type: 'info' },
  { value: 'PRE_ANALYZING', label: '分析中', desc: 'AI判定中', type: 'warning' },
  { value: 'PRE_RELEVANT', label: '相关', desc: '等待下载', type: 'success' },
  { value: 'IRRELEVANT', label: '不相关', desc: '已过滤', type: 'info' },
  { value: 'DOWNLOADING', label: '下载中', desc: '获取PDF', type: 'warning' },
  { value: 'DOWNLOADED', label: '已下载', desc: '等待处理', type: 'success' },
  { value: 'TRANSFORMING', label: '转换中', desc: 'PDF→文本', type: 'warning' },
  { value: 'TRANSFORMED', label: '已转换', desc: '等待向量化', type: 'success' },
  { value: 'EMBEDDING', label: '向量化中', desc: '生成向量', type: 'warning' },
  { value: 'COMPLETED', label: '已完成', desc: '全部完成', type: 'success' },
  { value: 'FAILED', label: '失败', desc: '需处理', type: 'danger' }
]

const sourceOptions = [
  { value: 'ARXIV', label: 'ArXiv' },
  { value: 'CORE', label: 'CORE' },
  { value: 'SEMANTIC_SCHOLAR', label: 'Semantic Scholar' },
  { value: 'MDPI', label: 'MDPI' },
  { value: 'SCIENCE_DIRECT', label: 'ScienceDirect' },
  { value: 'CROSSREF', label: 'Crossref' }
]

const processSteps = [
  { value: 'DISCOVERED', label: '发现' },
  { value: 'PRE_ANALYZING', label: 'AI分析' },
  { value: 'DOWNLOADING', label: '下载PDF' },
  { value: 'TRANSFORMING', label: '转换文本' },
  { value: 'EMBEDDING', label: '向量化' },
  { value: 'COMPLETED', label: '完成' }
]

const examplePapers = [
  {
    title: "A Supramolecular Reinforced Gel Fracturing Fluid with Low Permeability Damage",
    doi: "10.3390/gels10010002",
    paperSource: "MDPI",
    downloadUrl: "https://www.mdpi.com/2310-2861/10/1/2/pdf",
    authors: "Yongping Huang, Xinlong Yao, Caili Dai",
    yearPublished: 2023,
    abstractContent: "针对深层储层水力压裂需求，本研究构建了一种超分子增强凝胶压裂液..."
  },
  {
    title: "Flow of suspensions in a hydraulic fracture consisting of Herschel-Bulkley fluid",
    doi: "10.48550/arXiv.2412.19903",
    paperSource: "ARXIV",
    downloadUrl: "https://arxiv.org/pdf/2412.19903v1.pdf",
    authors: "E. V. Dontsov, S. A. Boronin",
    yearPublished: 2024,
    abstractContent: "建立了Herschel-Bulkley流体与球形颗粒组成的悬浮液在裂缝中的流动模型..."
  },
  {
    title: "Optimum formulation design and properties of drilling fluids incorporated with green magnetite",
    doi: "10.1016/j.arabjc.2023.105355",
    paperSource: "SCIENCE_DIRECT",
    downloadUrl: "https://www.sciencedirect.com/science/article/pii/S1878535223009541/pdfft",
    authors: "Mahmoud Abdelaziz, Ahmed M. Al-Sabagh",
    yearPublished: 2024,
    abstractContent: "本研究合成并表征了一种绿色无涂层和聚合物涂层磁铁矿纳米颗粒..."
  }
]

const createForm = reactive({
  title: '',
  doi: '',
  paperSource: 'ARXIV',
  authors: '',
  yearPublished: new Date().getFullYear(),
  abstractContent: '',
  downloadUrl: '',
  citationCount: 0
})

const createRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  paperSource: [{ required: true, message: '请选择来源', trigger: 'change' }],
  yearPublished: [{ required: true, message: '请输入年份', trigger: 'change' }],
  downloadUrl: [
    { required: true, message: '请输入PDF下载链接', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: '链接需以 http:// 或 https:// 开头',
      trigger: 'blur'
    }
  ]
}

// ==================== 本地文件访问（后端代理版 - 核心修改） ====================

/**
 * 获取后端代理的文件访问 URL（核心修改）
 * 通过后端API访问本地文件，避开浏览器安全限制
 */
const getLocalFileUrl = (row) => {
  if (!row.paperId) return null
  return `${backendBaseUrl.value}/api/local-file/${row.paperId}`
}

/**
 * 获取本地文件完整路径（仅用于显示）
 */
const getLocalFilePath = (row) => {
  if (!localBasePath.value || !row.paperId) return null

  const base = localBasePath.value.endsWith('\\') ? localBasePath.value : localBasePath.value + '\\'
  const fileName = namingPattern.value === 'id'
      ? `paper${row.paperId}.pdf`
      : `paper_${row.paperId}.pdf`

  return `${base}${fileName}`
}

/**
 * 检查论文本地文件是否存在
 */
const isLocalFileExist = (row) => {
  // 如果后端明确返回了本地路径，优先使用
  if (row.localFilePath || row.fileKey) {
    return true
  }

  // 状态判断：这些状态肯定本地有文件
  const downloadedStates = [
    'DOWNLOADED', 'TRANSFORMING', 'TRANSFORMED',
    'EMBEDDING', 'EMBEDDING_COMPLETED', 'EXTRACTING',
    'MATERIAL_EXTRACTED', 'COMPLETED'
  ]
  if (downloadedStates.includes(row.state)) {
    return true
  }

  // FAILED 状态 + 有 paperId，假设可能存在（失败可能发生在转换阶段）
  if (row.state === 'FAILED' && row.paperId) {
    return true
  }

  return false
}

/**
 * 处理本地文件操作（核心修改 - 使用后端代理）
 * 通过后端API访问，避开浏览器安全限制
 */
const handleLocalFileAction = (row) => {
  if (!row.paperId) {
    ElMessage.warning('论文ID无效')
    return
  }

  // 构造后端API URL
  const apiUrl = getLocalFileUrl(row)

  // 在新标签页打开，浏览器会自动使用 PDF 阅读器打开
  window.open(apiUrl, '_blank')

  ElMessage.success('正在打开 PDF...')
}

/**
 * 打开文件所在文件夹（通过后端API）
 */
const openFolder = async (row) => {
  if (!row.paperId) return

  try {
    const response = await fetch(`${backendBaseUrl.value}/api/local-file/${row.paperId}/open-folder`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    })
    const result = await response.text()
    if (response.ok) {
      ElMessage.success(result)
    } else {
      ElMessage.warning(result || '打开文件夹失败，请确保后端服务在本地运行')
    }
  } catch (error) {
    ElMessage.error('请求失败: ' + error.message)
  }
}

/**
 * 复制本地路径到剪贴板
 */
const copyPath = (row) => {
  const path = getLocalFilePath(row)
  if (path) {
    navigator.clipboard.writeText(path).then(() => {
      ElMessage.success('本地路径已复制到剪贴板')
    }).catch(() => {
      ElMessage.error('复制失败')
    })
  }
}

// ==================== 按钮状态智能判断 ====================

const getProcessButtonType = (row) => {
  if (row.processing) return 'info'
  if (isLocalFileExist(row)) return 'success'
  if (row.state === 'FAILED') return 'warning'
  if (row.downloadUrl) return 'primary'
  return 'info'
}

const getProcessButtonIcon = (row) => {
  if (row.processing) return Loading
  if (isLocalFileExist(row)) return FolderOpened
  if (row.state === 'FAILED') return RefreshRight
  if (row.downloadUrl) return Download
  return Document
}

const getProcessButtonText = (row) => {
  if (row.processing) return '处理中'

  if (isLocalFileExist(row)) {
    return row.state === 'FAILED' ? '查看本地' : '查看本地'
  }

  if (row.state === 'DOWNLOADING') return '下载中'
  if (row.state === 'FAILED') return '重试下载'
  return row.downloadUrl ? '下载PDF' : '无链接'
}

// ==================== 状态格式化方法（关键修改） ====================

/**
 * 判断是否为原始处理中状态（用于库内模式判断）
 */
const isRawProcessingState = (state) => {
  return ['DOWNLOADING', 'TRANSFORMING', 'EMBEDDING', 'EXTRACTING', 'PRE_ANALYZING'].includes(state)
}

/**
 * 格式化状态显示（关键修改：库内模式下处理中状态显示为失败）
 */
const formatState = (state) => {
  // 库内模式下，处理中状态映射为对应的失败状态
  if (isLibraryMode.value && isRawProcessingState(state)) {
    const failMap = {
      'DOWNLOADING': '下载失败',
      'TRANSFORMING': '转换失败',
      'EMBEDDING': '向量化失败',
      'EXTRACTING': '提取失败',
      'PRE_ANALYZING': '分析失败'
    }
    return failMap[state] || '处理失败'
  }

  const found = paperStates.find(s => s.value === state)
  return found ? found.label : state
}

/**
 * 判断是否为处理中状态（关键修改：库内模式下返回false）
 */
const isProcessing = (state) => {
  // 库内模式下，所有处理中状态都视为静态失败状态，不显示动态效果
  if (isLibraryMode.value) {
    return false
  }
  return ['DOWNLOADING', 'TRANSFORMING', 'EMBEDDING', 'EXTRACTING', 'PRE_ANALYZING'].includes(state)
}

/**
 * 获取状态类型（用于标签颜色）
 */
const getStateType = (state) => {
  // 库内模式下，原始处理中状态显示为danger（失败）类型
  if (isLibraryMode.value && isRawProcessingState(state)) {
    return 'danger'
  }

  const found = paperStates.find(s => s.value === state)
  return found ? found.type : 'info'
}

// 其他格式化方法
const formatAuthors = (authors) => {
  if (!authors) return '未知作者'
  if (Array.isArray(authors)) {
    return authors.length > 2 ? `${authors[0]} 等 ${authors.length} 人` : authors.join(', ')
  }
  const arr = String(authors).split(',').map(s => s.trim()).filter(Boolean)
  return arr.length > 2 ? `${arr[0]} 等 ${arr.length} 人` : arr.join(', ')
}

const parseAuthors = (authors) => {
  if (!authors) return []
  if (Array.isArray(authors)) return authors
  return String(authors).split(',').map(s => s.trim()).filter(Boolean)
}

const formatSource = (source) => {
  const found = sourceOptions.find(s => s.value === source)
  return found ? found.label : source
}

const getSourceType = (source) => {
  const map = {
    'ARXIV': 'primary',
    'CORE': 'success',
    'SEMANTIC_SCHOLAR': 'warning',
    'MDPI': 'success',
    'SCIENCE_DIRECT': 'warning',
    'CROSSREF': 'info'
  }
  return map[source] || 'info'
}

const getProgress = (state) => {
  const map = {
    'DISCOVERED': 10, 'PRE_ANALYZING': 25, 'PRE_RELEVANT': 35,
    'DOWNLOADING': 50, 'DOWNLOADED': 60, 'TRANSFORMING': 70,
    'TRANSFORMED': 80, 'EMBEDDING': 90, 'COMPLETED': 100, 'FAILED': 0
  }
  return map[state] || 0
}

const getProgressStatus = (state) => {
  if (state === 'FAILED') return 'exception'
  if (state === 'COMPLETED') return 'success'
  return ''
}

const getProcessHint = (state) => {
  const hints = {
    'DOWNLOADING': '正在下载 PDF 到本地...',
    'TRANSFORMING': '正在将PDF转换为Markdown...',
    'EMBEDDING': '正在生成向量嵌入...',
    'PRE_ANALYZING': 'AI 正在分析论文相关性...'
  }
  return hints[state] || '处理中...'
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const getCurrentStepIndex = (state) => {
  const index = processSteps.findIndex(s => s.value === state)
  return index === -1 ? 0 : index
}

// 表格操作
const clearSelection = () => {
  selectedRows.value = []
  tableRef.value?.clearSelection()
}

const handleSelectionChange = (val) => {
  selectedRows.value = val
}

const handleSizeChange = (val) => {
  if (viewMode.value === 'library') {
    pagination.size = val
    pagination.page = 1
    loadLibraryPapers()
  } else {
    searchForm.pagination.size = val
    searchForm.pagination.page = 1
    handleSearch()
  }
}

const handleCurrentChange = (val) => {
  if (viewMode.value === 'library') {
    pagination.page = val
    loadLibraryPapers()
  } else {
    searchForm.pagination.page = val
    handleSearch()
  }
}

// 核心搜索逻辑（实时搜索模式）- 关键修复
const handleSearch = async () => {
  if (viewMode.value === 'library') {
    loadLibraryPapers()
    return
  }

  if (!searchForm.query.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  loading.value = true
  try {
    const params = {
      query: searchForm.query,
      pagination: {
        page: searchForm.pagination.page - 1,
        size: searchForm.pagination.size
      }
    }
    if (searchForm.state) params.filters = { paperState: [searchForm.state] }
    if (searchForm.paperSource) params.filters = { ...(params.filters || {}), type: searchForm.paperSource }

    const { data } = await keywordSearch(params)

    // 关键修复：统一数据结构，确保 results.value.papers 始终存在
    // 假设后端返回的是 { items: [], total: 0 }，需要包装成 { papers: {...} }
    if (data && data.papers) {
      // 如果后端已经返回嵌套结构
      results.value = data
    } else if (data && Array.isArray(data.items)) {
      // 如果后端返回的是扁平结构 { items: [], total: 0 }
      results.value = {
        papers: {
          items: data.items,
          total: data.total || 0
        }
      }
    } else {
      // 兜底：空数据
      results.value = { papers: { items: [], total: 0 } }
    }

    // 关键修复：同步更新 pagination.total，避免分页组件报错
    pagination.total = results.value.papers?.total || 0

    // 如果存在进行中的任务，自动恢复轮询
    searchResults.value.forEach(row => {
      if (isProcessing(row.state) && row.paperId) {
        startPolling(row, row.paperId)
      }
    })
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('搜索失败: ' + (error.message || '未知错误'))
    // 错误时重置结果，避免undefined
    results.value = { papers: { items: [], total: 0 } }
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// 加载库内论文（使用 OpenAPI: GET /api/papers/page）
const loadLibraryPapers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      state: searchForm.state || undefined
    }

    const { data } = await getPapersPage(params)

    // 适配数据结构
    const papers = data.items || data.records || []
    const total = data.total || data.totalCount || papers.length

    // 转换为组件使用的格式
    results.value = {
      papers: {
        items: papers.map(p => ({
          paperId: p.paperId || p.id,
          title: p.title,
          authors: p.authors,
          paperSource: p.paperSource,
          yearPublished: p.yearPublished,
          doi: p.doi,
          state: p.state || 'DISCOVERED',
          downloadUrl: p.downloadUrl,
          abstractContent: p.abstractContent,
          downloadInfo: p.downloadInfo,
          citationCount: p.citationCount,
          materials: p.materials,
          localFilePath: p.localFilePath,
          fileKey: p.fileKey
        })),
        total: total
      }
    }

    pagination.total = total
  } catch (error) {
    console.error('加载论文列表失败:', error)
    ElMessage.error('加载论文列表失败: ' + (error.message || '未知错误'))
    // 错误时重置结果，避免undefined
    results.value = { papers: { items: [], total: 0 } }
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// 视图模式切换
const handleViewModeChange = (mode) => {
  viewMode.value = mode
  // 关键修复：切换视图时重置结果，避免残留数据导致undefined错误
  results.value = { papers: { items: [], total: 0 } }
  pagination.page = 1

  if (mode === 'library') {
    loadLibraryPapers()
  } else {
    if (searchForm.query) handleSearch()
  }
}

// 状态筛选变化
const handleStateChange = (val) => {
  if (viewMode.value === 'library') {
    loadLibraryPapers()
  } else {
    handleSearch()
  }
}

const refreshSearch = () => {
  if (viewMode.value === 'library') {
    loadLibraryPapers()
  } else {
    handleSearch()
  }
}

const resetSearch = () => {
  searchForm.query = ''
  searchForm.state = ''
  searchForm.paperSource = ''
  searchForm.pagination.page = 1
  // 关键修复：重置时确保结构完整
  results.value = { papers: { items: [], total: 0 } }
  selectedRows.value = []
  localStorage.removeItem('paper_search_query')

  if (viewMode.value === 'library') {
    loadLibraryPapers()
  }
}

const showQuickSearch = () => {
  searchForm.query = 'fracturing fluid HPAM'
  handleSearch()
}

// ==================== 论文处理逻辑 ====================

const handleProcess = async (row) => {
  // 如果本地文件已存在（包括 FAILED 状态），直接打开
  if (isLocalFileExist(row)) {
    handleLocalFileAction(row)
    return
  }

  // 如果没有下载链接，提示用户
  if (!row.downloadUrl) {
    ElMessage.warning('该论文没有提供下载链接')
    return
  }

  row.processing = true
  try {
    // 准备作者数组
    let authorsArray = []
    if (Array.isArray(row.authors)) {
      authorsArray = row.authors
    } else if (typeof row.authors === 'string') {
      authorsArray = row.authors.split(',').map(s => s.trim()).filter(Boolean)
    }
    if (authorsArray.length === 0) authorsArray = ['Unknown']

    const payload = {
      title: row.title,
      authors: authorsArray,
      paperSource: row.paperSource,
      downloadUrl: row.downloadUrl,
      yearPublished: row.yearPublished,
      doi: row.doi?.trim() || null,
      abstractContent: row.abstractContent?.trim() || null
    }

    // 清理空字段
    if (!payload.doi) delete payload.doi
    if (!payload.abstractContent) delete payload.abstractContent

    const result = await createAndProcessPaper(payload, {
      onAIError: async (error) => {
        const action = await ElMessageBox.confirm(
            `AI 服务不可用(${error.message})，是否跳过分析直接下载？`,
            '服务降级',
            { confirmButtonText: '直接下载', cancelButtonText: '取消', type: 'warning' }
        ).catch(() => 'cancel')
        return action === 'confirm' ? 'bypass' : 'cancel'
      },
      onDuplicate: async (doi) => {
        const action = await ElMessageBox.confirm(
            `DOI ${doi} 已存在，是否查看现有论文？`,
            '论文重复',
            { confirmButtonText: '查看', cancelButtonText: '取消' }
        ).catch(() => 'cancel')
        return action === 'confirm' ? 'view' : 'cancel'
      },
      onSuccess: (paper, { bypassedAnalysis }) => {
        ElMessage.success(bypassedAnalysis ? '已跳过AI分析，直接下载' : '已开始处理')
      }
    })

    if (result.success && result.paper?.paperId) {
      Object.assign(row, result.paper)
      startPolling(row, result.paper.paperId)
    } else if (result.action === 'view' && result.paper) {
      viewDetail(result.paper)
    }
  } catch (error) {
    if (!error.cancelled) ElMessage.error(error.message || '处理失败')
  } finally {
    row.processing = false
  }
}

// 状态轮询 - 只更新状态，不重新获取论文详情
const startPolling = (row, paperId) => {
  if (activePolls.value.has(paperId)) {
    const oldController = activePolls.value.get(paperId)
    oldController?.stop?.()
  }

  const controller = startStatusPolling(paperId, {
    interval: 2000,
    maxAttempts: 300,
    onProgress: (status) => {
      nextTick(() => {
        // 只更新状态相关字段，避免覆盖其他数据
        row.state = status.state
        row.progress = status.progress
        if (status.downloadInfo) row.downloadInfo = status.downloadInfo

        // 如果详情弹窗打开的是当前论文，同步更新
        if (selectedPaper.value?.paperId === paperId) {
          selectedPaper.value = {
            ...selectedPaper.value,
            state: status.state,
            progress: status.progress,
            downloadInfo: status.downloadInfo || selectedPaper.value.downloadInfo
          }
        }
      })
    },
    onCompleted: (status) => {
      nextTick(() => {
        row.state = 'COMPLETED'
        row.downloadInfo = status.downloadInfo
        activePolls.value.delete(paperId)

        ElMessage.success({
          message: `《${row.title?.substring(0, 20)}...》处理完成`,
          duration: 3000,
          showClose: true
        })
      })
    },
    onFailed: (error) => {
      nextTick(() => {
        row.state = 'FAILED'
        activePolls.value.delete(paperId)
        ElMessage.error(`处理失败: ${error?.message || '未知错误'}`)
      })
    }
  })

  activePolls.value.set(paperId, controller)
}

const cancelProcessing = (row) => {
  ElMessageBox.confirm(
      `确定要停止处理《${row.title?.substring(0, 20)}...》吗？`,
      '确认停止',
      { confirmButtonText: '停止', cancelButtonText: '继续处理', type: 'warning' }
  ).then(() => {
    const controller = activePolls.value.get(row.paperId)
    if (controller) {
      controller.stop()
      activePolls.value.delete(row.paperId)
      row.state = 'FAILED'
      ElMessage.info('已停止处理')
    }
  }).catch(() => {})
}

// 批量处理优化：只处理本地不存在的论文
const batchProcess = async () => {
  if (!selectedRows.value.length) return

  // 过滤出本地不存在且有下载链接的论文
  const validRows = selectedRows.value.filter(row =>
      !isLocalFileExist(row) && row.downloadUrl && !isProcessing(row.state)
  )

  if (validRows.length === 0) {
    ElMessage.info('选中的论文已全部存在于本地或无法下载')
    return
  }

  // 如果有部分论文已存在，提示用户
  const existCount = selectedRows.value.length - validRows.length
  if (existCount > 0) {
    ElMessage.info(`已过滤 ${existCount} 篇已存在的论文，将下载剩余 ${validRows.length} 篇`)
  }

  try {
    await ElMessageBox.confirm(
        `将依次处理 ${validRows.length} 篇论文，处理过程将在后台进行`,
        '批量处理确认',
        { type: 'info' }
    )

    batchLoading.value = true

    for (let i = 0; i < validRows.length; i++) {
      const row = validRows[i]
      ElMessage.info(`开始处理第 ${i + 1}/${validRows.length} 篇: ${row.title?.substring(0, 20)}...`)
      await handleProcess(row)
      if (i < validRows.length - 1) await new Promise(r => setTimeout(r, 500))
    }

    ElMessage.success('批量处理任务已启动')
  } finally {
    batchLoading.value = false
    clearSelection()
  }
}

// 详情查看 - 纯客户端展示，不再调用 API
const viewDetail = (row) => {
  // 使用深拷贝隔离数据，避免详情弹窗修改影响列表
  selectedPaper.value = JSON.parse(JSON.stringify(row))
  detailVisible.value = true
}

const openDoi = (doi) => {
  window.open(`https://doi.org/${doi}`, '_blank')
}

const deletePaper = async (row) => {
  try {
    await ElMessageBox.confirm(
        `确定要删除 "${row.title?.substring(0, 30)}..." 吗？此操作不可恢复。`,
        '确认删除',
        { type: 'warning', confirmButtonClass: 'el-button--danger' }
    )
    await deletePaperApi(row.paperId || row.id)
    ElMessage.success('论文已删除')
    // 本地移除，避免重新搜索
    const index = searchResults.value.findIndex(item => (item.paperId || item.id) === (row.paperId || row.id))
    if (index > -1) {
      searchResults.value.splice(index, 1)
      if (results.value.papers) {
        results.value.papers.total--
      }
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

/**
 * 重试论文 - 先检查本地文件，避免重复下载
 */
const retryPaper = (row) => {
  // 如果本地文件已存在，询问用户是查看还是重新处理
  if (isLocalFileExist(row)) {
    ElMessageBox.confirm(
        '本地文件已存在，您是想要查看文件还是重新处理流程？',
        '文件已存在',
        {
          confirmButtonText: '重新处理',
          cancelButtonText: '查看文件',
          type: 'warning',
          distinguishCancelAndClose: true
        }
    ).then(() => {
      // 重新处理
      row.state = 'DISCOVERED'
      handleProcess(row)
    }).catch((action) => {
      if (action === 'cancel') {
        // 查看文件
        handleLocalFileAction(row)
      }
    })
  } else {
    // 本地不存在，直接重置状态处理
    row.state = 'DISCOVERED'
    handleProcess(row)
  }
}

/**
 * 强制重新下载 - 无论本地是否存在都重新下载
 */
const forceRedownload = (row) => {
  ElMessageBox.confirm(
      '确定要强制重新下载吗？这将覆盖本地已有文件（如果有）',
      '强制重新下载',
      { confirmButtonText: '重新下载', cancelButtonText: '取消', type: 'warning' }
  ).then(() => {
    row.state = 'DISCOVERED'
    handleProcess(row)
  }).catch(() => {})
}

const testPdfLink = (url) => {
  if (url) window.open(url, '_blank')
}

// 表单操作
const fillExample = (paper) => {
  Object.assign(createForm, paper)
  ElMessage.success('已填充示例数据')
}

const fillRandomExample = () => {
  const random = examplePapers[Math.floor(Math.random() * examplePapers.length)]
  fillExample(random)
}

const clearForm = () => {
  Object.assign(createForm, {
    title: '', doi: '', paperSource: 'ARXIV', authors: '',
    yearPublished: new Date().getFullYear(), abstractContent: '', downloadUrl: ''
  })
  createFormRef.value?.clearValidate()
}

const openCreateDialog = () => {
  clearForm()
  createVisible.value = true
}

const submitCreate = async () => {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch (e) { return }

  creating.value = true
  try {
    const payload = {
      title: createForm.title.trim(),
      authors: createForm.authors
          ? createForm.authors.split(',').map(s => s.trim()).filter(Boolean)
          : ['Unknown'],
      paperSource: createForm.paperSource,
      yearPublished: createForm.yearPublished,
      downloadUrl: createForm.downloadUrl.trim(),
      abstractContent: createForm.abstractContent?.trim() || null,
      doi: createForm.doi?.trim() || null,
      citationCount: createForm.citationCount || 0
    }

    if (!payload.doi) delete payload.doi
    if (!payload.abstractContent) delete payload.abstractContent

    const result = await createAndProcessPaper(payload, {
      onAIError: async () => {
        const res = await ElMessageBox.confirm('AI服务不可用，是否直接下载?', '提示', {
          confirmButtonText: '直接下载', cancelButtonText: '取消', type: 'warning'
        }).catch(() => 'cancel')
        return res === 'confirm' ? 'bypass' : 'cancel'
      }
    })

    if (result.success) {
      ElMessage.success('创建成功，开始处理论文')
      createVisible.value = false
      clearForm()
      // 添加到列表头部，无需重新搜索
      if (result.paper) {
        // 关键修复：确保results.value.papers存在
        if (!results.value.papers) {
          results.value.papers = { items: [], total: 0 }
        }
        results.value.papers.items.unshift(result.paper)
        results.value.papers.total++
      }
    }
  } catch (error) {
    if (!error.cancelled) {
      const msg = error.response?.data?.message || error.message || '创建失败'
      ElMessage.error(msg)
    }
  } finally {
    creating.value = false
  }
}

// 生命周期管理
const handleBeforeUnload = (e) => {
  if (activePolls.value.size > 0) {
    e.preventDefault()
    e.returnValue = '有论文正在后台处理中，确定要离开吗？'
    return e.returnValue
  }
}

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)

  // 检查是否从搜索配置页面跳转
  if (route.query.configId) {
    viewMode.value = 'library'
    searchForm.state = route.query.state || 'DISCOVERED'
    loadLibraryPapers()
  } else if (route.query.viewMode === 'library') {
    viewMode.value = 'library'
    loadLibraryPapers()
  } else {
    // 如果有缓存的搜索词，自动搜索
    if (searchForm.query) {
      handleSearch()
    }
  }
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  // 清理所有轮询
  activePolls.value.forEach(controller => {
    try {
      controller?.stop?.()
    } catch (e) {
      console.warn('停止轮询时出错:', e)
    }
  })
  activePolls.value.clear()
})
</script>

<style scoped>
.keyword-search-container {
  padding: 20px;
  max-width: 1600px;
  margin: 0 auto;
  background-color: #f5f7fa;
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
  gap: 12px;
}

.header-title-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.platform-tag {
  font-size: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-form {
  padding: 10px 0;
}

/* 状态选项样式 */
.state-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.state-desc {
  font-size: 12px;
  color: #909399;
}

.source-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 平台标识点 */
.platform-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.platform-dot.arxiv { background: #409EFF; }
.platform-dot.core { background: #67C23A; }
.platform-dot.semantic_scholar { background: #E6A23C; }
.platform-dot.mdpi { background: #67C23A; }
.platform-dot.science_direct { background: #E6A23C; }

/* 结果卡片 */
.result-card {
  border-radius: 12px;
}

.result-title-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.count-tag {
  font-size: 12px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 表格样式优化 - 关键修复区域 */
.paper-table {
  margin-top: 10px;
  /* 确保表格单元格内容不会撑开 */
  :deep(.el-table__cell) {
    padding: 8px 0;
  }
}

.paper-info-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 12px;
  /* 防止内容溢出 */
  max-width: 100%;
  overflow: hidden;
}

/* 标题区域布局优化 */
.title-section {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: nowrap; /* 防止换行导致布局混乱 */
  min-width: 0; /* 关键：允许flex item收缩 */
}

.paper-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
  /* 关键：确保标题不会撑开容器 */
  min-width: 0;
  flex: 1;
  word-break: break-all;
}

/* 标签容器 - 防止挤压标题 */
.title-badges {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0; /* 不让标签区域收缩 */
  margin-top: 2px;
}

/* 紧凑标签样式 - 优化版 */
.compact-tag {
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  white-space: nowrap;
  border: none;
  font-weight: 500;
  vertical-align: middle;
  line-height: 1;
}

/* 关键：深度选择器调整 Element Tag 内部结构 */
.compact-tag :deep(.el-tag__content) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 100%;
  line-height: 1;
}

/* 图标样式微调 */
.compact-tag :deep(.el-icon) {
  font-size: 13px;
  width: 13px;
  height: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 0; /* 移除之前的 margin，改用 gap */
}

/* 文字微调 */
.compact-tag :deep(.el-tag__content span) {
  line-height: 1;
  position: relative;
  top: 0.5px; /* 视觉垂直居中微调 */
}

/* 特定类型颜色增强 */
.compact-tag.el-tag--warning {
  background: linear-gradient(135deg, #fdf6ec 0%, #f5e6d3 100%);
  color: #b88230;
  border: 1px solid #f0d6a3;
  box-shadow: 0 1px 2px rgba(230, 162, 60, 0.1);
}

.compact-tag.el-tag--primary {
  background: linear-gradient(135deg, #ecf5ff 0%, #d9ecff 100%);
  color: #2b7de1;
  border: 1px solid #b3d8ff;
  box-shadow: 0 1px 2px rgba(64, 158, 255, 0.1);
}

.compact-tag.el-tag--success {
  background: linear-gradient(135deg, #f0f9eb 0%, #e1f3d8 100%);
  color: #529b2e;
  border: 1px solid #c2e7b0;
}

.compact-tag.el-tag--danger {
  background: linear-gradient(135deg, #fef0f0 0%, #fde2e2 100%);
  color: #c45656;
  border: 1px solid #f9a7a7;
}

/* 处理中标签特殊动效 */
.compact-tag.processing-tag {
  background: linear-gradient(90deg, #409EFF, #67C23A);
  color: white;
  border: none;
  animation: shimmer 2s infinite;
  box-shadow: 0 2px 4px rgba(64, 158, 255, 0.3);
}

.compact-tag.processing-tag :deep(.el-icon) {
  animation: rotate 1s linear infinite;
  font-size: 12px;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes shimmer {
  0% { opacity: 0.85; }
  50% { opacity: 1; }
  100% { opacity: 0.85; }
}
.processing-tag {
  background: linear-gradient(90deg, #409EFF, #67C23A);
  animation: shimmer 2s infinite;
}

@keyframes shimmer {
  0% { opacity: 0.8; }
  50% { opacity: 1; }
  100% { opacity: 0.8; }
}

.meta-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.meta-item.doi {
  color: #409EFF;
  background: #f0f9ff;
}

.meta-item.pdf {
  color: #67C23A;
  background: #f0f9eb;
}

.meta-item.year {
  color: #E6A23C;
  background: #fcf6ec;
}

.progress-mini {
  width: 100%;
  max-width: 200px;
  opacity: 0.7;
}

/* 作者单元格 */
.authors-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 13px;
  padding: 0 8px;
}

/* 状态单元格 - 全新设计，防止溢出 */
.status-cell {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0 4px;
}

/* 状态徽章 - 替代原有的el-tag */
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  height: 24px;
  line-height: 1;
  white-space: nowrap;
  box-sizing: border-box;
  border: 1px solid transparent;
}

/* 处理中状态 */
.status-badge.processing {
  background: #ecf5ff;
  color: #409EFF;
  border-color: #d9ecff;
  animation: pulse 2s infinite;
}

.status-badge.processing :deep(.el-icon) {
  font-size: 12px;
}

/* 失败状态 - 有文件 */
.status-badge.warning {
  background: #fdf6ec;
  color: #e6a23c;
  border-color: #f5dab1;
}

/* 失败状态 - 无文件 */
.status-badge.error {
  background: #fef0f0;
  color: #f56c6c;
  border-color: #fde2e2;
}

/* 其他状态颜色映射 */
.status-badge.success {
  background: #f0f9eb;
  color: #67c23a;
  border-color: #e1f3d8;
}

.status-badge.info {
  background: #f4f4f5;
  color: #909399;
  border-color: #e9e9eb;
}

.status-badge.primary {
  background: #ecf5ff;
  color: #409eff;
  border-color: #d9ecff;
}

.status-badge.danger {
  background: #fef0f0;
  color: #f56c6c;
  border-color: #fde2e2;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* 存储单元格优化 */
.storage-cell {
  display: flex;
  justify-content: center;
  align-items: center;
}

.storage-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.storage-icon.success {
  color: #67C23A;
  background: #f0f9eb;
}

.storage-icon.success:hover {
  background: #67C23A;
  color: white;
  transform: scale(1.1);
}

.mini-progress :deep(.el-progress-circle) {
  width: 24px !important;
  height: 24px !important;
}

.not-stored {
  color: #C0C4CC;
  font-size: 12px;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
}

.processing-actions {
  display: flex;
  gap: 6px;
}

/* 详情弹窗样式 */
.detail-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 20px;
  border-bottom: 1px solid #e4e7ed;
}

.detail-dialog :deep(.el-dialog__body) {
  padding: 20px;
  max-height: 70vh;
  overflow-y: auto;
}

.paper-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.process-panel {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e4e7ed;
}

.process-panel.active {
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
  border-color: #409EFF;
  position: relative;
  overflow: hidden;
}

.process-panel.active::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #409EFF, #67C23A);
  animation: loading-bar 2s infinite;
}

@keyframes loading-bar {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.processing-icon {
  font-size: 18px;
  color: #409EFF;
}

.panel-title {
  font-weight: 600;
  color: #303133;
  font-size: 15px;
}

.progress-percent {
  font-size: 20px;
  font-weight: 700;
  color: #409EFF;
}

.process-steps {
  display: flex;
  justify-content: space-between;
  margin: 20px 0;
  padding: 0 10px;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  position: relative;
}

.step-item:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 12px;
  right: -50%;
  width: 100%;
  height: 2px;
  background: #e4e7ed;
  z-index: 0;
}

.step-item.is-completed:not(:last-child)::after {
  background: #67C23A;
}

.step-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  z-index: 1;
  transition: all 0.3s;
  border: 2px solid transparent;
}

.step-item.is-active .step-dot {
  background: #409EFF;
  color: white;
  border-color: #a0cfff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.2);
}

.step-item.is-completed .step-dot {
  background: #67C23A;
  color: white;
}

.step-label {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  transition: all 0.3s;
  text-align: center;
}

.step-item.is-active .step-label {
  color: #409EFF;
  font-weight: 600;
}

.step-item.is-completed .step-label {
  color: #67C23A;
}

.process-hint {
  margin-top: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 4px solid #409EFF;
}

/* FAILED 但文件存在面板 */
.failed-file-panel {
  background: linear-gradient(135deg, #fdf6ec 0%, #f5e6d3 100%);
  border: 1px solid #e6a23c;
  border-radius: 8px;
  padding: 16px;
}

.file-warning {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.warning-icon {
  font-size: 24px;
  color: #E6A23C;
  margin-top: 2px;
}

.warning-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.warning-title {
  font-weight: 600;
  color: #E6A23C;
  font-size: 14px;
}

.warning-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

/* 本地文件面板 - 后端代理版样式 */
.local-file-panel {
  background: linear-gradient(135deg, #f0f9eb 0%, #e1f3d8 100%);
  border: 1px solid #b3e19d;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.file-info {
  flex: 1;
  min-width: 0; /* 防止flex item溢出 */
}

.file-status {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.success-icon {
  font-size: 24px;
  color: #67C23A;
  margin-top: 2px;
  flex-shrink: 0;
}

.file-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0; /* 防止flex item溢出 */
  flex: 1;
}

.file-title {
  font-weight: 600;
  color: #67C23A;
  font-size: 14px;
}

/* 后端访问链接样式（蓝色可点击） */
.file-path.backend-url {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #409EFF;
  background: #f0f9ff;
  border: 1px solid #d9ecff;
  padding: 6px 10px;
  border-radius: 4px;
  word-break: break-all;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}

.file-path.backend-url:hover {
  background: #409EFF;
  color: white;
  border-color: #409EFF;
}

/* 本地路径样式（灰色） */
.file-path.local-path-display {
  font-family: 'Courier New', monospace;
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
  word-break: break-all;
  display: flex;
  align-items: center;
  gap: 6px;
}

.file-size {
  font-size: 12px;
  color: #909399;
}

.file-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.id-code {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
}

.authors-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.author-tag {
  font-size: 13px;
}

.materials-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.material-tag {
  font-size: 13px;
  padding: 6px 12px;
}

.property-text {
  opacity: 0.8;
  font-weight: normal;
  margin-left: 4px;
}

.abstract-content {
  line-height: 1.8;
  color: #606266;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  white-space: pre-wrap;
  font-size: 14px;
  border-left: 4px solid #dcdfe6;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
  margin-top: 10px;
}

/* 新增论文弹窗 */
.example-section {
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
}

.example-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.example-card {
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
}

.example-card:hover {
  transform: translateY(-2px);
  border-color: #409EFF;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
}

.example-title {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 10px;
  line-height: 1.4;
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  color: #303133;
}

.example-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 响应式 */
@media (max-width: 768px) {
  .keyword-search-container {
    padding: 10px;
  }

  .process-steps {
    overflow-x: auto;
    gap: 8px;
  }

  .example-grid {
    grid-template-columns: 1fr;
  }

  .local-file-panel {
    flex-direction: column;
    align-items: flex-start;
  }

  .file-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .title-section {
    flex-wrap: wrap; /* 移动端允许换行 */
  }

  .header-actions {
    flex-wrap: wrap;
  }
}
</style>
