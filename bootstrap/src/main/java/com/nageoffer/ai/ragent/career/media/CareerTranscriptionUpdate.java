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

/**
 * Career 实时转写快照，前端可把 final 状态的 fullText 作为面试答案草稿。
 */
public record CareerTranscriptionUpdate(String fullText,
                                        String committedText,
                                        String liveText,
                                        String displayText,
                                        Integer revision,
                                        String resultStatus,
                                        Integer segmentId,
                                        String segmentText,
                                        String pgs,
                                        int[] rg,
                                        Integer bg,
                                        Integer ed,
                                        boolean finalPacket) {
}
