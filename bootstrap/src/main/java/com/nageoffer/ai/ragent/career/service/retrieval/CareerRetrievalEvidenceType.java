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

public enum CareerRetrievalEvidenceType {

    /**
     * 原始简历正文证据，可作为候选人真实事实使用。
     */
    RESUME_TEXT,

    /**
     * 目标 JD 正文证据，可作为岗位要求事实使用。
     */
    JD_TEXT,

    /**
     * 向量知识库召回片段，可用于补充背景或追问深度。
     */
    KNOWLEDGE_CHUNK,

    /**
     * HyDE 虚拟查询画像，只能用于检索和上下文参考，不能写入简历正文。
     */
    HYDE_QUERY
}
