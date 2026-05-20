package com.nageoffer.ai.ragent.career.service.tts;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class CareerTextToSpeechService {

    private final CareerTextToSpeechProperties properties;

    public CareerTextToSpeechService(CareerTextToSpeechProperties properties) {
        this.properties = properties == null ? new CareerTextToSpeechProperties() : properties;
    }

    public CareerTextToSpeechPlan plan(CareerTextToSpeechRequest request) {
        String text = normalizeText(request == null ? null : request.text());
        String sessionId = normalizeKey(request == null ? null : request.sessionId(), "session");
        String turnId = normalizeKey(request == null ? null : request.turnId(), "turn");
        String cancelKey = "career:tts:cancel:" + sessionId + ":" + turnId;
        if (!properties.isEnabled()) {
            return new CareerTextToSpeechPlan(false, "TEXT_FALLBACK", List.of(), "",
                    cancelKey, text, "tts disabled; text interview remains available",
                    properties.getVoice(), properties.getCacheTtlSeconds());
        }
        if (StrUtil.isBlank(text)) {
            return new CareerTextToSpeechPlan(false, "TEXT_FALLBACK", List.of(), "",
                    cancelKey, "", "blank text; nothing to synthesize",
                    properties.getVoice(), properties.getCacheTtlSeconds());
        }
        List<String> chunks = split(text, properties.getChunkMaxChars());
        String cacheKey = "career:tts:" + sessionId + ":" + turnId + ":" + sha256(text);
        return new CareerTextToSpeechPlan(true, "READY", chunks, cacheKey,
                cancelKey, text, "", properties.getVoice(), properties.getCacheTtlSeconds());
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
