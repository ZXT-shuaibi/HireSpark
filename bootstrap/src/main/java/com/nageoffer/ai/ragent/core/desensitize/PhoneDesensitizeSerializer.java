package com.nageoffer.ai.ragent.core.desensitize;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class PhoneDesensitizeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(mask(value));
    }

    static String mask(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 8) {
            return trimmed;
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
