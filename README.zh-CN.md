# 基于证据的文献 RAG 智能分析系统

[English README](README.md)

这是一个面向学术文献分析的端到端 RAG 系统：采集论文、筛选相关性、把 PDF 转成可检索证据，再用检索增强的大模型抽取材料、实验条件和性能指标。

本项目按照**大模型应用后端 + 确定性文档工作流**定位，不把它包装成已经生产就绪的自主 Agent。下面所有能力均按现有代码证据标注。

## 它解决什么问题

领域研究人员经常需要阅读大量论文，重复提取材料名称、实验条件、性能指标及其原文证据。本系统将这一过程拆成可追踪流水线：

1. 从 arXiv、CORE 和 Semantic Scholar 搜索论文；
2. 使用大模型对标题和摘要进行相关性预筛；
3. 下载 PDF 并转换为 Markdown；
4. 根据 Markdown 标题、段落、代码块和表格进行结构感知分块；
5. 生成 Embedding 并写入 PostgreSQL/pgvector；
6. 在单篇论文范围内召回证据片段；
7. 先识别材料，再抽取材料属性和指标；
8. 保存结构化观察记录，并提供文本检索、指标查询和研究问答接口。

项目真正体现的能力不是“调用过大模型 API”，而是如何把不稳定、有延迟的模型放进一个有状态、可重试、能够回溯证据的后端系统。

## 实际运行效果与复用边界

### 石油论文专用模式：已实现界面

#### 1. 系统运行看板

![展示任务与流水线状态的系统看板](docs/images/dashboard-overview.png)

*图 1：系统运行看板，同时展示等待、运行、成功、失败、取消任务，以及下载、预分析、搜索、转换、向量化和材料提取等负载。*

![任务趋势、最近任务与快捷操作](docs/images/dashboard-recent-tasks.png)

*图 2：任务趋势、当日吞吐、最近任务状态，以及新建任务、搜索论文和进入论文库等快捷入口。*

#### 2. 多源学术检索配置

![学术搜索配置列表](docs/images/search-configurations.png)

*图 3：面向石油关键词的持久化搜索配置，可查看数据源、执行状态、采集进度和调度控制。*

![多源学术搜索配置表单](docs/images/search-configuration-form.png)

*图 4：同时配置 arXiv、CORE、Semantic Scholar 的查询条件、主数据源、分页、排序、过滤器和执行计划。*

#### 3. 论文采集与处理

![单篇论文处理流水线](docs/images/paper-processing-pipeline.png)

*图 5：单篇论文从相关性预分析、下载、转换、向量化到材料提取的状态进度。*

![包含论文处理状态的石油领域文献工作台](docs/images/paper-library.png)

*图 6：石油相关论文工作台，展示向量化、材料提取、失败和重试等处理状态。*

#### 4. 材料实体知识库

![石油领域材料实体库](docs/images/material-library.png)

*图 7：材料实体库包含油气专业术语、来源论文数、抽取记录数、置信度、搜索过滤和 Redis 翻译缓存标识。*

#### 5. 结构化抽取与证据追溯

![结构化材料指标与实验条件](docs/images/material-structured-metrics.png)

*图 8：结构化材料指标，包括耐温数值、对应实验条件、置信度和关联证据数量。*

![带有观察记录和证据片段的石油材料抽取结果](docs/images/evidence-detail.png)

*图 9：观察记录展示作用机理、应用场景、局限性、置信度、翻译状态和支撑片段。*

![材料抽取结果对应的检索证据片段](docs/images/material-evidence-passages.png)

*图 10：证据片段标签页展示支撑 CMHEC 抽取结果的实际索引 Chunk。*

![带有关联证据的来源论文记录](docs/images/material-source-paper.png)

*图 11：来源论文标签页将抽取实体重新关联到论文元数据及保留的证据片段，便于人工复核。*

以上 11 张截图覆盖系统监控、任务编排、检索配置、论文采集、单篇处理进度、结构化指标、观察记录、证据片段和来源追踪，能够证明已经实现的石油论文处理链路，但不直接证明系统在其他语料上的准确率。

### 可复用的文档 RAG 链路

![可复用的科研文档 RAG 工作流](docs/images/research-workflow.png)

*图 12：项目采用的文档处理工作流。图片底部的部分运维和部署文字属于设计目标；已经验证的运行边界以本文后续能力表为准。*

可扩展性来自后端流水线和配置边界：

| 层次 | 石油领域专用实现 | 迁移到其他领域的复用方式 |
|---|---|---|
| 数据源 | 面向油气论文配置 arXiv、CORE、Semantic Scholar 检索 | 替换或增加数据源适配器，导入有权使用的目标文档 |
| 解析与分块 | 处理论文 PDF、Markdown 结构、表格、公式和引用 | 保留处理流水线，按目标文档结构调整 `DocumentChunkSplitter` |
| 向量召回 | `DocumentContextRetriever` 在单个 `documentId` 内执行 pgvector Top-K | 新领域文档完成向量化后直接复用同一召回器 |
| 大模型生成 | 使用材料识别和属性抽取 Prompt | 开启 `/api/rag/query`，通过 `RAG_CUSTOM_SYSTEM_PROMPT` 配置领域规则 |
| 证据输出 | 材料观察记录保留候选 Chunk ID 和原文片段 | 通用接口返回实际发送给模型、经过长度裁剪的证据文本 |

因此，本仓库同时包含已经运行的石油论文专用实现，以及可配置的单文档通用 RAG 基线。迁移到新领域后，仍需准备对应语料、metadata 规则、Prompt 和评测集，完成验证后才能声明该领域的实际效果。

## 能力完成度

| 能力 | 状态 | 代码边界 |
|---|---|---|
| arXiv、CORE、Semantic Scholar 搜索适配器 | 已实现，外部运行待验证 | 存在各平台 SearchExecutor、分页和限流 |
| 论文相关性预分析 | 已实现 | Prompt、阈值、DTO 解析、状态流转、任务处理器均存在 |
| PDF 下载与转换 | 已实现，依赖外部 Provider | 存在本地、MinerU、PaddleOCR、DeepSeek OCR 路径 |
| Markdown 结构感知分块 | 已实现 | 支持标题、段落、围栏代码块、表格、大小和重叠配置 |
| PostgreSQL pgvector 存储与召回 | 已实现 | document_chunk 使用 vector(2048)，先限定 paper_id 再做余弦排序 |
| 两阶段材料抽取 RAG | 已实现 | 材料识别 → 属性抽取，并携带候选 chunk ID |
| 可选通用文档 RAG | 已实现，默认关闭 | 配置开关控制 REST 接口，复用单文档 pgvector 召回并返回实际送入模型的证据 |
| Deep Research | 已实现 | 规则 QueryRouter + PostgreSQL FTS/指标 SQL + SSE |
| Elasticsearch 文本与结构化材料检索 | 已实现 | Analyzer、嵌套过滤、排序和批量同步路径存在 |
| Elasticsearch 向量/混合检索 | 部分实现 | 有向量字段和请求参数，但向量入库与融合评分尚未闭环 |
| 原生 JSON Schema Structured Output | 未实现 | 当前依靠 Prompt JSON、清洗、Jackson 和 DTO |
| HNSW/IVFFlat ANN | 未实现 | 当前有效向量链路是单篇论文范围内的精确排序 |
| 自主 Agent / 多 Agent | 未实现 | 当前是确定性任务编排，模型不会自主选择任意工具 |

完整证据见 [能力声明—源码证据矩阵](docs/CLAIM_EVIDENCE_MATRIX.md)。

## 系统架构

~~~mermaid
flowchart LR
    UI["Vue 3 前端"] --> API["Spring Boot REST / SSE"]
    API --> APP["应用服务与任务编排"]
    APP --> DOMAIN["Paper 聚合、15 状态生命周期、白名单转换"]
    APP --> SEARCH["arXiv / CORE / Semantic Scholar"]
    APP --> TRANSFORM["下载与 PDF 转 Markdown"]
    APP --> INDEX["分块与 Embedding"]
    INDEX --> PG[("PostgreSQL + pgvector")]
    APP --> RAG["材料抽取 / 可选文档问答"]
    RAG --> PG
    APP --> RESEARCH["FTS / 指标工具 + LLM 回答"]
    RESEARCH --> PG
    APP --> ES[("Elasticsearch 文本检索")]
    APP --> REDIS[("Redis 缓存 / 协调")]
    APP -. 可选 .-> MQ["RocketMQ 模式"]
~~~

### RAG 数据流

~~~mermaid
sequenceDiagram
    participant T as 文档转换
    participant C as 分块器
    participant E as Embedding 模型
    participant V as pgvector
    participant R as 召回器
    participant L as Chat 模型
    participant P as PostgreSQL

    T->>C: Markdown + 论文元数据
    C->>E: 按可配置批大小发送 Chunk
    E-->>C: 2048 维向量
    C->>V: Upsert paper_id + order_no + 内容 + 向量
    R->>E: 材料、属性或用户问题
    E-->>R: 查询向量
    R->>V: 单篇论文范围 Top-K
    V-->>R: 候选证据片段
    R->>L: Prompt + Chunk 证据标签
    L-->>R: 结构化结果 + chunk_ids
    R->>P: 材料、观察、指标和证据关联
~~~

## 项目目录

~~~text
.
├── backend/                 Java 17 / Spring Boot 后端
│   ├── src/main/java/       领域、应用、基础设施、接口层
│   ├── src/main/resources/  配置与 MyBatis Mapper
│   ├── src/test/            离线测试与按需集成测试
│   └── sql/                 PostgreSQL + pgvector 建表脚本
├── frontend/                Vue 3 / Vite 前端
├── docs/                    架构、审计、扩展与安全文档
├── README.md                英文说明
└── README.zh-CN.md          中文说明
~~~

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5.5、Spring AI 1.1.0-M4、MyBatis-Plus |
| 大模型接入 | OpenAI 兼容的 Chat 与 Embedding API |
| 主数据库 | PostgreSQL、pgvector、JSONB、tsvector |
| 检索 | PostgreSQL FTS、Elasticsearch Java Client 8.11.4 |
| 缓存与协调 | Redis、Redisson |
| 可选消息模式 | 默认 Spring Event；可选 RocketMQ Profile |
| 前端 | Vue 3、Vite 7、Element Plus、ECharts、Axios |

## 快速启动

### 前置条件

- JDK 17、Maven 3.6+
- Node.js 20.19+ 或 22.12+
- PostgreSQL + pgvector、Redis、Elasticsearch 8.11.x（ES 可以由 Docker Desktop 启动）
- 完整 AI 流程所需的 Chat 模型与 Embedding 模型 API Key

### 1. 配置环境变量

~~~bash
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
~~~

至少需要配置 SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME 和 SPRING_DATASOURCE_PASSWORD。执行 LLM 和向量化阶段前，再填写 OPENAI_API_KEY 与 OPENAI_EMBEDDING_API_KEY。

仓库中不包含任何可用密钥，详见 [安全审计](docs/SECURITY_AUDIT.md)。

### 2. 准备本地基础设施

使用本机服务或 Docker Desktop 启动 PostgreSQL/pgvector（5432）、Redis（6379）和 Elasticsearch（9200），然后在新 PostgreSQL 数据库中执行 backend/sql/paper_collector_schema.sql。

本仓库有意不保留 Compose 文件：当前环境只通过 Docker Desktop 运行 Elasticsearch，PostgreSQL/pgvector 与 Redis 使用本机服务，因此尚未验证一套完整 Compose 编排。

### 3. 启动后端

~~~bash
cd backend
mvn spring-boot:run
~~~

默认后端地址为 http://localhost:8080。

### 4. 启动前端

~~~bash
cd frontend
npm install
npm run dev
~~~

开发服务器会把 /api 代理到后端。只有当前后端位于其他域名时才需要设置 VITE_API_ORIGIN。

## 关键配置契约

| 环境变量 | 是否必需 | 作用 |
|---|---|---|
| SPRING_DATASOURCE_URL / USERNAME / PASSWORD | 是 | PostgreSQL 连接，数据库必须安装 pgvector |
| OPENAI_API_KEY | LLM 阶段必需 | OpenAI 兼容 Chat API |
| OPENAI_BASE_URL | 可选 | Chat API 地址 |
| OPENAI_CHAT_MODEL | 可选 | Chat/推理模型名 |
| OPENAI_EMBEDDING_API_KEY | 向量阶段必需 | Embedding API |
| OPENAI_EMBEDDING_BASE_URL | 可选 | Embedding 服务地址 |
| OPENAI_EMBEDDING_MODEL | 可选 | Provider 模型名 |
| OPENAI_EMBEDDING_DIMENSION | 可选 | 必须与 PostgreSQL vector(2048) 一致，默认 2048 |
| CORE_API_KEY | 可选 | CORE 搜索 |
| SEMANTIC_SCHOLAR_API_KEY | 可选 | 提高 Semantic Scholar 配额 |
| PAPER_STORAGE_PATH | 可选 | 后端可访问的 PDF 目录 |
| APP_CORS_ALLOWED_ORIGINS | 可选 | 允许的浏览器来源，逗号分隔 |
| LOCAL_FILE_OPEN_FOLDER_ENABLED | 可选 | 默认 false，仅适合本机开发 |
| RAG_CUSTOM_ENABLED | 可选 | 是否注册通用 `POST /api/rag/query` 接口，默认 false |
| RAG_CUSTOM_TOP_K | 可选 | 每次查询默认召回片段数，范围 1–20，默认 8 |
| RAG_CUSTOM_MAX_CONTEXT_CHARS | 可选 | 发送给 Chat 模型的证据文本上限，默认 12000 字符 |
| RAG_CUSTOM_SYSTEM_PROMPT | 可选 | 由部署方配置、面向目标科研领域的可信系统指令 |

## 本轮验证结果

审计日期：2026-08-03。

- 后端已使用 Corretto JDK 17 完成干净编译和 Maven 测试；
- 共发现 21 个测试：8 个离线测试通过，13 个集成测试按环境条件跳过，0 个失败；
- 集成测试需配置 PostgreSQL、Elasticsearch，并设置 RUN_INTEGRATION_TESTS=true；
- 前端 vue-tsc 类型检查通过；
- Vite 已生成生产构建，仍存在大 Chunk 性能警告；
- 系统已连接本地 PostgreSQL/pgvector、Redis 和 Docker Desktop 中的 Elasticsearch，并使用 DeepSeek Chat 与 DashScope `text-embedding-v4` 完成一次自定义 RAG 端到端实测；
- 本轮只生成了该问题所需的查询向量，没有重新执行全量 Embedding，也没有爬取或复制论文。

## 必须诚实说明的限制

- 当前没有完整鉴权体系，只适合本机开发或受信网络，不能直接作为公网生产服务；
- 本地 PDF 接口已限制 paperId 和存储边界，远程打开服务器文件夹默认关闭；
- Prompt 约束 JSON 不等于 Provider 原生 JSON Schema；
- 保存候选 Chunk 不代表每条生成结论都已通过蕴含验证；
- Elasticsearch Hybrid 尚未闭环，当前真正工作的向量 RAG 是单篇论文 pgvector 召回；
- 通用 RAG 接口仍限定单个文档，尚未实现跨文档排序、多租户隔离和访问控制；
- 仓库不包含论文 PDF、数据库导出、向量导出、模型输出日志或真实业务数据；
- 当前没有 Recall@K、Faithfulness、延迟、成本等评测结论；
- 在确认代码、截图和第三方资产权属前，不添加 License。

## 可选的自定义文档 RAG

材料抽取仍是默认工作流。对于已经完成转换、分块、Embedding 和 pgvector 入库的任意文档，可以显式开启一个更通用的单文档问答接口。前端选择器通过 `GET /api/rag/documents` 自动列出所有至少拥有一个非空向量分块的论文；本次实测数据库共返回 92 篇，无需逐篇手工配置。

~~~properties
RAG_CUSTOM_ENABLED=true
RAG_CUSTOM_TOP_K=8
RAG_CUSTOM_MAX_CONTEXT_CHARS=12000
RAG_CUSTOM_SYSTEM_PROMPT=只依据给定证据回答，并使用目标科研领域的规范术语。
~~~

重启后端后，针对一个已索引文档发起查询：

~~~bash
curl -X POST http://localhost:8080/api/rag/query \
  -H "Content-Type: application/json" \
  -d '{"documentId":"existing-paper-id","question":"这篇文档支持哪些主要结论？","topK":6}'
~~~

### 真实运行示例

以下结果由系统在 2026-08-03 实际运行生成：查询向量使用 DashScope `text-embedding-v4`（2048 维），证据约束回答使用 DeepSeek `deepseek-chat`。

- 论文：*Preparation of a novel fracturing fluid with good heat and shear resistance*
- 问题：该论文中的哪些配方和实验结果证明了压裂液的耐温、耐剪切性能？
- 结果：系统回答提取了 0.3 wt% MAS-1 + 0.8 wt% Zr-CL 配方、150 ℃ 和 170 s⁻¹ 条件下剪切 120 分钟后约 135 mPa·s 的黏度，以及高剪切条件下的黏度保持率对比，并返回 6 个可展开核验的证据分块。

![对已索引石油论文执行真实自定义 RAG 问答](docs/images/custom-rag-live-example.png)

*图：真实自定义 RAG 运行结果。API Key 仅通过进程环境变量注入，未写入仓库。*

没有召回结果时，接口返回 `abstained: true`，并且不会调用 Chat 模型。当前 `documentId` 对应既有 `paper_id`，因此它是可复用的单文档 RAG 基线，不是已经完成的多租户知识库平台。

迁移到其他科研领域时，可以替换或扩展数据源与解析器，调整 `DocumentChunkSplitter`，配置可信系统 Prompt，补充必要的 metadata 过滤，并建立标注评测集。更换 Embedding 时必须同时迁移模型、维度配置、pgvector Schema 和已有向量。

[RAG 定制指南](docs/CUSTOMIZING_RAG.md)给出了法律、医疗、金融、企业知识库和技术文档等方向的具体映射。

## 详细文档

- [架构与审计报告](docs/AUDIT_REPORT.md)
- [能力声明—源码证据矩阵](docs/CLAIM_EVIDENCE_MATRIX.md)
- [其他领域 RAG 定制指南](docs/CUSTOMIZING_RAG.md)
- [安全审计](docs/SECURITY_AUDIT.md)

## License

当前没有授予开源许可证。只有在代码、截图与第三方资产权属确认后才应添加。
