# Career 后融合亮点引入审核

## 审核结论

本轮已经把后融合计划中的 P1-P6 纳入 ragent 的统一底座：工程交付、OpenAPI、字体治理、混合压缩记忆、长文本 TTS、神态/表情分析和统一验收矩阵都有代码或文档锚点。复核后确认，TTS 与神态分析仍是可降级骨架而非真实供应商能力；Single-flight 主体已具备，但曾存在 200K+ 大结果硬截断风险，已在深度缺口阶段 1 中补齐 gzip 回放。

## 已引入亮点

| 亮点 | 当前落点 | 审核结果 |
| --- | --- | --- |
| OpenAPI 契约交付 | `OpenApiDocumentationConfig`、Career Controller `@Tag` / `@Operation`、`CareerOpenApiContractTest` | 已引入 |
| Docker/Compose/CI/Actuator | `Dockerfile`、`ragent-dev-stack.compose.yaml`、`.github/workflows/ci.yml`、Actuator health/readiness | 已引入 |
| 字体治理与导出可追溯 | `ResumeRenderFontRegistry`、`ResumeRenderValidationResult.fontStrategy`、导出记录 validation payload | 已引入 |
| 混合压缩记忆 | `ConversationMemoryBucket`、`ConversationMemoryCompressionPlanner`、trigger policy 与 memory tests | 已引入 |
| 长文本 TTS | `CareerTextToSpeechService`、`XunfeiLongTextToSpeechProvider`、`/career/interviews/{sessionId}/tts/plan`、`CareerTextToSpeechServiceTest` | 已引入为可降级 provider 能力，默认关闭 |
| 神态/表情分析 | `CareerDemeanorAnalysisService`、`/career/interviews/{sessionId}/demeanor/analyze`、`CareerDemeanorAnalysisServiceTest` | 已引入为可关闭辅助信号 |
| 统一验收矩阵 | `post-fusion-acceptance-matrix.md`、`scripts/verify_post_fusion_acceptance.py` | 已引入 |
| Single-flight 大结果回放 | `CareerSingleFlightLlmServiceImpl`、`CareerSingleFlightTest.llmWrapperPersistsLargeAiResultWithoutTruncatingReplay` | 已补齐 gzip + Base64 完整回放 |

## 仍未引入但需按深度计划补齐的内容

| 项 | 当前状态 | 后续处理 |
| --- | --- | --- |
| 真实第三方 TTS 音频供应商调用 | 已接入讯飞长文本 TTS provider，尚需 mock HTTP 覆盖和真实密钥联调 | 继续补阶段 2 的 provider 级 HTTP 测试与联调记录 |
| 真实神态/表情模型 | 已接入讯飞星辰 workflow provider，支持图片 URL/Base64、工作流调用、结果归一化和失败降级，默认关闭 | 后续补充真实密钥联调记录与供应商响应样例归档 |
| 邮件验证码 | Auth 当前只有登录/登出 | 按阶段 4 增加验证码、TTL、频控和注册/找回密码校验 |
| FollowUp 规则链配置化 | 已有节点式规则链，但不是 YAML/LiteFlow 热配置 | 按阶段 5 做配置顺序、启停和命中审计 |
| RAG 双向 WebSocket | RAG 聊天仍是 SSE 单向 | 按阶段 6 作为可选通道接入 |

## 仍未引入且不应直接搬入的内容

| 项 | 原因 | 后续处理 |
| --- | --- | --- |
| 第二套认证、模型 SDK、Trace、前端 Shell | 违反 ragent 唯一运行底座原则 | 不引入 |
| FreeMarker 默认模板链路 | 当前 Markdown 模板和字段映射已覆盖本轮验收，FreeMarker 仍是复杂模板后的可选升级 | 暂不引入 |
| 完整浏览器播放队列 UI | 后端已提供 TTS 计划，前端播放体验可独立迭代 | 后续体验任务 |

## 当前风险

- Maven 本地验证仍受依赖缓存和网络授权限制影响。需要在可访问 Maven Central 或企业镜像的环境里运行完整测试。
- TTS 和神态分析当前是平台契约与降级骨架，不包含外部音频/视觉模型供应商实现。
- 神态分析结果必须保持 `includedInScore=false`，不得成为招聘或评分的唯一依据。
- Single-flight 大结果已使用 gzip + Base64 回放，后续仍可补充错误分类枚举和压缩指标观测。

## 建议回归命令

```bash
python scripts/verify_post_fusion_acceptance.py
mvn -pl bootstrap -am -Dtest=CareerOpenApiContractTest,CareerTextToSpeechServiceTest,CareerDemeanorAnalysisServiceTest,ConversationMemoryCompressionPlannerTest,ConversationMemoryImportanceScorerTest test
mvn -pl bootstrap -am -DskipTests compile
cd frontend && npm run build
```
