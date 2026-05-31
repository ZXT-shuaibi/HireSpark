# HireSpark Frontend Design

日期：2026-05-31

## 1. 设计目标

为 `HireSpark` 设计一套独立的桌面 Web 前端产品方案。  
它不是 `agent` 的附属工具页，而是一个品牌化的“职业成长操作系统”。

本次设计聚焦三件事：

1. 明确 `Resume Lab` 与 `Interview` 双主线业务闭环。
2. 将 RAG 从后端能力提升为前端可感知、可解释、可按需展开的产品能力。
3. 为后续独立前端目录、原型设计和实现提供统一信息架构与视觉方向。

## 2. 产品定位

`HireSpark = 职业成长操作系统`

它围绕求职用户最关键的两个环节构建：

- `Resume Lab`：优化简历、版本管理、JD 对齐、证据驱动改写
- `Interview`：真实场景驱动的模拟面试、追问、复盘、成长建议

`Interview` 不是单一体验，而是两种训练模式：

- `Elite Training Desk`
  - 更专业、更结构化、更像高端训练工作台
  - 适合高压模拟、深度拷打、严肃复盘
- `AI Practice Pod`
  - 更灵动、更沉浸、更有陪练感
  - 适合日常练习、低门槛启动、快速连续训练

## 3. 用户与场景

核心用户：

- 目标大厂或高质量岗位的求职者
- 以 Java 后端 / AI 应用开发方向为第一主场景
- 会反复查看简历版本、面试表现、知识证据、成长建议

典型使用环境：

- 白天桌面办公环境
- 长时间阅读、训练、比对和复盘
- 需要可信、克制、精密，而不是炫技式 AI 界面

## 4. 业务闭环

### 4.1 Resume Lab 主线

1. 上传或选择简历版本
2. 创建或选择目标 JD
3. 运行 JD 对齐
4. 查看匹配分、缺口、风险、证据
5. 接收优化建议
6. 采纳 / 编辑 / 拒绝建议
7. 生成新简历版本
8. 准备进入面试训练

### 4.2 Interview 主线

1. 选择训练模式
2. 选择场景、简历版本、JD
3. 开始模拟面试
4. 回答问题并接收追问
5. 查看阶段反馈与状态
6. 生成复盘报告
7. 根据报告进入下一轮训练或返回 `Resume Lab`

## 5. RAG 产品化原则

RAG 必须真实融入产品，但默认收起、按需展开。

### 5.1 Resume Lab 中的 RAG

作为：

- JD 对齐证据来源
- 优化建议依据
- 表达参考来源
- 真实性风险说明来源

展开后展示：

- 关联 JD 条目
- 简历证据命中点
- 缺口说明
- 建议改写依据

### 5.2 Interview 中的 RAG

作为：

- 真实大厂面试场景来源
- 题源依据
- 追问依据
- 参考证据
- 训练推荐理由

展开后展示：

- 场景标签
- 考察维度
- 来源摘要
- 追问触发原因
- 推荐下一步训练的逻辑

### 5.3 展示原则

- 默认只露出核心任务和答案交互
- RAG 依据放在折叠层、侧栏或详情抽屉
- 不展示大段原始自由文本
- 优先展示结构化字段、标签、证据摘要与理由

## 6. 信息架构

### 6.1 用户端一级导航

- `Home`
- `Resume Lab`
- `Interview`
- `Scenarios`
- `Reports`
- `Growth`
- `Assets`

### 6.2 用户端导航说明

#### Home

- 今日成长入口
- 双主线快捷开始
- 最近训练与最近优化
- 下一步建议

#### Resume Lab

- 简历版本列表
- JD 对齐结果
- 建议处理与改稿台
- 导出与版本差异对比

#### Interview

- 训练模式选择
- 快速开局
- 最近会话恢复
- 进入 `Elite Training Desk` / `AI Practice Pod`

#### Scenarios

- 真实大厂场景库
- 岗位 / 轮次 / 维度 / 难度筛选
- 场景详情和适配说明

#### Reports

- 历史复盘
- 雷达图
- 逐题回放
- 推荐训练路径

#### Growth

- 长期进步轨迹
- 薄弱项趋势
- 连续训练记录
- 个性化成长计划

#### Assets

- 简历
- JD
- 对齐结果
- 资料关联

### 6.3 管理端一级导航

- `Overview`
- `Resume Ops`
- `Interview Ops`
- `Scenario RAG`
- `Reports Ops`
- `Traces`
- `Rubrics`
- `Runtime`
- `Settings`

## 7. 核心页面原型范围

### 7.1 用户端必做页面

1. `Home`
2. `Resume Lab Workspace`
3. `Interview Mode Selector`
4. `Elite Training Desk`
5. `AI Practice Pod`
6. `Scenario Library`
7. `Interview Report`
8. `Growth Dashboard`
9. `Assets Hub`

### 7.2 管理端必做页面

1. `Overview`
2. `Resume Ops`
3. `Interview Ops`
4. `Scenario RAG`
5. `Reports Ops`
6. `Trace & Runtime`
7. `Rubrics`

## 8. 页面策略

### 8.1 Home

目标：

- 强化“职业成长操作系统”定位
- 同时提供 `Resume Lab` 与 `Interview` 双主入口
- 不做普通卡片墙 dashboard

结构：

- 品牌 Hero
- 双主线入口区
- 今日进展区
- 推荐动作区
- 最近训练 / 最近优化区

### 8.2 Resume Lab Workspace

目标：

- 同时具备“高端编辑器”和“职业顾问工作台”气质

结构：

- 左侧：版本 / 章节 / JD 上下文
- 中部：简历正文与修改区
- 右侧：建议、证据、顾问提示
- 顶部：版本状态、导出、切换

### 8.3 Elite Training Desk

目标：

- 专业、锐利、结构化
- 强化高压模拟与过程掌控感

结构：

- 左侧：题目列表 / 当前轮次 / 阶段进度
- 中部：当前问题、回答区、状态流
- 右侧：反馈摘要、按需展开的 RAG 依据

### 8.4 AI Practice Pod

目标：

- 更沉浸、更轻盈、更有陪练感

结构：

- 中心化单任务界面
- 更强的微动态与即时反馈
- 弱化复杂控制项
- 保留按需展开的专业信息层

### 8.5 Scenario Library

目标：

- 把“真实大厂面试场景”产品化
- 让用户先看到场景，再看到题目

结构：

- 顶部筛选
- 场景卡片列表
- 详情抽屉
- 适配岗位 / 维度 / 推荐模式

### 8.6 Interview Report

目标：

- 不是分数页，而是成长复盘页

结构：

- 总体评价
- 雷达图
- 逐题回放
- 失分与亮点
- 推荐训练
- 返回 `Resume Lab` 的反馈入口

## 9. 状态设计

必须覆盖：

- 首次进入
- 无简历 / 无 JD
- 无历史会话
- 场景为空
- 召回中 / 生成中 / 评分中
- SSE 进度状态
- 面试恢复状态
- 报告失败但问答已保留
- 低置信 RAG 依据
- 风险提示与人工确认

## 10. 视觉方向

### 10.1 总体方向

- 主基调：`浅色精英式`
- 产品寄存器：`product UI`
- 色彩策略：`Restrained` 为主，局部进入 `Committed`
- 品牌气质：高端、可信、克制、现代

### 10.2 视觉原则

- 不使用大面积纯白
- 不走泛紫泛蓝 AI 套路
- 不做满屏发光或重玻璃态
- 用细边界、轻阴影、层次背景建立高级感
- 动效只服务状态、层级和注意力引导

### 10.3 风格板候选

#### A. Institutional Premium

- 最稳、最可信
- 适合整体产品与管理端

#### B. Editorial Workspace

- 更像高端编辑器和研究平台
- 适合 `Resume Lab`

#### C. Soft Tech Luxury

- 轻科技感但保持浅色高级
- 适合品牌首页和 `Interview`

#### D. Training Command Lite

- 轻指挥舱感
- 适合 `Elite Training Desk`

#### E. Mentor Studio

- 更强顾问感和陪伴感
- 适合 `Resume Lab` 与 `Growth`

#### F. Precision Assessment

- 更强调评估、报告和治理
- 适合管理端和 `Reports`

### 10.4 推荐组合

- 整体品牌：`Institutional Premium + Soft Tech Luxury`
- `Resume Lab`：`Editorial Workspace + Mentor Studio`
- `Elite Training Desk`：`Training Command Lite`
- `AI Practice Pod`：`Soft Tech Luxury`
- 管理端：`Precision Assessment`

## 11. 动效策略

允许但要克制：

- 首页 Hero 背景微动效
- 模式切换过渡
- 训练状态反馈
- 报告图表过渡
- Trace / Runtime 轻量状态动画

禁止：

- 纯装饰性连续动画
- 影响阅读的高频动效
- 布局抖动和重度发光

## 12. 前端独立目录建议

建议将品牌前端独立为新目录：

- `hirespark-web`

建议内部结构：

- `app-shell`
- `modules/home`
- `modules/resume-lab`
- `modules/interview`
- `modules/scenarios`
- `modules/reports`
- `modules/growth`
- `modules/assets`
- `modules/admin`
- `shared/ui`
- `shared/layout`
- `shared/api`

原则：

- 按产品域划分
- 贴合现有 agent 后端能力
- 避免继续耦合旧前端页面结构

## 13. 与现有后端的对齐原则

前端原型与后续实现必须优先贴合现有能力：

- Resume / JD / Alignment
- Optimization
- Interview Session / Turn / Report
- Admin Overview / Task / Trace / Rubric
- SSE / WebSocket / ASR
- RAG / Settings / Runtime

允许包装，不允许脱离现有契约大规模虚构功能。

## 14. 下一步输出

在本设计确认后，进入下一阶段：

1. 站点地图
2. 用户端低保真原型
3. 管理端低保真原型
4. 风格板细化
5. 独立前端目录与实现计划
