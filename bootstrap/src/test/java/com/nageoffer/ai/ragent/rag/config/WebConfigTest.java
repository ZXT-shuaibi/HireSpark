package com.nageoffer.ai.ragent.rag.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigTest {

    @Test
    void extendMessageConvertersPrependsUtf8StringConverterWithoutDroppingDefaults() {
        WebConfig webConfig = new WebConfig();
        ByteArrayHttpMessageConverter existing = new ByteArrayHttpMessageConverter();
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(existing);

        webConfig.extendMessageConverters(converters);

        assertTrue(converters.get(0) instanceof StringHttpMessageConverter);
        StringHttpMessageConverter stringConverter = (StringHttpMessageConverter) converters.get(0);
        assertEquals(StandardCharsets.UTF_8, stringConverter.getDefaultCharset());
        assertSame(existing, converters.get(1));
    }
}
