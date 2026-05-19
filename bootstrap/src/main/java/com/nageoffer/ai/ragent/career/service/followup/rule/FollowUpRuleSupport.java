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

package com.nageoffer.ai.ragent.career.service.followup.rule;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.InterviewTurnDO;
import com.nageoffer.ai.ragent.career.service.followup.InterviewFollowUpDecisionRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 追问规则公共工具，统一处理空请求、空字符串和单值列表兼容逻辑。
 */
final class FollowUpRuleSupport {

    private FollowUpRuleSupport() {
    }

    /**
     * 返回安全的会话轮次列表，避免空请求导致规则节点异常。
     */
    static List<InterviewTurnDO> safeTurns(InterviewFollowUpDecisionRequest request) {
        if (request == null || request.sessionTurns() == null) {
            return List.of();
        }
        return request.sessionTurns();
    }

    /**
     * 将字段值统一转换为列表，兼容单值和集合值。
     */
    static List<Object> valuesOf(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    /**
     * 去除字符串首尾空白并将空字符串转换为 null。
     */
    static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
