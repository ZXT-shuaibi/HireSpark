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

package com.nageoffer.ai.ragent.career.controller.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerAdminTaskItemVO {

    private String id;

    private String type;

    private String status;

    private String userId;

    private String businessId;

    private String summary;

    private String failureReason;

    private String traceId;

    private Date createTime;

    private Date updateTime;

    private BigDecimal qualityScore;

    private String reviewStatus;

    private Boolean riskFlag;

    private String riskSummary;

    private String runtimeStatus;

    private String scene;

    private Long fencingToken;

    private Integer requestCount;

    private Integer currentTurnNo;

    private String modelName;

    private Boolean replayed;

    private Long latencyMs;
}
