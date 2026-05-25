package com.nageoffer.ai.ragent.core.desensitize;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneDesensitizeSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void masksMainlandPhoneNumberDuringSerialization() throws Exception {
        String json = objectMapper.writeValueAsString(new Sample("13812345678"));

        assertThat(json).contains("138****5678");
        assertThat(json).doesNotContain("13812345678");
    }

    @Test
    void keepsShortInvalidValueReadable() throws Exception {
        String json = objectMapper.writeValueAsString(new Sample("12345"));

        assertThat(json).contains("12345");
    }

    private record Sample(@PhoneDesensitize String phone) {
    }
}
