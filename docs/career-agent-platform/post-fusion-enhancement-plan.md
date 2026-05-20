# Career Agent Platform 后融合增强计划

## 1. 最新状态校正

本计划基于最新 re-audit 结论，用于替代旧版“深度融合下一轮计划”中已经过时的缺口判断。当前 Career domain 已经吸收了较多 AI-Meeting 和 JobNavigator 能力，下一轮不应重复规划已经落地的基础项，而应聚焦后融合增强、工程交付补齐和体验扩展。

### 1.1 已融入 Career domain 的能力

| 能力 | 当前判断 | 后续关注点 |
| --- | --- | --- |
| Single-flight | 已融入 Career AI 调用治理 | 后续只做稳定性、观测和边界用例加固 |
| Redis + PostgreSQL 快照恢复 | 已融入面试运行时恢复链路 | 后续补恢复验收、回放一致性和异常演练 |
| FollowUpDecisionRule 链 | 已有规则链替代纯 LLM 决策 | 后续只做规则命中可观测和配置解释 |
| WebSocket ASR | 已有实时语音转写链路 | 后续扩展长文本 TTS，不把 ASR 当作未融入缺口重复建设 |
| Resilience4j guard | 已用于 AI 调用保护 | 后续补超时、重试、熔断参数治理和验收样例 |
| Judge-Executor loop | 已融入简历优化质量闭环 | 后续补质量门禁可解释性和失败重试策略 |
| 多格式渲染 | 已具备 Markdown、HTML、PDF、DOCX 等路径 | 后续重点是模板字段、导出失效和字体资源治理 |
| HyDE/Rerank | 已接入检索增强链路 | 后续关注 query-only 边界和证据污染防护 |
| Career agent observability | 已有 Agent 级观测基础 | 后续补 trace 查询体验和脱敏审计 |
| Plan-and-Execute | 已融入面试编排思路 | 后续关注多 Agent 阶段交接的可解释性 |

### 1.2 仍需后续增强的真实缺口

| 优先级 | 增强项 | 状态校正 |
| ---: | --- | --- |
| 1 | Springdoc OpenAPI | 尚未形成完整 API 契约交付物，优先服务工程联调和验收 |
| 2 | 完整 Docker Compose / GitHub Actions CI | 当前不应只靠本地命令验收，需要补齐可复现环境和自动化门禁 |
| 3 | 渲染字体治理 | 当前只是 CSS fallback，不等于完成 PDF/DOCX/HTML 字体资源治理 |
| 4 | 混合压缩记忆 | 长会话需要把热上下文、摘要、关键事实和检索证据分层管理 |
| 5 | 长文本 TTS | WebSocket ASR 已有基础，TTS 仍需补长文本切片、缓存和降级 |
| 6 | 神态/表情分析 | 没有找到 `DemeanorNormalizationStrategy` 源码，不能把神态归一化当作已落地能力 |
| 7 | FreeMarker 模板升级 | 暂不优先，仅在模板逻辑复杂到 Markdown/当前模板方式难以维护后再考虑 |

### 1.3 明确纠偏

- 没有找到 `DemeanorNormalizationStrategy` 源码；神态/表情分析应作为后续体验增强项重新设计和验收。
- 字体当前只是 CSS fallback；尚未完成字体文件来源、授权、加载、PDF/DOCX 嵌入、跨平台渲染和缺字回退治理。
- FreeMarker 暂不优先；当前阶段只保留为模板复杂化后的可选升级，不作为 P0/P1/P2 交付阻塞项。
- 不再把 Single-flight、快照恢复、FollowUpDecisionRule、WebSocket ASR、Resilience4j guard、Judge-Executor、多格式渲染、HyDE/Rerank、Agent observability、Plan-and-Execute 作为“未融合”主缺口。

## 2. 三种路线选择

### 路线 A：先工程交付，再体验增强（推荐）

先补 OpenAPI、Docker Compose、GitHub Actions CI、字体治理等工程交付底座，再推进混合压缩记忆、长文本 TTS、神态/表情分析。优点是验收路径清晰、多人协作成本低、后续体验增强有稳定门禁。缺点是前期视觉和语音体验提升不如路线 B 直观。

推荐选择路线 A，因为当前 re-audit 的主要风险已经不是核心 Career 能力缺失，而是“已融合能力如何被稳定交付、复现、联调和验收”。

### 路线 B：先体验增强，再补工程交付

优先做长文本 TTS、神态/表情分析和面试体验升级。优点是演示效果更快变强；缺点是 CI、契约、字体和环境未稳定前，体验能力容易变成难复现、难验收、难回归的孤岛。

### 路线 C：先治理收口，再统一推进

先做技术债清单、配置收敛、观测看板和文档体系，再分批实现工程交付和体验增强。优点是风险低；缺点是容易把实际交付推迟，且与当前 P0 文档纠偏任务相比过重。

## 3. P0-P6 分阶段计划

### P0：文档纠偏和交付边界冻结

目标：把后融合状态写清楚，避免团队重复建设已融合能力，明确哪些工作本轮不改代码、不改依赖、不改 CI/Compose。

范围：
- 新增后融合增强计划文档。
- 明确已融合能力和真实后续缺口。
- 明确 FreeMarker、字体、神态分析的状态纠偏。
- 明确第一轮 P0/P1/P2 验收标准。

非范围：
- 不修改 Java、前端、pom、CI、Docker Compose。
- 不回滚他人改动。

### P1：工程交付底座

目标：让 Career domain 的既有融合能力可以被外部联调、自动验收和环境复现。

计划：
- 引入 Springdoc OpenAPI，覆盖 Career 用户端、管理端和运行时关键接口。
- 补齐 Docker Compose，覆盖 PostgreSQL、Redis、后端、前端或必要的本地运行依赖。
- 补齐 GitHub Actions CI，至少包含后端编译/测试、前端构建、基础文档检查。
- 梳理环境变量、端口、数据库初始化和示例数据。

注意：本 P0 文档任务不直接实现上述文件，仅定义后续实施计划。

### P2：渲染字体治理和模板交付

目标：把“能导出”升级为“可验收、可复现、跨环境不乱码”。

计划：
- 建立字体资源清单：来源、授权、支持字符集、加载方式。
- 明确 HTML/CSS fallback、PDF 字体嵌入、DOCX 字体声明的不同策略。
- 建立缺字、粗体、斜体、符号、中文标点的渲染验收样例。
- 给导出记录补充字体策略、模板版本、渲染引擎和失败原因。
- FreeMarker 仅作为模板复杂化后的备选，不进入本阶段默认方案。

### P3：混合压缩记忆

目标：为长会话和多 Agent 面试提供稳定上下文管理，避免无限堆叠原文上下文。

计划：
- 将记忆分为热上下文、短摘要、长期事实、关键证据、风险标记。
- 定义压缩触发条件：轮次、token、阶段切换、恢复事件。
- 压缩结果必须可追溯到原始 turn、证据和 trace。
- 任何压缩记忆不得覆盖用户原始答案和评分依据。

### P4：长文本 TTS

目标：在不影响文字面试主链路的前提下，为问题、追问和反馈提供可降级的语音播报。

计划：
- 长文本切片、播放队列、缓存和取消机制。
- TTS 失败时降级到文本展示，不阻塞面试流程。
- 与 WebSocket ASR 解耦，避免语音输入和语音输出互相阻塞。
- 补语速、音色、断句和多段落播放验收。

### P5：神态/表情分析

目标：在确认合规、授权和数据边界后，将神态/表情作为可选辅助信号，而不是评分唯一依据。

计划：
- 先确认采集授权、隐私提示、数据保留和删除策略。
- 重新设计神态归一化模型和接口；当前未发现 `DemeanorNormalizationStrategy` 源码，不能基于不存在的类继续规划。
- 输出只作为报告辅助维度，必须展示置信度和局限性。
- 支持关闭该能力，不影响文字面试和报告生成。

### P6：体验整合和治理复盘

目标：把 P1-P5 的能力统一进产品演示、管理观测和交付文档。

计划：
- 更新 quick start、验收脚本和常见故障排查。
- 将 OpenAPI、CI、Compose、字体、记忆、TTS、神态能力纳入统一验收矩阵。
- 对已融合能力做回归验证，确保增强项没有破坏 Single-flight、快照恢复、规则链、ASR、guard、Judge-Executor、HyDE/Rerank 和 Plan-and-Execute。

## 4. 第一轮 P0/P1/P2 验收标准

### P0 验收标准

- 存在 `agent/docs/career-agent-platform/post-fusion-enhancement-plan.md`。
- 文档明确列出已融入 Career domain 的能力，不再把这些能力误判为未融合缺口。
- 文档明确后续增强项：长文本 TTS、神态/表情分析、混合压缩记忆、Springdoc OpenAPI、完整 Docker Compose/GitHub Actions CI、渲染字体治理。
- 文档明确三项纠偏：未找到 `DemeanorNormalizationStrategy` 源码；字体当前只是 CSS fallback；FreeMarker 暂不优先。
- 本轮只修改 `agent/docs/career-agent-platform/**` 下文档，不改代码、不改 pom、不改 CI/Compose。

### P1 验收标准

- Springdoc OpenAPI 能生成 Career API 文档，接口分组清晰，包含用户端、管理端、面试运行时和导出相关接口。
- OpenAPI 文档中的请求/响应模型与实际 Controller 契约一致，至少覆盖主流程 smoke 所需接口。
- Docker Compose 能启动 Career 联调所需依赖，并包含数据库初始化说明。
- GitHub Actions CI 至少执行后端编译或测试、前端构建，以及必要的文档路径检查。
- 本地和 CI 的环境变量、端口、数据库、Redis 配置有明确说明。

### P2 验收标准

- 字体治理不再停留在 CSS fallback；必须有字体资源清单、授权说明、加载策略和失败回退策略。
- HTML、PDF、DOCX 至少各有一组中文、英文、符号、粗体、长段落样例验收。
- 导出记录可以追踪模板版本、字体策略、渲染引擎、traceId 和失败原因。
- 缺字、乱码、字体缺失时返回可诊断错误或受控降级结果。
- FreeMarker 没有被提前引入为默认依赖；只有当模板逻辑复杂度超过当前方案承载能力时，才进入单独评审。

## 5. 第一轮执行建议

建议第一轮只推进 P0 到 P2：

1. P0 先完成文档纠偏和交付边界冻结。
2. P1 再做工程交付底座，让现有融合能力可联调、可复现、可自动验收。
3. P2 最后治理渲染字体，因为它直接影响 PDF/DOCX/HTML 导出的用户可信度。

P3-P6 不建议与 P1/P2 并行抢同一批实现资源。混合压缩记忆、长文本 TTS、神态/表情分析都依赖更稳定的工程门禁和验收矩阵，适合在第一轮工程交付稳定后进入。
