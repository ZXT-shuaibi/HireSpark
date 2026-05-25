/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.career.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XunfeiAstAudioTranscriptionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildAstUrlIncludesSignedQuery() throws Exception {
        XunfeiAstAudioTranscriptionService service = newService();

        String url = service.buildAstUrl("session-1");

        assertTrue(url.startsWith("wss://example.test/ast?"));
        assertTrue(url.contains("accessKeyId=key"));
        assertTrue(url.contains("appId=app"));
        assertTrue(url.contains("audio_encode=pcm_s16le"));
        assertTrue(url.contains("sessionId=session-1"));
        assertTrue(url.contains("signature="));
    }

    @Test
    void extractAstTextUsesFirstCandidateWords() throws Exception {
        XunfeiAstAudioTranscriptionService service = newService();
        JsonNode root = objectMapper.readTree("""
                {
                  "data": {
                    "cn": {
                      "st": {
                        "rt": [
                          {
                            "ws": [
                              {"cw": [{"w": "Hello"}, {"w": "ignored"}]},
                              {"cw": [{"w": " world"}]}
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
                """);

        assertEquals("Hello world", service.extractAstText(root));
    }

    @Test
    void resolveSegmentIdPrefersAstSegmentIdAndFallsBackToCounter() throws Exception {
        XunfeiAstAudioTranscriptionService service = newService();
        JsonNode withSegmentId = objectMapper.readTree("{\"data\":{\"seg_id\":7}}");
        AtomicInteger fallback = new AtomicInteger();

        assertEquals(7, service.resolveSegmentId(withSegmentId, null, fallback));
        assertEquals(8, service.resolveSegmentId(objectMapper.readTree("{}"), null, fallback));
    }

    @Test
    void extractRgAndFinalFlagFromStNode() throws Exception {
        XunfeiAstAudioTranscriptionService service = newService();
        JsonNode root = objectMapper.readTree("""
                {
                  "data": {
                    "cn": {
                      "st": {
                        "ls": true,
                        "rg": [3, 5]
                      }
                    }
                  }
                }
                """);
        JsonNode st = service.extractAstSt(root);

        assertNotNull(st);
        assertEquals(3, service.extractRg(st)[0]);
        assertEquals(5, service.extractRg(st)[1]);
        assertTrue(service.isAstFinal(root));
    }

    private XunfeiAstAudioTranscriptionService newService() {
        XunfeiAstProperties properties = new XunfeiAstProperties();
        properties.setAppId("app");
        properties.setApiKey("key");
        properties.setApiSecret("secret");
        properties.setWsBaseUrl("wss://example.test/ast");
        return new XunfeiAstAudioTranscriptionService(properties);
    }
}
