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

package com.nageoffer.ai.ragent.career.service.render;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class ResumeTemplateFieldMapper {

    private static final String DEFAULT_NAME = "未命名候选人";
    private static final String DEFAULT_HEADLINE = "目标岗位待补充";
    private static final String DEFAULT_SUMMARY = "暂无个人总结";
    private static final String DEFAULT_CONTACT = "联系方式待补充";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将简历版本中的结构化 JSON 和少量兜底字段映射为稳定模板字段。
     */
    public Map<String, String> map(ResumeVersionDO version) {
        JsonNode root = parseContentJson(version == null ? null : version.getContentJson());
        JsonNode basic = section(root, "basic");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", firstNotBlank(text(basic, "name"), text(root, "name"),
                version == null ? null : version.getTitle(), DEFAULT_NAME));
        fields.put("headline", firstNotBlank(text(basic, "headline"), text(root, "headline"), DEFAULT_HEADLINE));
        fields.put("contact", renderContact(basic));
        fields.put("summary", firstNotBlank(text(root, "summary"), text(basic, "summary"), DEFAULT_SUMMARY));
        fields.put("skills", renderSkills(section(root, "skills", "skillList")));
        fields.put("projects", renderList(section(root, "projects", "projectExperience", "projectExperiences"),
                "暂未填写项目经历", this::renderProjectItem));
        fields.put("experiences", renderList(section(root, "experiences", "workExperiences", "workExperience"),
                "暂未填写工作经历", this::renderExperienceItem));
        fields.put("education", renderList(section(root, "education", "educations"),
                "暂未填写教育经历", this::renderEducationItem));
        fields.put("highlights", renderSimpleList(section(root, "highlights"), "暂未填写亮点"));
        fields.put("certificates", renderSimpleList(section(root, "certificates", "certifications"), "暂未填写证书"));
        fields.put("rawMarkdown", renderRawMarkdown(version));
        fields.put("rawMarkdownReference", fields.get("rawMarkdown"));
        return fields;
    }

    /**
     * 读取结构化简历 JSON；空内容由模板兜底，非法 JSON 直接中断导出。
     */
    private JsonNode parseContentJson(String contentJson) {
        if (StrUtil.isBlank(contentJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            return root == null ? objectMapper.createObjectNode() : root;
        } catch (Exception ex) {
            throw new ClientException("简历内容 JSON 无法解析");
        }
    }

    /**
     * 从多个候选字段中返回第一个存在的节点。
     */
    private JsonNode section(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return MissingNode.getInstance();
        }
        for (String fieldName : fieldNames) {
            JsonNode child = node.path(fieldName);
            if (!child.isMissingNode() && !child.isNull()) {
                return child;
            }
        }
        return MissingNode.getInstance();
    }

    /**
     * 读取对象节点上的文本字段，忽略空字符串和复杂结构。
     */
    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText();
        return StrUtil.isBlank(text) ? null : text.trim();
    }

    /**
     * 渲染联系方式行，避免字段缺失时出现空分隔符。
     */
    private String renderContact(JsonNode basic) {
        List<String> contacts = new ArrayList<>();
        addLabeledValue(contacts, "手机", text(basic, "phone"));
        addLabeledValue(contacts, "邮箱", text(basic, "email"));
        addLabeledValue(contacts, "城市", firstNotBlank(text(basic, "city"), text(basic, "location")));
        addLabeledValue(contacts, "主页", firstNotBlank(text(basic, "website"), text(basic, "homepage")));
        return contacts.isEmpty() ? DEFAULT_CONTACT : String.join(" | ", contacts);
    }

    /**
     * 渲染技能字段，兼容字符串数组、对象数组和按类别分组的对象。
     */
    private String renderSkills(JsonNode skills) {
        if (isEmptyNode(skills)) {
            return "- 暂未填写技能";
        }
        List<String> lines = new ArrayList<>();
        if (skills.isArray()) {
            for (JsonNode skill : skills) {
                String item = renderSkillItem(skill);
                if (StrUtil.isNotBlank(item)) {
                    lines.add("- " + item);
                }
            }
        } else if (skills.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = skills.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String value = compactValue(entry.getValue());
                if (StrUtil.isNotBlank(value)) {
                    lines.add("- " + entry.getKey() + "：" + value);
                }
            }
        } else {
            lines.add("- " + compactValue(skills));
        }
        return lines.isEmpty() ? "- 暂未填写技能" : String.join("\n", lines);
    }

    /**
     * 渲染单个技能条目。
     */
    private String renderSkillItem(JsonNode skill) {
        if (skill == null || skill.isMissingNode() || skill.isNull()) {
            return "";
        }
        if (!skill.isObject()) {
            return compactValue(skill);
        }
        String name = firstNotBlank(text(skill, "name"), text(skill, "skill"), text(skill, "title"));
        String level = firstNotBlank(text(skill, "level"), text(skill, "proficiency"));
        String keywords = firstNotBlank(compactValue(section(skill, "keywords")),
                compactValue(section(skill, "items")));
        String detail = joinNonBlank("，", level, keywords);
        return StrUtil.isBlank(detail) ? firstNotBlank(name, compactValue(skill)) : firstNotBlank(name, "技能") + "：" + detail;
    }

    /**
     * 渲染通用数组结构，缺失时返回温和兜底条目。
     */
    private String renderList(JsonNode node, String fallback, Function<JsonNode, String> itemRenderer) {
        if (isEmptyNode(node)) {
            return "- " + fallback;
        }
        List<String> lines = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String line = itemRenderer.apply(item);
                if (StrUtil.isNotBlank(line)) {
                    lines.add(line);
                }
            }
        } else {
            String line = itemRenderer.apply(node);
            if (StrUtil.isNotBlank(line)) {
                lines.add(line);
            }
        }
        return lines.isEmpty() ? "- " + fallback : String.join("\n", lines);
    }

    /**
     * 渲染项目经历，兼容常见的名称、角色、技术栈和亮点字段。
     */
    private String renderProjectItem(JsonNode project) {
        if (project == null || project.isMissingNode() || project.isNull()) {
            return "";
        }
        if (!project.isObject()) {
            return "- " + firstNotBlank(compactValue(project), "项目经历待补充");
        }
        String name = firstNotBlank(text(project, "name"), text(project, "projectName"), text(project, "title"), "项目经历");
        String meta = joinNonBlank(" / ", text(project, "role"), period(project));
        StringBuilder builder = new StringBuilder("- **").append(name).append("**");
        appendMeta(builder, meta);
        appendChildLine(builder, "说明", firstNotBlank(text(project, "description"), text(project, "summary"), text(project, "overview")));
        appendChildLine(builder, "技术", firstNotBlank(compactValue(section(project, "techStack")),
                compactValue(section(project, "technologies")), text(project, "tech")));
        appendChildLine(builder, "亮点", firstNotBlank(compactValue(section(project, "highlights")),
                compactValue(section(project, "achievements")), compactValue(section(project, "responsibilities"))));
        return builder.toString();
    }

    /**
     * 渲染工作经历，兼容公司、岗位、时间和职责字段。
     */
    private String renderExperienceItem(JsonNode experience) {
        if (experience == null || experience.isMissingNode() || experience.isNull()) {
            return "";
        }
        if (!experience.isObject()) {
            return "- " + firstNotBlank(compactValue(experience), "工作经历待补充");
        }
        String company = firstNotBlank(text(experience, "company"), text(experience, "companyName"),
                text(experience, "name"), "工作经历");
        String meta = joinNonBlank(" / ", firstNotBlank(text(experience, "position"), text(experience, "title"),
                text(experience, "role")), period(experience));
        StringBuilder builder = new StringBuilder("- **").append(company).append("**");
        appendMeta(builder, meta);
        appendChildLine(builder, "说明", firstNotBlank(text(experience, "description"), text(experience, "summary")));
        appendChildLine(builder, "职责", firstNotBlank(compactValue(section(experience, "responsibilities")),
                compactValue(section(experience, "highlights")), compactValue(section(experience, "achievements"))));
        return builder.toString();
    }

    /**
     * 渲染教育经历，兼容学校、学历、专业和时间字段。
     */
    private String renderEducationItem(JsonNode education) {
        if (education == null || education.isMissingNode() || education.isNull()) {
            return "";
        }
        if (!education.isObject()) {
            return "- " + firstNotBlank(compactValue(education), "教育经历待补充");
        }
        String school = firstNotBlank(text(education, "school"), text(education, "schoolName"),
                text(education, "name"), "教育经历");
        String meta = joinNonBlank(" / ", text(education, "degree"), text(education, "major"), period(education));
        StringBuilder builder = new StringBuilder("- **").append(school).append("**");
        appendMeta(builder, meta);
        appendChildLine(builder, "说明", firstNotBlank(text(education, "description"), text(education, "summary")));
        return builder.toString();
    }

    /**
     * 渲染简单列表字段，适用于亮点和证书等辅助块。
     */
    private String renderSimpleList(JsonNode node, String fallback) {
        if (isEmptyNode(node)) {
            return "- " + fallback;
        }
        List<String> lines = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = compactValue(item);
                if (StrUtil.isNotBlank(value)) {
                    lines.add("- " + value);
                }
            }
        } else {
            String value = compactValue(node);
            if (StrUtil.isNotBlank(value)) {
                lines.add("- " + value);
            }
        }
        return lines.isEmpty() ? "- " + fallback : String.join("\n", lines);
    }

    /**
     * 将历史 Markdown 作为参考块注入模板，避免它直接成为导出主体。
     */
    private String renderRawMarkdown(ResumeVersionDO version) {
        String rawMarkdown = version == null ? null : version.getMarkdownContent();
        if (StrUtil.isBlank(rawMarkdown)) {
            return "> 暂无原始 Markdown 参考";
        }
        String normalized = rawMarkdown.replace("\r\n", "\n").replace('\r', '\n').trim();
        String[] lines = normalized.split("\n", -1);
        List<String> quoted = new ArrayList<>();
        for (String line : lines) {
            quoted.add("> " + line);
        }
        return String.join("\n", quoted);
    }

    /**
     * 提取经历或项目的时间段。
     */
    private String period(JsonNode node) {
        String period = firstNotBlank(text(node, "period"), text(node, "time"), text(node, "duration"));
        if (StrUtil.isNotBlank(period)) {
            return period;
        }
        String start = firstNotBlank(text(node, "startDate"), text(node, "startTime"), text(node, "start"));
        String end = firstNotBlank(text(node, "endDate"), text(node, "endTime"), text(node, "end"));
        if (StrUtil.isBlank(start) && StrUtil.isBlank(end)) {
            return "";
        }
        if (StrUtil.isBlank(start)) {
            return end;
        }
        return start + " - " + firstNotBlank(end, "至今");
    }

    /**
     * 将 JSON 节点压缩为适合 Markdown 行内展示的文本。
     */
    private String compactValue(JsonNode node) {
        if (isEmptyNode(node)) {
            return "";
        }
        if (node.isValueNode()) {
            String value = node.asText();
            return value == null ? "" : value.trim();
        }
        List<String> parts = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = compactValue(item);
                if (StrUtil.isNotBlank(value)) {
                    parts.add(value);
                }
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String value = compactValue(entry.getValue());
                if (StrUtil.isNotBlank(value)) {
                    parts.add(entry.getKey() + "：" + value);
                }
            }
        }
        return String.join("、", parts);
    }

    /**
     * 判断节点是否为空结构或空值。
     */
    private boolean isEmptyNode(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                || (node.isArray() && node.isEmpty())
                || (node.isObject() && node.isEmpty())
                || (node.isValueNode() && StrUtil.isBlank(node.asText()));
    }

    /**
     * 追加带标签的非空值。
     */
    private void addLabeledValue(List<String> target, String label, String value) {
        if (StrUtil.isNotBlank(value)) {
            target.add(label + "：" + value);
        }
    }

    /**
     * 追加项目或经历的元信息。
     */
    private void appendMeta(StringBuilder builder, String meta) {
        if (StrUtil.isNotBlank(meta)) {
            builder.append("（").append(meta).append("）");
        }
    }

    /**
     * 追加 Markdown 二级列表行。
     */
    private void appendChildLine(StringBuilder builder, String label, String value) {
        if (StrUtil.isNotBlank(value)) {
            builder.append("\n  - ").append(label).append("：").append(value);
        }
    }

    /**
     * 拼接非空文本。
     */
    private String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(delimiter, parts);
    }

    /**
     * 返回第一个非空文本。
     */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
