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
 * ASR 分段转写包，承载 AST 的 seg_id、pgs、rg、bg、ed 等关键字段。
 */
public record AstTranscriptionSegment(Integer segmentId,
                                      String pgs,
                                      int[] rg,
                                      Integer bg,
                                      Integer ed,
                                      String text,
                                      boolean finalPacket) {
}
