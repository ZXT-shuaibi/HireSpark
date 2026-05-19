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

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RedisInterviewSessionHotSnapshotService implements InterviewSessionHotSnapshotService {

    private static final String KEY_PREFIX = "career:interview:hot-snapshot:";

    private final StringRedisTemplate stringRedisTemplate;
    private final InterviewSessionSnapshotProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将面试热快照写入 Redis，作为冷快照之外的快速恢复入口。
     */
    @Override
    public void save(HotSnapshot snapshot) {
        if (!properties.isHotEnabled() || snapshot == null
                || StrUtil.isBlank(snapshot.getSessionId()) || StrUtil.isBlank(snapshot.getUserId())) {
            return;
        }
        stringRedisTemplate.opsForValue().set(redisKey(snapshot.getSessionId(), snapshot.getUserId()),
                writeJson(snapshot), properties.resolvedHotTtl());
    }

    /**
     * 从 Redis 读取面试热快照，读取不到时返回空结果。
     */
    @Override
    public Optional<HotSnapshot> load(String sessionId, String userId) {
        if (!properties.isHotEnabled() || StrUtil.isBlank(sessionId) || StrUtil.isBlank(userId)) {
            return Optional.empty();
        }
        String value = stringRedisTemplate.opsForValue().get(redisKey(sessionId, userId));
        if (StrUtil.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(readJson(value));
    }

    /**
     * 生成用户隔离的 Redis 热快照键。
     */
    private String redisKey(String sessionId, String userId) {
        return KEY_PREFIX + userId + ":" + sessionId;
    }

    /**
     * 将热快照对象序列化为 JSON 字符串。
     */
    private String writeJson(HotSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new ServiceException("面试热快照序列化失败");
        }
    }

    /**
     * 将 Redis 中的 JSON 字符串反序列化为热快照对象。
     */
    private HotSnapshot readJson(String value) {
        try {
            return objectMapper.readValue(value, HotSnapshot.class);
        } catch (Exception ex) {
            throw new ServiceException("面试热快照反序列化失败");
        }
    }
}
