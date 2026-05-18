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

    public static final String INTERVIEW_REPORT = """
            你是面试复盘顾问。请基于全部问答生成复盘报告。
            输出 JSON 字段必须包含：overallScore, radar, playback, suggestions, summary。
            radar 每项必须包含 dimension, score, comment。
            playback 每项必须包含 question, answer, score, feedback。
            suggestions 每项必须包含 title, action, priority。
            面试问答 JSON：
            %s
            """;

    private CareerPromptTemplates() {
    }
}
