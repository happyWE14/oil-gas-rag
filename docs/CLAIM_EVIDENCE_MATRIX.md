# 能力声明—源码证据矩阵

审计基线：2026-08-03 的整理副本。状态含义：

- **已实现**：入口、执行逻辑和主要数据读写可以在源码中闭环；
- **部分实现**：存在结构或局部路径，但不能证明端到端能力；
- **未实现**：当前源码没有相应执行逻辑；
- **待外部验证**：代码存在，但依赖尚未在本轮运行。

| 声明 | 状态 | 主要证据 | 准确边界 |
|---|---|---|---|
| arXiv 搜索 | 已实现，待外部验证 | ArxivSearchExecutor、ArxivApiProperties、RateLimiter | 存在查询、分页、限流；本轮未请求线上 API |
| CORE 搜索 | 已实现，待外部验证 | CoreSearchExecutor、CoreApiProperties | Key 已改为环境变量；本轮未请求线上 API |
| Semantic Scholar 搜索 | 已实现，待外部验证 | SemanticScholarSearchExecutor、SemanticScholarRequestGate | 包含共享限流与关系扩展开关 |
| OpenAlex 搜索 | 未实现 | PaperSource 中只有 OPEN_ALEX 枚举 | 没有对应 SearchProvider 和 SearchExecutor |
| PDF 下载 | 已实现，Provider 待验证 | PaperDownloadExecutor、PaperDownloadGateway | 成功率取决于论文来源、版权与外部下载服务 |
| PDF 转 Markdown | 部分实现，Provider 待验证 | PaperTransformExecutor、OcrTransformGateway 及多个实现 | 多 Provider 路径存在，本轮未调用 OCR 服务 |
| 结构感知分块 | 已实现 | DocumentChunkSplitter | 保留标题、段落、表格和围栏结构；不会自动把章节标题复制给每个后续 Chunk |
| 可配置 Chunk overlap | 已实现 | EmbeddingBusinessProperties、DocumentChunkSplitter | overlap 被限制为不超过 chunkSize 的三分之一 |
| 可配置 Embedding batch | 已实现 | EmbeddingBusinessProperties、PaperEmbeddingExecutor | 本轮修复了原先硬编码为 8 的问题 |
| pgvector 存储 | 已实现 | document_chunk、PgVectorTypeHandler、DocumentChunkMapper.xml | 初始化契约为 vector(2048) |
| 向量维度保护 | 已实现 | EmbeddingBusinessProperties.requireExpectedDimensions | 入库向量和查询向量都会在数据库访问前检查维度 |
| 全库 ANN / HNSW | 未实现 | 建表脚本未创建 ANN 索引 | 当前查询先按 paper_id 限定，再精确余弦排序 |
| 两阶段材料 RAG | 已实现 | MaterialAnalysisAgent、DocumentContextRetriever、MaterialExtractionExecutor | 先识别材料，再按材料检索属性证据 |
| 通用单文档问答 | 已实现，默认关闭 | CustomRagController、CustomRagService、DocumentContextRetriever | 仅查询一个已向量化 documentId；空召回拒答，尚无跨文档排序与鉴权 |
| 原生 JSON Schema Structured Output | 未实现 | Prompt、AiJsonResponseCleaner、Jackson DTO | JSON schema 文本写在 Prompt 中，不等于 Provider 强制结构化输出 |
| Chunk 证据追踪 | 部分实现 | chunk_ids、DocumentChunk、MaterialObservation | 能保存候选/引用 ID；尚无逐条结论蕴含验证 |
| Deep Research 向量 RAG | 未实现 | DeepResearchService、ResearchTools | 真实实现主要是 PostgreSQL FTS 与结构化 SQL |
| Deep Research SSE | 已实现 | DeepResearchService 和对应 Controller | 会话存储与生产级断线恢复仍有限 |
| Elasticsearch 文本检索 | 已实现 | MaterialSearchRepositoryImpl、MaterialEsDocument | 支持文本、嵌套字段、过滤和排序 |
| Elasticsearch Hybrid | 部分实现 | 请求含 queryVector/权重，映射含 dense_vector | 当前文档构建未稳定写入向量，查询未融合向量得分 |
| 15 状态论文生命周期 | 已实现 | PaperState | 实际为 15 个状态，不是旧图中的 11 个或需求文本中的 14 个 |
| 白名单状态转换 | 已实现 | PaperStateTransition、Paper 聚合方法 | 本轮修复 IRRELEVANT→COMPLETED 与终态判断冲突 |
| 异步任务记录 | 已实现 | TaskRecord、TaskOrchestrator、TaskProcessor | 任务与论文状态分离 |
| 并发门控 / 背压 | 已实现 | TaskExecutionProfile、TaskOrchestrator、TaskExecutionConfig | 按任务类型限制并发；没有全系统自适应流量控制 |
| 重试与退避 | 已实现 | TaskFailureClassifier、BackoffStrategy、TaskDeferredScheduler | 区分限流、瞬时和不可重试错误 |
| Lease / stale-running 恢复 | 已实现 | TaskOrchestrator.executeLeaseCheck、TaskRecoveryScheduler | 默认本地调度；RocketMQ 模式有延迟消息与本地回退 |
| Transactional Outbox | 部分实现 | OutboxEventRecorder、OutboxRelayScheduler、MaterialSyncApplicationService | 存在两组 Outbox/ES 同步设施，所有入口是否统一经过同一事务边界仍需收敛 |
| Dead-letter queue | 未实现 | 未发现 DLQ 消费与重放实现 | 失败状态和重试上限不等于真正 DLQ |
| Circuit breaker | 未实现 | 未发现 Resilience4j 或等价状态机 | 有限流、重试和部分 fallback，但不是断路器 |
| Graceful degradation | 部分实现 | RocketMQ 调度失败回落本地、部分 Provider fallback | 没有统一降级策略、SLO 或故障演练 |
| Redis 缓存 | 已实现，外部待验证 | TranslationServiceImpl、Redisson 配置 | Redis 不是当前向量知识库 |
| API 鉴权 | 未实现 | Controller 与配置中未发现完整认证授权链 | 仅建议本地或可信网络运行 |
| 可复现单元测试 | 已实现 | Surefire 3.2.5、8 个离线测试 | 13 个外部集成测试默认按环境变量跳过 |

## 面试表达原则

可以说：

> 项目实现了单篇论文范围内的 pgvector 证据召回与两阶段结构化抽取，并把 FTS、结构化 SQL 和 Elasticsearch 文本搜索用于不同查询场景。

不能说：

> 项目已经实现全库 HNSW、Elasticsearch 混合向量检索、原生 JSON Schema、完整 Agent、DLQ、断路器和生产级鉴权。

把“预留字段”“设计文档”“类名”和“真实运行闭环”区分开，是这份项目最重要的工程素养证明之一。
