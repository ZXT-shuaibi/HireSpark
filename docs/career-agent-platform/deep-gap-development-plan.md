# Career 深度缺口开发计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Commit messages must be Chinese. Do not add new Apache/ASF license headers.

**Goal:** 把后融合审计中仍停留在骨架层或存在数据完整性风险的能力，按业务价值和工程风险逐步补成可验证的生产能力。

**Architecture:** 所有能力必须落回 ragent 现有 Spring Boot、Sa-Token、Redis、PostgreSQL、Trace、配置和测试底座；不得搬入第二套认证、第二套模型运行时或独立前端 Shell。外部供应商能力通过 provider/client 适配层接入，默认关闭，配置完整后启用，失败时降级到文字链路或既有同步链路。

**Tech Stack:** Java 17, Spring Boot 3.5.7, MyBatis-Plus, PostgreSQL JSONB, Redis/Redisson, OkHttp, Jackson, JUnit 5, Mockito, SSE/WebSocket.

---

## 已确认缺口

| 优先级 | 缺口 | 当前状态 | 目标状态 |
| --- | --- | --- | --- |
| P0 | Single-flight gzip 压缩回放 | `CareerSingleFlightLlmServiceImpl` 对超过 200K 字符的 AI 结果直接截断 | 大结果 gzip + Base64 存储，Follower 回放完整还原，旧 JSON 格式兼容 |
| P1 | 讯飞长文本 TTS 实际合成 | `CareerTextToSpeechService` 只做分片、缓存 key、取消 key | provider 化，支持鉴权、任务提交、轮询、音频下载或音频地址、失败降级 |
| P1 | 神态/表情实际 AI 调用 | `CareerDemeanorAnalysisService` 只聚合外部 observation | provider 化，支持授权校验、图片/帧输入、模型调用、结果归一化、隐私边界 |
| P1 | 邮件验证码 | Auth 只有登录/登出 | 支持验证码发送、TTL、频控、注册/找回密码校验 |
| P1 | FollowUp 规则链配置化 | 已有 `FollowUpDecisionRule` 节点链，但顺序主要由 Spring 注入/默认链决定 | 配置控制规则启停、顺序、阈值，并记录命中审计 |
| P2 | RAG 双向 WebSocket | RAG 聊天是 SSE 单向，Career 有 ASR WebSocket | 保留 SSE，新增可选双向 WebSocket 协议，支持发问、停止、心跳、事件流 |

## 不进入本轮主线

| 缺口 | 原因 | 后续处理 |
| --- | --- | --- |
| RBloomFilter 缓存穿透防护 | 需要明确高风险查询入口和误判策略 | 放入高并发专项 |
| PreventDuplicateSubmit 注解+AOP | 与当前幂等键、Single-flight 有重叠 | 放入接口治理专项 |
| 手机号脱敏序列化 | 当前 Career 主链路主要使用邮箱/用户名 | 放入隐私治理专项 |
| MessageSequenceAllocator | 需要先统一 RAG/Career 消息模型 | 放入消息架构专项 |
| AgentResolver/BusinessAgentScene | 会改变 Agent 编排边界 | 放入多 Agent 架构专项 |
| Conversation Ports/Adapters | 属于分层重构，收益依赖后续模型抽象 | 放入架构整洁专项 |
| DecisionIndex | 需要跨 Agent 决策追踪统一模型 | 放入观测专项 |
| InterviewRuleBasedScorer | 可能与现有模型评分冲突 | 放入评分可信度专项 |
| FlightErrorType | 可随 Single-flight gzip 后续失败分类迭代补齐 | 暂不单独做 |
| Spark IAT 文件级转写 | 当前已有 Career AST 实时转写 | 放入语音专项 |
| 讯飞密钥启动校验器 | 可在 TTS/神态 provider 接入时补 provider-local 校验 | 随 P1 实施 |
| EasyExcel/WebMagic/Dynamic datasource/严格 DTO-BO-PO | 与当前缺口主线关联弱 | 暂缓 |

## 阶段 1：P0 Single-flight gzip 压缩回放

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLlmServiceImpl.java`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerSingleFlightTest.java`

- [x] Step 1: 增加失败测试，构造超过 `RESULT_COMPRESSION_THRESHOLD` 的模型结果，断言 `completeSuccess` 写入的 `resultJson` 不丢字符，`unwrapResult` 后与原文一致。
- [x] Step 2: 增加旧格式兼容测试，断言 `{"response":"..."}` 仍可正常回放。
- [x] Step 3: 实现结果 codec：短文本继续写 `response`；长文本写 `encoding=gzip-base64`、`responseGzipBase64`、`originalLength`。
- [x] Step 4: 回放时按 encoding 自动解压；异常时抛出明确 `ServiceException`，避免静默返回压缩 payload。
- [x] Step 5: 运行 `mvn -pl bootstrap -am -Dtest=CareerSingleFlightTest test`。

**Acceptance:**
- 大于 200K 字符的结果不再截断。
- Follower 等待后拿到完整结果。
- 已存旧格式仍可回放。
- 不新增外部依赖，不改 DB schema。

## 阶段 2：P1 讯飞长文本 TTS provider

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/tts/`
- Modify: `bootstrap/src/main/resources/application.yaml`
- Modify: `bootstrap/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerTextToSpeechServiceTest.java`

- [x] Step 1: 定义 `CareerTextToSpeechProvider`，将当前 plan 层与实际 provider 调用解耦。
- [x] Step 2: 新增 Xunfei long-text TTS properties，包含 appId、apiKey、apiSecret、submitUrl、queryUrl、downloadUrl、timeout、poll interval、max polls。
- [x] Step 3: 用 OkHttp 实现 HMAC-SHA256 鉴权、任务提交、轮询、音频下载解析。
- [x] Step 4: provider 失败时返回 `TEXT_FALLBACK`，保留 `fallbackText`、`degradeReason`。
- [ ] Step 5: 使用 mock HTTP 覆盖成功、任务失败、轮询超时、配置不完整。

**Acceptance:**
- 配置完整且 provider 成功时返回可播放音频信息。
- 配置缺失、超时或供应商失败时不阻塞文字面试。
- 不把供应商 SDK 绑进主流程。

## 阶段 3：P1 神态/表情 provider

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/demeanor/`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/request/CareerDemeanorAnalysisSubmitRequest.java`
- Modify: `bootstrap/src/main/resources/application.yaml`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CareerDemeanorAnalysisServiceTest.java`

- [x] Step 1: 定义 `CareerDemeanorAnalysisProvider`，将 observation 聚合与模型调用解耦。
- [x] Step 2: 请求 DTO 支持图片 URL/Base64 帧、采样时间、授权标识。
- [x] Step 3: provider 调用视觉/表情模型后归一化为 signal、confidence、limitation。
- [x] Step 4: 任何情况下 `includedInScore=false`；无授权直接 `CONSENT_REQUIRED`。
- [x] Step 5: 使用 mock provider 覆盖成功、无授权、失败降级、低置信度。

**Acceptance:**
- 神态分析只作为辅助信号。
- 结果包含授权、置信度、局限性和保留策略。
- provider 失败不影响文字面试和报告生成。

## 阶段 4：P1 邮件验证码

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/user/controller/AuthController.java`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/user/service/`
- Modify: `bootstrap/src/main/resources/application.yaml`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/user/service/AuthServiceImplTest.java`

- [ ] Step 1: 新增验证码发送请求、校验请求和服务接口。
- [ ] Step 2: 用 Redis 保存验证码 hash、TTL、发送频控。
- [ ] Step 3: dev 模式可日志输出，生产模式走 JavaMail 或可替换 sender。
- [ ] Step 4: 注册/找回密码必须校验验证码。
- [ ] Step 5: 覆盖过期、错误、重复发送过快、成功消费后失效。

**Acceptance:**
- 未通过验证码不能注册或重置密码。
- 验证码不会明文长期落库。
- 邮件不可用时返回可理解错误。

## 阶段 5：P1 FollowUp 规则链配置化

**Files:**
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/`
- Modify: `bootstrap/src/main/resources/application.yaml`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewFollowUpDecisionServiceTest.java`

- [ ] Step 1: 配置 `career.interview.follow-up.rule-chain`，支持规则名、enabled、order。
- [ ] Step 2: rule registry 根据配置生成执行链。
- [ ] Step 3: 审计输出记录命中规则、跳过原因和最终决策。
- [ ] Step 4: 保留默认链，配置缺失时行为不变。

**Acceptance:**
- 可以通过配置关闭低分追问或调整 AI 建议规则顺序。
- 命中审计可解释每次追问产生原因。
- 不强制引入 LiteFlow 运行时。

## 阶段 6：P2 RAG 双向 WebSocket

**Files:**
- Create/Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/websocket/`
- Modify: `bootstrap/src/main/java/com/nageoffer/ai/ragent/rag/service/handler/`
- Test: `bootstrap/src/test/java/com/nageoffer/ai/ragent/rag/websocket/`

- [ ] Step 1: 定义浏览器请求事件：`chat.start`、`chat.stop`、`ping`。
- [ ] Step 2: 定义服务端事件：`meta`、`message`、`retrieval`、`finish`、`done`、`error`、`pong`。
- [ ] Step 3: 复用现有 `StreamTaskManager` 取消语义。
- [ ] Step 4: SSE 端点保持不变，WebSocket 默认关闭。

**Acceptance:**
- WebSocket 可完成一轮 RAG 对话和停止。
- SSE 行为不回归。
- 鉴权、限流和取消语义一致。

## 执行顺序

1. 立即执行阶段 1。
2. 阶段 2/3 需要供应商 API 细节或可用 AI-Meeting 源码对照；缺少时先做 provider 接口和 mock client，不伪造真实成功。
3. 阶段 4/5 可与阶段 2/3 并行，但提交必须独立。
4. 阶段 6 等 RAG 协议确认后执行。
