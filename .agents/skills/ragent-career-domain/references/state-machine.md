# 状态机与追问规则

## 适用场景

修改面试会话、题目轮次、评分、追问、手动重试或补偿时先读本文件。

## 当前实现路径

- 状态机：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/flow/InterviewFlowStateMachine.java`
- 主链路：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/InterviewSessionServiceImpl.java`
- 轮次运行态：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/runtime/InterviewTurnRuntimeServiceImpl.java`
- 追问决策入口：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/DefaultInterviewFollowUpDecisionService.java`
- 追问规则节点：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/followup/rule/`
- 补偿 worker：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/schedule/InterviewEvaluationCompensationWorker.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewSessionStateTest.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/InterviewFollowUpDecisionServiceTest.java`

## 主流程

1. 创建面试会话后生成计划或多 Agent 提问。
2. 用户提交答案时使用题级幂等键保护同一轮次。
3. 答案进入评分阶段，失败时保留答案和轮次上下文。
4. 评分完成后进入追问决策，规则链决定追问、下一主问题或结束。
5. 补偿 worker 只补同一轮未完成评分，不新建无关轮次。

## 合法迁移约束

- 不能从已完成会话继续生成追问。
- 不能跳过答案保存直接评分。
- 不能在评分失败后自动跳题。
- 重复提交应命中已有轮次结果或幂等记录。
- 追问次数、低分阈值、遗漏知识点和 AI 建议都要进入规则命中原因。

## 追问规则顺序

| 规则 | 当前职责 |
| --- | --- |
| `CompletedStateGuardRule` | 会话完成后拒绝继续追问 |
| `FollowUpLimitRule` | 控制单题追问次数上限 |
| `AiSuggestionRule` | 读取 LLM 建议中的追问信号 |
| `LowScoreRule` | 低分触发追问 |
| `MissingPointsRule` | 知识点遗漏触发追问 |

## 修改检查

- 新增状态必须补 `InterviewFlowStateMachine` 测试。
- 新增追问规则必须返回清晰 `matchedRule` 和中文命中原因。
- 修改答题入口时必须同时检查幂等、Single-flight、补偿 worker 和恢复逻辑。
