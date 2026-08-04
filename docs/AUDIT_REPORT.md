# 项目架构与优化审计报告

## 1. 审计范围与方法

本报告基于 2026-08-03 的本机工作区快照，覆盖：

- backend：Java 17 / Spring Boot 后端；
- frontend：Vue 3 / Vite 前端；
- PostgreSQL 建表脚本与 MyBatis Mapper；
- Redis、Elasticsearch、可选 RocketMQ 配置；
- 文献检索、转换、分块、Embedding、材料抽取与 Deep Research；
- 测试、构建、配置、密钥、绝对路径和公开仓库卫生。

事实判断优先级为：可执行源码与 SQL Mapper > 主建表脚本 > 配置 > 旧 README 和架构图。没有执行逻辑支撑的设计名词不会被算作已实现能力。

原始后端位于 refactor/ddd_lkj 分支，HEAD 为 9bd2f62，但包含约 35 项已修改内容和多项未跟踪文件。原始前端仓库没有正式提交，文件处于 staged、modified 和 untracked 混合状态。本发行副本因此保留“当前可见实现”，不代表远端提交，也没有复制两个子项目的 .git。

## 2. 规模画像

| 指标 | 审计值 |
|---|---:|
| 后端 Java 文件 | 378 |
| REST 映射 | 50 |
| PostgreSQL CREATE TABLE | 18 |
| Paper 生命周期状态 | 15 |
| 整理后测试类 | 7 |
| Maven 发现测试 | 19 |
| 离线通过 | 6 |
| 条件跳过的集成测试 | 13 |

数量只描述规模，不代表质量。真正重要的是检索、状态与数据契约是否一致。

## 3. 分层架构

后端采用接近 DDD 的四层组织：

~~~text
interfaces
  REST Controller、请求/响应边界
       ↓
application
  应用服务、任务编排、工作流、事件处理
       ↓
domain
  Paper、Task、Search、Material 模型及状态规则
       ↑
infrastructure
  PostgreSQL、MyBatis、Redis、Elasticsearch、外部 API、MQ
~~~

优点：

- Paper 和 Task 两套状态被区分，长任务不会完全依赖 HTTP 生命周期；
- SearchExecutor、OcrTransformGateway 等位置形成了真实扩展点；
- 领域事件、重试、租约和 Outbox 为故障恢复提供基础；
- PostgreSQL 同时承担事务数据、JSONB、FTS 和 pgvector，适合当前规模。

问题：

- 部分接口或“Agent”命名比真实职责更大；
- 有两组 Outbox/ES 同步设施，边界和事务一致性尚未统一；
- 默认 Spring Event 与可选 RocketMQ 两种执行方式提高了理解成本；
- 部分旧文档、截图和配置落后于当前源码；
- Controller 尚无完整认证授权。

## 4. 一篇论文的真实生命周期

~~~text
DISCOVERED
→ PRE_ANALYZING
→ PRE_RELEVANT ───────────────┐
→ DOWNLOADING                 │
→ DOWNLOADED                  │
→ TRANSFORMING                │
→ TRANSFORMED                 │
→ EMBEDDING                   │
→ EMBEDDING_COMPLETED         │
→ EXTRACTING                  │
→ MATERIAL_EXTRACTED          │
→ COMPLETED                   │
                              │
PRE_ANALYZING → IRRELEVANT → COMPLETED
任意受支持阶段 → FAILED → ABANDONED
~~~

状态白名单位于 PaperStateTransition，聚合内部通过 transitionTo 校验。原代码把 IRRELEVANT 同时声明为终态，又允许 IRRELEVANT→COMPLETED；整理版移除了这一冲突并留下回归测试。

任务类型共有搜索、预分析、下载、转换、关系扩展、Embedding、材料抽取和清理。每类任务拥有并发度、执行器和 stale-running 超时配置。

## 5. RAG 架构审计

### 5.1 离线索引

PaperTransformExecutor 取得 Markdown 后，由 DocumentChunkSplitter 做结构感知分块，再由 PaperEmbeddingExecutor 批量调用 Embedding 模型，最终通过 DocumentChunkMapper upsert。

稳定业务键是 paper_id + order_no。它让相同论文重复向量化时可以覆盖同一逻辑 Chunk，而不是无界追加。

### 5.2 向量契约

原工作区存在四项冲突：

- Java 实际请求 2048 维；
- PostgreSQL 使用 vector(4096)；
- ES 映射使用 2048 维；
- 数据标签写成 QWEN3_EMBEDDING_8B，实际 Provider 模型为 text-embedding-v4。

整理版完成：

- Embedding 的 Provider 模型、维度、领域模型 ID、批大小统一进入 EmbeddingBusinessProperties；
- 初始化 SQL 改成 vector(2048)；
- 入库与查询向量都执行维度检查；
- 模型标签改为 TEXT_EMBEDDING_V4；
- batch-size 配置真正控制调用批量；
- 模型返回数量与输入文本数量不一致时立即失败，避免错位写库。

对于已经存在的 4096 维数据库，没有执行破坏性 ALTER 或清空向量。正确做法是先备份，再创建新列/新表并重新嵌入，验证完成后切换。

### 5.3 材料抽取

材料链路是项目中最完整的向量 RAG：

1. 按通用材料查询从单篇论文召回候选 Chunk；
2. LLM 输出材料名称、别名、类别、角色、置信度和 chunk_ids；
3. 对每种材料再次构造属性查询并召回；
4. LLM 提取属性、数值、单位、条件和证据；
5. 保存原始结果、结构化材料、观察、指标与候选证据。

它是“检索增强信息抽取”，不等同于开放域问答。

### 5.4 Deep Research

Deep Research 使用规则 QueryRouter 选择 ResearchTools。工具主要查询 PostgreSQL FTS、材料表和指标表，然后由模型生成 SSE 文本和引用。

因此准确描述是“词法 + 结构化 RAG”，不是当前项目的向量问答链路。QueryRouter 也不是自主 Agent：模型没有动态规划或任意工具执行权。

### 5.5 Elasticsearch

已经存在文档模型、Analyzer、文本/嵌套查询、批量同步和向量字段，但以下环节未闭环：

- 构建 ES 文档时没有稳定填充向量；
- search 没有实际使用 queryVector；
- textWeight / vectorWeight 没有进入分数融合；
- 索引名、部分范围条件、Bulk 错误与 dataHash 仍有一致性问题。

因此 README 只声明 ES 文本和结构化过滤，不声明 Hybrid 已完成。

## 6. 可靠性审计

| 机制 | 结论 |
|---|---|
| 状态白名单 | 已实现，已补冲突回归测试 |
| 任务幂等 | 有状态检查、业务键和 upsert，但仍需端到端重复事件测试 |
| 分类重试 | 已实现限流、瞬时和不可重试分类 |
| Backoff | 已实现策略与 Retry-After 支持 |
| Lease | 已实现 running 超时检测与恢复调度 |
| Outbox | 有完整骨架，但多条同步路径需收敛 |
| DLQ | 未实现 |
| Circuit breaker | 未实现 |
| Graceful degradation | 局部存在，不是统一系统能力 |
| Backpressure | 有按任务类型并发门控，不是全局自适应流控 |

## 7. 安全与可移植性修复

- 删除 7 处疑似 sk- 类型值、CORE Key、数据库密码和示例 PowerShell Key 命令；
- 删除 Dockerfile 私人 IP、密码、个人邮箱和私有模型代理；
- 后端 PDF 路径、前端 API Origin 和本地论文目录全部改成配置；
- CORS 从单一配置来源读取，不再同时存在 Controller 通配符；
- LocalFileController 增加 paperId 校验、规范化根目录约束，默认禁用服务器文件夹打开；
- 文件信息接口不再返回服务器绝对路径；
- 发布副本排除 Git 元数据、PDF、数据、向量、日志、node_modules、target 和 dist。

## 8. 构建与测试

后端：

~~~powershell
$env:JAVA_HOME='C:\path\to\jdk-17'
mvn clean test
~~~

原项目没有声明支持 JUnit 5 的 Surefire，导致 Maven 显示 0 tests。整理版固定 Surefire 3.2.5 后结果为：

~~~text
Tests run: 19
Failures: 0
Errors: 0
Skipped: 13
BUILD SUCCESS
~~~

13 个跳过项是会访问 PostgreSQL 或 Elasticsearch 的集成测试，只在 RUN_INTEGRATION_TESTS=true 时启用。

前端：

- vue-tsc --noEmit -p tsconfig.app.json 通过；
- Vite 7 完成过一次生产 bundle；
- npm run build 改为顺序执行类型检查和构建，避免并发占用；
- 当前 Node 24 / npm 11 环境的 npm ci 出现 npm 自身 Exit handler never called；
- Vite 后续重复构建在本机出现 Windows 原生进程异常，因此发布结论不写成“所有 Node 环境稳定通过”；
- 首次成功 bundle 给出两个超过 500 kB 的 Chunk 警告，后续应按页面拆分 ECharts/Element Plus 依赖。

## 9. 剩余风险与优先级

### P0：公开部署前必须处理

1. 增加身份认证、接口授权、速率限制和租户/用户数据边界；
2. 对上传与下载内容增加文件大小、MIME、恶意文档和超时限制；
3. 轮换原仓库出现过的所有密钥，即使它们已经失效；
4. 对模型日志、论文正文和用户查询制定脱敏与保留策略。

### P1：求职作品集最有价值

1. 建立 30–50 篇论文、100–200 条问题/抽取项的版本化评测集；
2. 测 Recall@K、MRR、Citation Precision/Recall、Faithfulness、延迟和 Token；
3. 修复指标单位换算、区间语义和 QueryRouter 中文覆盖；
4. 为模型输出引入真正 JSON Schema 或显式 Schema Validator；
5. 收敛 Outbox 和 ES 同步路径，并做重复事件/部分失败测试。

### P2：规模增长后处理

1. 评估 halfvec、降维、分区或其他 ANN 方案，而不是直接宣称 HNSW；
2. 完成 ES 向量入库、融合策略和离线对照评测；
3. 拆分前端超大 Chunk；
4. 增加 OpenTelemetry、指标、成本和 SLO；
5. 只有在规则路由确实限制复杂任务时，再引入受限工具调用或 Agent。

## 10. 求职结论

这个项目最适合证明：

- Java / Spring AI 应用后端；
- 文档摄取、Chunk、Embedding、pgvector 与结构化抽取；
- PostgreSQL FTS 与 Elasticsearch 的职责选择；
- 长任务的状态、重试、并发和恢复；
- 识别“架构声明与实现证据不一致”的维护能力。

它当前不适合证明：

- 模型训练或微调；
- 大规模分布式向量检索；
- 生产级安全和 SRE；
- 完整 Agent / 多 Agent；
- 有真实数据支撑的 RAG 性能提升。

面试时把这些边界讲清楚，比使用更多框架名更可信。

## 11. 验收结论

| 验收项 | 结果 |
|---|---|
| 中英文 README | 通过 |
| 真实 Mermaid 架构与流程图 | 通过 |
| RAG 定制到其他领域的指导 | 通过 |
| 求职学习与面试指南 | 通过 |
| 能力声明—源码证据矩阵 | 通过 |
| 密钥、Token、密码和私人路径扫描 | 通过，最终扫描 0 命中 |
| 后端 JDK 17 干净编译 | 通过 |
| 后端测试发现与执行 | 通过：19 总计、6 通过、13 条件跳过、0 失败 |
| 前端类型检查 | 通过 |
| 前端生产 bundle | 曾成功生成；重复构建受当前 Node 24 Windows 原生异常影响 |
| PostgreSQL/Redis/ES 端到端 | 未验证：本轮 Docker Desktop 守护进程未能启动 |
| 真实 LLM/OCR/Embedding 调用 | 未执行，避免付费与数据外发 |
| 公网生产就绪 | 不通过：缺少完整鉴权、评测、观测和外部集成验证 |

发行副本适合作为源码学习、毕业设计说明和 AI 应用开发求职作品集；在补齐 P0 项之前，不应直接部署到公网。
