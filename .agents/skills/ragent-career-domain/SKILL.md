---
name: ragent-career-domain
description: Use when modifying ragent Career capabilities such as resume parsing, JD alignment, optimization, interview flow, AI guard, single-flight, recovery, rendering, progress, or agent observability.
---

# ragent-career-domain

这个 Skill 用于处理 ragent Career 域需求。它只描述当前 ragent 底座中的落地实现，不把 AI-Meeting 或 JobNavigator 的运行时原样搬入。

## 使用顺序

| 场景 | 先读参考 |
| --- | --- |
| 修改面试创建、答题、评分、追问、补偿 | `references/state-machine.md`、`references/interview-agents.md` |
| 修改 AI 调用去重、等待、回放、降级 | `references/single-flight.md` |
| 修改缓存丢失恢复、快照、轮次归档 | `references/recovery.md` |
| 修改简历导出、模板、PDF、DOCX | `references/rendering.md` |
| 排查线上异常或验收失败 | `references/debug-playbook.md` |

## 关键入口

- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerResumeController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerOptimizationController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/CareerInterviewController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/admin/CareerAdminController.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/InterviewSessionServiceImpl.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/ResumeOptimizationServiceImpl.java`
- `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/CareerSingleFlightLlmServiceImpl.java`

## 必守边界

- ragent 是唯一运行底座，不新增 Mongo、Qdrant、第二套认证、第二个前端 Shell 或第二套模型 SDK。
- 当前文字闭环优先，Phase 4 之前不把 ASR/TTS/WebSocket 语音链路混入主流程。
- 所有 Career AI 调用必须经过 `CareerSingleFlightLlmService`，不得绕过 Single-flight、AI Guard 和 Agent Trace。
- 面试状态推进必须经过 `InterviewFlowStateMachine` 或已有服务中的合法迁移校验。
- 简历优化必须保留 Judge-Executor 多轮复核和 `qualityScore >= 0.8` 交付门禁。
- HyDE 只能作为检索查询或证据扩展，不能写回候选人简历正文。
- 导出必须保留模板版本、内容类型、Trace 和字段校验结果。

## 当前融合能力

- AI-Meeting：状态机流控、追问规则链、题级幂等、补偿 worker、热/冷快照恢复、Redis Lua Single-flight、AI Guard、雷达评估。
- JobNavigator：Judge-Executor 简历优化、HyDE/Rerank、PDF/DOCX 渲染、Plan-and-Execute 面试 Agent、Agent 级观测、SSE 实时进度。

## 常见误区

- 不要把参考项目路径当成 ragent 的实现路径，落地时只改 `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/**` 和对应前端服务。
- 不要把状态字段当普通枚举直接 `setStatus`，先确认合法迁移和补偿边界。
- 不要把重复请求简单抛错，Follower 应尽量等待 Owner 结果并回放。
- 不要在排障时补自由文本，应先查 Trace、任务事件、快照、Single-flight 记录和导出记录。
