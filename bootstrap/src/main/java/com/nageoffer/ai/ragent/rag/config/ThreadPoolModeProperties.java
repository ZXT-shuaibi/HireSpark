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

package com.nageoffer.ai.ragent.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 线程池模式配置。
 *
 * <p>通过配置中心（Nacos / Apollo）或 application.yml 控制：
 *
 * <pre>
 * ragent:
 *   threadpool:
 *     mode: adaptive       # 默认，自适应缓冲线程池
 *     # mode: jdk-legacy   # 切回 JDK 原生线程池
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "ragent.threadpool")
public class ThreadPoolModeProperties {

    /**
     * 线程池模式：{@code adaptive}（默认）或 {@code jdk-legacy}。
     */
    private String mode = "adaptive";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
