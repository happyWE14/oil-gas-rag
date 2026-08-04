<template>
  <div class="material-library">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-icon">
          <el-icon :size="40" color="#fff"><Box /></el-icon>
        </div>
        <div class="header-text">
          <h1>材料实体库</h1>
          <p class="subtitle">基于 AI 提取的材料科学知识库 · 智能分析石油工程领域文献</p>
        </div>
      </div>
      <div class="header-actions">
        <div class="header-stats">
          <div class="stat-pill">
            <el-icon><Document /></el-icon>
            <span>{{ stats.totalMaterials }} 种材料</span>
          </div>
          <div class="stat-pill">
            <el-icon><DataAnalysis /></el-icon>
            <span>来源 {{ stats.totalPapers }} 篇论文</span>
          </div>
        </div>
        <el-button type="primary" class="refresh-btn" @click="refreshData">
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 - 数据来自全局统计 -->
    <div class="stats-section">
      <div class="stat-card" v-for="(stat, index) in statCards" :key="index" :class="stat.type">
        <div class="stat-icon-bg">
          <el-icon :size="24"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <!-- 动态热门标签 -->
    <div class="keywords-section">
      <div class="section-header">
        <span class="section-label">热门研究领域</span>
        <div class="keywords-list">
          <button
              v-for="(keyword, index) in displayKeywords"
              :key="index"
              class="keyword-btn"
              :class="{ active: searchQuery === keyword.en || selectedCategory === keyword.category }"
              @click="handleKeywordClick(keyword)"
          >
            <span class="keyword-name">{{ keyword.en }}</span>
            <span class="keyword-desc">{{ keyword.cn }}</span>
            <span v-if="keyword.isDynamic" class="dynamic-badge">列表</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 搜索与筛选区域 -->
    <div class="search-section">
      <div class="search-container">
        <div class="search-main">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索材料名称、化学式、属性，如 HPAM..."
              @keyup.enter="handleSearch"
              class="search-input"
          />
          <button v-if="searchQuery" class="clear-btn" @click="clearSearch">
            <el-icon><CircleClose /></el-icon>
          </button>
        </div>

        <div class="search-filters">
          <el-select v-model="selectedCategory" placeholder="材料分类" clearable class="filter-select" @change="handleCategoryChange">
            <el-option label="全部分类" value="" />
            <el-option label="聚合物" value="聚合物" />
            <el-option label="表面活性剂" value="表面活性剂" />
            <el-option label="破胶剂" value="破胶剂" />
            <el-option label="纳米材料" value="纳米材料" />
            <el-option label="稳定剂" value="稳定剂" />
            <el-option label="化学添加剂" value="化学添加剂" />
          </el-select>

          <el-select v-model="minConfidence" placeholder="置信度" clearable class="filter-select">
            <el-option label="全部置信度" :value="null" />
            <el-option label="高置信度 (>80%)" :value="0.8" />
            <el-option label="中等置信度 (>50%)" :value="0.5" />
            <el-option label="低置信度 (<50%)" :value="0" />
          </el-select>

          <el-button type="primary" class="search-btn" @click="handleSearch">
            搜索
          </el-button>
        </div>
      </div>

      <!-- 快捷筛选 -->
      <div class="quick-filters">
        <span class="filter-label">快捷筛选：</span>
        <div class="filter-tags">
          <button
              v-for="filter in quickFilters"
              :key="filter.value"
              class="filter-chip"
              :class="{ active: activeFilter === filter.value }"
              @click="applyFilter(filter.value)"
          >
            {{ filter.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- 数据内容 -->
    <div v-loading="loading" class="data-content">
      <el-empty
          v-if="!loading && list.length === 0"
          description="暂无材料数据"
          :image-size="180"
      >
        <template #description>
          <p class="empty-title">暂无符合条件的材料实体</p>
          <p class="empty-hint">尝试点击上方热门关键词或调整搜索条件</p>
        </template>
        <el-button type="primary" @click="refreshData">重置筛选</el-button>
      </el-empty>

      <!-- 卡片网格 -->
      <div v-else class="materials-grid">
        <div
            v-for="item in list"
            :key="item.materialKey"
            class="material-card"
            :class="{ 'has-category': getMaterialCategory(item.material), 'is-translated': item.showTranslation }"
            @click="openDetail(item)"
        >
          <!-- 分类标签 -->
          <div class="category-tag" :class="getCategoryType(item.material)" v-if="getMaterialCategory(item.material)">
            {{ getMaterialCategory(item.material) }}
          </div>

          <!-- 置信度指示器 -->
          <div class="confidence-badge" :class="getConfidenceClass(item.maxConfidence)">
            <svg viewBox="0 0 36 36" class="circular-chart">
              <path class="circle-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
              <path class="circle" :stroke-dasharray="`${item.maxConfidence * 100}, 100`"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
            </svg>
            <div class="confidence-text">{{ (item.maxConfidence * 100).toFixed(0) }}%</div>
          </div>

          <div class="card-content">
            <div class="material-header">
              <div class="title-row">
                <!-- 翻译后显示翻译文本，否则显示原文 -->
                <h3 class="material-title" :class="{ 'translated-text': item.showTranslation }" :title="item.material">
                  {{ item.showTranslation && item.translatedName ? item.translatedName : item.material }}
                </h3>

                <!-- 翻译控制按钮组 -->
                <div class="translation-controls">
                  <!-- 未翻译时显示翻译按钮 -->
                  <el-button
                      v-if="!item.translatedName"
                      class="translate-btn"
                      size="small"
                      :loading="item.translating"
                      @click.stop="translateMaterialName(item)"
                  >
                    <el-icon><Reading /></el-icon>
                    翻译
                  </el-button>

                  <!-- 已翻译后显示切换按钮（原文/译文） -->
                  <el-button
                      v-else
                      class="toggle-btn"
                      size="small"
                      :type="item.showTranslation ? 'primary' : 'default'"
                      @click.stop="toggleMaterialTranslation(item)"
                  >
                    <el-icon><component :is="item.showTranslation ? 'Document' : 'Check'" /></el-icon>
                    {{ item.showTranslation ? '原文' : '译文' }}
                  </el-button>
                </div>
              </div>

              <!-- 翻译元信息 -->
              <div v-if="item.translatedName" class="translation-feedback">
                <el-tag size="small" :type="item.translationSource === 'cache' ? 'success' : 'warning'" effect="light">
                  <el-icon v-if="item.translationSource === 'cache'"><CircleCheck /></el-icon>
                  <el-icon v-else><MagicStick /></el-icon>
                  {{ item.translationSource === 'cache' ? 'Redis缓存' : 'DeepSeek API' }} · {{ item.translationCostTime }}ms
                </el-tag>
              </div>

              <code class="material-code">{{ item.materialKey }}</code>
            </div>

            <div class="material-metrics">
              <div class="metric">
                <div class="metric-value">{{ item.paperCount }}</div>
                <div class="metric-label">来源论文</div>
              </div>
              <div class="metric-divider"></div>
              <div class="metric">
                <div class="metric-value">{{ item.occurrenceCount }}</div>
                <div class="metric-label">提及次数</div>
              </div>
            </div>

            <div class="card-footer">
              <span class="view-detail">查看详情</span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="list.length > 0" class="pagination-wrapper">
        <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[12, 24, 48, 96]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
        v-model="drawerVisible"
        :title="drawerTitle"
        size="900px"
        class="detail-drawer"
        destroy-on-close
    >
      <template #header>
        <div class="drawer-header-compact">
          <div class="header-left">
            <div class="drawer-tags" style="margin-bottom: 8px;">
              <div class="category-badge" :class="getCategoryType(currentMaterial?.material)" v-if="getMaterialCategory(currentMaterial?.material)">
                {{ getMaterialCategory(currentMaterial?.material) }}
              </div>
            </div>

            <div class="drawer-title-row">
              <h2 class="drawer-title" :class="{ 'translated-text': currentMaterial?.showTranslation }">
                {{ currentMaterial?.showTranslation && currentMaterial?.translatedName ? currentMaterial?.translatedName : currentMaterial?.material }}
              </h2>

              <!-- 详情页翻译控制 -->
              <div class="drawer-trans-controls" v-if="currentMaterial">
                <template v-if="!currentMaterial.translatedName">
                  <el-button
                      size="small"
                      :loading="currentMaterial.translating"
                      @click.stop="translateDetailMaterial"
                  >
                    <el-icon><Reading /></el-icon>
                    翻译
                  </el-button>
                </template>

                <template v-else>
                  <el-tag size="small" :type="currentMaterial.translationSource === 'cache' ? 'success' : 'warning'" effect="light" class="source-tag">
                    <el-icon v-if="currentMaterial.translationSource === 'cache'"><CircleCheck /></el-icon>
                    <el-icon v-else><MagicStick /></el-icon>
                    {{ currentMaterial.translationSource === 'cache' ? '缓存' : 'API' }}
                  </el-tag>

                  <el-button
                      size="small"
                      :type="currentMaterial.showTranslation ? 'primary' : 'default'"
                      @click.stop="toggleDetailTranslation"
                  >
                    {{ currentMaterial.showTranslation ? '显示原文' : '显示译文' }}
                  </el-button>
                </template>
              </div>
            </div>

            <p class="drawer-subtitle">{{ currentMaterial?.materialKey }}</p>
          </div>
          <div class="confidence-tag" :class="getConfidenceClass(currentMaterial?.maxConfidence)">
            <el-icon><CircleCheck /></el-icon>
            <span>{{ (currentMaterial?.maxConfidence * 100).toFixed(1) }}% 置信度</span>
          </div>
        </div>
      </template>

      <div v-if="currentMaterial" class="detail-body">
        <!-- 关键指标栏 -->
        <div class="detail-stats">
          <div class="detail-stat" v-for="(stat, idx) in detailStats" :key="idx">
            <div class="stat-icon-wrapper" :class="stat.color">
              <el-icon><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-detail">
              <div class="stat-number">{{ stat.value }}</div>
              <div class="stat-desc">{{ stat.label }}</div>
            </div>
          </div>
        </div>

        <!-- 标签页 -->
        <el-tabs v-model="activeTab" type="border-card" class="detail-tabs">
          <!-- 结构化指标 -->
          <el-tab-pane label="结构化指标" name="metrics">
            <div v-loading="papersLoading" class="tab-content">
              <el-empty v-if="!papersLoading && metrics.length === 0" description="暂无结构化指标数据" />

              <div v-else class="timeline-container">
                <el-timeline>
                  <el-timeline-item
                      v-for="(metric, idx) in metrics"
                      :key="idx"
                      :type="getMetricType(metric.metricKey)"
                      placement="top"
                      :hollow="true"
                  >
                    <div class="timeline-card metric-card">
                      <div class="card-row header-row">
                        <div class="metric-title">
                          <el-icon><CollectionTag /></el-icon>
                          <span>{{ translateMetric(metric.metricKey) }}</span>
                        </div>
                        <el-tag size="small" :type="metric.valueType === 'number' ? 'success' : 'info'" effect="light">
                          {{ getValueTypeLabel(metric.valueType) }}
                        </el-tag>
                      </div>

                      <div class="metric-value-section" :class="metric.valueType">
                        <template v-if="metric.valueType === 'number'">
                          <span class="value-num">{{ metric.valueNum }}</span>
                          <span v-if="metric.unit" class="value-unit">{{ metric.unit }}</span>
                        </template>
                        <template v-else-if="metric.valueType === 'range'">
                          <span class="value-num">{{ metric.valueMin }}</span>
                          <span class="range-separator">~</span>
                          <span class="value-num">{{ metric.valueMax }}</span>
                          <span v-if="metric.unit" class="value-unit">{{ metric.unit }}</span>
                        </template>
                        <template v-else>
                          <span class="value-text">{{ metric.valueText }}</span>
                          <span v-if="metric.unit" class="value-unit">{{ metric.unit }}</span>
                        </template>
                      </div>

                      <div v-if="metric.conditions && Object.keys(metric.conditions).length > 0" class="conditions-section">
                        <div class="section-subtitle">
                          <el-icon><Setting /></el-icon>
                          测量条件
                        </div>
                        <div class="conditions-grid">
                          <div v-for="(val, key) in metric.conditions" :key="key" class="condition-item">
                            <span class="condition-key">{{ translateCondition(key) }}</span>
                            <span class="condition-val">{{ val }}</span>
                          </div>
                        </div>
                      </div>

                      <div class="card-row footer-row">
                        <el-tag size="small" :type="getConfidenceType(metric.confidence)" effect="light">
                          <el-icon><CircleCheck /></el-icon>
                          置信度 {{ ((metric.confidence || 0) * 100).toFixed(0) }}%
                        </el-tag>
                        <span class="evidence-count">
                          <el-icon><DocumentCopy /></el-icon>
                          {{ metric.chunkIds?.length || 0 }} 个证据片段
                        </span>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>
          </el-tab-pane>

          <!-- 观察记录 -->
          <el-tab-pane label="观察记录" name="observations">
            <div v-loading="papersLoading" class="tab-content">
              <el-empty v-if="!papersLoading && observationsList.length === 0" description="暂无观察记录" />

              <div v-else class="timeline-container">
                <el-timeline>
                  <el-timeline-item
                      v-for="(obs, idx) in observationsList"
                      :key="obs.id"
                      :type="getObservationType(obs.type)"
                      :icon="getObservationIcon(obs.type)"
                      placement="top"
                      :hollow="true"
                  >
                    <div class="timeline-card observation-card">
                      <div class="card-row header-row">
                        <el-tag size="small" effect="light" :type="getObservationType(obs.type)" class="obs-type-tag">
                          <el-icon><component :is="getObservationIcon(obs.type)" /></el-icon>
                          {{ translateObservationType(obs.type) }}
                        </el-tag>

                        <div class="header-actions">
                          <!-- 观察记录翻译控制 -->
                          <template v-if="!obs.translatedContent">
                            <el-button
                                size="small"
                                type="primary"
                                text
                                :loading="obs.translating"
                                @click.stop="translateObservation(obs)"
                            >
                              <el-icon><Reading /></el-icon>
                              翻译
                            </el-button>
                          </template>

                          <template v-else>
                            <el-tag size="small" :type="obs.translationSource === 'cache' ? 'success' : 'warning'" effect="light">
                              {{ obs.translationSource === 'cache' ? '缓存' : 'API' }}
                            </el-tag>

                            <el-button
                                size="small"
                                :type="obs.showTranslation ? 'primary' : 'default'"
                                @click.stop="toggleObservationTranslation(obs)"
                            >
                              {{ obs.showTranslation ? '原文' : '译文' }}
                            </el-button>
                          </template>

                          <el-tag size="small" :type="getConfidenceType(obs.confidence)" effect="plain">
                            {{ ((obs.confidence || 0) * 100).toFixed(0) }}% 置信度
                          </el-tag>
                        </div>
                      </div>

                      <!-- 观察内容 -->
                      <div class="obs-content-text" :class="{ 'translated-text': obs.showTranslation }">
                        {{ obs.showTranslation && obs.translatedContent ? obs.translatedContent : obs.content }}
                      </div>

                      <div v-if="obs.chunkIds?.length" class="card-row footer-row muted">
                        <el-icon><Connection /></el-icon>
                        <span>{{ obs.chunkIds.length }} 个来源片段支持</span>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>
          </el-tab-pane>

          <!-- 证据片段 -->
          <el-tab-pane label="证据片段" name="chunks">
            <div v-loading="papersLoading" class="tab-content">
              <el-empty v-if="!papersLoading && chunksList.length === 0" description="暂无证据片段" />

              <div v-else class="chunks-list">
                <el-collapse v-model="activeChunks" accordion>
                  <el-collapse-item
                      v-for="(chunk, idx) in chunksList"
                      :key="chunk.id"
                      :name="chunk.id"
                  >
                    <template #title>
                      <div class="chunk-title-row">
                        <div class="chunk-title-left">
                          <el-tag size="small" type="primary" effect="light" class="chunk-index">片段 #{{ chunk.orderNo || idx + 1 }}</el-tag>
                          <span class="chunk-paper-title" v-if="chunk.paperTitle">{{ chunk.paperTitle }}</span>
                        </div>
                        <div class="chunk-title-right">
                          <el-button v-if="chunk.paperId" link type="primary" size="small" @click.stop="viewPaper(chunk.paperId)">
                            查看论文 <el-icon><ArrowRight /></el-icon>
                          </el-button>
                        </div>
                      </div>
                    </template>
                    <div class="chunk-content-box">
                      <!-- 证据片段翻译控制 -->
                      <div class="chunk-trans-controls" v-if="!chunk.translatedContent">
                        <el-button
                            size="small"
                            type="primary"
                            plain
                            :loading="chunk.translating"
                            @click.stop="translateChunk(chunk)"
                        >
                          <el-icon><Reading /></el-icon>
                          翻译此片段
                        </el-button>
                      </div>

                      <div v-else class="chunk-trans-feedback">
                        <el-tag size="small" :type="chunk.translationSource === 'cache' ? 'success' : 'warning'" effect="light">
                          <el-icon v-if="chunk.translationSource === 'cache'"><CircleCheck /></el-icon>
                          <el-icon v-else><MagicStick /></el-icon>
                          {{ chunk.translationSource === 'cache' ? 'Redis缓存' : 'DeepSeek API' }} · {{ chunk.translationCostTime }}ms
                        </el-tag>

                        <el-button
                            size="small"
                            :type="chunk.showTranslation ? 'primary' : 'default'"
                            @click.stop="toggleChunkTranslation(chunk)"
                        >
                          {{ chunk.showTranslation ? '显示原文' : '显示译文' }}
                        </el-button>
                      </div>

                      <!-- 证据内容 -->
                      <p class="chunk-text" :class="{ 'translated-text': chunk.showTranslation }">
                        {{ chunk.showTranslation && chunk.translatedContent ? chunk.translatedContent : chunk.content }}
                      </p>

                      <div v-if="chunk.embeddingModel" class="chunk-meta">
                        <el-icon><Cpu /></el-icon>
                        <span>向量化模型: {{ chunk.embeddingModel }}</span>
                      </div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
          </el-tab-pane>

          <!-- 来源论文 -->
          <el-tab-pane label="来源论文" name="papers">
            <div v-loading="papersLoading" class="tab-content">
              <el-empty v-if="!papersLoading && papersList.length === 0" description="暂无来源论文数据">
                <el-button type="primary" size="small" @click="loadPapers">重新加载</el-button>
              </el-empty>

              <div v-else class="timeline-container">
                <el-timeline>
                  <el-timeline-item
                      v-for="(paper, idx) in papersList"
                      :key="paper._key"
                      :type="paper.confidence > 0.8 ? 'primary' : 'info'"
                      placement="top"
                      :hollow="true"
                  >
                    <div class="timeline-card paper-card">
                      <div class="paper-header-section">
                        <div class="paper-title-row">
                          <!-- 论文标题翻译 -->
                          <h4 :class="{ 'translated-text': paper.showTranslation }">
                            {{ paper.showTranslation && paper.translatedTitle ? paper.translatedTitle : paper.paper?.title }}
                          </h4>

                          <div class="paper-title-actions">
                            <template v-if="!paper.translatedTitle">
                              <el-button
                                  size="small"
                                  type="primary"
                                  text
                                  :loading="paper.translating"
                                  @click.stop="translatePaperTitle(paper)"
                              >
                                <el-icon><Reading /></el-icon>
                                翻译
                              </el-button>
                            </template>

                            <template v-else>
                              <el-tag size="small" :type="paper.translationSource === 'cache' ? 'success' : 'warning'" effect="light">
                                {{ paper.translationSource === 'cache' ? '缓存' : 'API' }}
                              </el-tag>

                              <el-button
                                  size="small"
                                  :type="paper.showTranslation ? 'primary' : 'default'"
                                  @click.stop="togglePaperTranslation(paper)"
                              >
                                {{ paper.showTranslation ? '原文' : '译文' }}
                              </el-button>
                            </template>

                            <el-tag size="small" :type="getConfidenceType(paper.confidence)" effect="light">
                              {{ ((paper.confidence || 0) * 100).toFixed(0) }}%
                            </el-tag>
                          </div>
                        </div>

                        <div class="paper-meta-row">
                          <span v-if="paper.paper?.yearPublished" class="meta-item">
                            <el-icon><Calendar /></el-icon>
                            {{ paper.paper.yearPublished }}年发表
                          </span>
                          <span v-if="paper.paper?.doi" class="meta-item">
                            <el-icon><Link /></el-icon>
                            DOI: {{ paper.paper.doi }}
                          </span>
                        </div>
                      </div>

                      <div v-if="paper.property || paper.method" class="paper-properties">
                        <div class="prop-grid">
                          <div class="prop-item" v-if="paper.property">
                            <span class="prop-label">属性</span>
                            <span class="prop-value">{{ paper.property }}</span>
                          </div>
                          <div class="prop-item" v-if="paper.method">
                            <span class="prop-label">方法</span>
                            <span class="prop-value">{{ paper.method }}</span>
                          </div>
                        </div>
                      </div>

                      <div v-if="paper.chunks?.length" class="paper-chunks-section">
                        <div class="chunks-toggle" @click="togglePaperChunks(paper)">
                          <el-icon><DocumentCopy /></el-icon>
                          <span>证据片段 ({{ paper.chunks.length }}个)</span>
                          <el-icon class="expand-icon" :class="{ 'is-expand': paper.showChunks }"><ArrowDown /></el-icon>
                        </div>
                        <el-collapse-transition>
                          <div v-show="paper.showChunks" class="chunks-list-nested">
                            <div v-for="(chunk, cidx) in paper.chunks" :key="cidx" class="nested-chunk">
                              <div class="chunk-index-tag">#{{ chunk.orderNo || cidx + 1 }}</div>
                              <p class="chunk-text-small">{{ chunk.content }}</p>
                            </div>
                          </div>
                        </el-collapse-transition>
                      </div>

                      <div class="paper-actions-row">
                        <el-button type="primary" link size="small" @click="viewPaper(paper.paper?.paperId)">
                          <el-icon><View /></el-icon>
                          查看论文详情
                        </el-button>
                      </div>
                    </div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, Box, Document, CircleCheck, CircleClose,
  ArrowRight, DataAnalysis, TrendCharts, ArrowUp,
  CollectionTag, ChatDotRound, Star, DocumentCopy,
  Connection, Cpu, Calendar, Link, View, ArrowDown, Setting,
  Reading, MagicStick
} from '@element-plus/icons-vue'
import { pageMaterials, pageMaterialPapers } from '@/api/material'
import { API_ORIGIN as API_BASE_URL } from '@/config/runtime'
import axios from 'axios'

const router = useRouter()

// 关键词映射表
const keywordMap = {
  'fracturing fluid': '压裂液',
  'drilling fluid': '钻井液',
  'drilling mud': '钻井泥浆',
  'slickwater': '滑溜水',
  'stimulation': '增产措施',
  'EOR': '提高采收率',
  'produced water': '采出水',
  'HPAM': '部分水解聚丙烯酰胺',
  'partially hydrolyzed polyacrylamide': '部分水解聚丙烯酰胺',
  'HPG': '羟丙基瓜尔胶',
  'guar gum': '瓜尔胶',
  'CMHPG': '羧甲基羟丙基瓜尔胶',
  'VES': '粘弹性表面活性剂',
  'viscoelastic surfactant': '粘弹性表面活性剂',
  'associative polymer': '疏水缔合聚合物',
  'PAM': '聚丙烯酰胺',
  'polyacrylamide': '聚丙烯酰胺',
  'viscosity': '粘度',
  'rheology': '流变性',
  'flow behavior index': '流变指数',
  'consistency index': '稠度指数',
  'temperature resistance': '耐温性',
  'breaker': '破胶剂',
  'surface tension': '表面张力',
  'core damage': '岩心伤害率',
  'molecular weight': '分子量',
  'shear rate': '剪切速率',
  'solid content': '固含量',
  'hydrolysis degree': '水解度',
  'particle size': '粒度',
  'apparent viscosity': '表观粘度',
  'yield stress': '屈服应力',
  'elastic modulus': '弹性模量',
  'viscous modulus': '粘性模量',
  'clay stabilizer': '粘土稳定剂',
  'biocide': '杀菌剂',
  'ph regulator': 'pH调节剂',
  'flowback aid': '助排剂',
  'nanoparticle': '纳米颗粒',
  'surfactant': '表面活性剂'
}

// 预设热门关键词
const presetKeywords = [
  { en: 'HPAM', cn: '部分水解聚丙烯酰胺', category: 'polymer' },
  { en: 'HPG', cn: '羟丙基瓜尔胶', category: 'polymer' },
  { en: 'fracturing fluid', cn: '压裂液', category: 'fluid' },
  { en: 'drilling fluid', cn: '钻井液', category: 'fluid' },
  { en: 'viscosity', cn: '粘度', category: 'property' },
  { en: 'rheology', cn: '流变性', category: 'property' },
  { en: 'VES', cn: '粘弹性表面活性剂', category: 'surfactant' },
  { en: 'temperature resistance', cn: '耐温性', category: 'property' }
]

// 响应式状态
const searchQuery = ref('')
const minConfidence = ref(null)
const selectedCategory = ref('')
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)
const loading = ref(false)
const activeFilter = ref('all')
const list = ref([])

// 动态关键词列表
const dynamicKeywords = ref([])

// 详情抽屉
const drawerVisible = ref(false)
const currentMaterial = ref(null)
const activeTab = ref('metrics')
const papersLoading = ref(false)

// 使用ref而不是computed来存储详情数据，避免重新计算导致重新渲染
const papersList = ref([])
const observationsList = ref([])
const chunksList = ref([])
const activeChunks = ref([]) // 控制el-collapse的展开状态

// 全局统计数据 - 从数据库正确映射，不随翻页变化
const stats = reactive({
  totalMaterials: 0,
  totalPapers: 0,
  totalOccurrences: 0,
  avgConfidence: 0
})

// 计算显示的合并关键词
const displayKeywords = computed(() => {
  const combined = [...presetKeywords]
  dynamicKeywords.value.forEach(dk => {
    const exists = combined.find(pk => pk.en.toLowerCase() === dk.en.toLowerCase())
    if (!exists) {
      combined.push({ ...dk, isDynamic: true })
    }
  })
  return combined
})

const quickFilters = [
  { label: '全部', value: 'all' },
  { label: '高置信度', value: 'high' },
  { label: '聚合物', value: 'polymer' },
  { label: '表面活性剂', value: 'surfactant' },
  { label: '破胶剂', value: 'breaker' }
]

// 计算属性 - 统计卡片数据（基于全局统计）
const statCards = computed(() => [
  { icon: 'Box', value: stats.totalMaterials, label: '材料种类', type: 'blue' },
  { icon: 'Document', value: stats.totalPapers, label: '来源论文', type: 'green' },
  { icon: 'ChatDotRound', value: stats.totalOccurrences, label: '提取记录', type: 'orange' },
  { icon: 'TrendCharts', value: stats.avgConfidence + '%', label: '平均置信度', type: 'purple' }
])

const detailStats = computed(() => [
  { icon: 'Document', value: currentMaterial.value?.paperCount || 0, label: '来源论文', color: 'blue' },
  { icon: 'ChatDotRound', value: currentMaterial.value?.occurrenceCount || 0, label: '提取记录', color: 'green' },
  { icon: 'DocumentCopy', value: papersList.value.reduce((sum, p) => sum + (p.chunks?.length || 0), 0), label: '证据片段', color: 'orange' }
])

const drawerTitle = computed(() => {
  if (!currentMaterial.value) return ''
  return currentMaterial.value.showTranslation && currentMaterial.value.translatedName
      ? currentMaterial.value.translatedName
      : currentMaterial.value.material
})

const metrics = computed(() => {
  const allMetrics = []
  papersList.value.forEach(paper => {
    if (paper.detailJson) {
      try {
        const detail = JSON.parse(paper.detailJson)
        if (detail.core_metrics) {
          Object.entries(detail.core_metrics).forEach(([key, values]) => {
            if (Array.isArray(values)) {
              values.forEach(val => {
                allMetrics.push({
                  metricKey: key,
                  valueType: val.value?.type || 'text',
                  valueNum: val.value?.num,
                  valueMin: val.value?.min,
                  valueMax: val.value?.max,
                  valueText: val.value?.raw || val.value?.text,
                  unit: val.unit,
                  conditions: val.condition,
                  confidence: val.confidence,
                  chunkIds: val.chunk_ids || []
                })
              })
            }
          })
        }
      } catch (e) {
        console.error('解析 detailJson 失败:', e)
      }
    }
  })
  return allMetrics
})

// 工具函数
const translateMaterial = (name) => {
  if (!name) return ''
  for (const [en, cn] of Object.entries(keywordMap)) {
    if (name.toLowerCase().includes(en.toLowerCase())) {
      return `${name} (${cn})`
    }
  }
  return name
}

const translateMetric = (key) => {
  const metricMap = {
    'molecular_weight': '分子量',
    'solid_content_pct': '固含量',
    'hydrolysis_degree_pct': '水解度',
    'particle_size_desc': '粒度描述',
    'dissolution_time_min': '溶解时间',
    'testing_concentration_pct_wt': '测试浓度',
    'apparent_viscosity_mPa_s': '表观粘度',
    'rheology_model': '流变模型',
    'flow_behavior_index_n': '流变指数 n',
    'consistency_index_k': '稠度系数 K',
    'temperature_resistance_C': '耐温性',
    'breaker_type': '破胶剂类型',
    'breaker_dosage_pct': '破胶剂用量',
    'breaker_time_h': '破胶时间',
    'breaker_final_viscosity_mPa_s': '破胶后粘度',
    'breaker_surface_tension_mN_m': '表面张力',
    'core_damage_rate_pct': '岩心伤害率'
  }
  return metricMap[key] || key
}

const translateCondition = (key) => {
  const conditionMap = {
    'temperature_C': '温度',
    'shear_rate_s_1': '剪切速率',
    'concentration_pct_wt': '浓度',
    'salinity_g_L': '矿化度',
    'pH': 'pH值',
    'pressure_MPa': '压力',
    'condition_text': '条件'
  }
  return conditionMap[key] || key
}

const translateObservationType = (type) => {
  const typeMap = {
    'formulation': '配方',
    'experimental_setup': '实验设置',
    'measurement_method': '测量方法',
    'result_summary': '结果总结',
    'mechanism': '机理分析',
    'application': '应用场景',
    'limitation': '局限性',
    'comparison': '对比分析',
    'other': '其他'
  }
  return typeMap[type] || type || '观察'
}

const getConfidenceClass = (c) => {
  if (!c) return 'low'
  if (c >= 0.8) return 'high'
  if (c >= 0.5) return 'medium'
  return 'low'
}

const getConfidenceType = (c) => {
  if (!c) return 'info'
  if (c >= 0.8) return 'success'
  if (c >= 0.5) return 'warning'
  return 'danger'
}

const getMaterialCategory = (material) => {
  if (!material) return null
  const lower = material.toLowerCase()
  if (lower.includes('hpam') || lower.includes('polyacrylamide') || lower.includes('hpg') || lower.includes('guar') || lower.includes('polymer') || lower.includes('pam')) {
    return '聚合物'
  }
  if (lower.includes('ves') || lower.includes('surfactant')) {
    return '表面活性剂'
  }
  if (lower.includes('breaker')) {
    return '破胶剂'
  }
  if (lower.includes('nanoparticle') || lower.includes('nano')) {
    return '纳米材料'
  }
  if (lower.includes('clay') || lower.includes('stabilizer')) {
    return '稳定剂'
  }
  return '化学添加剂'
}

const getCategoryType = (material) => {
  const category = getMaterialCategory(material)
  const typeMap = {
    '聚合物': 'primary',
    '表面活性剂': 'success',
    '破胶剂': 'warning',
    '纳米材料': 'danger',
    '稳定剂': 'info',
    '化学添加剂': 'default'
  }
  return typeMap[category] || 'default'
}

const getMetricType = (key) => {
  if (key.includes('viscosity')) return 'primary'
  if (key.includes('temperature')) return 'danger'
  if (key.includes('breaker')) return 'warning'
  if (key.includes('molecular')) return 'success'
  return 'info'
}

const getValueTypeLabel = (type) => {
  const map = { 'number': '数值型', 'range': '范围型', 'text': '文本型' }
  return map[type] || type
}

const getObservationType = (type) => {
  const typeMap = {
    'formulation': 'primary',
    'result_summary': 'success',
    'measurement_method': 'warning',
    'comparison': 'info',
    'limitation': 'danger'
  }
  return typeMap[type] || 'info'
}

const getObservationIcon = (type) => {
  const iconMap = {
    'formulation': 'CollectionTag',
    'result_summary': 'TrendCharts',
    'measurement_method': 'DataAnalysis',
    'comparison': 'ScaleToOriginal',
    'limitation': 'Warning'
  }
  return iconMap[type] || 'InfoFilled'
}

// 翻译API调用
const translateText = async (text, type, context = '') => {
  try {
    const response = await axios.post(`${API_BASE_URL}/api/materials/translate`, {
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

// 列表材料名称翻译
const translateMaterialName = async (item) => {
  if (item.translating) return

  item.translating = true
  try {
    const result = await translateText(item.material, 'MATERIAL_NAME', 'petroleum engineering material')

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

// 切换材料名称显示（原文/译文）
const toggleMaterialTranslation = (item) => {
  if (!item.translatedName) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  item.showTranslation = !item.showTranslation
}

// 详情页材料名称翻译
const translateDetailMaterial = async () => {
  if (!currentMaterial.value || currentMaterial.value.translating) return

  currentMaterial.value.translating = true
  try {
    const result = await translateText(
        currentMaterial.value.material,
        'MATERIAL_NAME',
        'petroleum engineering material'
    )

    currentMaterial.value.translatedName = result.translatedText
    currentMaterial.value.translationSource = result.source
    currentMaterial.value.translationCostTime = result.costTime
    currentMaterial.value.showTranslation = true

    ElMessage.success(`翻译完成（${result.source === 'cache' ? 'Redis缓存' : 'DeepSeek API'}，耗时 ${result.costTime}ms）`)
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    if (currentMaterial.value) {
      currentMaterial.value.translating = false
    }
  }
}

// 切换详情页显示
const toggleDetailTranslation = () => {
  if (!currentMaterial.value?.translatedName) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  currentMaterial.value.showTranslation = !currentMaterial.value.showTranslation
}

// 观察记录翻译 - 直接修改数组中的对象，避免重新渲染整个列表
const translateObservation = async (obs) => {
  if (obs.translating) return

  obs.translating = true
  try {
    const result = await translateText(obs.content, 'OBSERVATION', 'scientific observation')

    // 直接修改对象属性，保持引用不变
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

// 切换观察记录显示 - 直接修改，不触发重新计算
const toggleObservationTranslation = (obs) => {
  if (!obs.translatedContent) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  obs.showTranslation = !obs.showTranslation
}

// 证据片段翻译
const translateChunk = async (chunk) => {
  if (chunk.translating) return

  chunk.translating = true
  try {
    const result = await translateText(
        chunk.content,
        'EVIDENCE_CHUNK',
        `material: ${currentMaterial.value?.material || ''}`
    )

    chunk.translatedContent = result.translatedText
    chunk.translationSource = result.source
    chunk.translationCostTime = result.costTime
    chunk.showTranslation = true

    if (result.source === 'api') {
      ElMessage.success(`实时翻译完成（${result.costTime}ms）`)
    }
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    chunk.translating = false
  }
}

// 切换证据片段显示
const toggleChunkTranslation = (chunk) => {
  if (!chunk.translatedContent) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  chunk.showTranslation = !chunk.showTranslation
}

// 论文标题翻译
const translatePaperTitle = async (paper) => {
  if (paper.translating || !paper.paper?.title) return

  paper.translating = true
  try {
    const result = await translateText(paper.paper.title, 'PAPER_TITLE', 'academic paper title')

    paper.translatedTitle = result.translatedText
    paper.translationSource = result.source
    paper.translationCostTime = result.costTime
    paper.showTranslation = true

    if (result.source === 'api') {
      ElMessage.success(`实时翻译完成（${result.costTime}ms）`)
    }
  } catch (error) {
    ElMessage.error('翻译失败：' + (error.message || '未知错误'))
  } finally {
    paper.translating = false
  }
}

// 切换论文标题显示
const togglePaperTranslation = (paper) => {
  if (!paper.translatedTitle) {
    ElMessage.warning('请先点击翻译按钮')
    return
  }
  paper.showTranslation = !paper.showTranslation
}

// 控制论文片段展开/收起
const togglePaperChunks = (paper) => {
  paper.showChunks = !paper.showChunks
}

// 从列表提取动态关键词
const extractDynamicKeywords = (items) => {
  const categories = new Map()
  items.forEach(item => {
    const category = getMaterialCategory(item.material)
    if (category && !categories.has(category)) {
      categories.set(category, {
        en: item.material.split(' ')[0],
        cn: category,
        category: category.toLowerCase().replace(/\s/g, '_')
      })
    }
  })
  return Array.from(categories.values())
}

// 获取全局统计数据 - 调用专门的统计接口
const fetchGlobalStats = async () => {
  try {
    // 调用新的统计接口，直接从 material_extraction 表获取准确统计
    const response = await axios.get(`${API_BASE_URL}/api/materials/stats`)

    if (response.data) {
      stats.totalMaterials = response.data.totalMaterials || 0
      // 关键修改：totalPapers 现在来自 material_extraction 表的不同 paper_id 统计
      stats.totalPapers = response.data.totalPapers || 0
      stats.totalOccurrences = response.data.totalOccurrences || 0
      stats.avgConfidence = response.data.avgConfidence || 0
    }
  } catch (error) {
    console.error('获取全局统计失败:', error)
    ElMessage.error('获取统计数据失败')
  }
}

// 搜索逻辑 - 仅处理列表数据，不更新全局统计
const handleSearch = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: pageSize.value,
      q: searchQuery.value || undefined,
      minConfidence: minConfidence.value,
      category: selectedCategory.value || undefined
    }

    const { data } = await pageMaterials(params)

    if (data && data.items) {
      list.value = data.items.map(item => ({
        ...item,
        translating: false,
        translatedName: null,
        translationSource: null,
        translationCostTime: null,
        showTranslation: false
      }))
      total.value = data.total || 0

      // 注意：不再这里更新 stats，确保统计卡片显示全局数据而非当前页数据

      // 动态关键词仍然基于当前搜索结果提取
      dynamicKeywords.value = extractDynamicKeywords(data.items)
    } else {
      list.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('搜索失败:', error)
    ElMessage.error('搜索失败: ' + (error.message || '未知错误'))
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 关键词点击处理
const handleKeywordClick = (keyword) => {
  if (keyword.category && ['聚合物', '表面活性剂', '破胶剂', '纳米材料', '稳定剂', '化学添加剂'].includes(keyword.cn)) {
    selectedCategory.value = keyword.cn
    searchQuery.value = ''
  } else {
    searchQuery.value = keyword.en
    selectedCategory.value = ''
  }
  handleSearch()
}

// 分类变更处理
const handleCategoryChange = (val) => {
  if (val) {
    searchQuery.value = ''
    activeFilter.value = ''
  }
  handleSearch()
}

const clearSearch = () => {
  searchQuery.value = ''
  handleSearch()
}

// 刷新数据 - 同时刷新全局统计和列表
const refreshData = () => {
  page.value = 1
  searchQuery.value = ''
  minConfidence.value = null
  selectedCategory.value = ''
  activeFilter.value = 'all'

  // 同时刷新全局统计和列表数据
  fetchGlobalStats()
  handleSearch()
}

const applyFilter = (filter) => {
  activeFilter.value = filter
  selectedCategory.value = ''

  if (filter === 'high') {
    minConfidence.value = 0.8
    searchQuery.value = ''
  } else if (filter === 'all') {
    minConfidence.value = null
    searchQuery.value = ''
  } else {
    const categoryMap = {
      'polymer': '聚合物',
      'surfactant': '表面活性剂',
      'breaker': '破胶剂'
    }
    if (categoryMap[filter]) {
      selectedCategory.value = categoryMap[filter]
      minConfidence.value = null
      searchQuery.value = ''
    } else {
      searchQuery.value = filter
      minConfidence.value = null
    }
  }
  handleSearch()
}

const handlePageChange = (newPage) => {
  page.value = newPage
  handleSearch()
}

const handleSizeChange = (newSize) => {
  pageSize.value = newSize
  page.value = 1
  handleSearch()
}

const openDetail = async (row) => {
  currentMaterial.value = row
  drawerVisible.value = true
  activeTab.value = 'metrics'
  // 清空之前的数据
  papersList.value = []
  observationsList.value = []
  chunksList.value = []
  activeChunks.value = []
  await loadPapers()
}

const loadPapers = async () => {
  if (!currentMaterial.value) return
  papersLoading.value = true
  try {
    const params = {
      material: currentMaterial.value.material,
      page: 1,
      size: 50,
      includeChunks: true,
      chunkLimit: 5,
      minConfidence: minConfidence.value
    }
    const { data } = await pageMaterialPapers(params)
    if (data && data.items) {
      // 初始化论文数据，添加翻译状态
      papersList.value = data.items.map((p, idx) => ({
        ...p,
        showChunks: false,
        translating: false,
        translatedTitle: null,
        translationSource: null,
        translationCostTime: null,
        showTranslation: false,
        _key: `paper_${p.paper?.paperId || idx}_${Date.now()}` // 稳定key
      }))

      // 初始化观察记录列表（从papers中提取）
      const obsList = []
      const chunkList = []

      data.items.forEach((paper, paperIdx) => {
        // 处理观察记录
        if (paper.detailJson) {
          try {
            const detail = JSON.parse(paper.detailJson)
            if (detail.observations && Array.isArray(detail.observations)) {
              detail.observations.forEach((obs, obsIdx) => {
                obsList.push({
                  type: obs.type,
                  content: obs.text,
                  confidence: obs.confidence,
                  chunkIds: obs.chunk_ids || [],
                  translating: false,
                  translatedContent: null,
                  translationSource: null,
                  translationCostTime: null,
                  showTranslation: false,
                  id: `obs_${paper.paper?.paperId}_${obsIdx}_${Date.now()}` // 唯一且稳定的ID
                })
              })
            }
          } catch (e) {
            console.error('解析 detailJson 失败:', e)
          }
        }

        // 处理证据片段
        if (paper.chunks?.length) {
          paper.chunks.forEach((chunk, chunkIdx) => {
            chunkList.push({
              ...chunk,
              paperTitle: paper.paper?.title,
              paperId: paper.paper?.paperId,
              translating: false,
              translatedContent: null,
              translationSource: null,
              translationCostTime: null,
              showTranslation: false,
              id: `chunk_${paper.paper?.paperId}_${chunkIdx}_${Date.now()}` // 唯一且稳定的ID
            })
          })
        }
      })

      observationsList.value = obsList
      chunksList.value = chunkList

      // 默认展开第一个证据片段（如果有）
      if (chunkList.length > 0) {
        activeChunks.value = [chunkList[0].id]
      }
    } else {
      papersList.value = []
      observationsList.value = []
      chunksList.value = []
    }
  } catch (error) {
    console.error('加载论文失败:', error)
    ElMessage.error('加载详情失败')
    papersList.value = []
    observationsList.value = []
    chunksList.value = []
  } finally {
    papersLoading.value = false
  }
}

const viewPaper = (paperId) => {
  if (paperId) {
    router.push(`/papers/${paperId}`)
  }
}

onMounted(() => {
  // 分别加载全局统计和列表数据
  fetchGlobalStats()
  handleSearch()
})
</script>

<style scoped>
/* 基础样式与第二版保持一致 */
.material-library {
  padding: 32px;
  max-width: 1440px;
  margin: 0 auto;
  background: #fafbfc;
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid #e8eaed;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}

.header-text h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  letter-spacing: -0.02em;
}

.subtitle {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 14px;
  font-weight: 400;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-stats {
  display: flex;
  gap: 12px;
}

.stat-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f3f4f6;
  border-radius: 20px;
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}

.refresh-btn {
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  background: #2563eb;
  border: none;
  box-shadow: 0 1px 3px rgba(37, 99, 235, 0.1);
  transition: all 0.2s;
}

/* 统计卡片 */
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02), 0 1px 2px rgba(0,0,0,0.04);
  border: 1px solid #e5e7eb;
  transition: all 0.2s;
}

.stat-card:hover {
  box-shadow: 0 4px 6px rgba(0,0,0,0.02), 0 2px 4px rgba(0,0,0,0.04);
  transform: translateY(-2px);
}

.stat-icon-bg {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-card.blue .stat-icon-bg { background: #eff6ff; color: #2563eb; }
.stat-card.green .stat-icon-bg { background: #f0fdf4; color: #16a34a; }
.stat-card.orange .stat-icon-bg { background: #fff7ed; color: #ea580c; }
.stat-card.purple .stat-icon-bg { background: #faf5ff; color: #9333ea; }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

/* 关键词区域 */
.keywords-section {
  margin-bottom: 24px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.keywords-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.keyword-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: #4b5563;
  position: relative;
}

.keyword-btn:hover {
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}

.keyword-btn.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.dynamic-badge {
  font-size: 10px;
  padding: 1px 5px;
  background: rgba(255,255,255,0.3);
  border-radius: 10px;
  font-weight: 600;
  margin-left: 2px;
}

.keyword-name {
  font-weight: 600;
}

.keyword-desc {
  opacity: 0.7;
  font-size: 12px;
}

/* 搜索区域 */
.search-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 28px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  border: 1px solid #e5e7eb;
}

.search-container {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-main {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 16px;
  color: #9ca3af;
  font-size: 18px;
  z-index: 1;
}

.search-input {
  width: 100%;
  padding: 12px 16px 12px 44px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 15px;
  transition: all 0.2s;
  background: #fafbfc;
}

.search-input:focus {
  outline: none;
  border-color: #2563eb;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.clear-btn {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: #f3f4f6;
  color: #6b7280;
}

.search-filters {
  display: flex;
  gap: 8px;
}

.filter-select {
  width: 140px;
}

.search-btn {
  border-radius: 8px;
  padding: 0 24px;
  background: #2563eb;
  border: none;
  font-weight: 500;
}

.quick-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

.filter-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}

.filter-tags {
  display: flex;
  gap: 8px;
}

.filter-chip {
  padding: 5px 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.filter-chip:hover {
  background: #f3f4f6;
  border-color: #d1d5db;
}

.filter-chip.active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #2563eb;
}

/* 材料网格 */
.materials-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 32px;
}

.material-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.material-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 10px 15px -3px rgba(0,0,0,0.05), 0 4px 6px -2px rgba(0,0,0,0.025);
  transform: translateY(-2px);
}

.material-card.is-translated {
  border-color: #86efac;
  box-shadow: 0 0 0 1px rgba(16, 185, 129, 0.1), 0 4px 12px rgba(16, 185, 129, 0.08);
}

.category-tag {
  position: absolute;
  top: 0;
  left: 0;
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom-right-radius: 10px;
  z-index: 2;
}

.category-tag.primary { background: #eff6ff; color: #2563eb; }
.category-tag.success { background: #f0fdf4; color: #16a34a; }
.category-tag.warning { background: #fff7ed; color: #ea580c; }
.category-tag.danger { background: #fef2f2; color: #dc2626; }
.category-tag.info { background: #f0f9ff; color: #0284c7; }
.category-tag.default { background: #f3f4f6; color: #4b5563; }

/* 置信度徽章 */
.confidence-badge {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 44px;
  height: 44px;
}

.circular-chart {
  display: block;
  margin: 0 auto;
  max-width: 100%;
  max-height: 250px;
}

.circle-bg {
  fill: none;
  stroke: #f3f4f6;
  stroke-width: 3;
}

.circle {
  fill: none;
  stroke-width: 3;
  stroke-linecap: round;
  animation: progress 1s ease-out forwards;
}

.confidence-badge.high .circle { stroke: #10b981; }
.confidence-badge.medium .circle { stroke: #f59e0b; }
.confidence-badge.low .circle { stroke: #ef4444; }

@keyframes progress {
  0% { stroke-dasharray: 0 100; }
}

.confidence-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 11px;
  font-weight: 700;
  color: #374151;
}

.card-content {
  padding-top: 8px;
  padding-right: 60px;
}

.material-header {
  margin-bottom: 20px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}

.material-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #111827;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  flex: 1;
  transition: all 0.3s;
}

.material-title.translated-text {
  color: #059669;
  font-weight: 700;
}

/* 翻译控制按钮 */
.translation-controls {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.translate-btn, .toggle-btn {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.toggle-btn {
  font-weight: 600;
}

/* 翻译反馈信息 */
.translation-feedback {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.translation-feedback .el-tag {
  font-size: 11px;
  height: 20px;
  padding: 0 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.material-code {
  font-size: 12px;
  color: #9ca3af;
  font-family: 'Monaco', 'Menlo', monospace;
  background: #f9fafb;
  padding: 2px 6px;
  border-radius: 4px;
}

.material-metrics {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.metric {
  display: flex;
  flex-direction: column;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1;
}

.metric-label {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
  font-weight: 500;
}

.metric-divider {
  width: 1px;
  height: 24px;
  background: #e5e7eb;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

.view-detail {
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
}

.arrow {
  color: #9ca3af;
  transition: all 0.2s;
}

.material-card:hover .arrow {
  color: #2563eb;
  transform: translateX(4px);
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

/* 详情抽屉 */
.detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #fafbfc;
}

.drawer-header-compact {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
  padding-right: 40px;
}

.drawer-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.category-badge {
  display: inline-flex;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.category-badge.primary { background: #eff6ff; color: #2563eb; }
.category-badge.success { background: #f0fdf4; color: #16a34a; }
.category-badge.warning { background: #fff7ed; color: #ea580c; }
.category-badge.danger { background: #fef2f2; color: #dc2626; }
.category-badge.info { background: #f0f9ff; color: #0284c7; }
.category-badge.default { background: #f3f4f6; color: #4b5563; }

.drawer-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.drawer-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  transition: all 0.3s;
}

.drawer-title.translated-text {
  color: #059669;
}

.drawer-trans-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.drawer-trans-controls .source-tag {
  font-size: 12px;
  height: 24px;
}

.drawer-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  color: #6b7280;
  font-family: monospace;
}

.confidence-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}

.confidence-tag.high { background: #d1fae5; color: #065f46; }
.confidence-tag.medium { background: #fef3c7; color: #92400e; }
.confidence-tag.low { background: #fee2e2; color: #991b1b; }

.detail-body {
  padding: 24px;
  background: #fafbfc;
  min-height: 100%;
}

/* 详情统计 */
.detail-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.detail-stat {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid #e5e7eb;
}

.stat-icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.stat-icon-wrapper.blue { background: #eff6ff; color: #2563eb; }
.stat-icon-wrapper.green { background: #f0fdf4; color: #16a34a; }
.stat-icon-wrapper.orange { background: #fff7ed; color: #ea580c; }

.stat-number {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.stat-desc {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
  font-weight: 500;
}

/* 时间线容器 */
.timeline-container :deep(.el-timeline-item__node) {
  background-color: #e5e7eb;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #e5e7eb;
}

.timeline-container :deep(.el-timeline-item__tail) {
  border-left: 2px solid #e5e7eb;
  left: 4px;
}

.timeline-container :deep(.el-timeline-item__wrapper) {
  padding-left: 28px;
}

.timeline-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
  transition: all 0.2s;
}

.timeline-card:hover {
  box-shadow: 0 4px 6px rgba(0,0,0,0.02);
  border-color: #bfdbfe;
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-row.header-row {
  margin-bottom: 16px;
}

.card-row.footer-row {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

.card-row.footer-row.muted {
  color: #9ca3af;
  font-size: 13px;
  gap: 6px;
  justify-content: flex-start;
}

/* 指标卡片样式 */
.metric-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #374151;
  font-size: 15px;
}

.metric-title .el-icon {
  color: #2563eb;
}

.metric-value-section {
  background: #f9fafb;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  margin-bottom: 16px;
}

.metric-value-section.number .value-num,
.metric-value-section.range .value-num {
  font-size: 28px;
  font-weight: 700;
  color: #2563eb;
}

.metric-value-section .range-separator {
  color: #9ca3af;
  margin: 0 8px;
  font-size: 20px;
}

.metric-value-section .value-unit {
  font-size: 14px;
  color: #6b7280;
  margin-left: 4px;
  font-weight: 500;
}

.metric-value-section.text {
  text-align: left;
  font-size: 15px;
  color: #374151;
  line-height: 1.6;
}

.conditions-section {
  margin-bottom: 16px;
  padding: 12px;
  background: #fafbfc;
  border-radius: 8px;
  border-left: 3px solid #f59e0b;
}

.section-subtitle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.conditions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
}

.condition-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: #fff;
  border-radius: 6px;
  font-size: 13px;
}

.condition-key {
  color: #9ca3af;
}

.condition-val {
  color: #374151;
  font-weight: 600;
}

.evidence-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #6b7280;
}

/* 观察记录样式 */
.observation-card .obs-type-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.obs-content-text {
  color: #374151;
  line-height: 1.8;
  font-size: 14px;
  text-align: justify;
  transition: all 0.3s;
}

.obs-content-text.translated-text {
  color: #059669;
  font-weight: 500;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 证据片段样式 */
.chunks-list :deep(.el-collapse-item__header) {
  padding: 16px 20px;
  font-size: 14px;
  font-weight: 500;
  background: #fff;
  border-bottom: 1px solid #f3f4f6;
}

.chunks-list :deep(.el-collapse-item__content) {
  padding: 0;
}

.chunk-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 20px;
}

.chunk-title-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chunk-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chunk-index {
  font-weight: 600;
}

.chunk-paper-title {
  color: #374151;
  font-weight: 500;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chunk-content-box {
  padding: 20px;
  background: #fafbfc;
}

/* 证据片段翻译控制 */
.chunk-trans-controls {
  margin-bottom: 12px;
}

.chunk-trans-feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.chunk-text {
  color: #4b5563;
  line-height: 1.8;
  font-size: 14px;
  margin: 0 0 12px;
  transition: all 0.3s;
}

.chunk-text.translated-text {
  color: #0c4a6e;
  font-weight: 500;
  background: #f0f9ff;
  padding: 12px;
  border-radius: 8px;
  border-left: 3px solid #0ea5e9;
}

.chunk-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #9ca3af;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}

/* 论文卡片样式 */
.paper-header-section {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f3f4f6;
}

.paper-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.paper-title-row h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
  flex: 1;
  transition: all 0.3s;
}

.paper-title-row h4.translated-text {
  color: #059669;
}

.paper-title-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.paper-meta-row {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #6b7280;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.paper-properties {
  margin-bottom: 16px;
}

.prop-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.prop-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  background: #f9fafb;
  border-radius: 6px;
}

.prop-label {
  font-size: 12px;
  color: #9ca3af;
}

.prop-value {
  font-size: 14px;
  color: #374151;
  font-weight: 600;
}

.paper-chunks-section {
  margin-bottom: 16px;
}

.chunks-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  background: #eff6ff;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #2563eb;
  font-weight: 500;
  transition: all 0.2s;
}

.chunks-toggle:hover {
  background: #dbeafe;
}

.expand-icon {
  margin-left: auto;
  transition: transform 0.3s;
}

.expand-icon.is-expand {
  transform: rotate(180deg);
}

.chunks-list-nested {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nested-chunk {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
  border-left: 3px solid #3b82f6;
}

.chunk-index-tag {
  font-size: 12px;
  color: #3b82f6;
  font-weight: 700;
  background: #eff6ff;
  padding: 2px 8px;
  border-radius: 4px;
  height: fit-content;
  white-space: nowrap;
}

.chunk-text-small {
  margin: 0;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.6;
}

.paper-actions-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #f3f4f6;
}

/* 响应式 */
@media (max-width: 1024px) {
  .stats-section {
    grid-template-columns: repeat(2, 1fr);
  }

  .detail-stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .material-library {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    gap: 20px;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .stats-section {
    grid-template-columns: 1fr;
  }

  .search-container {
    flex-direction: column;
  }

  .search-filters {
    width: 100%;
  }

  .filter-select {
    flex: 1;
  }

  .materials-grid {
    grid-template-columns: 1fr;
  }

  .title-row {
    flex-wrap: wrap;
  }

  .drawer-title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .paper-title-row {
    flex-direction: column;
  }

  .paper-title-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .chunk-paper-title {
    max-width: 200px;
  }

  .conditions-grid {
    grid-template-columns: 1fr;
  }

  .prop-grid {
    grid-template-columns: 1fr;
  }
}
</style>
