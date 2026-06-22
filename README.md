# HireSpark · AI 求职智能体平台

> 业务定位:**面向求职者的 AI 智能体业务平台**,从简历上传到多轮模拟面试、JD 对齐、面试复盘、简历优化,完整跑通"找工作"这一真实业务链路。
>
> 技术定位:Java 17 + Spring Boot 3 的企业级 **Agentic RAG** 系统,自研 LLM 客户端 + 多模型路由 + 分布式 SingleFlight + 多 Agent 协作 + MCP 工具调用。
>
> 本仓库基于开源项目 [nageoffer/ragent](https://github.com/nageoffer/ragent) 二次开发,核心业务能力与系统骨架源自 Ragent,本仓库在其之上做了工程化打磨与定制。

---

## 📖 项目简介

HireSpark 不是又一个 RAG Demo,它是一个**真实可用的求职业务系统**。求职者上传简历,系统会:

1. 解析简历(支持 PDF / DOC / DOCX / Markdown / 图片 OCR);
2. 抓取 JD 或手动录入 JD,与简历做多维度对齐分析;
3. 基于对齐结果,自动生成**阶段化的多轮模拟面试计划**;
4. 在面试过程中,**多 Agent 协作**完成追问、评分、复盘;
5. 产出**面试报告**(含雷达图、Rubric 维度打分、关键证据引用);
6. 基于面试表现,生成**简历优化建议**与下一轮优化任务。

整个过程对求职者是端到端可见的:JD 对齐报告、面试过程全程 Trace、面试报告 PDF、简历优化建议 Excel/Word,以及**管理后台 Dashboard** 给运营/HR 看。

---

## 🏛️ 项目来源说明(必读)

本仓库是基于 **nageoffer/ragent**(一个企业级 Agentic RAG 平台)二次开发的版本,主要做了以下工作:

- **业务化定制**:在 Ragent 框架基础上,以"求职/招聘"为主线,沉淀了 `career` 业务包(19 张实体表、5 类 Agent、1 套追问规则引擎、1 套评分维度);
- **工程化打磨**:增加 Lua 脚本协调的分布式 SingleFlight(防 LLM 重复调用)、自适应线程池、RocketMQ 幂等生产消费链路、Agent Trace 与 Tool Invocation 全链路审计;
- **可观测性**:管理后台增加 Dashboard 时间桶聚合(Agent 调用次数、Token 消耗、面试会话数等)。

使用本仓库请遵守 Apache-2.0 License,并保留对原项目 [nageoffer/ragent](https://github.com/nageoffer/ragent) 的署名。

---

## 🧭 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| Web 框架 | Spring Boot 3.5.7 |
| 持久层 | MyBatis-Plus 3.5.14 + PostgreSQL(HikariCP 连接池) |
| 缓存 / 分布式协调 | Redis + Redisson 4.0 |
| 消息队列 | RocketMQ Spring Boot Starter 2.3.5 |
| 向量数据库 | Milvus 2.6.6(可切换为 pgvector,见 `rag.vector.type`) |
| 文档解析 | Apache Tika 3.2(PDF/DOC/DOCX/Markdown)、Docx4j、CommonMark、OpenHTMLtoPDF |
| LLM 客户端 | **自研 OkHttp 实现**(详见 `infra-ai` 模块) |
| 多模型适配 | 阿里百炼(BaiLian)、SiliconFlow、Ollama 等 OpenAI 兼容协议 |
| Agent / 工具协议 | MCP SDK 1.1.2(独立 `mcp-server` 模块) |
| 熔断 / 限流 | Resilience4j 2.2.0 |
| 鉴权 | Sa-Token 1.43 |
| 链路追踪 | 自研 `RagTraceContext` / `RagTraceNode` / `RagTraceRoot` |
| Excel / 文档 | EasyExcel 3.3、OpenHTMLtoPDF、Docx4j |
| 实时通信 | Spring WebSocket(面试实时音频转写) |
| 爬虫 | WebMagic 0.7.5(招聘 JD 抓取) |
| OCR / TTS | 科大讯飞私有协议接入 |
| 前端 | Vue 3 + Vite + Tailwind(见 `frontend/` 子项目) |
| 构建 / 部署 | Maven 多模块 + Dockerfile(基于 `eclipse-temurin:17`) |

**为什么不用 Spring AI / LangChain4j?**

本项目自研了 LLM 客户端(`infra-ai` 模块),原因是生产环境需要:

- **多模型路由**:百炼主调 + SiliconFlow 兜底 + Ollama 本地降级;
- **SSE 流式取消**:用户切走页面要立刻停掉流;
- **首包探测 + 自动降级**:模型 5 秒没回首包就切通道;
- **多厂商协议适配**:百炼、SiliconFlow、OpenAI、Ollama 共存。

这些能力 Spring AI / LangChain4j 在早期版本没有完整抽象,所以基于 OkHttp 自研。`infra-ai` 模块已抽象为可替换的 `ChatClient / EmbeddingClient / RerankClient`,后续若要切换框架,业务代码零改动。

---

## 🧱 项目结构

```
HireSpark/
├── bootstrap/                # 业务装配入口,核心业务代码都在这里
│   ├── src/main/java/com/nageoffer/ai/ragent/
│   │   ├── RagentApplication.java
│   │   ├── career/           # 求职业务核心(简历/面试/对齐/优化)
│   │   ├── conversation/     # 会话编排与消息序列号
│   │   ├── admin/            # 管理后台(Dashboard / 配置)
│   │   ├── rag/              # RAG 检索增强生成
│   │   ├── ingestion/        # 文档入库管道
│   │   ├── knowledge/        # 知识库管理
│   │   ├── user/             # 用户与权限
│   │   └── mcp/              # MCP 客户端
│   └── src/main/resources/
│       ├── application.yaml  # 主配置
│       ├── prompt/           # Prompt 模板(16 个 .st 文件)
│       ├── lua/              # Redis Lua 脚本(SingleFlight 协调)
│       └── fonts/            # 中文字体(OpenHTMLtoPDF 中文渲染)
├── framework/                # 自研基础框架层
│   └── src/main/java/com/nageoffer/ai/ragent/framework/
│       ├── cache/            # 缓存抽象
│       ├── idempotent/       # 幂等提交/幂等消费切面(SpEL)
│       ├── mq/producer/      # RocketMQ 生产者 / 事务监听器
│       ├── retrieve/         # BM25 评分器
│       ├── threadpool/       # 自适应线程池 / 拒绝策略 / 动态刷新
│       ├── trace/            # RagTrace 链路追踪
│       └── exception/        # 统一异常体系
├── infra-ai/                 # 自研 AI 基础设施
│   └── src/main/java/com/nageoffer/ai/ragent/infra/
│       ├── chat/             # Chat 客户端(多厂商适配 + SSE 流式)
│       ├── embedding/        # Embedding 客户端
│       ├── rerank/           # Rerank 客户端
│       ├── model/            # 模型路由 / 健康检查 / 优先级调度
│       └── token/            # Token 计数
├── mcp-server/               # MCP 工具服务(独立进程)
├── frontend/                 # Vue 3 前端
├── docs/                     # 业务设计文档
├── resources/
│   ├── database/             # 数据库迁移脚本
│   └── docker/               # docker-compose 编排
├── scripts/                  # 验收脚本
├── Dockerfile
└── pom.xml                   # Maven 父 POM
```

---

## 🚀 核心业务能力

### 1. 简历与 JD 处理

| 能力 | 关键代码 |
| --- | --- |
| 多格式简历解析 | `bootstrap/.../career/service/parser/` + Apache Tika + Docx4j |
| 图片简历 OCR | `bootstrap/.../career/service/ocr/` + 科大讯飞 |
| JD 抓取 | `bootstrap/.../career/crawler/WebMagicJobPostingCrawler.java` |
| JD-简历对齐分析 | `bootstrap/.../career/service/decision/` + `JobAlignmentReportDO` |
| 简历多版本管理 | `bootstrap/.../career/dao/entity/ResumeVersionDO` + `ResumeOptimizationTaskDO` |

### 2. 多 Agent 协作的模拟面试

`bootstrap/.../career/service/interview/agent/`

| Agent | 职责 |
| --- | --- |
| `InterviewCoordinatorAgent` | 基于 JD 对齐摘要 + RAG 增强 + Prompt Template,生成阶段化面试计划 |
| `JdAlignmentInterviewAgent` | 围绕 JD 关键能力点追问,验证候选人匹配度 |
| `TechnicalInterviewerAgent` | 技术深度追问 |
| `InterviewReflectorAgent` | 面试复盘,产出评分维度证据 |

每次 Agent 调用都走 **SingleFlight LLM 服务** 防重复调用(详见下文技术亮点),并通过 `CareerAgentTraceService` 落库执行轨迹。

### 3. 追问决策规则引擎

`bootstrap/.../career/service/followup/`

责任链模式,5 条规则按顺序匹配,首个命中即返回,带审计字段 `hitRule`:

| 规则 | 触发条件 |
| --- | --- |
| `CompletedStateGuardRule` | 拦截已完成会话 |
| `FollowUpLimitRule` | 限制追问次数 |
| `LowScoreRule` | 低分强制追问 |
| `MissingPointsRule` | 关键知识点缺失追问 |
| `AiSuggestionRule` | 接受 AI 追问建议 |

### 4. 面试评分与报告

`bootstrap/.../career/service/scoring/` + `InterviewReportDO` + Rubric 维度配置

面试结束后,生成:

- 多维度评分雷达图(`CareerRadarItemVO`);
- 关键证据引用(每条评分都关联到面试中的具体回答);
- 报告 PDF / Word 导出(`CareerExcelExportController`、`openhtmltopdf-pdfbox`、`docx4j`)。

### 5. 简历优化建议

`bootstrap/.../career/service/optimization/` + `ResumeOptimizationTaskDO` + `ResumeOptimizationSuggestionDO`

- 基于面试表现生成具体可执行的优化建议;
- 支持建议审核 / 驳回 / 重提;
- 优化后生成新简历版本(`ResumeVersionDO`)。

### 6. 管理后台 Dashboard

`bootstrap/.../admin/`

- `DashboardController`:KPI 概览、时间桶趋势、Agent 调用指标;
- `DashboardBucketedStatsQueryService`:按时间桶(日/周/月)聚合 Agent 调用次数、Token 消耗、面试会话数。

---

## 🔥 技术亮点(为什么这是一个能写进简历的项目)

### 1. 分布式 SingleFlight 防 LLM 重复调用

**痛点**:Agent 高频调用 LLM 时,网络重试 + 用户重复点击会产生大量重复请求,既烧 Token 又会引发并发规划不一致。

**方案**:`bootstrap/.../career/service/singleflight/` + `bootstrap/src/main/resources/lua/career_single_flight_*.lua`

- **L1 本地回放缓存**(`CareerSingleFlightLocalReplayCache`):进程内秒级复用最近响应;
- **L2 Redis Lua 协调**(`CareerSingleFlightRedisCoordinator` + 4 个 Lua 脚本):
  - `career_single_flight_acquire.lua`:原子抢占 owner;
  - `career_single_flight_heartbeat.lua`:心跳续约;
  - `career_single_flight_complete.lua`:完成回调;
  - `career_single_flight_replay.lua`:结果回放;
- **L3 数据库兜底**(`CareerSingleFlightRecordDO` + `CareerSingleFlightRecordMapper`):Redis 不可用时降级到 MySQL 唯一索引;
- **Fencing Token**:防止延迟回包覆盖新 owner 的结果;
- **心跳机制**:长任务 owner 持续续约,超时未续约则被抢占。

### 2. 多模型路由 + 首包探测 + 自动降级

`infra-ai/.../model/`

- `ModelSelector` + `ModelTarget`:按优先级选择模型;
- `ModelHealthStore`:记录每个模型的健康状态(连续失败次数、最近一次延迟);
- `ModelRoutingExecutor`:首包探测,5 秒内无响应则切下一通道;
- `RoutingLLMService` + `RoutingEmbeddingService` + `RoutingRerankService`:统一路由抽象。

支持厂商:阿里百炼(BaiLian)、SiliconFlow、Ollama,任何兼容 OpenAI 协议的厂商可一行配置接入。

### 3. SSE 流式响应 + 流式取消

`infra-ai/.../chat/`

- `OpenAIStyleSseParser`:解析 SSE 流;
- `ProbeStreamBridge`:首包探测桥;
- `StreamAsyncExecutor`:异步流式执行;
- `StreamCancellationHandle` + `StreamCancellationHandles`:每个会话一个取消句柄,WebSocket 断开 / 用户切走页面立刻停掉 LLM 调用。

### 4. RocketMQ 幂等生产与消费

`framework/.../mq/producer/` + `framework/.../idempotent/`

- `RocketMQProducerAdapter` + `DelegatingTransactionListener` + `TransactionChecker`:事务消息支持;
- `IdempotentSubmitAspect` + `IdempotentConsumeAspect`:基于 **SpEL 表达式**从入参 / 消息体抽取幂等 Key;
- 状态机:`CONSUMING → CONSUMED / FAILED`,失败可重试,幂等可证。

长耗时任务(简历解析、面试复盘、报告生成)全部走异步链路,业务接口秒回。

### 5. 自适应线程池

`framework/.../threadpool/`

- `AdaptiveBufferedThreadPoolExecutor`:缓冲队列 + 自适应拒绝策略;
- `AdaptiveRejectedHandler`:区分 CallerRuns / DiscardOldest / 自定义降级;
- `RefreshableExecutor`:运行期动态刷新核心线程数、最大线程数、队列容量。

Agent 多 Agent 并行调用 LLM 时,线程池隔离避免下游雪崩。

### 6. RAG 检索增强

`bootstrap/.../career/service/retrieval/`

- `HierarchicalRetrievalService`:**粗筛 + 精检**两阶段检索
  - 粗筛 `topK=6` 锁定候选 doc_id;
  - 精检 `topK=12` 在候选 doc_id 范围内拉完整 chunk;
  - 通过 `metadata["doc_id"] in [...]` 缩小搜索空间约 10 倍。
- `CareerHydeQueryGenerator`:Hyde(Hypothetical Document Embeddings)查询改写;
- `CareerRetrievalEnhancement`:证据分类(KB 知识 / MCP 工具 / 业务数据);
- `CareerRetrievalEnhancementServiceImpl`:Hyde + 多路检索 + 重排序流水线。

### 7. Agent 全链路 Trace

`bootstrap/.../career/service/observability/` + `framework/.../trace/`

- 每次 Agent 调用 LLM:落 `CareerAgentExecutionTraceDO`(场景、Prompt、响应、Token、耗时、TraceId);
- 每次 Agent 调用工具:落 `CareerAgentToolInvocationDO`(工具名、入参、出参、状态);
- 会话级统计:`CareerAgentSessionStatsDO`;
- 全局链路:`RagTraceContext` / `RagTraceNode` / `RagTraceRoot` 支持树形 Trace。

管理员可在 Dashboard 回放任意一次 Agent 决策,看到完整的 Prompt / 工具调用链 / 响应。

### 8. MCP 工具调用

`mcp-server/`(独立模块,可独立部署)

- 基于 `io.modelcontextprotocol.sdk 1.1.2`;
- 内置默认 MCP Server(`http://localhost:9099`);
- 业务侧通过 `bootstrap/.../mcp/` 调用工具;
- `prompt/mcp-parameter-extract.st`:从用户问题中抽取 MCP 工具调用参数;
- `prompt/answer-chat-mcp.st` / `answer-chat-mcp-kb-mixed.st`:纯工具调用 / 工具 + KB 混合回答模板。

---

## 🚄 快速开始

### 前置依赖

- JDK 17
- Maven 3.9+
- PostgreSQL 14+
- Redis 6+
- RocketMQ 5.x(NameServer + Broker)
- Milvus 2.6+(可选,也可切 pgvector,见 `rag.vector.type: pg`)

### 本地启动

```bash
# 1. 克隆仓库
git clone https://github.com/ZXT-shuaibi/HireSpark.git
cd HireSpark

# 2. 初始化数据库
psql -U postgres -f resources/database/schema_pg.sql
psql -U postgres -f resources/database/init_data_pg.sql

# 3. 修改 bootstrap/src/main/resources/application.yaml
#    配置 datasource / redis / rocketmq / milvus / LLM API Key

# 4. 启动 MCP Server(可选,独立进程)
mvn -pl mcp-server spring-boot:run

# 5. 启动主应用
mvn -pl bootstrap spring-boot:run
```

应用启动后监听 `http://localhost:9090/api/ragent`,OpenAPI 文档访问 `/swagger-ui`。

### Docker 启动

```bash
docker build -t hirespark:latest .
docker run -p 9090:9090 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/ragent \
  -e SPRING_DATA_REDIS_HOST=redis-host \
  -e ROCKETMQ_NAME_SERVER=rocketmq-host:9876 \
  hirespark:latest
```

完整 docker-compose 见 `resources/docker/ragent-dev-stack.compose.yaml`。

### 验证脚本

```bash
python scripts/verify_post_fusion_acceptance.py
```

该脚本会检查 Dockerfile 健康检查、CI 流程、Compose 健康端点、OpenAPI 分组、中文字体策略、TTS 契约等关键交付项。

---

## 🗺️ 业务流程(求职侧)

```
[简历上传]
   │  Apache Tika / OCR
   ▼
[简历解析 → ResumeVersionDO]
   │
   ├───► [手动录入 JD]  ──┐
   │                     │
   └───► [JD 抓取(WebMagic)] ──┐
                             │
                             ▼
              [JD-简历对齐分析]
              JobAlignmentReportDO
                             │
                             ▼
              [Coordinator Agent]
              生成阶段化面试计划
                             │
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
   [JdAlignmentAgent] [TechnicalAgent] [ReflectorAgent]
            │                │                │
            └────────────────┼────────────────┘
                             │
                             ▼
              [追问决策规则链]
              FollowUpDecisionRule × 5
                             │
                             ▼
              [面试评分 + 报告生成]
              InterviewReportDO + PDF/Word
                             │
                             ▼
              [简历优化建议]
              ResumeOptimizationTaskDO
                             │
                             ▼
              [Dashboard 统计 / Agent Trace 回放]
```

---

## 🔧 改造方向(适合作为二次开发起点)

1. **新业务场景**:复用 Agent 框架与 SingleFlight,新增"AI 模拟群面"、"AI 模拟 HR 面"、"AI 模拟笔试"等场景;
2. **RAG 增强**:在 `HierarchicalRetrievalService` 之前增加 query 改写器(query2doc / HyDE / step-back prompting);
3. **评测体系**:构造 100 题面试评测集,接入 Agent Trace 做端到端自动评测;
4. **A/B 实验框架**:支持同时上线两版 Prompt Template 并对比效果;
5. **Spring AI 对比模块**:新增 `infra-ai-springai` 实验模块,用 Spring AI 重构某条链路作为对比验证;
6. **行业垂直化**:把"求职"改为"律师考试备考"、"医生执业考试"、"教师资格证"等垂直行业。

---

## 📜 License

本仓库遵循 **Apache License 2.0**,详见 [LICENSE](LICENSE) 文件。

基于 [nageoffer/ragent](https://github.com/nageoffer/ragent) 二次开发,使用请保留原作者署名。

---

## 🙏 致谢

- [nageoffer/ragent](https://github.com/nageoffer/ragent) —— 提供了企业级 Agentic RAG 平台骨架与核心设计
- 所有为本项目提交 Issue / PR 的贡献者
