package com.nageoffer.ai.ragent.career.service;

import com.nageoffer.ai.ragent.career.service.tts.CareerTextToSpeechProperties;
import com.nageoffer.ai.ragent.career.service.tts.CareerTextToSpeechRequest;
import com.nageoffer.ai.ragent.career.service.tts.CareerTextToSpeechService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerTextToSpeechServiceTest {

    @Test
    void disabledTtsFallsBackToTextWithoutBlockingInterview() {
        CareerTextToSpeechProperties properties = new CareerTextToSpeechProperties();
        properties.setEnabled(false);
        CareerTextToSpeechService service = new CareerTextToSpeechService(properties);

        var plan = service.plan(CareerTextToSpeechRequest.of("session-1", "turn-1", "请介绍一个项目"));

        assertThat(plan.enabled()).isFalse();
        assertThat(plan.status()).isEqualTo("TEXT_FALLBACK");
        assertThat(plan.fallbackText()).isEqualTo("请介绍一个项目");
        assertThat(plan.chunks()).isEmpty();
        assertThat(plan.degradeReason()).contains("disabled");
    }

    @Test
    void enabledTtsSplitsLongTextAndBuildsCacheAndCancelKeys() {
        CareerTextToSpeechProperties properties = new CareerTextToSpeechProperties();
        properties.setEnabled(true);
        properties.setChunkMaxChars(6);
        CareerTextToSpeechService service = new CareerTextToSpeechService(properties);

        var plan = service.plan(CareerTextToSpeechRequest.of("session-1", "turn-1", "abcdefghijk"));

        assertThat(plan.enabled()).isTrue();
        assertThat(plan.status()).isEqualTo("READY");
        assertThat(plan.chunks()).containsExactly("abcdef", "ghijk");
        assertThat(plan.cacheKey()).startsWith("career:tts:session-1:turn-1:");
        assertThat(plan.cancelKey()).isEqualTo("career:tts:cancel:session-1:turn-1");
        assertThat(plan.fallbackText()).isEqualTo("abcdefghijk");
    }
}
