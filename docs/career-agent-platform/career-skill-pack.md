# Career Skill Pack

## 版本说明

- 文档版本：`career-skill-pack-v2`
- Skill 入口：`.agents/skills/ragent-career-domain/SKILL.md`
- 对应范围：AI-Meeting 与 JobNavigator 亮点融合后的 ragent Career 当前实现
- 角色：给 Code Agent 直接消费的职业域知识资产索引，不是用户介绍页

## 项目定位

Career Skill Pack 是 ragent Career 域的可执行知识资产。它把状态机、Single-flight、恢复、多 Agent 面试、简历渲染和排障规则拆成模块化 reference，供后续 Agent 在开发、排障和验收时按需读取。

它不是第二套运行时。所有能力必须落回 ragent 的统一认证、模型路由、Redis、PostgreSQL、Trace、SSE 和前端服务。

## Skill 结构

| 文件 | 用途 |
| --- | --- |
| `.agents/skills/ragent-career-domain/SKILL.md` | 入口，说明什么时候使用、先读哪些 reference、哪些外部运行时禁止引入 |
| `.agents/skills/ragent-career-domain/references/state-machine.md` | 面试状态机、题级幂等、追问规则、补偿 worker |
| `.agents/skills/ragent-career-domain/references/single-flight.md` | Career AI 调用入口、Redis Lua Single-flight、Follower 等待、AI Guard |
| `.agents/skills/ragent-career-domain/references/recovery.md` | Redis 热快照、PostgreSQL JSONB 冷快照、CAS、轮次归档、恢复范围 |
| `.agents/skills/ragent-career-domain/references/interview-agents.md` | JD 对齐、协调者、技术面试官、反思裁决、Agent Trace |
| `.agents/skills/ragent-career-domain/references/rendering.md` | Markdown、HTML、PDF、DOCX 简历渲染流水线和模板版本 |
| `.agents/skills/ragent-career-domain/references/debug-playbook.md` | 重复 AI、卡轮次、缓存丢失、导出失败、SSE 异常排障 |

## 当前已融合亮点

| 来源 | 融合点 | ragent 落地位置 |
| --- | --- | --- |
| AI-Meeting | 状态机流控 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/flow/InterviewFlowStateMachine.java` |
| AI-Meeting | 追问规则链 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/` |
| AI-Meeting | Redis Lua Single-flight | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/` |
| AI-Meeting | AI Guard | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/guard/` |
| AI-Meeting | 热/冷快照恢复 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/` |
| JobNavigator | Judge-Executor 简历优化 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/ResumeOptimizationServiceImpl.java` |
| JobNavigator | HyDE/Rerank | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/retrieval/` |
| JobNavigator | 多格式渲染 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/` |
| JobNavigator | Plan-and-Execute 面试 Agent | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/` |
| JobNavigator | Agent 级观测 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/observability/` |
| JobNavigator | SSE 实时进度 | `bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/progress/` |

## 使用要求

- 修改 Career 域前，先加载 `.agents/skills/ragent-career-domain/SKILL.md`。
- 只读取与当前任务相关的 reference，不要一次性展开所有文件。
- 新增能力时优先扩展现有 ragent 服务，不复制 AI-Meeting 或 JobNavigator 的运行时边界。
- 提交信息、方法注释、业务说明统一使用中文简体。
- 每个功能单独提交，提交前运行对应测试。

## 禁止引入

- MongoDB、Qdrant、第二套认证、第二个前端 Shell、第二套模型 SDK。
- Phase 4 前的 ASR/TTS/WebSocket 语音链路。
- 绕过 `CareerSingleFlightLlmService` 的 Career AI 调用。
- 绕过 `InterviewFlowStateMachine` 的面试状态推进。
