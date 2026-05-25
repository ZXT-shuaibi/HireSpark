# Ragent Career Agent Platform PRD

版本：v0.3 能力融合评审稿
日期：2026-05-17
基座项目：Ragent AI
融合来源：JobNavigator、AI-Meeting
目标状态：将 Ragent 从通用 Agentic RAG 平台升级为“面向求职成长闭环的企业级 AI Agent 平台”

## 1. 版本说明与评审结论

v0.2 将 v0.1 的 BRD/立项叙事改写为可评审、可开发、可测试的产品 PRD。本文档是 Career Agent Platform 的需求单一事实源，实施计划只引用本文档，不再以旧版评审稿为准。

产品展示名暂不作为需求阻塞项。本文档暂用 “Ragent Career Agent Platform”，页面展示可简称 “Ragent Career”，后续可以通过文案或配置调整，不影响 MVP 功能验收。

本轮已经确认以下结论：

- 以 Ragent 为唯一运行基座，不做 JobNavigator、AI-Meeting、Ragent 三项目并列部署。
- JobNavigator 和 AI-Meeting 作为能力来源，不作为独立子系统嵌入。
- MVP 先完成 Phase 1 和 Phase 2：简历/JD 对齐、简历优化、文字模拟面试、复盘报告。
- Phase 3 和 Phase 4 作为路线图能力，不阻塞 MVP 验收。
- 第一版主场景聚焦 Java 后端 / AI 应用开发求职者。
- MVP 不做语音 ASR/TTS、视频面试、神态分析、真实招聘 ATS、支付会员和第三方招聘平台账号打通。
- 所有核心 AI 输出必须结构化，前端不能依赖解析自由文本来展示核心结果。
- 简历、JD、优化建议、面试会话、复盘报告必须绑定用户、版本和 Trace，支持追溯与演示。
- 本轮补充的重点不是“引用两个项目名称”，而是把 AI-Meeting 和 JobNavigator 的工程亮点拆成可验收的产品能力、领域模型和研发任务。
- AI-Meeting 的状态机、轮次幂等、Single-flight、会话恢复、ASR/TTS 等能力按“文字链路优先、实时媒体后置”的原则吸收。
- JobNavigator 的裁判-执行者优化、质量门禁、HyDE/Rerank、多格式渲染和进度可视化按“先结构化闭环、再自动化增强”的原则吸收。

## 2. 背景与问题

Ragent 当前已经具备企业级 RAG 平台底座能力：知识库、文档入库 Pipeline、多路检索、意图识别、模型路由、会话记忆、MCP 工具调用、Trace 可观测性、用户与管理后台。这些能力适合作为统一平台基座。

JobNavigator 提供求职前链路能力，包括简历解析、简历优化、JD 对齐、人岗匹配、HyDE/RAG 检索、简历多格式渲染和 Agent 可观测性。

AI-Meeting 提供求职后链路能力，包括简历驱动出题、模拟面试、多 Agent 评分与追问、雷达图报告、面试记录、ASR/TTS 与 WebSocket 实时交互。

### 2.1 融合来源能力分析

本 PRD 对两个来源项目采用“能力迁移”而不是“系统迁移”。Ragent 继续提供统一认证、模型路由、Trace、知识库、PostgreSQL 和前端 Shell；来源项目只贡献产品机制、领域模型和工程模式。

| 来源 | 亮点能力 | Ragent Career 吸收方式 | 阶段 |
| --- | --- | --- | --- |
| AI-Meeting | 简历驱动出题、多 Agent 评分、追问官裁决、雷达报告 | 面试计划、答题评分、追问裁决和报告都绑定简历版本、JD、Rubric 和 Trace | Phase 2 |
| AI-Meeting | EnumMap 状态机 + LiteFlow 规则链 | 不强制引入 LiteFlow；在 Career 域内落地显式面试状态机和可替换追问规则接口 | Phase 2/3 |
| AI-Meeting | 题级分布式锁、stepIdempotency、轮次补偿 | 引入答题轮次幂等键、同题重复提交防重、评分失败可手动重试、异常轮次补偿任务 | Phase 2/3 |
| AI-Meeting | 分布式 Single-flight、Fencing Token、心跳与结果回放 | 抽象为 Career AI 调用治理层，优先覆盖评分、追问、抽题、优化、报告生成，避免重复扣费和重复产物 | Phase 3 |
| AI-Meeting | Mongo Snapshot + Redis 懒加载长会话恢复 | 不新增 Mongo 运行依赖；用 PostgreSQL JSONB 快照 + 可选 Redis 热态缓存实现可恢复运行态 | Phase 3 |
| AI-Meeting | WebSocket + Xunfei AST ASR、TTS、神态分析 | 作为文字面试稳定后的实时交互扩展，ASR 只作为答案输入来源，不作为报告唯一依据 | Phase 4 |
| AI-Meeting | Skill 业务知识体系 | 将 Career Skill Pack 固化为可被 Agent 消费的项目知识单元，沉淀 Rubric、Prompt、状态机和排障手册 | Phase 3 |
| JobNavigator | 裁判-执行者双智能体简历优化 | 简历优化由“执行者生成建议 + 裁判评分/验真/反思”组成，支持多轮定向优化 | Phase 1/3 |
| JobNavigator | Score > 0.8 质量门禁和优化过程可视化 | 质量分低于门禁时不直接交付为最终版本，前端展示优化轮次、评分变化和失败原因 | Phase 3 |
| JobNavigator | LLM 简历深度解析与结构化画像 | 作为 `CandidateProfile` 和 `ResumeVersion` 的结构化输入契约 | Phase 1 |
| JobNavigator | HyDE + RAG + Rerank 人岗匹配 | 通过 Ragent 统一检索抽象接入，不引入 Qdrant；用于 JD 对齐、样例简历检索和面试题增强 | Phase 1/3 |
| JobNavigator | Markdown/HTML/PDF/Word 渲染流水线 | MVP 先做 Markdown/HTML；PDF/Word 在独立渲染器任务中补齐字段校验、模板和排版一致性 | Phase 1/3 |
| JobNavigator | 异步任务与 Agent 可观测 | 统一落到 Career task、attempt、progress event 和 Ragent Trace | Phase 1/3 |

### 2.2 能力吸收原则

- 能力进 Ragent，运行时不分裂：不复制 AI-Meeting 的 Mongo/MySQL/Spring AI 运行时，不复制 JobNavigator 的 Qdrant 运行时。
- 先产品闭环，后工程增强：Phase 1/2 先保证用户能完成求职闭环；Phase 3 把去重、恢复、门禁、进度和渲染做厚。
- 所有 Agent 输出必须可解释：评分、优化、追问、报告都要保留输入摘要、证据、Rubric、Prompt 版本和 Trace。
- 所有自动优化必须尊重真实性：不能把 AI 编造经历自动写入简历，必须通过风险标记、裁判验真和用户确认。
- 实时语音和多模态不能绑死主链路：文字面试必须独立可完成，ASR/TTS/神态分析只能增强体验。
- Career Skill Pack 是项目知识资产：Rubric、Prompt、状态机、排障和验收规则必须可版本化、可消费、可演示。

三者可以组合为一条求职成长闭环：

```text
知识库与岗位资料入库
  -> 简历解析与职业画像
  -> JD 对齐与简历优化
  -> 基于简历/JD/知识库的模拟面试
  -> 面试复盘与能力雷达
  -> 反哺简历优化与下一轮训练
```

### 2.3 亮点吸收总表

| 来源项目 | 亮点能力 | Ragent Career 落点 | 阶段 |
| --- | --- | --- | --- |
| AI-Meeting | 简历驱动出题、多 Agent 评分、追问裁决、雷达报告 | 面试计划、答题评分、追问裁决和面试报告统一绑定简历版本、JD、Rubric、证据和 Trace，形成可复盘的文字面试闭环 | Phase 2 |
| AI-Meeting | 完整答题链路、状态机、缓存与快照 | 从 Controller 到 Service 到 Agent 的答题主链路显式化，答案保存、评分、追问、快照、恢复和 Trace 串成一条可观测链路 | Phase 2/3 |
| AI-Meeting | EnumMap 状态机 + LiteFlow 风格规则链 | 在 Career 域内实现显式面试状态机、可替换追问规则和受控流程推进，不依赖独立工作流引擎 | Phase 2/3 |
| AI-Meeting | 题级分布式锁、stepIdempotency、轮次补偿 worker | 以 `stepIdempotencyKey`、题级互斥、补偿 worker、失败重试和补偿状态覆盖重复提交与异常轮次，保证答题闭环不丢轮次 | Phase 2/3 |
| AI-Meeting | 手动重试评分 | 评分失败后保留答案和轮次上下文，用户可手动重试本题评分，不会重复创建追问或丢失原始 attempt | Phase 2/3 |
| AI-Meeting | 长会话状态治理与恢复 | 用 PostgreSQL JSONB 快照 + 可选 Redis 热态缓存实现会话恢复，支持热冷分层、CAS 保护和恢复幂等 | Phase 3 |
| AI-Meeting | 分布式 Single-flight、owner heartbeat、Fencing Token、结果回放 | 将 AI 调用治理成统一去重层，覆盖评分、追问、优化、报告生成，避免重复扣费和重复产物 | Phase 3 |
| AI-Meeting | WebSocket + ASR/TTS + 分段转写重建 | 作为 Phase 4 扩展，支持实时语音答题、转写增量去重和展示分层，但不替代文字面试主链路 | Phase 4 |
| AI-Meeting | Skill 业务知识体系 | 收敛为 `Career Skill Pack`，沉淀 Rubric、Prompt、状态机、排障和验收规则，供 Agent 直接消费 | Phase 3 |
| JobNavigator | LLM 简历深度解析与结构化画像 | 用于 `CandidateProfile`、`ResumeVersion` 和 `ResumeDocument` 的结构化输入契约，支撑后续优化与面试 | Phase 1 |
| JobNavigator | 裁判-执行者双智能体简历优化 | 将优化拆成执行者生成与裁判验真/反思两步，所有建议都要有证据、风险标记和版本归属 | Phase 1/3 |
| JobNavigator | 多轮裁判-执行者迭代与 `0.8` 质量门禁 | 低于门禁时不直接交付，必须进入复核或下一轮定向优化，并记录进度事件和原因 | Phase 3 |
| JobNavigator | HyDE + RAG + Rerank 人岗匹配 | 通过 Ragent 统一检索抽象接入，用于 JD 对齐、缺口分析、样例证据检索和面试题增强 | Phase 1/3 |
| JobNavigator | Markdown/HTML/PDF/Word 渲染流水线 | 建立同一结构化数据源到多格式输出的一次生成、多端交付模型，后续补齐模板、字段和失效治理 | Phase 1/3 |
| JobNavigator | 异步任务处理与 Agent 可观测 | 统一落到 Career task、attempt、progress event 和 Trace，支撑可重试、可追踪、可解释 | Phase 1/3 |

当前问题分为两层：

- 用户层：简历优化、岗位匹配、面试训练和复盘通常分散在不同工具里，数据不能连续使用，AI 建议也经常缺少证据和版本追溯。
- 项目层：Ragent 作为通用 RAG 学习平台，业务场景厚度不足；如果直接拼接三个项目，又会形成多套登录、多套模型调用、多套 Trace 和多套数据模型。

### 2.4 融合后目标架构

Ragent Career 不复制 AI-Meeting 和 JobNavigator 的运行时，而是把它们的亮点收敛到 Ragent 的 Career 领域内，形成五层架构：

1. Ragent 平台基座层：继续复用统一认证、模型路由、知识库、Trace、PostgreSQL、Redis 和前端 Shell。
2. Career 领域资产层：统一管理简历、JD、优化任务、面试会话、报告、导出记录和版本关系。
3. AI-Meeting 运行治理层：吸收状态机、轮次幂等、补偿 worker、手动重试、快照恢复、Single-flight、追问规则链和 AI 调用守卫。
4. JobNavigator 质量交付层：吸收裁判-执行者、多轮反思、0.8 质量门禁、HyDE/Rerank、Plan-and-Execute 面试编排、雷达报告和多格式渲染。
5. Skill Pack 与可观测层：把 Rubric、Prompt、状态机、排障、验收规则、Agent 调用轨迹和任务进度沉淀成可被 Agent 消费和人类评审的资产。

落地顺序遵循“先闭环、再治理、再增强”：先稳定文字简历/JD/优化/面试闭环，再补齐面试运行时治理和 Single-flight，然后补追问规则链、AI Guard、4-Agent 编排、雷达计算、PDF/DOCX 真渲染，最后进入语音 ASR/TTS 和多模态扩展。

## 3. 产品目标、非目标与成功指标

### 3.1 产品目标

Ragent Career Agent Platform 是一个基于 RAG、Agent、多模型路由和 Trace 的求职成长平台。它帮助求职者围绕目标岗位完成简历诊断、JD 匹配、简历优化、模拟面试、复盘改进和下一轮简历迭代。

### 3.2 非目标

- 不做真实招聘企业 ATS。
- 不为招聘方提供候选人自动排名、自动淘汰或录用建议。
- 不做商业支付、会员订阅、订单系统。
- 不做第三方招聘平台账号打通和批量投递。
- 不做视频面试反作弊。
- 不承诺 AI 评分具备真实招聘决策效力。
- 不把 JobNavigator 和 AI-Meeting 原系统原样运行在 Ragent 内。

### 3.3 MVP 成功指标

- 用户可以完成完整链路：上传简历 -> 修正解析结果 -> 创建 JD -> JD 对齐 -> 生成优化建议 -> 创建新简历版本 -> 文字模拟面试 -> 复盘报告 -> 下一轮简历优化建议。
- 每条核心 AI 建议都能关联到简历版本、JD、证据来源和 Trace。
- 前端能从结构化字段稳定渲染报告，不解析自由文本获取核心数据。
- 管理员或评审者可以查看任务状态、失败原因和 Trace 链路。
- Ragent 原有知识库问答、用户体系、模型路由、Trace 和管理后台不被破坏。

## 4. Persona 与默认演示场景

### 4.1 MVP 主要用户

默认 Persona：Java 后端 / AI 应用开发求职者。

- 经验：应届生到 3 年工作经验。
- 目标：将技术项目经历整理成面向 Java 后端、RAG、Agent 或 AI 应用开发岗位的可投递简历，并完成针对性面试训练。
- 典型焦虑：简历太泛、项目亮点讲不清、JD 匹配度不确定、面试回答没有结构、AI 建议可能编造经历。

### 4.2 默认演示包

MVP 需要准备一套可复现样例：

- 一份 Java 后端 / AI 应用开发求职者简历。
- 一份目标 JD，包含 Spring Boot、RAG、Agent、PostgreSQL、Redis、工程质量和协作要求。
- 一套面试 Rubric，覆盖技术深度、项目 ownership、工程质量、表达结构、岗位匹配和学习潜力。
- 一条完整 Trace 链路，用于演示“为什么系统给出这个建议或评分”。

### 4.3 非默认岗位的通用模式

MVP 允许非 Java 后端 / AI 应用开发岗位继续使用，例如产品经理、前端、测试、运维、数据分析等岗位。

当系统识别到简历或 JD 不属于默认 Java 后端 / AI 应用开发场景时，应进入通用模式：

- 允许继续完成简历解析、JD 对齐、简历优化、文字模拟面试和复盘报告。
- 使用通用职业能力 Rubric，而不是 Java 后端专用 Rubric。
- 使用通用 JD 结构和通用面试问题生成策略，不承诺岗位专用题库。
- 前端需要提示“当前为通用模式，专业岗位模板和专用评分维度暂未启用”。
- 验收和答辩仍以 Java 后端 / AI 应用开发样例作为主线，不把所有岗位的专业深度作为 MVP 通过条件。

其他岗位可以作为扩展场景，但 v1 不承诺为每个岗位做专用模板、专用 Rubric 或专用题库。

## 5. 角色与权限

### 5.1 求职用户

求职用户可以：

- 上传、查看、修正、删除自己的简历。
- 创建、查看、删除自己的 JD。
- 运行自己的简历-JD 对齐、简历优化和模拟面试。
- 查看自己的 Trace 摘要、报告、历史记录和导出文件。

求职用户不能：

- 访问其他用户的简历、JD、面试记录和报告。
- 修改全局 Rubric、模型配置、样例数据和后台统计。

### 5.2 管理员

管理员可以：

- 管理知识库、样例简历、样例 JD 和 Prompt 版本。
- 在 MVP 查看固定 Rubric 模板；Rubric 编辑能力放到 Phase 3。
- 查看 Career 任务列表、状态、失败原因和 Trace 链接。
- 查看基础数量摘要；统计图表放到 Phase 3。
- 配置模型候选列表和基础降级策略。

管理员不能：

- 将用户敏感简历正文直接写入公开样例、公开日志或演示材料。
- 代表真实招聘方使用 AI 分数做自动筛选决策。

### 5.3 删除与隐私保留策略

当用户删除简历、JD、面试记录或复盘报告时，系统采用“级联软删除 + 敏感内容脱敏保留 Trace 摘要”的策略。

删除后：

- 用户侧不再展示被删除对象及其关联版本、报告、导出文件和历史入口。
- 关联导出文件应失效，不允许继续下载。
- 关联优化任务、面试会话和报告应标记为已删除或不可见，不产生孤儿页面。
- Trace 可保留任务类型、状态、模型、耗时、错误类型、字段级摘要和脱敏后的证据引用。
- Trace 不得保留完整简历正文、完整 JD 原文、完整面试回答、手机号、邮箱、证件号、详细地址等敏感内容。
- 管理员可以查看任务摘要和失败原因，但不能通过 Trace 恢复用户删除的完整内容。
- 如果底层对象存储暂不支持立即物理删除，必须让用户侧下载链接失效，并在后台保留清理任务记录。

### 5.4 开发者 / 评审者

开发者和评审者关注：

- Ragent 是否仍然是唯一运行基座。
- 简历、JD、优化、面试、报告是否是清晰可替换的深模块。
- AI 链路是否可观测、可回放、可定位失败。
- 产品闭环是否真实，不是多个 Demo 的堆叠。

## 6. MVP 用户旅程

### 6.1 主路径

1. 用户进入 `/career`，看到求职成长闭环入口。
2. 用户上传简历文件。
3. 系统提取文本，创建解析任务。
4. 系统生成结构化职业画像和初始简历版本。
5. 用户检查并修正基础信息、教育经历、工作经历、项目经历、技能和亮点。
6. 用户创建目标 JD。
7. 系统解析 JD，拆解岗位职责、硬性要求、加分项、技术栈和软素质。
8. 用户选择一个简历版本和一个 JD，运行对齐分析。
9. 系统返回匹配分、维度分、证据、缺口、风险和建议动作。
10. 用户生成简历优化建议。
11. 系统按经历强化、技能补齐、项目表达、量化成果、风险提示输出建议。
12. 用户对建议进行采纳、编辑或拒绝。
13. 系统基于已采纳和已编辑建议创建新的简历版本。
14. 用户基于新简历版本和目标 JD 开始文字模拟面试。
15. 系统生成面试计划和第一道问题。
16. 用户逐题回答。
17. 系统评分、反馈，并根据需要追问。
18. 用户暂停、恢复或结束面试。
19. 系统生成面试复盘报告。
20. 用户查看雷达维度、逐题复盘、风险点、下一步训练建议和可反哺简历的建议。

### 6.2 主要分支路径

- 简历解析失败：任务进入 `FAILED`，用户看到失败原因和重试入口。
- JD 文本太短或不清楚：系统标记低质量 JD，并提示补充职责、要求、年限、技术栈。
- AI 输出格式错误：任务失败或进入可重试状态，系统记录原始输出和解析错误，前端不展示半截报告。
- 用户拒绝全部优化建议：系统保留原简历版本，不创建空的新版本。
- 面试中断：会话可暂停并恢复，已完成轮次不丢失。
- 报告生成失败：用户可以重试，系统保留已完成问答。
- 用户访问他人数据：系统拒绝访问并记录权限失败。

## 7. 信息架构与页面清单

### 7.1 用户侧页面

| 页面 | 路由 | 主要任务 |
| --- | --- | --- |
| Career 首页 | `/career` | 展示闭环进度、最近简历、最近 JD、最近面试和下一步入口 |
| 简历中心 | `/career/resumes` | 上传简历、查看解析状态、修正职业画像、管理简历版本 |
| JD 对齐 | `/career/alignment` | 创建 JD、查看 JD 解析、选择简历版本运行匹配分析 |
| 简历优化工作台 | `/career/optimization` | 查看优化任务、处理建议、生成新版本、导出 Markdown/HTML |
| 模拟面试 | `/career/interview` | 创建文字面试、查看计划、答题、暂停、恢复、结束 |
| 面试复盘报告 | `/career/reports/:reportId` | 查看总分、雷达图、逐题复盘、风险和下一步建议 |

### 7.2 管理侧页面

| 页面 | 路由 | 主要任务 |
| --- | --- | --- |
| Career 仪表盘 | `/admin/career` | MVP 展示基础数量摘要和最近失败任务；统计图表放到 Phase 3 |
| Career 任务管理 | `/admin/career/tasks` | 查看解析、对齐、优化、面试、报告任务状态与失败原因 |
| Rubric 查看 | `/admin/career/rubrics` | MVP 只读展示固定面试评分维度，编辑能力放到 Phase 3 |
| Trace 查询 | 复用现有 Trace 页面 | 从 Career 任务跳转到详细 Trace |

## 8. Phase 1 功能需求：简历与 JD 对齐闭环

### 8.1 简历上传与解析

用户目标：上传简历，并让系统生成可检查、可修正、可版本化的职业画像。

前置条件：

- 用户已登录。
- 上传文件属于当前用户。
- 文件格式第一版支持文本可提取的 PDF、DOCX、Markdown 或纯文本；无法提取文本的扫描件进入失败或人工补录路径。

页面入口：`/career/resumes`

输入：

- 简历文件。
- 可选：目标岗位方向、期望城市、期望行业。

输出：

- `ResumeDocument`
- `CandidateProfile`
- 初始 `ResumeVersion`
- 解析任务状态和 Trace 引用

主流程：

1. 用户选择文件并上传。
2. 系统创建 `ResumeDocument`，状态为 `PENDING`。
3. 系统提取文本，状态变为 `RUNNING`。
4. 系统调用简历解析 Prompt，生成结构化职业画像。
5. 系统保存 `CandidateProfile` 和初始 `ResumeVersion`。
6. 任务状态变为 `SUCCESS`。
7. 前端展示解析结果和可编辑字段。

状态：

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`

错误状态：

- 文件为空。
- 文件过大。
- 文件类型不支持。
- 文本提取失败。
- AI 输出格式不合法。
- 保存结构化结果失败。

权限要求：

- 用户只能查看和修改自己的简历。
- 删除简历时，需要同时处理关联版本、任务、报告、导出文件和 Trace 摘要，不允许产生孤儿页面。
- 删除简历后，业务数据对用户不可见，导出文件失效，Trace 只保留脱敏摘要。

Trace 要求：

- 记录文本提取摘要、Prompt 版本、模型、结构化解析结果、错误原因。
- 日志不得直接输出完整敏感简历正文。

验收标准：

- Given 一个合法样例简历，When 用户上传，Then 系统创建解析任务、职业画像和初始简历版本。
- Given 解析失败，When 用户查看任务，Then 能看到失败原因和重试入口。
- Given 用户 A 的简历，When 用户 B 请求详情，Then 系统拒绝访问。

### 8.2 简历画像修正与版本管理

用户目标：修正 AI 解析错误，并维护不同岗位使用的简历版本。

页面入口：`/career/resumes`

输入：

- 基础信息、教育经历、工作经历、项目经历、技能、证书、亮点。
- 版本名称和版本说明。

输出：

- 更新后的 `CandidateProfile`
- 一个或多个 `ResumeVersion`

主流程：

1. 用户查看解析结果。
2. 用户编辑错误字段。
3. 系统保存职业画像。
4. 用户创建或复制简历版本。
5. 系统保留版本号、标题、来源版本和创建时间。

错误状态：

- 必填字段为空。
- 版本名称重复。
- 用户编辑了不属于自己的版本。

验收标准：

- Given 解析出的项目经历有错误，When 用户修改并保存，Then 后续 JD 对齐使用修正后的版本。
- Given 一个用户有多个岗位方向，When 用户创建多个版本，Then 每个版本都能独立用于 JD 对齐和面试。

### 8.3 JD 创建与解析

用户目标：录入目标岗位 JD，并让系统拆解成可匹配的要求维度。

页面入口：`/career/alignment`

MVP 来源范围：

- 手动粘贴或录入 JD 文本。
- 上传包含 JD 内容的文本可提取文件。

后续来源范围：

- 招聘网站或公开 JD 链接抓取。
- 链接抓取需要在后续独立任务中处理网页解析、反爬、登录态、版权、内容清洗和抓取失败重试。

MVP 不要求支持链接抓取。用户输入 URL 时，系统应提示“链接抓取暂未启用，请粘贴 JD 正文或上传文件”。

输入：

- JD 标题。
- 公司名称，可选。
- JD 原文。

输出：

- `JobDescription`
- JD 解析结构

主流程：

1. 用户粘贴、录入或上传 JD。
2. 系统校验 JD 长度和基本质量。
3. 系统提取岗位名称、公司、年限、业务域、职责、硬性要求、加分项、技术栈、软素质和关键词。
4. 前端展示拆解结果。

错误状态：

- JD 文本过短。
- JD 缺少岗位职责或任职要求。
- 用户输入 URL 但链接抓取暂未启用。
- AI 解析失败。

验收标准：

- Given 一份 Java 后端 JD，When 用户创建 JD，Then 系统能拆解技术栈、硬性要求、加分项和软素质。
- Given JD 内容只有一句话，When 用户提交，Then 系统提示补充信息，不直接生成低质量匹配报告。
- Given 用户输入招聘网站链接，When 链接抓取未启用，Then 系统提示粘贴 JD 正文或上传文件。

### 8.4 简历-JD 对齐分析

用户目标：知道当前简历与目标岗位的匹配程度、证据、缺口和风险。

页面入口：`/career/alignment`

输入：

- `resumeVersionId`
- `jdId`

输出：

- `JobAlignmentReport`
- 总分、维度分、要求匹配、缺口、风险、建议动作、Trace 引用

主流程：

1. 用户选择简历版本和 JD。
2. 系统校验二者归属当前用户。
3. 系统调用 JD 对齐链路。
4. 系统输出结构化匹配报告。
5. 前端展示总分、维度分、证据、缺口和建议。

错误状态：

- 简历版本不存在。
- JD 不存在。
- 权限不匹配。
- AI 输出字段缺失。
- Trace 写入失败但业务结果成功时，需要提示 Trace 部分异常。

验收标准：

- Given 一份解析后的简历和 JD，When 用户运行对齐，Then 报告包含总分、维度分、证据、缺口、风险和 Trace。
- Given AI 输出缺少必要字段，When 后端解析失败，Then 任务标记失败，前端展示可重试错误。

### 8.5 简历优化建议与新版本生成

用户目标：基于 JD 对齐结果获得可采纳、可编辑、可拒绝的简历优化建议。

页面入口：`/career/optimization`

输入：

- `resumeVersionId`
- `jdId`
- 可选 `alignmentReportId`

输出：

- `ResumeOptimizationTask`
- `ResumeOptimizationSuggestion[]`
- 新的 `ResumeVersion`

主流程：

1. 用户选择匹配报告并生成优化建议。
2. 系统按经历强化、技能补齐、项目表达、量化成果、风险提示生成建议。
3. 每条建议展示原文、建议文本、原因、证据引用和真实性风险。
4. 用户选择采纳、编辑或拒绝。
5. 用户确认生成新版本。
6. 系统创建新的 `ResumeVersion`，原版本不被覆盖。

建议状态：

- `PENDING`
- `ACCEPTED`
- `EDITED`
- `REJECTED`

错误状态：

- 所有关联建议都被拒绝时，不创建新版本。
- 建议引用的原文不存在时，标记为低置信建议。
- AI 建议包含疑似编造经历时，必须标记风险，不自动写入简历。

验收标准：

- Given 优化建议列表，When 用户采纳部分建议，Then 系统创建新版本并保留原版本。
- Given 建议存在真实性风险，When 前端展示，Then 用户能看到风险级别和原因。

### 8.5.1 裁判-执行者优化增强

JobNavigator 的“裁判-执行者”双智能体协作不应只停留在描述层。Ragent Career 需要把它拆成两个可观测步骤：

- 执行者 Agent：根据简历版本、JD、对齐报告和知识库证据生成优化建议。
- 裁判 Agent：逐条判断建议是否提升匹配度、是否有证据支撑、是否存在编造经历风险，并给出质量分。

MVP 可以先把裁判结果作为建议字段输出，不要求自动多轮迭代。Phase 3 增加自动反思迭代：

- 当整体质量分低于 `0.8` 时，系统不把结果标记为“可直接交付”，而是标记为 `NEEDS_REVIEW` 或触发下一轮定向优化。
- 最多自动迭代 3 轮，避免成本失控。
- 每一轮都必须记录执行者输出、裁判评分、拒绝原因、采纳原因、Trace 和耗时。
- 前端展示优化进度，让用户看到“生成 -> 评审 -> 修正 -> 达标/需人工确认”的过程。

质量门禁不等于自动改写用户简历。只有用户采纳或编辑后的建议，才能进入新的 `ResumeVersion`。

验收标准：

- Given 一条建议缺少简历原文证据，When 裁判 Agent 评审，Then 该建议标记为高风险或低置信度，不能自动进入新版本。
- Given 优化任务质量分低于 `0.8`，When 用户查看任务，Then 页面提示需要复核或继续优化，而不是展示为最终交付版本。
- Given 优化任务经历多轮迭代，When 管理员查看 Trace，Then 能看到每轮执行者输出、裁判评分和最终门禁结果。

### 8.6 简历导出

用户目标：将优化后的简历导出为可投递或可检查的格式。

MVP 范围：

- 支持 Markdown。
- 支持 HTML。

后续范围：

- PDF。
- Word。

PDF 和 Word 不进入 MVP，不作为 Phase 1/2 验收条件。后续只有在独立渲染器任务明确字体、分页、模板和中文兼容策略后再启用。

Phase 3 的完整渲染流水线需要吸收 JobNavigator 的“一次优化，多端交付”能力：

- 结构化简历对象必须先通过字段校验，再进入模板渲染。
- Markdown、HTML、PDF、Word 共享同一份结构化数据源，不能各自拼接一套字段。
- PDF/Word 启用前必须定义模板、字体、分页、中文兼容、图片和链接处理规则。
- 每次导出生成 `ResumeExportRecord`，记录格式、模板版本、字段校验结果、文件地址、失效状态和 Trace。
- 简历版本被删除或不可见后，关联导出文件必须失效。

验收标准：

- Given 一个简历版本，When 用户导出 Markdown，Then 系统返回可下载文件。
- Given 用户请求 PDF 或 Word，When 渲染器尚未启用，Then 系统给出明确提示，不返回损坏文件。

## 9. Phase 2 功能需求：文字模拟面试与复盘闭环

### 9.1 面试会话创建与计划生成

用户目标：基于某个简历版本和目标 JD 开启一次贴近岗位的文字模拟面试。

页面入口：`/career/interview`

输入：

- `resumeVersionId`
- 可选 `jdId`
- 可选面试轮次偏好：技术、项目深挖、行为、岗位动机。

输出：

- `InterviewSession`
- `InterviewPlan`
- 第一道 `InterviewTurn`

主流程：

1. 用户选择简历版本和 JD。
2. 系统校验权限。
3. 系统生成面试计划，覆盖技术深度、项目 ownership、沟通表达、JD 匹配和学习潜力。
4. 系统创建会话并生成第一题。

错误状态：

- 简历版本不存在。
- JD 不存在。
- 面试计划生成失败。
- 用户无权限访问简历或 JD。

验收标准：

- Given 一个简历版本和 JD，When 用户创建面试，Then 系统生成面试计划和第一题。
- Given 用户只选择简历版本，When 创建面试，Then 系统允许以通用求职训练模式开始，但标记 JD 匹配维度缺失。

### 9.2 答题、评分与追问

用户目标：逐题回答，并获得即时评分、反馈和必要追问。

页面入口：`/career/interview`

输入：

- 当前题目。
- 用户文本答案。

输出：

- `InterviewTurn`
- 评分、维度分、优点、问题、参考回答、可选追问

主流程：

1. 系统展示当前问题。
2. 用户提交答案。
3. 系统保存答案。
4. 系统调用评分 Prompt。
5. 系统保存分数和反馈。
6. 如果薄弱点需要追问，系统生成追问题。
7. 如果无需追问且计划还有题目，系统生成下一题。
8. 如果没有剩余题目，系统允许结束会话。

轮次状态：

- `ASKED`
- `ANSWERED`
- `EVALUATED`

会话状态：

- `CREATED`
- `IN_PROGRESS`
- `PAUSED`
- `COMPLETED`

错误状态：

- 空答案。
- 会话已完成后继续提交。
- AI 评分失败。
- 追问生成失败。

验收标准：

- Given 一道已回答问题，When 系统评分，Then 返回分数、反馈、弱点和可选追问。
- Given 会话已完成，When 用户提交新答案，Then 系统拒绝。

### 9.2.1 面试答题状态机、幂等与补偿

AI-Meeting 的答题主链路需要在 Career 域中沉淀为显式状态机，而不是散落在 Controller 和 Service 判断里。

每个 `InterviewTurn` 至少包含以下运行态字段：

- `turnNo`：会话内递增轮次。
- `stepIdempotencyKey`：由 `sessionId + turnNo + answerRevision` 生成，用于防止同一答案重复评分。
- `answerStatus`：答案保存状态。
- `evaluationStatus`：评分状态。
- `followUpDecisionStatus`：追问裁决状态。
- `compensationStatus`：异常补偿状态。

答题推进规则：

```text
ASKED
  -> ANSWER_SAVED
  -> EVALUATING
  -> EVALUATED
  -> FOLLOW_UP_DECIDING
  -> FOLLOW_UP_CREATED | NEXT_MAIN_CREATED | SESSION_COMPLETED
```

异常推进规则：

```text
EVALUATING -> EVALUATION_FAILED -> WAITING_RETRY
FOLLOW_UP_DECIDING -> FOLLOW_UP_FAILED -> WAITING_RETRY
ANSWER_SAVED -> COMPENSATING -> EVALUATED
```

幂等要求：

- 同一 `stepIdempotencyKey` 的重复请求必须返回已保存结果或当前处理中状态，不得重复创建评分、追问或报告。
- 评分失败时保留答案，允许用户或补偿任务重试本轮评分。
- 已完成轮次不可被后续重试覆盖，除非用户明确创建新的答案修订。
- Trace 需要记录每次 attempt、失败分类、最终接管结果和用户可见状态。
- 手动重试评分只能复用同一轮次上下文，不得新建轮次。
- 补偿 worker 只处理未完成轮次，不得覆盖用户已经确认的评分结果。

Phase 2 先实现单实例幂等、手动重试评分和失败补偿。Phase 3 再升级为多实例分布式锁、Single-flight 和补偿 worker 调度。

### 9.3 MVP Rubric 策略

MVP 面试评分使用固定 Rubric 模板，不支持管理员编辑权重、维度或提示词。

默认 Rubric：

- Java 后端 / AI 应用开发岗位使用 `career-java-backend-v1`。
- 非默认岗位通用模式使用 `career-general-v1`。

每次 `InterviewSession` 和 `InterviewReport` 必须记录使用的 Rubric 版本。后续 Phase 3 如果支持 Rubric 编辑，历史报告不自动重算，仍绑定创建时的 Rubric 版本。

MVP 后台只读展示 Rubric 维度、说明和版本号，不提供编辑、发布、回滚、权重调整和审计日志能力。

### 9.4 暂停、恢复与结束

用户目标：中断面试后还能继续，或者主动结束并生成报告。

主流程：

1. 用户点击暂停。
2. 系统将会话状态改为 `PAUSED`。
3. 用户点击恢复。
4. 系统恢复到 `IN_PROGRESS`，展示当前题目。
5. 用户点击结束。
6. 系统将会话状态改为 `COMPLETED`，允许生成报告。

验收标准：

- Given 一个暂停的会话，When 用户恢复，Then 已完成轮次和当前题目不丢失。
- Given 一个未完成计划的会话，When 用户主动结束，Then 系统允许生成阶段性报告，但标记覆盖维度不足。

### 9.4.1 长会话状态治理与恢复

AI-Meeting 的 Mongo Snapshot + Redis 懒加载能力在 Ragent 中改造为 PostgreSQL JSONB 快照 + 可选 Redis 热态缓存：

- `InterviewSession` 保存主状态、当前轮次、Rubric 版本和恢复版本号。
- `InterviewTurn` 保存每轮问题、答案、评分、追问裁决和 attempt 结果。
- `InterviewSessionSnapshot` 或 `session_snapshot_json` 保存恢复视图，包含当前游标、最近轮次、总分聚合、待补偿动作和 lastAppliedStepKey。
- Redis 只作为热态缓存或锁，不作为长期真相源。
- 恢复时以 PostgreSQL 持久化数据为准，缓存缺失不得导致会话不可恢复。
- 并发恢复或重复恢复必须通过版本号/CAS 思路保护，避免旧快照覆盖新状态。
- 补偿 worker 必须基于持久化轮次和快照推进，不得跳过已完成的人工评分。

验收标准：

- Given Redis 热态缓存丢失，When 用户恢复面试，Then 系统能从持久化轮次和快照恢复当前问题与历史答案。
- Given 两个请求同时恢复同一会话，When 后端处理，Then 最终只有一个恢复结果生效，不产生重复追问或重复评分。

### 9.5 面试复盘报告

用户目标：知道本次面试表现、每道题问题、能力维度、风险和下一步行动。

页面入口：`/career/reports/:reportId`

输出：

- `InterviewReport`
- 总分
- 雷达维度
- 逐题复盘
- 风险点
- 下一步训练建议
- 可反哺简历的建议

主流程：

1. 用户结束面试。
2. 系统汇总所有已评分轮次。
3. 系统调用报告 Prompt。
4. 系统生成报告。
5. 前端展示总分、雷达图、逐题复盘和下一步建议。
6. 用户可将报告建议发送到下一轮简历优化任务。

错误状态：

- 没有任何已评分轮次时，不生成报告。
- 报告生成失败时允许重试。
- AI 总分缺失时，用已评分轮次均值作为兜底，并标记兜底来源。

验收标准：

- Given 一个完成的面试，When 用户生成报告，Then 报告包含总分、雷达维度、逐题复盘、风险和简历反馈建议。
- Given 报告生成失败，When 用户重试，Then 已有问答不丢失。

## 10. AI 失败与重试策略

MVP 采用“任务层自动重试 1 次 + 用户手动重试”的策略。

### 10.1 自动重试范围

以下异步任务允许自动重试 1 次：

- 简历解析。
- JD 解析。
- 简历-JD 对齐分析。
- 简历优化建议生成。
- 面试报告生成。

触发自动重试的错误：

- AI 调用超时。
- 临时模型错误。
- AI 输出 JSON 格式错误。
- AI 输出缺少必填字段。
- 可识别的短时网络错误。

不自动重试的错误：

- 用户无权限。
- 输入为空。
- 文件不可读。
- 文件类型不支持。
- JD 文本明显不足。
- 用户主动取消或删除相关对象。

### 10.2 即时面试评分失败

面试答题评分属于交互链路，不做后台长时间自动重试。

当评分失败时：

- 当前 `InterviewTurn` 保留用户答案。
- 前端提示“本题评分失败，可重试评分”。
- 用户可以手动重试本题评分。
- 系统不得因为评分失败丢失答案或自动跳到下一题。

### 10.3 幂等与 Trace 要求

重试必须复用同一业务输入：

- 同一次简历解析重试不得创建重复 `ResumeDocument`。
- 同一次优化任务重试不得创建重复建议组。
- 同一次报告生成重试不得创建重复报告。
- 只有最终成功结果可以写入对用户可见的业务产物。

Trace 必须记录：

- 每次 attempt 的序号。
- Prompt 版本。
- 模型名称。
- 耗时。
- 错误类型。
- 是否触发自动重试。
- 最终状态。

自动重试仍失败后，任务进入 `FAILED`，用户可以手动重试。手动重试应创建新的 attempt 记录，但仍关联同一业务对象或原任务来源。

### 10.4 Career AI Single-flight

AI-Meeting 的分布式 Single-flight 能力在 Career 中定位为“AI 调用治理层”，目标是减少重复扣费、重复输出和跨节点竞态。

适用链路：

- 简历解析。
- JD 解析与对齐。
- 简历优化执行者 Agent。
- 简历优化裁判 Agent。
- 面试评分。
- 追问裁决。
- 面试报告生成。

MVP 先实现业务幂等键和任务 attempt；Phase 3 增加分布式 Single-flight：

- `singleFlightKey` 由业务输入摘要、用户、场景和版本组成。
- 同 key 已有进行中任务时，新请求进入等待、轮询或返回处理中状态。
- 成功结果可回放给同 key 请求，但必须通过用户归属和输入摘要校验。
- 失败结果按模型超时、格式错误、业务校验失败、权限失败分类。
- 超时后允许新 owner 接管，接管需要 fencing token 防止旧 owner 回写。
- owner heartbeat 用于判断任务是否仍活跃。
- 本地降级只允许返回明确失败或使用 Ragent 已配置候选模型，不允许生成无 Trace 的结果。
- 补偿 worker 和手动重试评分也必须走单飞治理，避免重复补偿、重复评分和重复写回。

验收标准：

- Given 同一用户连续点击同一优化任务，When 多个请求同时到达，Then 最终只产生一组优化建议，其他请求看到同一任务结果或处理中状态。
- Given owner 超时，When 新 owner 接管，Then 旧 owner 的迟到结果不能覆盖新结果。
- Given Single-flight 回放结果，When 管理员查看 Trace，Then 能看到原始 owner、等待请求数、回放次数和失败分类。

## 11. RAG 与知识库增强

Career Agent Platform 复用 Ragent 知识库能力，不新建第二套知识库系统。

MVP 统一使用 Ragent 现有向量存储和知识库抽象，不把 JobNavigator 的 Qdrant 作为运行依赖。JobNavigator 的 HyDE/RAG/Qdrant 经验可以作为检索策略参考，但不能引入第二套部署、第二套配置或第二套数据写入链路。

后续如果需要多向量库适配，必须通过 Ragent 统一检索抽象接入，不能让 Career 业务模块直接依赖 Qdrant 客户端。

MVP 支持：

- 岗位资料、面试题库、公司业务材料、学习资料入库。
- JD 对齐、简历优化和面试题生成时可检索知识库。
- 报告中的建议尽量引用简历片段、JD 条款或知识库 Chunk。

Phase 3 支持 JobNavigator 风格的 HyDE/Rerank 增强：

- 对 JD 生成假设性优秀候选人画像或假设性高质量简历片段，用作检索 query 扩展。
- 对用户简历生成缺口 query，检索相关项目表达、技术关键词和面试题样例。
- 检索结果先走 Ragent 统一向量检索，再通过 Ragent `RerankService` 或等价后处理排序。
- HyDE 生成内容只能作为检索 query 或候选证据，不能直接写入用户简历。
- 报告展示证据时必须区分“用户简历原文”“JD 原文”“知识库资料”“HyDE 生成 query”。

意图识别需要区分：

- 知识问答。
- 简历解析。
- JD 对齐。
- 简历优化。
- 模拟面试。
- 报告复盘。
- 工具调用。

验收标准：

- Given 一个建议引用了知识库资料，When 用户查看详情，Then 能看到资料来源或 Trace 引用。
- Given 知识库不可用，When 核心简历/JD 链路运行，Then 系统给出降级提示，不伪造证据。
- Given JobNavigator 原能力依赖 Qdrant，When 迁移到 MVP，Then 只迁移检索策略和业务思路，不新增 Qdrant 运行依赖。

## 12. AI 输出契约

所有核心 AI 链路必须输出 JSON 或等价结构化对象。自然语言总结可以作为字段值存在，但不能作为唯一结果。

### 11.1 简历解析输出

```json
{
  "basicInfo": {
    "name": "string",
    "targetRole": "string",
    "location": "string",
    "contactSummary": "string"
  },
  "education": [
    {
      "school": "string",
      "degree": "string",
      "major": "string",
      "startDate": "string",
      "endDate": "string",
      "highlights": ["string"]
    }
  ],
  "workExperiences": [
    {
      "company": "string",
      "role": "string",
      "startDate": "string",
      "endDate": "string",
      "responsibilities": ["string"],
      "achievements": ["string"]
    }
  ],
  "projects": [
    {
      "name": "string",
      "role": "string",
      "techStack": ["string"],
      "background": "string",
      "actions": ["string"],
      "results": ["string"],
      "metrics": ["string"]
    }
  ],
  "skills": [
    {
      "category": "string",
      "name": "string",
      "level": "string",
      "evidence": "string"
    }
  ],
  "certifications": [
    {
      "name": "string",
      "issuer": "string",
      "date": "string"
    }
  ],
  "highlights": [
    {
      "text": "string",
      "evidenceSource": "string"
    }
  ],
  "missingFields": [
    {
      "field": "string",
      "reason": "string",
      "severity": "LOW|MEDIUM|HIGH"
    }
  ],
  "riskFlags": [
    {
      "field": "string",
      "riskType": "string",
      "explanation": "string"
    }
  ]
}
```

### 11.2 JD 解析输出

必备字段：

- `jobTitle`
- `company`
- `seniority`
- `businessDomain`
- `responsibilities`
- `hardRequirements`
- `niceToHaveRequirements`
- `techStack`
- `softSkills`
- `experienceYears`
- `keywords`

### 11.3 对齐分析输出

必备字段：

- `overallScore`
- `dimensionScores`: `dimension`, `score`, `reason`
- `requirementMatches`: `requirement`, `resumeEvidence`, `matchLevel`
- `gaps`: `requirement`, `gapType`, `improvementAction`
- `risks`: `riskType`, `explanation`, `severity`
- `recommendedActions`

### 11.4 优化建议输出

必备字段：

- `suggestions`: `category`, `originalText`, `suggestedText`, `reason`, `riskLevel`, `evidenceRef`
- `userAction`: `ACCEPTED | EDITED | REJECTED`
- `versionImpact`: `section`, `changeSummary`

### 11.5 面试计划输出

必备字段：

- `rounds`: `turnNo`, `questionType`, `dimension`, `questionGoal`, `evidenceRef`
- `coverage`: `technicalDepth`, `projectOwnership`, `communication`, `jdFit`, `learningPotential`

### 11.6 答案评分输出

必备字段：

- `score`
- `dimensionScores`
- `strengths`
- `weaknesses`
- `followUpQuestion`
- `referenceAnswer`
- `evidenceRef`

### 11.7 面试报告输出

必备字段：

- `overallScore`
- `radar`: `dimension`, `score`, `explanation`
- `turnReviews`: `turnNo`, `question`, `answerSummary`, `score`, `feedback`
- `risks`
- `nextTrainingActions`
- `resumeFeedbackSuggestions`

## 13. 数据对象与状态机

### 12.1 核心对象

| 对象 | 产品含义 |
| --- | --- |
| `CandidateProfile` | 用户职业画像，来自简历解析和用户修正 |
| `ResumeDocument` | 原始简历文件及解析任务状态 |
| `ResumeVersion` | 可用于投递、JD 对齐和面试的简历版本 |
| `JobDescription` | 用户创建的目标岗位 JD |
| `JobAlignmentReport` | 简历版本和 JD 的匹配分析结果 |
| `ResumeOptimizationTask` | 一次简历优化生成任务 |
| `ResumeOptimizationSuggestion` | 一条可采纳、编辑或拒绝的优化建议 |
| `ResumeOptimizationReview` | 裁判 Agent 对优化结果的评分、验真和门禁结论 |
| `CareerTaskAttempt` | 一次 AI 调用或补偿执行 attempt，记录模型、Prompt、耗时、错误和 Trace |
| `CareerProgressEvent` | 展示给用户或管理员的任务进度事件 |
| `ResumeExportRecord` | 一次简历导出记录 |
| `InterviewSession` | 一次模拟面试会话 |
| `InterviewTurn` | 一道问题、答案、评分和追问 |
| `InterviewSessionSnapshot` | 面试长会话恢复视图，保存当前游标、最近轮次、聚合分和待补偿动作 |
| `CareerSingleFlightRecord` | Phase 3 AI 调用去重、owner、heartbeat、fencing token 和结果回放记录 |
| `InterviewReport` | 一次面试的复盘报告 |

### 12.2 关系

- 一个用户可以有多个 `CandidateProfile`。
- 一个 `CandidateProfile` 可以关联多个 `ResumeVersion`。
- 一个 `JobDescription` 可以和多个 `ResumeVersion` 生成匹配报告。
- 一个 `ResumeOptimizationTask` 可以包含多轮执行者输出和裁判评审。
- 一个 `ResumeOptimizationReview` 必须绑定一个优化任务和一个优化轮次。
- 一个 `InterviewSession` 必须绑定一个 `ResumeVersion`，可以选择绑定一个 `JobDescription`。
- 一个 `InterviewSession` 可以生成多个 `InterviewSessionSnapshot` 或更新同一份恢复视图。
- 一个 `InterviewReport` 必须绑定一个 `InterviewSession`。
- 一个 `InterviewReport` 可以生成下一轮 `ResumeOptimizationTask` 的输入。
- 一个 `CareerTaskAttempt` 必须绑定一个业务任务、AI 场景和 Trace。

### 12.3 状态机

简历解析：

```text
PENDING -> RUNNING -> SUCCESS
                   -> FAILED
```

优化任务：

```text
PENDING -> RUNNING -> SUCCESS
                   -> FAILED
                   -> NEEDS_REVIEW
```

优化质量门禁：

```text
DRAFT -> REVIEWING -> PASSED
                  -> NEEDS_REVISION
                  -> BLOCKED_BY_RISK
```

优化建议：

```text
PENDING -> ACCEPTED
        -> EDITED
        -> REJECTED
```

面试会话：

```text
CREATED -> IN_PROGRESS -> PAUSED -> IN_PROGRESS -> COMPLETED
        -> RECOVERING -> IN_PROGRESS
```

面试轮次：

```text
ASKED -> ANSWERED -> EVALUATED
      -> WAITING_RETRY
      -> COMPENSATING -> EVALUATED
```

报告生成：

```text
PENDING -> RUNNING -> SUCCESS
                   -> FAILED
```

## 14. API 需求摘要

API 命名遵循 Ragent 现有 REST 风格，返回体沿用统一响应结构。本文只定义产品级 API 组，具体 DTO 和方法签名由实施计划细化。

### 13.1 Resume APIs

- 上传简历。
- 查询解析状态。
- 查询简历详情。
- 更新解析字段。
- 查询版本列表。
- 创建版本。
- 删除简历或版本。
- 导出 Markdown/HTML。

### 13.2 JD APIs

- 创建 JD。
- 解析 JD。
- 查询 JD 详情。
- 查询 JD 列表。
- 将 JD 与简历版本进行对齐分析。

### 13.3 Optimization APIs

- 创建优化任务。
- 查询优化任务。
- 查询建议列表。
- 采纳、编辑、拒绝建议。
- 基于建议创建优化后的简历版本。

### 13.4 Interview APIs

- 创建面试会话。
- 查询面试计划。
- 查询当前问题。
- 提交答案。
- 暂停会话。
- 恢复会话。
- 结束会话。
- 生成报告。
- 查询报告。

### 13.5 Admin APIs

- 查询 Career 仪表盘。
- 查询 Career 任务列表。
- 查询任务详情。
- 查询 Rubric。
- 跳转 Trace。

MVP 管理 API 以任务定位为目标，只要求返回基础数量摘要、任务列表、任务状态、失败原因和 Trace 链接。趋势图、维度聚合图、耗时分布图、模型质量图等统计图表接口放到 Phase 3。

## 15. 非功能需求

- 可用性：核心文字链路应在本地依赖可用时稳定跑通。
- 可观测性：所有复杂 AI 链路必须记录 Trace 节点。
- 可扩展性：简历优化、JD 对齐、面试评分、追问策略应是可替换模块。
- 安全性：用户只能访问自己的简历、JD、面试记录和报告。
- 数据治理：用户敏感信息不能写入公开样例、公开日志正文或演示材料；用户删除数据后，关联业务内容对用户不可见，导出文件失效，Trace 只保留脱敏摘要。
- 性能：普通简历解析和 JD 对齐支持异步任务；面试答题反馈优先保证交互延迟。
- 降级：模型失败时应返回明确错误或使用候选模型降级，不应生成半截报告。
- 重试幂等：自动重试和手动重试必须复用同一业务输入，不得重复创建简历版本、优化建议组或面试报告。
- 兼容性：不得引入第二套登录、第二套模型 SDK、第二套 Trace 或第二套前端 Shell。

## 16. 竞品分析

### 15.1 通用 RAG / Agent 平台矩阵

| 产品 | 知识库管理 | Workflow / Agent | Trace 可观测 | 垂直业务闭环 | 结构化 AI 输出 | Career 场景深度 |
| --- | --- | --- | --- | --- | --- | --- |
| Dify | 强 | 强 | 中 | 弱 | 中 | 弱 |
| FastGPT | 强 | 中 | 中 | 弱 | 中 | 弱 |
| MaxKB | 强 | 中 | 中 | 弱 | 中 | 弱 |
| RAGFlow | 强 | 中 | 中 | 弱 | 中 | 弱 |
| Ragent Career | 中 | 中 | 强 | 强 | 强 | 强 |

结论：通用 RAG/Agent 平台的问题是场景宽但闭环浅。Ragent Career 不追求成为新的通用低代码 AI 应用平台，而是用已有工程底座支撑求职成长闭环。

### 15.2 求职 / 面试工具矩阵

| 产品 | 简历优化 | JD Tailoring | 模拟面试 | 语音反馈 | Rubric 评分 | 简历/JD/面试闭环 | 工程可观测 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Teal | 强 | 强 | 弱 | 弱 | 中 | 中 | 弱 |
| Yoodli | 弱 | 弱 | 强 | 强 | 中 | 弱 | 弱 |
| HireVue | 弱 | 弱 | 强 | 强 | 强 | 中 | 弱 |
| Ragent Career | 强 | 强 | 中 | 后续 | 强 | 强 | 强 |

结论：求职工具的问题是场景具体但工程底座不可见。Ragent Career 的差异化机会是把简历、JD、面试和报告连成可追溯闭环，并用 Trace 展示 RAG/Agent 链路质量。

### 15.3 产品取舍

- 不和 Dify/FastGPT 竞争“通用低代码 AI 应用构建器”。
- 不和 HireVue 竞争“企业招聘筛选系统”。
- 不把语音和视频作为 MVP 卖点。
- 优先证明“可信、可追溯、可迭代的求职成长闭环”。

### 15.4 参考来源

- Dify Documentation: https://docs.dify.ai/
- FastGPT Documentation: https://doc.fastgpt.io/
- MaxKB Documentation: https://maxkb.cn/docs/
- RAGFlow: https://ragflow.io/
- Teal Resume Builder: https://www.tealhq.com/resume-builder
- Yoodli: https://yoodli.ai/
- HireVue: https://www.hirevue.com/

## 17. 验收与测试矩阵

测试应验证外部行为，不绑定 Prompt 文案、私有方法或内部执行顺序。对 AI 输出的测试以结构、边界、降级和关键字段为主，不以完全一致的自然语言为断言目标。

| 编号 | Given | When | Then |
| --- | --- | --- | --- |
| A1 | 一个合法样例简历 | 用户上传简历 | 系统创建解析任务、职业画像和初始简历版本 |
| A2 | 简历解析失败 | 用户查看解析任务 | 系统展示失败原因和重试入口 |
| A3 | 已解析简历和合法 JD | 用户运行对齐 | 报告包含分数、证据、缺口、风险和 Trace |
| A4 | AI 对齐输出格式错误 | 后端解析结果 | 任务失败，前端展示可重试错误 |
| A5 | 一组优化建议 | 用户采纳部分建议 | 系统创建新简历版本并保留原版本 |
| A6 | 所有优化建议被拒绝 | 用户生成新版本 | 系统不创建空版本 |
| A7 | 简历版本和 JD | 用户创建模拟面试 | 系统生成面试计划和第一题 |
| A8 | 一道面试题 | 用户提交答案 | 系统保存答案并返回评分、反馈和可选追问 |
| A9 | 面试被暂停 | 用户恢复 | 已完成轮次和当前题目不丢失 |
| A10 | 已完成面试 | 用户生成报告 | 报告包含总分、雷达维度、逐题复盘、风险和简历反馈建议 |
| A11 | 用户 A 的 Career 数据 | 用户 B 请求访问 | 系统拒绝访问 |
| A12 | 模型调用失败 | 用户查看任务 | 系统返回明确失败原因或降级结果，不展示半截报告 |
| A13 | 管理员进入任务管理 | 查询 Career 任务 | 能看到任务状态、类型、用户、失败原因和 Trace 链接 |
| A14 | 原有 Ragent 知识库问答 | Career 功能上线后 | 原功能可用，认证、模型路由和 Trace 不被破坏 |
| A15 | 一个非 Java 后端岗位 JD | 用户继续运行对齐和面试 | 系统进入通用模式，允许完成流程，并提示不启用岗位专用模板和专用 Rubric |
| A16 | 用户删除一份简历 | 用户再次访问关联版本、报告或导出链接 | 业务内容不可见，导出链接失效，Trace 仅保留脱敏摘要和任务状态 |
| A17 | 管理员打开 Rubric 页面 | MVP 阶段查看 Rubric | 页面只读展示 `career-java-backend-v1` 和 `career-general-v1`，不提供编辑入口 |
| A18 | AI 输出格式错误 | 异步任务第一次解析失败 | 系统自动重试 1 次，Trace 记录两次 attempt；仍失败则任务进入 `FAILED` 并允许用户手动重试 |
| A19 | 面试答题评分失败 | 用户提交答案后评分链路异常 | 系统保留答案，提示可重试本题评分，不自动跳到下一题；手动重试只复用同一题输入摘要和证据 |
| A20 | 管理员打开 MVP 后台 | 查看 Career 仪表盘和任务管理 | 能看到基础数量摘要、最近失败任务、任务状态、失败原因和 Trace 链接，不要求统计图表 |
| A21 | 优化建议进入裁判评审 | 建议缺少简历证据或疑似编造 | 裁判 Agent 标记低置信或高风险，建议不能自动进入新版本 |
| A22 | 优化任务质量分低于 `0.8` | 用户查看优化结果 | 系统展示需复核或继续优化状态，不把结果标记为最终交付 |
| A23 | 同一优化任务被重复触发 | 多个请求并发进入 | Phase 3 只产生一组建议，其他请求回放结果或返回处理中状态 |
| A24 | 面试会话热态缓存丢失 | 用户恢复面试 | 系统从持久化轮次和快照恢复当前题目与历史答案 |
| A25 | 同一题答案重复提交 | 后端收到相同 `stepIdempotencyKey` | 系统返回已保存结果或处理中状态，不重复评分或创建追问 |
| A26 | HyDE 检索生成假设性内容 | 系统生成优化建议 | HyDE 内容只作为检索 query 或候选证据，不直接写入用户简历 |
| A27 | 用户导出 PDF 或 Word | Phase 3 渲染器启用后 | 系统先完成字段校验和模板渲染，生成文件记录模板版本和 Trace |
| A28 | 面试补偿 worker 扫描异常轮次 | 存在未完成评分或追问裁决 | worker 只补偿未完成轮次，不覆盖已完成或人工确认结果 |
| A29 | Career Skill Pack 被更新 | Agent 读取项目知识 | 能找到 Rubric、Prompt、状态机、排障和验收规则，不需要翻散落文档 |
| A30 | ASR 产生重复或乱序分段 | Phase 4 转写组装器处理实时结果 | 展示文本稳定，不删除已确认答案，断线重连不影响已保存轮次 |

重点测试模块：

- Career Profile：简历解析结果保存、用户修正、权限隔离。
- Job Alignment：JD 解析、匹配报告结构、缺口和证据字段。
- Resume Optimization：建议生成、采纳/拒绝、版本生成。
- Interview Session：创建、答题、暂停、恢复、结束。
- Interview Evaluation：评分结构、追问条件、最终报告。
- Interview Runtime：轮次状态机、幂等键、失败重试、会话恢复。
- Resume Review：执行者/裁判输出、质量门禁、真实性风险。
- Single-flight：同输入去重、结果回放、owner 超时接管和 fencing token。
- Career Trace：关键 AI 链路是否产生 Trace 节点。
- Export：Markdown、HTML 导出结果是否可下载且字段完整。

## 18. 交付计划

### Phase 1：简历与 JD 对齐 MVP

目标：完成“上传简历 -> JD 匹配 -> 优化建议 -> 新版本”的闭环。

范围：

- 简历上传与解析。
- 简历画像修正。
- JD 创建与解析。
- 匹配报告。
- 优化建议。
- 简历版本。
- Markdown/HTML 导出。
- 基础前端页面。

### Phase 2：文字版模拟面试

目标：完成“选择简历版本和 JD -> 模拟面试 -> 报告复盘”的闭环。

范围：

- 面试计划。
- 问题生成。
- 答题评分。
- 简单追问。
- 暂停和恢复。
- 最终报告。
- 雷达图。
- 报告反哺简历优化。

### Phase 3：工程增强与后台管理

目标：让项目更适合演示、运维和深入讲解。

范围：

- Career Trace 视图。
- Rubric 编辑与版本管理。
- 样例数据。
- 任务统计图表。
- Prompt 版本管理。
- 异常重试和降级策略。
- JobNavigator 风格裁判-执行者多轮优化、`Score > 0.8` 质量门禁和进度可视化。
- JobNavigator 风格 HyDE/Rerank 增强检索，但只通过 Ragent 统一检索抽象接入。
- JobNavigator 风格 PDF/Word 渲染流水线，补齐字段校验、模板版本和导出失效治理。
- AI-Meeting 风格答题轮次补偿、长会话恢复、Single-flight、owner heartbeat、fencing token 和结果回放。
- Career Skill Pack 业务知识体系，把 Rubric、Prompt、状态机、排障和验收规则沉淀为 Agent 可消费文档。

### Phase 4：语音和多模态增强

目标：吸收 AI-Meeting 的实时交互能力。

范围：

- ASR 语音答题。
- TTS 面试官朗读。
- WebSocket 实时状态。
- 可选神态分析。
- 分段增量去重、有序转写重建和 `committedText/liveText/displayText` 三层展示。
- 音频接收与下游推流解耦，ASR 结果作为 `InterviewTurn` 的可选答案来源。
- `TranscriptionSessionContext` 解耦音频接收、ASR 上游调用、分段组装和 UI 推送。
- 转写组装器基于 `seg_id/pgs/rg/bg/ed` 做有序重建、重复文本过滤和前缀修正。

启动条件：

- Phase 1 和 Phase 2 文字闭环通过 smoke verification。
- 报告生成不依赖语音转写。
- ASR 结果只是可选答案来源，不是唯一答案来源。

### Implementation Plan Link

实施计划见 `docs/career-agent-platform/implementation-plan.md`。

## 19. 风险与争议点

| 风险 | 影响 | 处理方式 |
| --- | --- | --- |
| 一次性合并过大 | 工期不可控，容易变成代码拼贴 | 严格按 Phase 1/2/3/4 分阶段交付 |
| 多套 AI SDK 冲突 | 模型调用、配置和 Trace 分裂 | 统一收敛到 Ragent 模型服务与路由 |
| 数据库模型不一致 | MySQL/Mongo/Qdrant/PostgreSQL 混用复杂 | MVP 以 Ragent 当前存储体系和知识库抽象为主，不复制旧项目存储架构，不新增 Qdrant 运行依赖 |
| 面试评分不稳定 | 用户不信任评分 | 使用 Rubric、结构化输出、Trace 和人工样例集约束 |
| AI 建议编造经历 | 简历可信度受损 | 每条建议必须有风险字段，用户确认后才进入新版本 |
| 重试产生重复业务产物 | 用户看到重复版本、重复建议或重复报告 | 重试复用同一业务输入和任务来源，只有最终成功结果对用户可见 |
| 竞品范围过宽 | 产品定位发散 | 只做求职成长闭环，不做通用招聘 ATS |
| 敏感数据泄露 | 用户隐私风险 | 日志和样例禁止写入完整简历正文；删除后导出文件失效，Trace 只保留脱敏摘要 |

## 20. Open Questions for Review

当前无阻塞性开放问题。产品名称可在后续文案阶段调整，不影响 MVP 范围。

## 21. Further Notes

本融合不以“搬代码”为目标，而以“建立统一产品闭环”为目标。

理想效果是：

- 用户看到一个完整求职成长平台。
- 开发者看到一套统一 RAG/Agent 工程体系。
- 评审者看到一个能讲清楚业务、架构、可观测和迭代路线的厚项目。

后续任何实施计划、任务拆分、验收用例和答辩材料都应以本文档为准。
