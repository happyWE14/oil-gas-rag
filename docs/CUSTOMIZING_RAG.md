# 把当前项目定制为其他类型 RAG

## 0. 直接启用通用文档问答

当前代码已经提供一个默认关闭的单文档 RAG 接口。它复用现有 PDF 转换、分块、Embedding、`document_chunk` 和 pgvector 查询，不改变材料抽取主流程。

在 `backend/.env` 中配置：

~~~properties
RAG_CUSTOM_ENABLED=true
RAG_CUSTOM_TOP_K=8
RAG_CUSTOM_MAX_CONTEXT_CHARS=12000
RAG_CUSTOM_SYSTEM_PROMPT=只依据给定证据回答，引用 [ChunkId-<id>]，证据不足时明确拒答。
~~~

调用方式：

~~~http
POST /api/rag/query
Content-Type: application/json

{
  "documentId": "已完成向量化的 paper_id",
  "question": "需要依据该文档回答的问题",
  "topK": 8
}
~~~

接口具有以下确定边界：

- `documentId` 只检索一个已索引文档；
- `topK` 限制为 1–20；
- 问题长度限制为 2000 字符；
- 发送给模型的证据受 `RAG_CUSTOM_MAX_CONTEXT_CHARS` 限制；
- 空召回直接返回 `NO_RELEVANT_EVIDENCE`，不调用大模型；
- 返回的 `evidence` 是实际进入模型上下文的文本，而不是未经裁剪的原始召回结果；
- 系统 Prompt 只能由可信部署配置设置，客户端不能覆盖。

这条链路适合作为其他研究领域的最小基线。跨文档检索、租户权限、文档版本和删除传播仍需按业务模型单独设计。

## 1. 先理解哪些可以复用

当前系统可以拆成七个稳定阶段：

~~~text
数据源 → 文档转换 → 分块 → Embedding → 召回 → 领域抽取/生成 → 证据与评测
~~~

可以复用的是流水线、任务恢复、状态、数据契约和证据思路；不能直接复用的是油气材料 Prompt、字段、搜索式和评测标准。

## 2. 真实扩展点

| 要替换的能力 | 当前代码入口 | 定制动作 |
|---|---|---|
| 数据源 | SearchExecutor 及 arXiv/CORE/Semantic Scholar 实现 | 新增平台适配器、认证、分页、限流和去重 |
| 下载 | PaperDownloadGateway / PaperDownloadExecutor | 支持网页、对象存储、企业文件系统或用户上传 |
| 解析 | OcrTransformGateway | 增加 HTML、DOCX、扫描件、邮件或代码解析 |
| 分块 | DocumentChunkSplitter | 按领域标题、条款、病例段、表格或代码符号切分 |
| Embedding | AliyunEmbeddingConfig、EmbeddingBusinessProperties | 同时更新模型、维度、模型 ID、数据库 Schema 和重建策略 |
| 召回 | DocumentContextRetriever、DocumentChunkMapper | 修改查询模板、metadata filter、Top-K 和距离策略 |
| 领域输出 | MaterialAnalysisAgent、Prompt、DTO、数据库表 | 改字段、枚举、校验、置信度和拒答规则 |
| 跨文档问答 | QueryRouter、ResearchTools、DeepResearchService | 增加领域工具、引用校验和会话策略 |
| 搜索投影 | MaterialEsDocument、MaterialSearchRepositoryImpl | 改 ES Mapping、Analyzer、聚合与排序 |

只有一个实现时不要先创建新的抽象层。先替换现有实现，出现第二个真实实现后再提取公共接口。

## 3. 定制步骤

### 第一步：定义问题和输出

不要从选模型开始。先写 20–50 个真实问题和预期答案结构：

~~~json
{
  "answer": "领域结论",
  "evidence": [
    {
      "document_id": "doc-001",
      "chunk_id": "chunk-12",
      "quote_or_span": "支持该结论的原文范围"
    }
  ],
  "confidence": 0.0,
  "abstain_reason": null
}
~~~

如果业务是信息抽取，应先定义字段 Schema；如果是问答，应先定义引用与拒答标准。

### 第二步：建立文档身份与权限

至少确定：

- document_id 是否稳定；
- 同一文档版本如何表示；
- Chunk 是否能追溯页码、章节或段落；
- 用户能否检索该文档；
- 删除文档时如何删除 Chunk、向量、缓存和索引；
- 重复导入是否幂等。

企业 RAG 必须在检索前过滤权限，不能只在生成后隐藏答案。

### 第三步：选择分块策略

| 文档 | 推荐初始策略 |
|---|---|
| 法律法规 | 按章、条、款切分，保留法规名、版本、生效日期 |
| 医疗指南 | 按推荐等级、适应证、禁忌、证据等级切分 |
| 财报/研报 | 按公司、报告期、指标表、风险段落切分 |
| 企业制度 | 按文档、章节、流程步骤和角色切分 |
| API 文档 | 按 endpoint、参数、响应和示例切分 |
| 代码库 | 按符号、类、函数和依赖关系切分 |

先用可解释规则建立基线。只有评测证明规则不足时再加入语义分块。

### 第四步：统一 Embedding 契约

以下值必须一起变更：

~~~text
Provider 模型输出维度
= EmbeddingBusinessProperties.dimensions
= PostgreSQL vector(n)
= ES dense_vector dims（如果使用）
= 测试断言
= 数据版本与重建记录
~~~

不要直接 ALTER 一个已有向量列并假设旧数据仍然有效。通常需要新列/新表、重新嵌入、双读验证和切换。

### 第五步：组合召回方式

从最便宜且可解释的组合开始：

1. metadata filter：租户、文档类型、时间、版本；
2. lexical retrieval：精确名称、编号、法规条款、错误码；
3. vector retrieval：同义表达和语义相近片段；
4. structured SQL：数值、时间、状态和关系；
5. rerank：只有 Recall 基线稳定后再加入；
6. generation：只给模型必要证据。

当前项目的经验是：不同问题需要不同检索工具。所有查询都走向量并不更先进。

### 第六步：实现拒答与引用校验

最小规则：

- 没有检索结果时拒答；
- 最高分低于阈值时明确证据不足；
- 每个重要结论必须带 document_id 和 chunk_id；
- 引用 ID 必须存在于本次上下文；
- 数值结论需要校验单位和范围；
- 将模型原始输出、Prompt 版本和检索参数保存到受控日志。

进一步可以做 NLI/LLM-as-judge，但必须保留人工抽样，不能让另一个模型成为唯一真值。

### 第七步：先评测，再加架构

最小评测集建议：

- 30–50 份可公开或有授权的文档；
- 100–200 个问题或抽取字段；
- 正确 Chunk、答案要点、允许的同义表达；
- 10%–20% 应拒答问题；
- 按问题类型分层报告。

核心指标：

| 层 | 指标 |
|---|---|
| 摄取 | 解析成功率、页码/章节保留率、重复率 |
| 检索 | Recall@K、MRR、nDCG、空召回率 |
| 生成 | 正确性、Faithfulness、Citation Precision/Recall、拒答准确率 |
| 系统 | P50/P95 延迟、错误率、Token、单请求成本 |

没有固定评测集时，不应宣称某个 Chunk、模型、Reranker 或 Agent “提升了效果”。

## 4. 五种定制示例

### 4.1 法律 RAG

- SearchExecutor 换成法规库、判例库或内部合同库；
- Chunk 以条款和定义为单位；
- metadata 增加法域、效力层级、版本和生效日期；
- 输出增加条款号、冲突法规和适用时间；
- 重点评测引用准确性、版本过期和拒答。

### 4.2 医疗 RAG

- 数据只使用有授权的指南、药品说明书和内部知识；
- 增加指南版本、证据等级、适应证与患者人群；
- 强制区分信息检索与医疗建议；
- 高风险回答需要人工复核和明确免责声明；
- 不把本项目直接用于诊断或治疗决策。

### 4.3 金融研报 RAG

- 把 Paper 替换为公司公告、财报、会议纪要和研报；
- 使用 SQL 处理数值与期间，向量检索处理叙述性风险；
- 单位、币种、会计期间必须标准化；
- 避免把历史材料生成成实时投资建议。

### 4.4 企业知识库

- 数据源接入 SharePoint、对象存储、Wiki 或工单系统；
- document_id 绑定 ACL 与版本；
- 检索阶段必须带用户/部门过滤；
- 删除和权限变化需要同步清理向量与缓存；
- 重点评测权限泄露、过期文档和答案引用。

### 4.5 技术文档 / 代码 RAG

- Parser 保存文件、符号、语言、版本和依赖关系；
- 词法检索负责类名、错误码、配置键；
- 向量检索负责意图和概念；
- 上下文加入调用方、被调用方和测试；
- 输出引用具体文件与行号，不只引用仓库名。

## 5. 什么时候才需要 Agent

满足以下条件后再考虑：

1. 一个问题确实需要多步工具调用；
2. 固定 Router 无法覆盖且错误可以被限制；
3. 每个工具有输入 Schema、权限、超时和幂等；
4. 有执行步数、Token、成本和副作用上限；
5. 有可回放 Trace 和失败评测集。

在此之前，确定性工作流通常更便宜、更容易测试，也更适合形成可重复的科研实验。

## 6. 最小科研验证闭环

选择一个领域，先完成以下可重复验证闭环：

1. 20 份有权使用的文档；
2. 50 个标注问题；
3. 一种词法召回 + 一种向量召回；
4. 一份检索与引用评测报告；
5. 一条从请求到引用 Chunk 的 Trace；
6. 一张准确架构图；
7. 一个说明失败案例的 README。

完成上述基线并记录失败案例后，再根据评测结果决定是否引入知识图谱、多 Agent、GraphRAG 或其他向量数据库。
