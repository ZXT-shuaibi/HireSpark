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

package com.nageoffer.ai.ragent.career.service.retrieval;

import java.util.List;
import java.util.Map;

public record CareerRetrievalEnhancement(CareerRetrievalScenario scenario,
                                         String hydeQuery,
                                         List<CareerRetrievalEvidence> evidence) {

    public Map<String, Object> toPromptPayload() {
        return Map.of(
                "scenario", scenario.name(),
                "hydeQuery", hydeQuery == null ? "" : hydeQuery,
                "evidence", evidence.stream()
                        .map(item -> Map.of(
                                "type", item.type().name(),
                                "sourceId", item.sourceId() == null ? "" : item.sourceId(),
                                "text", item.text() == null ? "" : item.text(),
                                "score", item.score() == null ? 0F : item.score(),
                                "queryOnly", item.queryOnly()))
                        .toList()
        );
    }
}
