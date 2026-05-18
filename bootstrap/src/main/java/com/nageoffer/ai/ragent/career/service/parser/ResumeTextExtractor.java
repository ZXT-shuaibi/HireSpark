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

package com.nageoffer.ai.ragent.career.service.parser;

import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Component
public class ResumeTextExtractor {

    private final Tika tika = new Tika();

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ClientException("Resume file is empty");
        }
        try (InputStream inputStream = file.getInputStream()) {
            String text = tika.parseToString(inputStream);
            if (text == null || text.trim().isEmpty()) {
                throw new ClientException("Resume text is empty");
            }
            return text.trim();
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("Failed to read resume file", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
