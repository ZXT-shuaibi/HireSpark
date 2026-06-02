# AI-Meeting 作为 HireSpark 新前端对齐设计

日期：2026-06-02

## 1. 设计目标

本设计用于确定一个明确的前端迁移方向：

- 以 `AI-Meeting-JobSpark` 作为新的前端基线
- 以 `HireSpark` 作为唯一后端与业务内核
- 通过前端适配层把 AI-Meeting 的交互体验对齐到 HireSpark 的真实业务协议

本次设计不解决“是否要继续保留旧前端”的组织问题，而是直接回答：

1. 是否可以让 AI-Meeting 成为 HireSpark 的新前端
2. 需要如何对齐页面、路由、认证、接口和流式通信
3. 第一阶段应先交付哪些能力，哪些能力延后

## 2. 结论

可以。

但这不是“换一个皮肤”或“直接把两个仓库拼起来”，而是一项前端迁移工作。

推荐方案为：

- 保留 AI-Meeting 的前端结构、视觉语言、组件系统和交互节奏
- 不要求 HireSpark 后端兼容 `xunzhi/v1` 协议
- 在新前端内部建立一层 `HireSpark adapter`
- 让页面表现像 AI-Meeting，业务语义与接口契约完全服从 HireSpark

这意味着最终结果是：

- 用户看到的是 AI-Meeting 风格的新前端
- 系统运行的仍然是 HireSpark 的认证、简历、优化、面试、报告、后台能力

## 3. 现状判断

### 3.1 HireSpark 当前形态

HireSpark 当前是“同仓库前后端分离项目”，不是传统前后端不分离页面应用。

关键特征：

- 后端：Spring Boot，多模块 Maven 工程
- 前端：`frontend/` 独立 React + Vite 工程
- 开发模式：前端本地代理后端接口
- 后端上下文：`/api/ragent`
- 现有核心业务链路已经存在：认证、简历上传、JD 对齐、简历优化、模拟面试、面试报告、后台治理

### 3.2 AI-Meeting-JobSpark 当前形态

AI-Meeting-JobSpark 当前更像一个独立前端壳：

- 前端：React + Vite + Router + Tailwind
- 具备完整的产品化页面组织、营销首页、鉴权守卫、聊天与面试交互骨架
- 服务层与接口契约深度绑定 `xunzhi/v1/*`
- WebSocket、SSE、认证和 DTO 均按 AI-Meeting 语义设计

### 3.3 两者的根本差异

差异不只在“接口路径不同”，而在于“业务协议和产品语义不同”。

对比：

- AI-Meeting 认证：`/xunzhi/v1/users/*`
- HireSpark 认证：`/auth/*`、`/user/me`

- AI-Meeting 面试：`/xunzhi/v1/interview/*`
- HireSpark 面试：`/career/interviews/*`

- AI-Meeting 音频转写：面向用户级语音输入
- HireSpark 音频转写：面向面试 session 的转写 WebSocket

- AI-Meeting 页面主轴：营销、会话、简历、面试
- HireSpark 页面主轴：职业首页、简历资产、JD 对齐、优化、面试、报告、管理后台

因此不能用“最小改动前端 + 后端兼容一层”的方式硬接。

## 4. 对齐原则

### 4.1 单一后端原则

系统只保留一套真实业务后端：`HireSpark`。

不新增第二套长期维护的 `xunzhi/v1` 兼容协议，不让后端背负“双协议维护成本”。

### 4.2 前端主导迁移原则

新前端以 `AI-Meeting-JobSpark` 为基线，保留：

- 页面结构
- 交互节奏
- 视觉语言
- 组件体系
- 流式体验设计

同时替换：

- 接口服务层
- DTO 映射
- 路由语义
- 鉴权与会话处理
- WebSocket / SSE 对接逻辑

### 4.3 业务语义服从 HireSpark

所有业务名词、状态流和数据结构以 HireSpark 为准。

前端不再使用下列旧语义作为系统事实来源：

- `xunzhi`
- `agentId`
- AI-Meeting 原始 interview record 结构
- 与 HireSpark 不一致的用户、会话、报告 DTO

### 4.4 分阶段迁移原则

本次迁移必须拆阶段。

推荐先完成第一阶段：

- 登录
- 简历上传与版本入口
- JD 创建 / 对齐
- 简历优化
- 模拟面试
- 面试报告

下列能力不进入第一阶段：

- 管理后台全量迁移
- 通用聊天主链路重构
- 知识库与意图树后台整合
- 非关键营销增强页

## 5. 目标架构

## 5.1 目标拓扑

```text
AI-Meeting 风格前端
    -> 前端适配层（HireSpark adapter）
        -> HireSpark REST API
        -> HireSpark SSE
        -> HireSpark WebSocket
```

## 5.2 前端职责边界

新前端负责：

- 页面展示与交互
- 用户态维护
- 业务 DTO 到视图模型的转换
- 流式事件解析
- 面试输入、转写、进度展示

HireSpark 后端负责：

- 认证与权限
- 简历、JD、对齐、优化、面试、报告的真实业务状态
- SSE 进度流
- ASR / WebSocket 接入
- 管理后台数据

## 5.3 适配层职责

新增 `HireSpark adapter`，职责包括：

- 统一 API base 与鉴权头注入
- 把 HireSpark 响应模型转换成 AI-Meeting 风格页面可消费的 view model
- 隔离页面与后端契约
- 收口错误映射、分页映射、流式事件映射

适配层是本次迁移最重要的技术边界。

## 6. 页面与模块对齐

### 6.1 可以直接保留的部分

以下部分可高比例复用：

- 全局布局与导航容器
- 营销首页结构
- 登录页的视觉外壳
- 通用 UI 组件
- 边栏、卡片、按钮、弹窗、表单体系
- 音频控制与录音交互骨架
- SSE / WebSocket 的通用封装思路

### 6.2 需要改造的部分

以下部分需要适配或重写：

- `src/services/*`
- `src/lib/request.ts`
- `src/config/env.ts`
- 路由与鉴权守卫
- 简历页中的字段来源
- 面试页中的 session / turn / answer / report 数据结构
- 报告页的数据绑定

### 6.3 第一阶段要交付的页面

第一阶段页面建议收敛为：

1. `AuthPage`
2. `CareerHomePage`
3. `ResumeUploadPage`
4. `ResumeDetailPage`
5. `ResumeOptimizePage`
6. `InterviewIntroPage`
7. `InterviewPage`
8. `InterviewReportPage`
9. `InterviewReportDetailPage`

这批页面足以覆盖核心职业链路。

### 6.4 第二阶段再迁移的页面

延后页面：

1. `ChatPage`
2. 后台 Overview / Career Admin / Trace / Knowledge / Settings
3. Question Bank 类页面
4. 非核心营销扩展页

## 7. 路由对齐

### 7.1 路由语义目标

推荐把 AI-Meeting 当前面向“面试产品”的路由，改造成面向“职业成长工作流”的路由。

第一阶段建议路由：

- `/`
- `/auth`
- `/career`
- `/career/resumes/upload`
- `/career/resumes/:resumeVersionId`
- `/career/optimizations`
- `/career/interviews`
- `/career/interviews/:sessionId`
- `/career/interview-reports`
- `/career/interview-reports/:sessionId`

### 7.2 鉴权策略

未登录用户：

- 可以访问营销首页与登录页
- 不可以访问职业工作流页面

已登录用户：

- 默认进入 `/career`
- 从首页 CTA 进入职业链路

管理员路径延后到第二阶段接入。

## 8. 接口对齐

### 8.1 认证域

从：

- `/xunzhi/v1/users/login`
- `/xunzhi/v1/users/logout`
- `/xunzhi/v1/users/check-login`

切换到：

- `/auth/login`
- `/auth/logout`
- `/user/me`

需要完成：

- token 注入逻辑重写
- 未登录重定向逻辑重写
- 当前用户信息模型改写

### 8.2 简历与 JD 域

前端要对齐到：

- `/career/resumes/upload`
- `/career/resumes/versions/{versionId}`
- `/career/profiles/{profileId}/versions`
- `/career/jobs`
- `/career/jobs/{jdId}`
- `/career/alignments`
- `/career/alignments/{reportId}`

### 8.3 简历优化域

前端要对齐到：

- `POST /career/optimizations`
- `GET /career/optimizations/{taskId}`
- `GET /career/optimizations/{taskId}/progress/stream`
- `PUT /career/optimizations/suggestions/{suggestionId}`
- `POST /career/optimizations/{taskId}/versions`

### 8.4 面试域

前端要对齐到：

- `POST /career/interviews`
- `GET /career/interviews/{sessionId}`
- `GET /career/interviews/{sessionId}/next-question`
- `POST /career/interviews/{sessionId}/answers`
- `POST /career/interviews/{sessionId}/recover`
- `POST /career/interviews/{sessionId}/finish`
- `POST /career/interviews/{sessionId}/report`
- `GET /career/interviews/{sessionId}/report`

### 8.5 WebSocket 与流式能力

SSE：

- 优化任务进度流：`/career/optimizations/{taskId}/progress/stream`
- 面试进度流：`/career/interviews/{sessionId}/progress/stream`

WebSocket：

- 面试转写：`/career/interviews/{sessionId}/transcription/ws`

需要替换 AI-Meeting 现有的：

- 用户级语音转写 URL
- `xunzhi` 风格进度流解析
- 原有 interview stream DTO

## 9. 数据模型对齐策略

### 9.1 采用“双层模型”

禁止页面直接消费后端原始 DTO。

推荐结构：

- `api DTO`
- `adapter mapper`
- `view model`

这样做的价值：

- 页面不被后端字段名绑死
- 后续 admin/chat 二期接入时可复用映射层
- 替换旧前端更安全

### 9.2 重点映射对象

必须先定义稳定 view model 的对象：

- 当前用户
- 简历上传结果
- 简历版本
- JD
- 对齐报告
- 优化任务
- 优化建议
- 面试 session
- 面试题 turn
- 面试进度事件
- 面试报告

## 10. 第一阶段实施范围

### 10.1 包含

- 以前端新基线替换旧前端核心职业路径
- 打通 HireSpark 认证
- 打通简历 / JD / 对齐 / 优化 / 面试 / 报告
- 保证开发代理与环境变量可本地联调
- 保留 AI-Meeting 风格视觉与交互骨架

### 10.2 不包含

- 管理后台全量迁移
- 通用聊天链路重构
- 知识库与后台治理整合
- Docker 与生产部署最终收口
- 旧前端完全删除

## 11. 风险与应对

### 11.1 风险：接口语义不一致

表现：

- 路径不同
- DTO 不同
- 状态流不同

应对：

- 建立适配层
- 先映射 view model，再接页面

### 11.2 风险：迁移范围过大

表现：

- 想一次迁完 chat、career、admin

应对：

- 拆阶段
- 第一阶段只做职业主链路

### 11.3 风险：前端技术栈差异

表现：

- React 18 / 19
- Router 6 / 7 差异

应对：

- 新前端直接沿用 AI-Meeting 技术栈
- 不为兼容旧前端而回退技术栈

### 11.4 风险：旧语义污染新前端

表现：

- 页面仍依赖 `xunzhi` 命名
- service 层半迁移

应对：

- 新增 HireSpark 命名域
- 逐页剔除 `xunzhi` 协议与 DTO

## 12. 推荐实施顺序

1. 前端基座迁移
2. 认证对齐
3. 简历 / JD / 对齐对齐
4. 优化任务对齐
5. 面试与报告对齐
6. 验证第一阶段主链路
7. 再规划第二阶段 admin / chat

## 13. 验收标准

第一阶段完成后，应满足：

- 用户可以登录并进入职业首页
- 可以上传简历并进入后续职业链路
- 可以创建或查看 JD 对齐结果
- 可以创建优化任务、看到进度、处理建议、生成新版本
- 可以创建面试 session、获取问题、提交答案、查看进度、生成报告
- 页面视觉风格以 AI-Meeting 为主，不再是旧 HireSpark 默认页面风格
- 前端不再依赖 `xunzhi/v1` 作为核心业务协议

## 14. 下一步输出

本设计确认后，下一步只写“第一阶段实施计划”，不把 admin / chat 混入同一份计划。

第一阶段计划范围：

- 新前端基座
- 认证
- 简历 / 对齐 / 优化 / 面试 / 报告

第二阶段另写计划：

- admin
- chat
- 旧前端下线与部署收口
