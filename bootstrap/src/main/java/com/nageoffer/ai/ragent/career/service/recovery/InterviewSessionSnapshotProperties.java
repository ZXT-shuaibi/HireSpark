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

package com.nageoffer.ai.ragent.career.service.recovery;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "career.interview.snapshot")
public class InterviewSessionSnapshotProperties {

    /**
     * 是否启用 Redis 面试热快照。
     */
    private boolean hotEnabled = true;

    /**
     * Redis 热快照保留时间。
     */
    private Duration hotTtl = Duration.ofHours(6);

    /**
     * 返回热快照保留时间，避免配置为空或非正数导致 Redis 写入异常。
     */
    public Duration resolvedHotTtl() {
        if (hotTtl == null || hotTtl.isZero() || hotTtl.isNegative()) {
            return Duration.ofHours(6);
        }
        return hotTtl;
    }
}
