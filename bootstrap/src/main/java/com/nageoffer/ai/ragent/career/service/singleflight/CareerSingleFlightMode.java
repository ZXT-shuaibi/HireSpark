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

package com.nageoffer.ai.ragent.career.service.singleflight;

/**
 * 定义 Career AI single-flight 的运行模式。
 */
public enum CareerSingleFlightMode {

    /**
     * 仅使用本地数据库审计账本与本地回放缓存，不访问 Redis。
     */
    LOCAL,

    /**
     * 使用 Redis Lua 作为跨节点协调源，Redis 异常不做本地降级。
     */
    DISTRIBUTED,

    /**
     * 优先使用 Redis Lua，Redis 异常时降级到数据库账本。
     */
    HYBRID
}
