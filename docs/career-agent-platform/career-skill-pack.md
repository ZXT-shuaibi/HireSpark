# Career Skill Pack

## 版本说明
- 文档版本：`career-skill-pack-v1`
- 对应范围：PRD v0.3 和实施计划 Task 19-26
- 角色：给 Agent 直接消费的职业域知识资产，不是用户介绍页

## 项目定位
Career Skill Pack 是 Ragent Career 域的可消费知识资产。它把已经确认可执行的 Rubric、Prompt、状态机、排障规则和验收口径按版本封装起来，供 Agent 在简历、JD、优化、面试和报告五条链路里直接读取。

它不是第二套运行时，也不是泛化的产品介绍。所有能力都必须落回 Ragent 的统一认证、模型路由、存储和 Trace。

## Rubric 版本
| 版本 | 适用场景 | 说明 |
| --- | --- | --- |
| `career-java-backend-v1` | 默认演示 | 面向 Java 后端 / AI 应用岗位，MVP 只读 |
| `career-general-v1` | 通用兜底 | 非 Java 岗位使用通用维度，不另起模板 |

要求：
- Rubric 只读，不提供 MVP 编辑入口。
- Rubric 必须绑定简历版本、JD 版本和 Trace。
- Rubric 变化必须可追溯，不能在自然语言里隐式漂移。

## Prompt 契约
### 简历与 JD
- `RESUME_PARSE`：输入原始简历和版本号，输出结构化字段、证据片段、缺失项和解析状态，不编造经历。
- `JD_PARSE`：输入 JD 文本，输出岗位结构、技能栈、经验要求和风险词。
- `JD_ALIGNMENT`：输入简历版本、JD 版本和 Rubric，输出分数、缺口、证据和下一步动作。

### 优化
- `RESUME_OPTIMIZATION_EXECUTOR`：输出建议列表，每条建议都要带证据、预期收益和风险。
- `RESUME_OPTIMIZATION_REVIEW`：输出 `qualityScore`、`truthfulnessRisk`、`unsupportedClaims`、`acceptedSuggestionIds`、`rejectedSuggestionIds` 和 `revisionInstructions`。

### 面试
- `INTERVIEW_PLAN`：基于简历、JD 和 Rubric 生成题目顺序和追问路径。
- `INTERVIEW_SCORE`：输出评分、证据、追问建议和是否进入下一题。
- `INTERVIEW_FOLLOW_UP`：输出下一题或结束信号，不能丢失当前轮答案。
- `INTERVIEW_REPORT`：输出总分、维度分、风险、亮点和复盘建议，必须可回溯到 Trace。

要求：
- 所有契约都要带版本号、绑定对象和 TraceId。
- 所有输出都必须结构化，不能只返回自由文本。
- 任一失败都要明确返回失败原因或重试边界。

## 状态机
- 优化复核：`GENERATING -> REVIEWING -> REVISING -> PASSED / NEEDS_REVIEW / FAILED`
- 面试轮次：`ASKED -> ANSWER_SAVED -> EVALUATING -> EVALUATED -> FOLLOW_UP_DECIDING -> FOLLOW_UP_CREATED / NEXT_MAIN_CREATED / SESSION_COMPLETED`
- 评分失败与补偿：`EVALUATION_FAILED -> WAITING_RETRY`，`ANSWER_SAVED -> COMPENSATING -> EVALUATED`
- 会话恢复：`ACTIVE -> RECOVERING -> ACTIVE / FAILED`
- Single-flight：`PENDING -> RUNNING -> SUCCESS / FAILED`，重复请求命中 `REPLAY`

## 排障指南
- 看到字段缺失或格式错乱：先查 prompt 版本、输入版本和 Trace，不要用自由文本补洞。
- 看到重复提交：先查 `stepIdempotencyKey`，应返回已有结果，不应新建追问或重新评分。
- 看到评分失败：保留原答案和轮次上下文，走同一轮手动重试，不要新起一轮。
- 看到缓存丢失：从持久化轮次和快照恢复当前题目、已答轮次和补偿状态。
- 看到重复 AI 结果：先查 Single-flight key、owner heartbeat 和 fencing token。
- 看到 PDF/Word 导出失败：先查字段校验和模板版本；MVP 允许禁用导出，但不能破坏 Markdown/HTML。

## 验收参考
- `A18`：AI 输出格式错误时可自动重试，并记录尝试次数。
- `A19`：面试答案评分失败后保留答案，不自动跳题。
- `A20`：管理员能看到任务状态、失败原因和 Trace 链接。
- `A21` / `A22`：优化建议要经过裁判复核，`0.8` 以下不直接交付。
- `A23`：同一输入在并发下只生成一份 AI 结果。
- `A24`：缓存丢失后可从快照恢复当前面试会话。
- `A25`：重复答案提交不产生重复评分或重复追问。
- `A26`：HyDE 只作为查询或证据，不写回简历正文。
- `A27`：导出前必须完成字段校验，导出记录和 Trace 必须存在。

## 注意事项
- 这里的内容是知识资产，不是说明文档的替代品。
- 不要把 Career Skill Pack 当成第二套运行时、第二套模型路由或第二套存储。
- 不要把 JobNavigator 或 AI-Meeting 原系统原样搬进来，吸收的是能力，不是系统边界。
- 非 Java 岗位只走通用模式，不额外承诺岗位专用模板或专用 Rubric。
- 语音和多模态只属于 Phase 4，不能反向污染文字闭环。
- 任何新规则都必须同步版本、Trace 和关联对象，不能只改口径不改记录。
