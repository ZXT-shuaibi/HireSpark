# Career 后融合统一验收矩阵

## 目标

本矩阵把 OpenAPI、CI、Compose、字体、混合压缩记忆、长文本 TTS、神态/表情分析统一进同一套交付验收。它不引入第二套运行时，只检查 ragent 现有底座里的可联调、可回归、可降级能力。

## 矩阵

| 阶段 | 能力 | 验收锚点 | 轻量检查 |
| --- | --- | --- | --- |
| P1 | OpenAPI 契约 | `OpenApiDocumentationConfig` 暴露 `career-user`、`career-admin`、`career-runtime`、`career-export`；Career Controller 带 `@Tag` 和 `@Operation` | `CareerOpenApiContractTest` |
| P1 | 交付与运维底座 | 根目录 `Dockerfile`、Actuator health/readiness、compose app healthcheck、CI delivery-files | `scripts/verify_post_fusion_acceptance.py` |
| P2 | 字体治理与导出策略 | `ResumeRenderValidationResult` 输出 `fontStrategy`，导出记录保留模板版本、渲染引擎、traceId 和失败原因 | `ResumeRenderPipelineTest` |
| P3 | 混合压缩记忆 | 记忆分层为热上下文、摘要、长期事实、关键证据、风险标记；压缩结果可追溯到原始消息、trace 和触发原因 | `ConversationMemoryCompressionPlannerTest`、`ConversationMemoryImportanceScorerTest` |
| P4 | 长文本 TTS | TTS 文本切片、队列、缓存 key、取消标记和文本降级结果独立于 ASR，不阻塞文字面试 | `CareerTextToSpeechServiceTest` |
| P5 | 神态/表情分析 | 仅作为可关闭的辅助信号，必须记录授权、置信度、局限性和保留策略，不进入唯一评分依据 | `CareerDemeanorAnalysisServiceTest` |
| P6 | 治理复盘 | quick start、排障说明、验收矩阵和静态检查同时覆盖 P1-P5 | `scripts/verify_post_fusion_acceptance.py` |

## 回归边界

- Single-flight、快照恢复、追问规则链、AI Guard、Judge-Executor、HyDE/Rerank、Plan-and-Execute 不得被 P3-P5 绕过。
- TTS 和神态/表情分析必须是增强能力，失败时回到文字链路。
- 混合压缩记忆不得覆盖用户原始答案、评分依据、检索证据和 trace 摘要。
- 神态/表情分析不得在无授权、无置信度、无限制说明时出现在用户报告中。

## 执行方式

```bash
python scripts/verify_post_fusion_acceptance.py
```
