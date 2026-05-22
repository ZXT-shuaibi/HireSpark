package com.nageoffer.ai.ragent.career.service.nlp;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CareerNlpEnrichmentService {

    public static final String PAYLOAD_KEY = "xunfeiNlp";
    public static final String SCENE_JD_PARSE = "JD_PARSE";
    public static final String SCENE_RESUME_PARSE = "RESUME_PARSE";
    public static final String SCENE_INTERVIEW_REPORT = "INTERVIEW_REPORT";

    private final CareerNlpProvider provider;

    @Autowired
    public CareerNlpEnrichmentService(ObjectProvider<CareerNlpProvider> provider) {
        this(provider == null ? null : provider.getIfAvailable());
    }

    public CareerNlpEnrichmentService(CareerNlpProvider provider) {
        this.provider = provider;
    }

    public Map<String, Object> enrich(String scene, String text, String traceId) {
        if (provider == null || StrUtil.isBlank(text)) {
            return Map.of();
        }
        try {
            CareerNlpAnalysisResult result = provider.analyze(new CareerNlpAnalysisRequest(scene, text, traceId));
            if (result == null || isEmpty(result)) {
                return Map.of(
                        "status", "NO_SIGNAL",
                        "scene", StrUtil.blankToDefault(scene, ""),
                        "traceId", StrUtil.blankToDefault(traceId, ""));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "READY");
            payload.put("scene", StrUtil.blankToDefault(scene, ""));
            payload.put("traceId", StrUtil.blankToDefault(traceId, ""));
            payload.put("provider", StrUtil.blankToDefault(result.provider(), "xunfei-nlp"));
            payload.put("sid", StrUtil.blankToDefault(result.sid(), ""));
            payload.put("keywords", result.keywords() == null ? List.of() : result.keywords());
            payload.put("entities", result.entities() == null ? List.of() : result.entities());
            payload.put("sentiment", StrUtil.blankToDefault(result.sentiment(), ""));
            return payload;
        } catch (Exception ex) {
            return Map.of(
                    "status", "UNAVAILABLE",
                    "scene", StrUtil.blankToDefault(scene, ""),
                    "traceId", StrUtil.blankToDefault(traceId, ""),
                    "message", StrUtil.blankToDefault(ex.getMessage(), "NLP provider unavailable"));
        }
    }

    private boolean isEmpty(CareerNlpAnalysisResult result) {
        return (result.keywords() == null || result.keywords().isEmpty())
                && (result.entities() == null || result.entities().isEmpty())
                && StrUtil.isBlank(result.sentiment());
    }
}
