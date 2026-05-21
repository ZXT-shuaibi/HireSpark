package com.nageoffer.ai.ragent.career.service.tts;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class CareerTextToSpeechService {

    private final CareerTextToSpeechProperties properties;
    private final CareerTextToSpeechProvider provider;

    public CareerTextToSpeechService(CareerTextToSpeechProperties properties) {
        this(properties, (CareerTextToSpeechProvider) null);
    }

    public CareerTextToSpeechService(CareerTextToSpeechProperties properties,
                                     CareerTextToSpeechProvider provider) {
        this.properties = properties == null ? new CareerTextToSpeechProperties() : properties;
        this.provider = provider;
    }

    @Autowired
    public CareerTextToSpeechService(CareerTextToSpeechProperties properties,
                                     ObjectProvider<CareerTextToSpeechProvider> provider) {
        this(properties, provider == null ? null : provider.getIfAvailable());
    }

    public CareerTextToSpeechPlan plan(CareerTextToSpeechRequest request) {
        String text = normalizeText(request == null ? null : request.text());
        String sessionId = normalizeKey(request == null ? null : request.sessionId(), "session");
        String turnId = normalizeKey(request == null ? null : request.turnId(), "turn");
        String cancelKey = "career:tts:cancel:" + sessionId + ":" + turnId;
        if (!properties.isEnabled()) {
            return new CareerTextToSpeechPlan(false, "TEXT_FALLBACK", List.of(), "",
                    cancelKey, text, "tts disabled; text interview remains available",
                    properties.getVoice(), properties.getCacheTtlSeconds(),
                    "", "", "", "", "", false, false);
        }
        if (StrUtil.isBlank(text)) {
            return new CareerTextToSpeechPlan(false, "TEXT_FALLBACK", List.of(), "",
                    cancelKey, "", "blank text; nothing to synthesize",
                    properties.getVoice(), properties.getCacheTtlSeconds(),
                    "", "", "", "", "", false, false);
        }
        List<String> chunks = split(text, properties.getChunkMaxChars());
        String cacheKey = "career:tts:" + sessionId + ":" + turnId + ":" + sha256(text);
        if (provider == null) {
            return new CareerTextToSpeechPlan(true, "READY", chunks, cacheKey,
                    cancelKey, text, "", properties.getVoice(), properties.getCacheTtlSeconds(),
                    "", "", "", "", "", false, false);
        }
        try {
            CareerTextToSpeechProviderResult result = provider.synthesize(new CareerTextToSpeechProviderRequest(
                    sessionId, turnId, text, chunks, properties.getVoice(), cacheKey, cancelKey));
            if (result == null || !result.success()) {
                return new CareerTextToSpeechPlan(false, "TEXT_FALLBACK", chunks, cacheKey,
                        cancelKey, text, result == null ? "tts provider returned empty result" : result.message(),
                        properties.getVoice(), properties.getCacheTtlSeconds(),
                        "", "", "", "", "", false, false);
            }
            String status = result.completed() ? "AUDIO_READY" : "AUDIO_PENDING";
            return new CareerTextToSpeechPlan(true, status, chunks, cacheKey,
                    cancelKey, text, "", properties.getVoice(), properties.getCacheTtlSeconds(),
                    result.taskId(), result.taskStatus(), result.audioBase64(), result.audioUrl(),
                    result.pybufContent(), result.completed(), true);
        } catch (RuntimeException ex) {
            return new CareerTextToSpeechPlan(false, "TEXT_FALLBACK", chunks, cacheKey,
                    cancelKey, text, "tts provider failed: " + ex.getMessage(),
                    properties.getVoice(), properties.getCacheTtlSeconds(),
                    "", "", "", "", "", false, false);
        }
    }

    private List<String> split(String text, int maxChars) {
        int safeMax = Math.max(1, maxChars);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += safeMax) {
            chunks.add(text.substring(start, Math.min(text.length(), start + safeMax)));
        }
        return chunks;
    }

    private String normalizeText(String text) {
        return StrUtil.blankToDefault(text, "").trim();
    }

    private String normalizeKey(String value, String fallback) {
        return StrUtil.blankToDefault(value, fallback).trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate TTS cache key", ex);
        }
    }
}
