# 面试多 Agent 编排

## 适用场景

修改面试题目生成、JD 对齐、面试计划、技术提问、评分后路由、Agent Trace 时先读本文件。

## 当前实现路径

- 编排服务：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/InterviewPlanExecuteReflectService.java`
- Agent 类型：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/CareerInterviewAgentType.java`
- JD 对齐 Agent：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/JdAlignmentInterviewAgent.java`
- 协调者 Agent：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/InterviewCoordinatorAgent.java`
- 技术面试官 Agent：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/TechnicalInterviewerAgent.java`
- 反思裁决 Agent：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/interview/agent/InterviewReflectorAgent.java`
- Prompt：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/prompt/CareerPromptTemplates.java`
- 观测：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/observability/CareerAgentTraceService.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewPlanExecuteReflectTest.java`

## 编排顺序

1. `JdAlignmentInterviewAgent` 生成 JD 与简历对齐摘要。
2. `InterviewCoordinatorAgent` 根据摘要和 Rubric 生成阶段计划。
3. `TechnicalInterviewerAgent` 生成或选择当前题目。
4. 用户答题后，`InterviewReflectorAgent` 输出评分后路由建议。
5. 路由建议进入现有追问规则链和状态机，不绕过幂等与补偿。

## 路由语义

| 决策 | 语义 |
| --- | --- |
| `PROBE` | 继续追问当前题，必须受追问次数限制 |
| `NEXT` | 进入下一主问题 |
| `STAGE_FINISH` | 当前阶段结束，切换阶段或收束 |
| `FINISH` | 面试结束并进入报告生成 |

## 观测要求

- 每次 Agent 调用都要保留 `agentType`、`scene`、`sessionId`、`traceId`、耗时、状态、脱敏输入摘要和脱敏输出摘要。
- 工具调用、检索调用和 Rerank 可以挂到同一条 Agent Trace。
- 管理端查询入口在 `CareerAdminController`。

## 修改检查

- 新增 Agent 时，同步 `CareerInterviewAgentType`、prompt、Single-flight scene、Trace 和测试。
- 反思结果只能影响路由建议，不能直接跳过状态机。
- 编排失败时应回退到旧线性计划，不应导致会话创建失败。
