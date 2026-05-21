package com.nageoffer.ai.ragent.career.service.agent;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class CareerAgentResolver {

    private static final Map<String, AgentMapping> EXPLICIT_MAPPINGS = Map.ofEntries(
            Map.entry("INTERVIEW_EVALUATE", new AgentMapping(
                    "INTERVIEW_EVALUATOR", BusinessAgentScene.INTERVIEW, "INTERVIEW_EVALUATION")),
            Map.entry("INTERVIEW_PLAN", new AgentMapping(
                    "INTERVIEW_PLANNER", BusinessAgentScene.INTERVIEW, "INTERVIEW_PLAN")),
            Map.entry("INTERVIEW_REPORT", new AgentMapping(
                    "INTERVIEW_REPORTER", BusinessAgentScene.INTERVIEW, "INTERVIEW_REPORT")),
            Map.entry("OPTIMIZATION_EXECUTOR", new AgentMapping(
                    "RESUME_OPTIMIZATION_EXECUTOR", BusinessAgentScene.OPTIMIZATION, "RESUME_OPTIMIZATION")),
            Map.entry("OPTIMIZATION_REVIEW", new AgentMapping(
                    "RESUME_OPTIMIZATION_REVIEWER", BusinessAgentScene.OPTIMIZATION, "RESUME_OPTIMIZATION_REVIEW")),
            Map.entry("JD_PARSE", new AgentMapping(
                    "JD_PARSER", BusinessAgentScene.ALIGNMENT, "JD_PARSE")),
            Map.entry("JD_ALIGNMENT", new AgentMapping(
                    "JD_ALIGNMENT_AGENT", BusinessAgentScene.ALIGNMENT, "JD_ALIGNMENT")),
            Map.entry("RESUME_PARSE", new AgentMapping(
                    "RESUME_PARSER", BusinessAgentScene.RESUME, "RESUME_PARSE"))
    );

    public CareerAgentDescriptor resolve(String scene, String singleFlightKey) {
        String normalizedScene = normalizeScene(scene);
        AgentMapping mapping = EXPLICIT_MAPPINGS.getOrDefault(normalizedScene, inferMapping(normalizedScene));
        String[] keyParts = splitKey(singleFlightKey);
        return new CareerAgentDescriptor(
                mapping.agentType(),
                mapping.businessScene(),
                valueAt(keyParts, 1),
                valueAt(keyParts, 2),
                mapping.decisionType());
    }

    private AgentMapping inferMapping(String scene) {
        BusinessAgentScene businessScene = inferBusinessScene(scene);
        String decisionType = switch (businessScene) {
            case INTERVIEW -> "INTERVIEW_DECISION";
            case OPTIMIZATION -> "RESUME_OPTIMIZATION";
            case ALIGNMENT -> "JOB_ALIGNMENT";
            case RESUME -> "RESUME_PROCESSING";
            case RAG -> "RAG_CHAT";
            case CAREER -> "CAREER_DECISION";
        };
        return new AgentMapping(scene, businessScene, decisionType);
    }

    private BusinessAgentScene inferBusinessScene(String scene) {
        if (scene.contains("INTERVIEW")) {
            return BusinessAgentScene.INTERVIEW;
        }
        if (scene.contains("OPTIMIZATION")) {
            return BusinessAgentScene.OPTIMIZATION;
        }
        if (scene.contains("JD") || scene.contains("ALIGN")) {
            return BusinessAgentScene.ALIGNMENT;
        }
        if (scene.contains("RESUME")) {
            return BusinessAgentScene.RESUME;
        }
        if (scene.contains("RAG") || scene.contains("CHAT")) {
            return BusinessAgentScene.RAG;
        }
        return BusinessAgentScene.CAREER;
    }

    private String normalizeScene(String scene) {
        return StrUtil.blankToDefault(scene, "CAREER_AI")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String[] splitKey(String singleFlightKey) {
        if (StrUtil.isBlank(singleFlightKey)) {
            return new String[0];
        }
        return singleFlightKey.split(":", 4);
    }

    private String valueAt(String[] values, int index) {
        if (values.length <= index) {
            return null;
        }
        return StrUtil.isBlank(values[index]) ? null : values[index].trim();
    }

    private record AgentMapping(String agentType,
                                BusinessAgentScene businessScene,
                                String decisionType) {
    }
}
