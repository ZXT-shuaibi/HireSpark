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

package com.nageoffer.ai.ragent.career.service.guard;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.ai.guard")
public class CareerAiGuardProperties {

    private StagePolicy defaultPolicy = StagePolicy.defaults();

    private Map<String, StagePolicy> stages = defaultStages();

    /**
     * 按场景名称解析守卫策略，未知场景回退到默认策略。
     */
    public StagePolicy resolvePolicy(String scene) {
        String normalizedScene = normalize(scene);
        StagePolicy fallback = defaultPolicy == null ? StagePolicy.defaults() : defaultPolicy;
        StagePolicy policy = findStagePolicy(normalizedScene);
        return policy == null ? fallback : policy.merge(fallback);
    }

    /**
     * 规范化场景名称，避免配置键大小写或空白导致策略失效。
     */
    public String normalize(String scene) {
        return StrUtil.blankToDefault(scene, "CAREER_AI").trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /**
     * 按规范化后的场景名称查找场景策略，兼容配置键大小写和中划线差异。
     */
    private StagePolicy findStagePolicy(String normalizedScene) {
        if (stages == null || stages.isEmpty()) {
            return null;
        }
        StagePolicy direct = stages.get(normalizedScene);
        if (direct != null) {
            return direct;
        }
        return stages.entrySet().stream()
                .filter(entry -> StrUtil.equals(normalize(entry.getKey()), normalizedScene))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * 创建内置场景策略，保证核心 Career AI 场景都有显式条目。
     */
    private Map<String, StagePolicy> defaultStages() {
        Map<String, StagePolicy> defaults = new LinkedHashMap<>();
        defaults.put("RESUME_PARSE", new StagePolicy());
        defaults.put("JD_PARSE", new StagePolicy());
        defaults.put("JD_ALIGNMENT", new StagePolicy());
        defaults.put("OPTIMIZATION_EXECUTOR", new StagePolicy());
        defaults.put("OPTIMIZATION_REVIEW", new StagePolicy());
        defaults.put("INTERVIEW_PLAN", new StagePolicy());
        defaults.put("INTERVIEW_EVALUATE", StagePolicy.withTimeout(Duration.ofSeconds(8)));
        defaults.put("INTERVIEW_REPORT", StagePolicy.withTimeout(Duration.ofSeconds(8)));
        return defaults;
    }

    @Data
    public static class StagePolicy {

        private Integer failureRateThreshold;

        private Integer slidingWindowSize;

        private Integer permittedNumberOfCallsInHalfOpenState;

        private Duration waitDurationInOpenState;

        private Integer bulkheadMaxConcurrentCalls;

        private Duration timeout;

        private Integer retryMaxAttempts;

        private Duration retryWaitDuration;

        /**
         * 创建默认守卫策略。
         */
        public static StagePolicy defaults() {
            StagePolicy policy = new StagePolicy();
            policy.setFailureRateThreshold(50);
            policy.setSlidingWindowSize(50);
            policy.setPermittedNumberOfCallsInHalfOpenState(10);
            policy.setWaitDurationInOpenState(Duration.ofSeconds(30));
            policy.setBulkheadMaxConcurrentCalls(20);
            policy.setTimeout(Duration.ofSeconds(5));
            policy.setRetryMaxAttempts(3);
            policy.setRetryWaitDuration(Duration.ofMillis(200));
            return policy;
        }

        /**
         * 创建指定超时时间的场景策略。
         */
        public static StagePolicy withTimeout(Duration timeout) {
            StagePolicy policy = new StagePolicy();
            policy.setTimeout(timeout);
            return policy;
        }

        /**
         * 使用默认策略补齐未配置的字段。
         */
        public StagePolicy merge(StagePolicy defaultPolicy) {
            StagePolicy merged = new StagePolicy();
            merged.setFailureRateThreshold(firstNonNull(failureRateThreshold, defaultPolicy.failureRateThreshold));
            merged.setSlidingWindowSize(firstNonNull(slidingWindowSize, defaultPolicy.slidingWindowSize));
            merged.setPermittedNumberOfCallsInHalfOpenState(firstNonNull(
                    permittedNumberOfCallsInHalfOpenState, defaultPolicy.permittedNumberOfCallsInHalfOpenState));
            merged.setWaitDurationInOpenState(firstNonNull(waitDurationInOpenState, defaultPolicy.waitDurationInOpenState));
            merged.setBulkheadMaxConcurrentCalls(firstNonNull(
                    bulkheadMaxConcurrentCalls, defaultPolicy.bulkheadMaxConcurrentCalls));
            merged.setTimeout(firstNonNull(timeout, defaultPolicy.timeout));
            merged.setRetryMaxAttempts(firstNonNull(retryMaxAttempts, defaultPolicy.retryMaxAttempts));
            merged.setRetryWaitDuration(firstNonNull(retryWaitDuration, defaultPolicy.retryWaitDuration));
            return merged;
        }

        /**
         * 返回第一个非空配置值。
         */
        private <T> T firstNonNull(T value, T defaultValue) {
            return value == null ? defaultValue : value;
        }
    }
}
