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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.voice.xunfei-ast")
public class XunfeiAstProperties {

    private static final String DEFAULT_WS_BASE_URL = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";

    private Boolean enabled = false;

    private String appId;

    private String apiKey;

    private String apiSecret;

    private String wsBaseUrl = DEFAULT_WS_BASE_URL;

    private String audioEncode = "pcm_s16le";

    private String lang = "autodialect";

    private String sampleRate = "16000";

    private Integer chunkSizeBytes = 1280;

    private Integer sendIntervalMs = 40;

    /**
     * 返回安全的音频分片大小，避免异常配置导致发送线程空转。
     */
    public int effectiveChunkSizeBytes() {
        return chunkSizeBytes == null || chunkSizeBytes <= 0 ? 1280 : Math.min(chunkSizeBytes, 8192);
    }

    /**
     * 返回安全的分片发送间隔，兼容讯飞 AST 对实时流的节奏要求。
     */
    public int effectiveSendIntervalMs() {
        return sendIntervalMs == null || sendIntervalMs < 0 ? 40 : Math.min(sendIntervalMs, 500);
    }

    /**
     * 返回安全的 WebSocket 地址，未配置时使用讯飞 AST 默认地址。
     */
    public String effectiveWsBaseUrl() {
        return wsBaseUrl == null || wsBaseUrl.trim().isEmpty() ? DEFAULT_WS_BASE_URL : wsBaseUrl.trim();
    }
}
