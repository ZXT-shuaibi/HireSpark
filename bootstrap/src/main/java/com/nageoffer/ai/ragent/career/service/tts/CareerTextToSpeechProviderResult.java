package com.nageoffer.ai.ragent.career.service.tts;

import cn.hutool.core.util.StrUtil;

public record CareerTextToSpeechProviderResult(boolean success,
                                               boolean completed,
                                               String taskId,
                                               String taskStatus,
                                               String audioBase64,
                                               String audioUrl,
                                               String pybufContent,
                                               String message) {

    public static CareerTextToSpeechProviderResult success(String taskId,
                                                           String taskStatus,
                                                           String audioBase64,
                                                           String audioUrl,
                                                           String pybufContent) {
        return new CareerTextToSpeechProviderResult(true, true, taskId, taskStatus,
                audioBase64, audioUrl, pybufContent, "");
    }

    public static CareerTextToSpeechProviderResult pending(String taskId, String taskStatus, String message) {
        return new CareerTextToSpeechProviderResult(true, false, taskId, taskStatus,
                "", "", "", StrUtil.blankToDefault(message, "tts task is pending"));
    }
}
