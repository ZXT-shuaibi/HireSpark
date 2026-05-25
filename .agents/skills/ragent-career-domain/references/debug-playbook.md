# Career 排障手册

## 快速入口

| 现象 | 先查 |
| --- | --- |
| AI 输出格式错误 | Agent Trace、prompt scene、重试次数 |
| 重复 AI 计费 | Single-flight key、Redis 协调记录、DB 审计记录、本地回放缓存 |
| 答案提交后卡住 | 轮次状态、幂等键、补偿 worker、评分失败原因 |
| 缓存丢失 | Redis 热快照、PostgreSQL 冷快照、轮次归档、恢复范围 |
| 追问异常 | `matchedRule`、追问次数、低分阈值、遗漏知识点 |
| 优化结果质量不达标 | Judge-Executor 轮次、`qualityScore`、拒绝建议、进度事件 |
| 导出失败 | 字段校验、模板版本、contentType、PDF/DOCX 渲染异常 |
| 前端进度不刷新 | SSE 连接、DB 兜底事件、任务状态 |

## 常用路径

- Career 管理端：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/admin/CareerAdminController.java`
- Agent Trace：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/observability/`
- 实时进度：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/progress/`
- Single-flight：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/singleflight/`
- 恢复：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/recovery/`
- 状态机：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/flow/InterviewFlowStateMachine.java`

## 排障顺序

1. 先定位业务对象：resumeVersionId、jobId、optimizationTaskId、interviewSessionId、turnId。
2. 再定位 traceId：优先查 Agent Trace，其次查任务事件和 Single-flight 记录。
3. 对重复请求，先判断是否应该回放，不要直接重新调用 AI。
4. 对面试卡住，先恢复同一轮上下文，不要新建轮次。
5. 对导出失败，先确认 Markdown/HTML 是否可用，再看 PDF/DOCX 依赖。
6. 对前端进度问题，先看 SSE，再看 DB polling 兜底。

## 禁止动作

- 禁止为了修复单次异常绕过 `CareerSingleFlightLlmService`。
- 禁止手动改状态到完成态后跳过报告、归档或补偿。
- 禁止把 HyDE 生成内容写进简历正文。
- 禁止引入 Mongo、Qdrant、第二套认证、第二套前端 Shell 或第二套模型 SDK。
- 禁止在 Phase 4 前把 ASR/TTS 文件纳入文字闭环提交。
