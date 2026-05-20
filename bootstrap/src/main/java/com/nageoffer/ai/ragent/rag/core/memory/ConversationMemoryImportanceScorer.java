package com.nageoffer.ai.ragent.rag.core.memory;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.dao.entity.ConversationMessageDO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConversationMemoryImportanceScorer {

    private static final int MAX_SCORE = 100;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final List<String> IMPORTANT_TERMS = List.of(
            "preference", "constraint", "requirement", "fact", "evidence", "risk", "score",
            "interview", "answer", "budget", "deadline", "must", "cannot", "resume", "jd",
            "偏好", "约束", "要求", "事实", "证据", "风险", "评分", "分数", "面试", "答案",
            "预算", "时间", "数量", "必须", "不能", "简历", "候选人", "目标", "岗位"
    );

    public int score(ConversationMessageDO message) {
        if (message == null || StrUtil.isBlank(message.getContent())) {
            return 0;
        }
        String content = message.getContent().trim();
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int score = baseScore(message);

        for (String term : IMPORTANT_TERMS) {
            if (lowerContent.contains(term.toLowerCase(Locale.ROOT))) {
                score += 12;
            }
        }
        score += numberSignalScore(content);
        if (containsStructuredMarker(content)) {
            score += 10;
        }
        if (content.length() >= 40) {
            score += 8;
        }
        if (StrUtil.isNotBlank(message.getThinkingContent())) {
            score += 5;
        }
        return Math.min(MAX_SCORE, score);
    }

    public boolean hasProtectedSignal(ConversationMessageDO message) {
        return hasLongTermFactSignal(message)
                || hasKeyEvidenceSignal(message)
                || hasRiskSignal(message);
    }

    public boolean hasLongTermFactSignal(ConversationMessageDO message) {
        String content = normalizedContent(message);
        return containsAny(content, "fact", "preference", "constraint", "requirement", "budget", "deadline",
                "事实", "偏好", "约束", "要求", "预算", "时间", "数量", "必须", "不能", "候选人", "岗位");
    }

    public boolean hasKeyEvidenceSignal(ConversationMessageDO message) {
        String content = normalizedContent(message);
        return containsAny(content, "evidence", "score", "interview", "answer", "resume", "jd",
                "证据", "评分", "分数", "面试", "答案", "简历", "目标", "履历");
    }

    public boolean hasRiskSignal(ConversationMessageDO message) {
        String content = normalizedContent(message);
        return containsAny(content, "risk", "unsupported", "conflict", "missing", "gap",
                "风险", "不支持", "冲突", "缺失", "差距", "待确认");
    }

    private int baseScore(ConversationMessageDO message) {
        String role = StrUtil.blankToDefault(message.getRole(), "").trim();
        if ("user".equalsIgnoreCase(role)) {
            return 35;
        }
        if ("assistant".equalsIgnoreCase(role)) {
            return 15;
        }
        return 5;
    }

    private int numberSignalScore(String content) {
        Matcher matcher = NUMBER_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= 2) {
                break;
            }
        }
        if (count >= 2) {
            return 35;
        }
        if (count == 1) {
            return 18;
        }
        return 0;
    }

    private boolean containsStructuredMarker(String content) {
        return content.contains(":")
                || content.contains("：")
                || content.contains("-")
                || content.contains("；")
                || content.contains(";");
    }

    private String normalizedContent(ConversationMessageDO message) {
        if (message == null || StrUtil.isBlank(message.getContent())) {
            return "";
        }
        return message.getContent().trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String content, String... terms) {
        for (String term : terms) {
            if (content.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
