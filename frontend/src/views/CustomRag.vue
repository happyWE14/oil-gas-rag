<template>
  <div class="custom-rag-page">
    <section class="hero-card">
      <div>
        <div class="eyebrow">DOMAIN-AGNOSTIC RAG</div>
        <h1>自定义 RAG 问答</h1>
        <p>选择一个已完成向量化的文档，系统只依据召回证据生成回答，并保留证据片段用于核验。</p>
      </div>
      <el-tag type="success" effect="dark" round>自定义模式</el-tag>
    </section>

    <div class="workspace">
      <el-card class="query-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><ChatDotRound /></el-icon>
            <span>检索问答</span>
          </div>
        </template>

        <el-form label-position="top" @submit.prevent="submitQuery">
          <el-form-item label="目标文档">
            <el-select
              v-model="form.documentId"
              filterable
              allow-create
              default-first-option
              clearable
              :loading="loadingDocuments"
              placeholder="选择文档或输入 documentId"
              style="width: 100%"
            >
              <el-option
                v-for="document in documents"
                :key="document.documentId"
                :label="document.title || document.documentId"
                :value="document.documentId"
              >
                <div class="paper-option">
                  <span>{{ document.title || '未命名文档' }}</span>
                  <small>{{ document.chunkCount }} Chunks · {{ document.state }}</small>
                </div>
              </el-option>
            </el-select>
            <div class="field-tip">
              已加载 {{ documents.length }} 篇拥有向量分块的论文；其他领域文档完成入库和向量化后也会自动出现。
            </div>
          </el-form-item>

          <el-form-item label="问题">
            <el-input
              v-model="form.question"
              type="textarea"
              :rows="6"
              maxlength="2000"
              show-word-limit
              placeholder="例如：文档的核心结论是什么？请给出对应证据。"
              @keydown.ctrl.enter="submitQuery"
            />
          </el-form-item>

          <div class="query-actions">
            <el-form-item label="召回数量 Top-K" class="top-k-field">
              <el-input-number v-model="form.topK" :min="1" :max="20" />
            </el-form-item>
            <el-button type="primary" size="large" :loading="querying" @click="submitQuery">
              <el-icon><Search /></el-icon>
              开始检索问答
            </el-button>
          </div>
        </el-form>

        <el-alert
          v-if="errorMessage"
          class="status-alert"
          type="error"
          :title="errorMessage"
          show-icon
          :closable="false"
        />
      </el-card>

      <el-card class="result-card" shadow="never">
        <template #header>
          <div class="card-title">
            <el-icon><DocumentChecked /></el-icon>
            <span>回答与证据</span>
            <el-tag v-if="result" :type="result.abstained ? 'warning' : 'success'" size="small">
              {{ result.abstained ? '已拒答' : '证据已召回' }}
            </el-tag>
          </div>
        </template>

        <el-skeleton v-if="querying" :rows="6" animated />
        <el-empty v-else-if="!result" description="完成一次提问后，这里将展示回答和证据片段" />
        <div v-else-if="result.abstained" class="abstained-result">
          <el-result icon="warning" title="未找到足够证据" :sub-title="result.abstainReason || 'NO_RELEVANT_EVIDENCE'" />
        </div>
        <div v-else class="answer-result">
          <div class="answer-block">
            <h3>模型回答</h3>
            <div class="answer-text">{{ result.answer }}</div>
          </div>

          <div class="evidence-block">
            <div class="evidence-heading">
              <h3>召回证据</h3>
              <span>{{ result.evidence?.length || 0 }} 个片段</span>
            </div>
            <el-collapse v-if="result.evidence?.length">
              <el-collapse-item
                v-for="(item, index) in result.evidence"
                :key="item.chunkId || index"
                :name="index"
              >
                <template #title>
                  <el-tag size="small" effect="plain">Chunk {{ item.chunkId }}</el-tag>
                </template>
                <div class="evidence-content">{{ item.content }}</div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, DocumentChecked, Search } from '@element-plus/icons-vue'
import { listCustomRagDocuments, queryCustomRag } from '@/api/rag'

const documents = ref([])
const loadingDocuments = ref(false)
const querying = ref(false)
const result = ref(null)
const errorMessage = ref('')
const form = reactive({
  documentId: '',
  question: '',
  topK: 6
})

const loadDocuments = async () => {
  loadingDocuments.value = true
  try {
    const response = await listCustomRagDocuments()
    documents.value = response.data || []
  } catch {
    ElMessage.warning('文档列表加载失败，仍可手动输入 documentId')
  } finally {
    loadingDocuments.value = false
  }
}

const submitQuery = async () => {
  if (querying.value) return
  if (!form.documentId) {
    ElMessage.warning('请选择或输入目标文档')
    return
  }
  if (!form.question.trim()) {
    ElMessage.warning('请输入问题')
    return
  }

  querying.value = true
  result.value = null
  errorMessage.value = ''
  try {
    const response = await queryCustomRag({
      documentId: form.documentId,
      question: form.question.trim(),
      topK: form.topK
    })
    result.value = response.data
  } catch (error) {
    const status = error.response?.status
    errorMessage.value = status === 404
      ? '自定义 RAG 接口未启用，请设置 RAG_CUSTOM_ENABLED=true 后重启后端。'
      : (error.response?.data?.message || '问答请求失败，请检查模型 API Key、向量服务和后端日志。')
  } finally {
    querying.value = false
  }
}

onMounted(loadDocuments)
</script>

<style scoped>
.custom-rag-page {
  min-height: calc(100vh - 50px);
  padding: 24px;
  background: #f5f7fb;
}

.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  margin-bottom: 20px;
  color: #fff;
  background: linear-gradient(135deg, #1d4ed8 0%, #6d28d9 58%, #0f766e 100%);
  border-radius: 16px;
  box-shadow: 0 12px 32px rgb(37 99 235 / 18%);
}

.eyebrow {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  opacity: 0.8;
}

.hero-card h1 {
  margin: 0 0 10px;
  font-size: 28px;
}

.hero-card p {
  max-width: 760px;
  margin: 0;
  line-height: 1.7;
  opacity: 0.86;
}

.workspace {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(480px, 1.35fr);
  gap: 20px;
  align-items: start;
}

.query-card,
.result-card {
  border: 0;
  border-radius: 14px;
}

.card-title,
.evidence-heading,
.query-actions {
  display: flex;
  align-items: center;
}

.card-title {
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
}

.card-title .el-tag {
  margin-left: auto;
}

.paper-option,
.evidence-heading,
.query-actions {
  justify-content: space-between;
}

.paper-option {
  display: flex;
  gap: 18px;
  width: 100%;
}

.paper-option small,
.field-tip,
.evidence-heading span {
  color: #909399;
}

.field-tip {
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.5;
}

.query-actions {
  gap: 18px;
}

.top-k-field {
  margin-bottom: 0;
}

.query-actions > .el-button {
  margin-top: 22px;
}

.status-alert {
  margin-top: 20px;
}

.result-card {
  min-height: 520px;
}

.answer-block {
  padding: 20px;
  margin-bottom: 22px;
  background: #f0f7ff;
  border: 1px solid #dbeafe;
  border-radius: 12px;
}

.answer-block h3,
.evidence-heading h3 {
  margin: 0;
  font-size: 15px;
}

.answer-text,
.evidence-content {
  margin-top: 14px;
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
}

.evidence-heading {
  margin-bottom: 10px;
}

.evidence-heading span {
  font-size: 13px;
}

@media (max-width: 1050px) {
  .workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .custom-rag-page {
    padding: 14px;
  }

  .hero-card {
    padding: 22px;
  }

  .query-actions {
    align-items: flex-end;
  }
}
</style>
