# Ragent Career 深度融合下一轮实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. 每个任务独立提交，提交信息使用中文简体，方法注释使用中文简体。

**Goal:** 在现有 Ragent Career 闭环基础上，把 AI-Meeting 和 JobNavigator 已经“部分融入”的能力做厚，并按收益和依赖顺序补齐尚未真正融入的重点能力。

**Architecture:** Ragent 继续作为唯一运行底座，不引入 Mongo、Qdrant、第二套认证、第二套模型 SDK 或第二个前端 Shell。AI-Meeting 的运行时治理以 Career 域服务、Redis、PostgreSQL JSONB 和 Ragent Trace 落地；JobNavigator 的 Agent 编排、渲染、检索和观测能力以 Ragent 统一模型调用、检索抽象、任务事件和管理端视图落地。

**Tech Stack:** Java 17, Spring Boot 3.5.7, MyBatis-Plus, PostgreSQL JSONB, Redis Lua, Resilience4j, Ragent LLMService, Ragent Retriever/Rerank, React 18, Vite, TypeScript, SSE.

---

## 1. 当前状态判断

### 1.1 已经融入并可继续加厚

| 能力 | 当前实现 | 下一步目标 |
| --- | --- | --- |
| 多格式简历渲染 | `ResumeRenderPipeline` 已支持 Markdown、HTML、PDF、DOCX | 引入模板字段映射、模板资源、字体治理和导出失效闭环 |
| 分布式 Single-flight | Redis Lua + DB 审计 + Fencing Token + Follower 等待已具备 | 补齐 Hybrid 模式、本地 L1 回放缓存、持续 heartbeat、失败分类 TTL 和接管验收 |
| 状态机流控 | `InterviewFlowStateMachine` 已校验会话和轮次状态 | 把流程推进从大 Service 中拆出，形成可审计的 flow event |
| 热/冷快照恢复 | Redis 热快照 + PostgreSQL 冷快照已具备 | 补 CAS、单调性校验、Turn 归档、去抖刷新和分范围恢复 |
| 追问规则链 | 已有显式规则链替代完全依赖 LLM | 把规则拆成独立节点，支持配置阈值和规则命中审计 |
| HyDE/Rerank | 已有 query-only 证据和 Rerank 接入 | 补 LLM 生成“虚拟理想简历/候选人画像”后再过召回和重排 |
| Judge-Executor 优化 | 已有多轮裁判-执行者和 0.8 门禁 | 补实时 SSE 进度和 Agent 级 trace |
| 雷达图评估 | 已有 `WeightedRadarComputationStrategy` | 后续可接入神态维度，当前先保持文字链路 |

### 1.2 尚未真正融入的重点

| 排序 | 能力 | 来源 | 为什么排在这里 |
| ---: | --- | --- | --- |
| 1 | Agent 级可观测性框架 | JobNavigator | 后续多 Agent、SSE、质量门禁都需要统一 trace 口径，先补观测底座 |
| 2 | Plan-and-Execute 4 Agent 面试编排 | JobNavigator | 这是当前面试“线性流程”到“多 Agent 协作”的核心跃迁 |
| 3 | Career 专属 SSE 实时进度 | JobNavigator | 把优化和面试从 DB 轮询升级为实时可感知过程 |
| 4 | 可执行 Career Skill Pack | AI-Meeting | 让业务知识真正可被 Code Agent 消费，而不只是文档 |
| 5 | ASR/TTS/WebSocket 实时语音链路 | AI-Meeting | 依赖文字链路稳定，放到 Phase 4，不阻塞当前深度融合 |
| 6 | 分段转写组装器和神态分析 | AI-Meeting | 多模态增强，业务价值高但依赖较多，最后进入 |

## 2. 参考来源

### 2.1 AI-Meeting 参考点

- Single-flight：`D:\agent\AI-Meeting\admin\src\main\java\com\hewei\hzyjy\xunzhi\interview\application\guard\singleflight`
- AI Guard：`D:\agent\AI-Meeting\admin\src\main\java\com\hewei\hzyjy\xunzhi\interview\application\guard\core`
- 状态机：`D:\agent\AI-Meeting\admin\src\main\java\com\hewei\hzyjy\xunzhi\interview\application\flow`
- 追问规则链：`D:\agent\AI-Meeting\admin\src\main\java\com\hewei\hzyjy\xunzhi\interview\application\rule`
- 热冷快照：`D:\agent\AI-Meeting\admin\src\main\java\com\hewei\hzyjy\xunzhi\interview\application\runtime`
- ASR：`D:\agent\AI-Meeting\skills\xunzhi-media-domain\references\realtime-asr.md`

### 2.2 JobNavigator 参考点

- Plan-and-Execute：`D:\agent\JobNavigator\src\main\java\com\tengYii\jobspark\domain\agent\interview`
- Agent 观测：`D:\agent\JobNavigator\src\main\java\com\tengYii\jobspark\config\listener`
- Agent trace 持久化：`D:\agent\JobNavigator\src\main\java\com\tengYii\jobspark\domain\service\observability`
- HyDE/Rerank：`D:\agent\JobNavigator\src\main\java\com\tengYii\jobspark\domain\service\cv\ResumeRagService.java`
- 渲染管线：`D:\agent\JobNavigator\src\main\java\com\tengYii\jobspark\domain\render`
- 模板资源：`D:\agent\JobNavigator\src\main\resources\templates`

## 3. 落地顺序

### P0：执行前护栏

**目标：** 保证后续开发不把参考项目运行时直接搬进 Ragent。

**规则：**
- 只复用能力设计，不复制第二套认证、数据库、模型 SDK、前端 Shell。
- 新增注释、方法说明、提交信息全部中文简体。
- 每个功能单独提交；提交前跑对应测试。
- 语音/ASR/TTS 文件本轮不并入，除非进入 Phase 4。

**验收：**
- `git status --short --branch`
- 确认不在 `main` 开发。

### P1：把 AI-Meeting 运行时治理做厚

#### Task 1：Single-flight Hybrid 深化

**目标：** 从“Redis Lua 核心去重”升级为“LOCAL / DISTRIBUTED / HYBRID”完整治理。

**参考：**
- AI-Meeting `FlightMode`
- AI-Meeting `FlightHeartbeatManager`
- AI-Meeting `FlightReplayLocalCache`
- AI-Meeting `FlightErrorType`

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightProperties.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLlmServiceImpl.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightServiceImpl.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightMode.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLocalReplayCache.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightHeartbeatManager.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerSingleFlightTest.java`

**验收标准：**
- Redis 可用时走 DISTRIBUTED。
- Redis 异常时 HYBRID 降级到 LOCAL，不影响主链路。
- Owner 执行长耗时 AI 调用时 heartbeat 持续刷新，而不是只刷新一次。
- Follower 在等待窗口内拿到成功回放；超时返回明确失败类型。
- 本地 L1 只缓存成功回放，失败按短 TTL 控制。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=CareerSingleFlightTest test
```

**提交信息：**
```bash
feat: 完善 Career AI 单飞混合治理
```

#### Task 2：面试热冷快照 v2

**目标：** 从“可恢复”升级到“可并发恢复、可审计回放、不会倒退”。

**参考：**
- AI-Meeting `InterviewSessionRuntimeSnapshotService`
- AI-Meeting `InterviewSessionRuntimeHotRefreshCoordinator`
- AI-Meeting `InterviewSessionTurnArchive`
- AI-Meeting `InterviewRuntimeRehydrateScope`

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionRecoveryServiceImpl.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSessionHotSnapshotService.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/RedisInterviewSessionHotSnapshotService.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewSessionSnapshotDO.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/InterviewTurnArchiveDO.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/InterviewTurnArchiveMapper.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewRecoveryScope.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewSnapshotMonotonicGuard.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/InterviewHotSnapshotRefreshCoordinator.java`
- Modify: `bootstrap/src/main/resources/database/schema_pg.sql`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionRecoveryTest.java`

**验收标准：**
- 快照写入带 `version` CAS，旧版本不能覆盖新版本。
- `lastTurnSeq`、`archiveWatermark`、`scoreCount` 单调不倒退。
- Turn 归档不可变，恢复时可从归档重建回放。
- 热快照刷新支持短窗口合并，避免同一轮次高频重复写 Redis。
- 支持 `FLOW_ONLY`、`SCORE_ONLY`、`PLAYBACK_ONLY`、`HOT_RUNTIME`、`FULL_RUNTIME` 恢复范围。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=InterviewSessionRecoveryTest test
```

**提交信息：**
```bash
feat: 增强面试热冷快照恢复治理
```

#### Task 3：追问规则节点化和流程审计

**目标：** 保留当前轻量规则链，不引入 LiteFlow 运行时，但做到 AI-Meeting 风格的节点化、配置化和命中审计。

**参考：**
- AI-Meeting `interview-followup-chain.xml`
- AI-Meeting `FollowUpLimitGuardNode`
- AI-Meeting `LowScoreJudgeNode`
- AI-Meeting `MissingPointsJudgeNode`
- AI-Meeting `AiSuggestionJudgeNode`

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/DefaultInterviewFollowUpDecisionService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/FollowUpDecisionRule.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/FollowUpLimitRule.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/CompletedStateGuardRule.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/AiSuggestionRule.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/MissingPointsRule.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/LowScoreRule.java`
- Modify: `bootstrap/src/main/resources/application.yaml`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewFollowUpDecisionServiceTest.java`

**验收标准：**
- 每条规则有独立类、中文方法注释和清晰命中原因。
- 追问次数、低分阈值可配置。
- 决策结果返回 `matchedRule`，后续可落 trace。
- 已完成会话不能再生成追问。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=InterviewFollowUpDecisionServiceTest test
```

**提交信息：**
```bash
feat: 节点化面试追问裁决规则
```

### P2：把 JobNavigator 质量交付做厚

#### Task 4：真实 HyDE 虚拟画像检索

**目标：** 从 query seed 升级为 LLM 生成虚拟理想候选人画像，再过召回和 Rerank。

**参考：**
- JobNavigator `ResumeRagService`
- JobNavigator `RagRetrievalService`

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/CareerRetrievalEnhancementServiceImpl.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/CareerRetrievalEvidenceType.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt/CareerPromptTemplates.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/CareerHydeQueryGenerator.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerRetrievalEnhancementTest.java`

**验收标准：**
- HyDE 生成内容标记为 `queryOnly=true`，不能写入简历正文。
- 检索按 `limit * 3` 过召回，再 Rerank 到 TopN。
- LLM 生成失败时降级为当前 query seed。
- JD 对齐、优化、面试三个场景使用不同 HyDE 提示词。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=CareerRetrievalEnhancementTest test
```

**提交信息：**
```bash
feat: 增强 Career HyDE 检索画像生成
```

#### Task 5：渲染模板和导出失效闭环

**目标：** 从“能导出”升级为“模板化、可追溯、可失效、可验收”。

**参考：**
- JobNavigator `CvRendererFacade`
- JobNavigator `TemplateService`
- JobNavigator `TemplateFieldMapper`
- JobNavigator `templates/cv.md.ftl`
- JobNavigator `templates/fonts`

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeRenderPipeline.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeTemplateFieldMapper.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeTemplateRenderer.java`
- Create: `bootstrap/src/main/resources/templates/career-resume-template-v1.md`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/CandidateProfileServiceImpl.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeRenderPipelineTest.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CandidateProfileExportTest.java`

**验收标准：**
- 导出统一走模板字段映射，不直接把 JSON 当 Markdown 兜底输出。
- 导出记录保留 `templateVersion`、`contentType`、`traceId` 和校验结果。
- 删除简历版本后，关联导出记录不可继续下载。
- PDF/DOCX 中文字符不乱码。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=ResumeRenderPipelineTest,CandidateProfileExportTest test
```

**提交信息：**
```bash
feat: 完善简历模板渲染和导出失效
```

#### Task 6：Career SSE 实时进度

**目标：** 把优化和面试进度从 DB 轮询升级为实时 SSE，DB 事件保留为兜底。

**参考：**
- JobNavigator `OptimizationProgressContext`
- Ragent 现有 `RAGChatController` SSE 模式
- Ragent `SseEmitterSender`

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/progress/CareerProgressStreamService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/progress/CareerProgressStreamServiceImpl.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerOptimizationController.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerInterviewController.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/ResumeOptimizationServiceImpl.java`
- Modify: `frontend/src/services/careerService.ts`
- Modify: `frontend/src/pages/career/CareerOptimizationPage.tsx`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/controller/CareerOptimizationControllerMappingTest.java`

**验收标准：**
- 新增优化任务进度 SSE 端点。
- 进度事件先写 DB，再推送在线 SSE。
- SSE 断开后前端可继续通过原 DB 事件查询恢复。
- 前端不再只显示静态 `progressEvents`，能实时追加事件。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=CareerOptimizationControllerMappingTest test
cd frontend
npm run build
```

**提交信息：**
```bash
feat: 增加 Career 实时进度推送
```

### P3：补齐真正未融入的核心

#### Task 7：Agent 级可观测性框架

**目标：** 从任务 attempt 追踪升级为 Agent 调用、工具调用和会话聚合统计。

**参考：**
- JobNavigator `AgentListenerFactory`
- JobNavigator `AgentTracePersistService`
- JobNavigator `AgentExecutionTracePO`
- JobNavigator `AgentToolInvocationPO`
- JobNavigator `AgentSessionStatsPO`

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerAgentExecutionTraceDO.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerAgentToolInvocationDO.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/entity/CareerAgentSessionStatsDO.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CareerAgentExecutionTraceMapper.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CareerAgentToolInvocationMapper.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/dao/mapper/CareerAgentSessionStatsMapper.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/observability/CareerAgentTraceService.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/observability/CareerAgentTraceServiceImpl.java`
- Modify: `bootstrap/src/main/resources/database/schema_pg.sql`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLlmServiceImpl.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerAgentTraceTest.java`

**验收标准：**
- 每次 Career Agent/LLM 调用记录 agentType、scene、sessionId、traceId、耗时、状态、脱敏输入摘要、脱敏输出摘要。
- 工具调用和检索调用可以挂到同一个 agent trace。
- 管理端可查询最近 Agent trace。
- 敏感正文不完整入库，只保留摘要和字段级片段。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=CareerAgentTraceTest test
```

**提交信息：**
```bash
feat: 增加 Career Agent 调用观测
```

#### Task 8：Plan-and-Execute 多 Agent 面试编排

**目标：** 把当前线性 `generatePlan -> evaluate -> followUp` 升级为 JD 对齐、协调计划、技术提问、反思裁决四角色协作。

**参考：**
- JobNavigator `JDAlignmentAgent`
- JobNavigator `InterviewCoordinatorAgent`
- JobNavigator `JavaTechInterviewerAgent`
- JobNavigator `InterviewReflectorAgent`
- JobNavigator `JavaInterviewPlanAndExecuteWorkflow`

**Files:**
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/CareerInterviewAgentType.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/JdAlignmentInterviewAgent.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/InterviewCoordinatorAgent.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/TechnicalInterviewerAgent.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/InterviewReflectorAgent.java`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/InterviewPlanExecuteReflectService.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/InterviewSessionServiceImpl.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt/CareerPromptTemplates.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewPlanExecuteReflectTest.java`

**验收标准：**
- 创建会话时先生成 JD 对齐摘要，再由协调者生成阶段计划。
- 提问由技术面试官 Agent 生成或选择，不再只从计划数组线性取题。
- 评分后由反思 Agent 输出 `PROBE`、`NEXT`、`STAGE_FINISH`、`FINISH`。
- 反思结果进入现有追问规则链和状态机，不绕过幂等与补偿机制。
- 所有 Agent 调用接入 Task 7 的观测服务。

**测试命令：**
```bash
mvn -pl bootstrap -Dtest=InterviewPlanExecuteReflectTest,InterviewSessionStateTest,InterviewTurnIdempotencyTest test
```

**提交信息：**
```bash
feat: 引入面试多 Agent 编排
```

#### Task 9：可执行 Career Skill Pack

**目标：** 把当前文档型 Skill Pack 升级为可被 Code Agent 直接消费的模块化 skill 目录。

**参考：**
- AI-Meeting `skills/xunzhi-interview-domain`
- AI-Meeting `skills/xunzhi-ai-runtime`
- AI-Meeting `skills/xunzhi-debug-playbook`
- JobNavigator `skills/question-probing`
- JobNavigator `skills/jd-alignment`

**Files:**
- Create: `.agents/skills/ragent-career-domain/SKILL.md`
- Create: `.agents/skills/ragent-career-domain/references/state-machine.md`
- Create: `.agents/skills/ragent-career-domain/references/single-flight.md`
- Create: `.agents/skills/ragent-career-domain/references/recovery.md`
- Create: `.agents/skills/ragent-career-domain/references/interview-agents.md`
- Create: `.agents/skills/ragent-career-domain/references/rendering.md`
- Create: `.agents/skills/ragent-career-domain/references/debug-playbook.md`
- Modify: `docs/career-agent-platform/career-skill-pack.md`

**验收标准：**
- Skill 明确什么时候使用、读哪些参考、禁止引入哪些外部运行时。
- 参考文档覆盖状态机、Single-flight、恢复、面试 Agent、渲染和排障。
- 文档中所有路径指向 Ragent 当前实现，不再只描述来源项目。

**测试命令：**
```bash
git status --short
```

**提交信息：**
```bash
docs: 增加可执行 Career 领域技能包
```

### P4：语音和多模态扩展

P4 不进入下一轮主线，除非 P1-P3 全部通过。

#### Task 10：WebSocket + ASR 转写底座

**目标：** 支持实时语音答题输入，但不替代文字面试主链路。

**参考：**
- AI-Meeting `AudioTranscriptionWebSocketHandler`
- AI-Meeting `realtime-asr.md`
- AI-Meeting `XunfeiAudioServiceAssemblerTest`

**计划文件：**
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/TranscriptionSessionContext.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/AstTranscriptionAssembler.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/media/CareerAudioTranscriptionWebSocketHandler.java`

**验收标准：**
- `seg_id`、`pgs`、`rg`、`bg`、`ed` 能稳定组装文本。
- 已确认文本不能被后续部分分片误删。
- ASR 输出只作为 `InterviewTurn` 的可选答案来源。

## 4. 推荐执行批次

| 批次 | 包含任务 | 预计工期 | 价值 |
| --- | --- | ---: | --- |
| D1 | Task 1、Task 2、Task 3 | 5-6 人日 | 把 AI-Meeting 运行时治理真正做厚 |
| D2 | Task 4、Task 5、Task 6 | 4-5 人日 | 把 JobNavigator 质量交付和可见性做厚 |
| D3 | Task 7、Task 8、Task 9 | 6-8 人日 | 补齐 Agent 级观测、多 Agent 面试和 Skill 体系 |
| D4 | Task 10 | 4-6 人日 | 进入语音/ASR 扩展，不阻塞文字闭环 |

## 5. Subagent 拆包建议

执行时不要并行派多个实现型 subagent 到同一工作树。每个任务按“实现 -> 规格审查 -> 代码质量审查 -> 修复 -> 提交”闭环。

| 包 | 写入范围 | 推荐模型职责 | 是否可并行 |
| --- | --- | --- | --- |
| SG-D1-1 Single-flight | `career/service/singleflight`、Lua、单测 | 深入运行时治理 | 否 |
| SG-D1-2 快照恢复 | `career/service/recovery`、归档表、单测 | 深入数据一致性 | 否 |
| SG-D1-3 追问规则 | `career/service/followup`、配置、单测 | 轻量规则重构 | 否 |
| SG-D2-1 HyDE | `career/service/retrieval`、prompt、单测 | 检索增强 | 否 |
| SG-D2-2 渲染 | `career/service/render`、模板、导出测试 | 文档渲染 | 否 |
| SG-D2-3 SSE | `career/service/progress`、controller、frontend | 全栈实时进度 | 否 |
| SG-D3-1 Agent trace | observability 表、服务、admin | 观测底座 | 否 |
| SG-D3-2 多 Agent 面试 | interview agent 包、session service、prompt | 架构编排 | 否 |
| SG-D3-3 Skill Pack | `.agents/skills`、docs | 文档和技能资产 | 可在 D3-1 后并行审阅，不并行写代码 |

## 6. 总体验收命令

```bash
mvn -pl bootstrap -Dtest=CareerSingleFlightTest,InterviewSessionRecoveryTest,InterviewFollowUpDecisionServiceTest test
mvn -pl bootstrap -Dtest=CareerRetrievalEnhancementTest,ResumeRenderPipelineTest,CandidateProfileExportTest test
mvn -pl bootstrap -Dtest=CareerAgentTraceTest,InterviewPlanExecuteReflectTest test
mvn -pl bootstrap -DskipTests compile
cd frontend
npm run build
```

## 7. 下一步建议

下一步先执行 D1：

1. `Task 1：Single-flight Hybrid 深化`
2. `Task 2：面试热冷快照 v2`
3. `Task 3：追问规则节点化和流程审计`

原因是 D1 直接影响所有 AI 调用和面试闭环稳定性。D1 做完后，再进入 D2 的 HyDE、渲染和 SSE，最后用 D3 的 Agent trace 与 Plan-and-Execute 把系统从“流程型服务”升级为“可观测多 Agent 协作”。
