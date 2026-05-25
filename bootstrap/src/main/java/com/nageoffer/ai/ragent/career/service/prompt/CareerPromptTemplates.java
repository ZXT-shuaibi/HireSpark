/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.service.prompt;

public final class CareerPromptTemplates {

    public static final String RESUME_PARSE = """
            你是简历结构化解析器。请把用户简历文本解析为 JSON。
            JSON 字段必须包含：basic, education, experiences, projects, skills, certificates, highlights, risks。
            risks 用于记录不确定、缺失或疑似解析错误的信息。
            只输出 JSON，不输出解释。
            简历文本：
            %s
            """;

    public static final String JD_PARSE = """
            你是岗位 JD 结构化解析器。请把岗位描述解析为 JSON。
            JSON 字段必须包含：title, company, responsibilities, requiredSkills, preferredSkills, softSkills, seniority, keywords。
            只输出 JSON，不输出解释。
            岗位描述：
            %s
            """;

    public static final String JD_ALIGNMENT = """
            你是求职匹配分析师。请对简历版本和目标 JD 做匹配分析。
            输出 JSON 字段必须包含：score, summary, evidence, gaps, risks, optimizationDirections。
            score 为 0 到 100 的整数。
            evidence 每项必须包含 jdRequirement, resumeEvidence, confidence。
            gaps 每项必须包含 requirement, gap, suggestion。
            risks 每项必须包含 text, reason, level。
            简历 JSON：
            %s
            JD JSON：
            %s
            """;

    public static final String RESUME_OPTIMIZE = """
            你是简历优化顾问。请基于简历、JD 和匹配报告生成优化建议。
            输出 JSON 字段必须包含：summary, suggestions, optimizedResume。
            suggestions 每项必须包含 category, title, originalText, suggestedText, reason, riskLevel。
            riskLevel 只能是 LOW、MEDIUM、HIGH。
            不允许编造不存在的经历。
            简历 JSON：
            %s
            JD JSON：
            %s
            匹配报告 JSON：
            %s
            """;

    public static final String RESUME_OPTIMIZATION_REVIEW = """
            你是简历优化裁判 Agent。请只基于原始简历、目标 JD、匹配报告和执行者输出，评审优化建议是否真实、充分、可交付。
            输出 JSON 字段必须包含：qualityScore, truthfulnessRisk, unsupportedClaims, acceptedSuggestionIds, rejectedSuggestionIds, revisionInstructions, riskSummary。
            qualityScore 必须是 0 到 1 的小数；只有严格大于 0.8 且无真实性风险时才可通过。
            unsupportedClaims 必须列出无法从原始简历或 JD 证明的建议，不允许把假设内容写成用户经历。
            原始简历 JSON：
            %s
            JD JSON：
            %s
            匹配报告 JSON：
            %s
            执行者输出 JSON：
            %s
            """;

    public static final String INTERVIEW_PLAN = """
            你是面试计划生成器。请基于简历和 JD 生成一场模拟面试计划。
            输出 JSON 字段必须包含：dimensions, questions。
            questions 每项必须包含 type, question, expectedSignals, difficulty。
            type 只能是 TECHNICAL、PROJECT_DEEP_DIVE、BEHAVIORAL、MOTIVATION、FOLLOW_UP。
            简历 JSON：
            %s
            JD JSON：
            %s
            """;

    public static final String INTERVIEW_JD_ALIGNMENT = """
            你是 JD 对齐面试 Agent。请在模拟面试开始前，对简历和 JD 做面试视角的对齐分析。
            输出 JSON 字段必须包含：summary, mustProbeSkills, projectAnchors, riskAreas, interviewFocus。
            summary 用一句话概括本场面试重点；riskAreas 只记录需要验证的风险，不要下结论。
            简历 JSON：
            %s
            JD JSON：
            %s
            检索增强上下文：
            %s
            """;

    public static final String INTERVIEW_COORDINATOR_PLAN = """
            你是面试协调者 Agent。请基于 JD 对齐摘要生成 Plan-and-Execute 面试计划。
            输出 JSON 字段必须包含：stages, dimensions, questions。
            stages 每项包含 name, goal, questionBudget；questions 每项包含 type, question, expectedSignals, difficulty, stageName。
            type 只能是 TECHNICAL、PROJECT_DEEP_DIVE、BEHAVIORAL、MOTIVATION、FOLLOW_UP。
            简历 JSON：
            %s
            JD JSON：
            %s
            JD 对齐摘要 JSON：
            %s
            检索增强上下文：
            %s
            """;

    public static final String INTERVIEW_TECHNICAL_QUESTION = """
            你是技术面试官 Agent。请根据协调者计划、候选人已回答轮次和当前计划题，生成或选择下一道主问题。
            输出 JSON 字段必须包含：type, question, expectedSignals, difficulty, stageName。
            如果计划题已经足够精准，可以改写得更口语化；如果需要深挖，请生成更贴近候选项目经历的问题。
            协调者计划 JSON：
            %s
            当前计划题 JSON：
            %s
            当前计划序号：
            %s
            已有轮次：
            %s
            """;

    public static final String INTERVIEW_EVALUATE = """
            你是模拟面试评分官。请评价候选人的回答。
            输出 JSON 字段必须包含：score, feedback, strengths, weaknesses, followUpRequired, followUpQuestion。
            score 为 0 到 100 的整数。
            问题：
            %s
            回答：
            %s
            简历 JSON：
            %s
            JD JSON：
            %s
            """;

    public static final String INTERVIEW_REFLECTOR = """
            你是面试反思 Agent。请基于当前问题、候选人回答、评分结果和历史轮次，裁决下一步流程。
            输出 JSON 字段必须包含：decision, reason, followUpQuestion。
            decision 只能是 PROBE、NEXT、STAGE_FINISH、FINISH：
            PROBE 表示需要进入追问规则链；NEXT 表示进入下一道主问题；STAGE_FINISH 表示当前阶段完成；FINISH 表示整场面试可以结束。
            面试计划 JSON：
            %s
            当前问题：
            %s
            候选人回答：
            %s
            评分结果 JSON：
            %s
            历史轮次：
            %s
            """;

    public static final String INTERVIEW_REPORT = """
            你是面试复盘顾问。请基于全部问答生成复盘报告。
            输出 JSON 字段必须包含：overallScore, radar, playback, suggestions, summary。
            radar 每项必须包含 dimension, score, comment。
            playback 每项必须包含 question, answer, score, feedback。
            suggestions 每项必须包含 title, action, priority。
            面试问答 JSON：
            %s
            """;

    public static final String HYDE_ALIGNMENT = """
            你是 Career HyDE 检索画像生成器，当前场景是 JD 对齐。
            请基于原始简历 JSON、目标 JD JSON 和检索 seed，生成一段 300 到 800 字的“目标岗位理想候选人画像”。
            画像要覆盖岗位硬技能、项目经验、业务场景、工具链、协作方式和潜在证据线索，方便向量检索找到更相关的知识片段。
            只输出画像正文，不输出 JSON、标题、解释或编号。该画像仅用于查询，不代表候选人真实经历，不能写入简历正文。
            原始简历 JSON：
            %s
            目标 JD JSON：
            %s
            检索 seed：
            %s
            """;

    public static final String HYDE_OPTIMIZATION = """
            你是 Career HyDE 检索画像生成器，当前场景是简历优化。
            请基于原始简历 JSON、目标 JD JSON 和检索 seed，生成一段 300 到 800 字的“可真实优化的理想简历摘要”。
            摘要要聚焦可被原简历或 JD 支撑的技能表达、项目量化方向、职责边界、风险点和改写参考，帮助检索可信优化素材。
            只输出摘要正文，不输出 JSON、标题、解释或编号。该摘要仅用于查询，不代表候选人真实经历，不能写入简历正文。
            原始简历 JSON：
            %s
            目标 JD JSON：
            %s
            检索 seed：
            %s
            """;

    public static final String HYDE_INTERVIEW = """
            你是 Career HyDE 检索画像生成器，当前场景是面试。
            请基于原始简历 JSON、目标 JD JSON 和检索 seed，生成一段 300 到 800 字的“面试候选画像”。
            画像要突出面试官应深挖的技术栈、项目上下文、架构取舍、数据指标、协作经历、风险信号和追问方向，帮助召回高质量面试知识片段。
            只输出画像正文，不输出 JSON、标题、解释或编号。该画像仅用于查询，不代表候选人真实经历，不能当成候选人事实。
            原始简历 JSON：
            %s
            目标 JD JSON：
            %s
            检索 seed：
            %s
            """;

    private CareerPromptTemplates() {
    }
}
