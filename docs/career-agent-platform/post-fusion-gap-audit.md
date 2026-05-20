# Career 后融合亮点引入审核

## 审核结论

本轮已经把后融合计划中的 P1-P6 纳入 ragent 的统一底座：工程交付、OpenAPI、字体治理、混合压缩记忆、长文本 TTS、神态/表情分析和统一验收矩阵都有代码或文档锚点。当前没有发现仍应复制 AI-Meeting 或 JobNavigator 运行时的亮点。

## 已引入亮点

| 亮点 | 当前落点 | 审核结果 |
| --- | --- | --- |
| OpenAPI 契约交付 | `OpenApiDocumentationConfig`、Career Controller `@Tag` / `@Operation`、`CareerOpenApiContractTest` | 已引入 |
| Docker/Compose/CI/Actuator | `Dockerfile`、`ragent-dev-stack.compose.yaml`、`.github/workflows/ci.yml`、Actuator health/readiness | 已引入 |
| 字体治理与导出可追溯 | `ResumeRenderFontRegistry`、`ResumeRenderValidationResult.fontStrategy`、导出记录 validation payload | 已引入 |
| 混合压缩记忆 | `ConversationMemoryBucket`、`ConversationMemoryCompressionPlanner`、trigger policy 与 memory tests | 已引入 |
| 长文本 TTS | `CareerTextToSpeechService`、`/career/interviews/{sessionId}/tts/plan`、`CareerTextToSpeechServiceTest` | 已引入为可降级计划层 |
| 神态/表情分析 | `CareerDemeanorAnalysisService`、`/career/interviews/{sessionId}/demeanor/analyze`、`CareerDemeanorAnalysisServiceTest` | 已引入为可关闭辅助信号 |
| 统一验收矩阵 | `post-fusion-acceptance-matrix.md`、`scripts/verify_post_fusion_acceptance.py` | 已引入 |

## 仍未引入但不应直接搬入的内容

| 项 | 原因 | 后续处理 |
| --- | --- | --- |
| 第二套认证、模型 SDK、Trace、前端 Shell | 违反 ragent 唯一运行底座原则 | 不引入 |
| FreeMarker 默认模板链路 | 当前 Markdown 模板和字段映射已覆盖本轮验收，FreeMarker 仍是复杂模板后的可选升级 | 暂不引入 |
| 真实第三方 TTS 音频供应商调用 | 本轮先建立切片、缓存、取消和降级契约，避免供应商耦合主流程 | 作为 provider adapter 独立接入 |
| 真实神态/表情模型 | 涉及授权、隐私、模型误判和数据保留边界，本轮只建立合规辅助信号接口 | 在明确合规与模型来源后接入 |
| 完整浏览器播放队列 UI | 后端已提供 TTS 计划，前端播放体验可独立迭代 | 后续体验任务 |

## 当前风险

- Maven 本地验证仍受依赖缓存和网络授权限制影响。需要在可访问 Maven Central 或企业镜像的环境里运行完整测试。
- TTS 和神态分析当前是平台契约与降级骨架，不包含外部音频/视觉模型供应商实现。
- 神态分析结果必须保持 `includedInScore=false`，不得成为招聘或评分的唯一依据。

## 建议回归命令

```bash
python scripts/verify_post_fusion_acceptance.py
mvn -pl bootstrap -am -Dtest=CareerOpenApiContractTest,CareerTextToSpeechServiceTest,CareerDemeanorAnalysisServiceTest,ConversationMemoryCompressionPlannerTest,ConversationMemoryImportanceScorerTest test
mvn -pl bootstrap -am -DskipTests compile
cd frontend && npm run build
```
