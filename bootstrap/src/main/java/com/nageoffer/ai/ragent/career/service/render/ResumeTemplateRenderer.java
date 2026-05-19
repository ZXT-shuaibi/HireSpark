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

package com.nageoffer.ai.ragent.career.service.render;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.dao.entity.ResumeVersionDO;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResumeTemplateRenderer {

    private static final String DEFAULT_TEMPLATE_LOCATION = "classpath:templates/career-resume-template-v1.md";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final ResumeTemplateFieldMapper fieldMapper;
    private final ResourceLoader resourceLoader;
    private final String templateLocation;

    /**
     * 兼容纯单元测试场景，使用默认字段映射器和类路径资源加载器。
     */
    public ResumeTemplateRenderer() {
        this(new ResumeTemplateFieldMapper(), new DefaultResourceLoader(), DEFAULT_TEMPLATE_LOCATION);
    }

    /**
     * 使用 Spring 注入的字段映射器和资源加载器渲染默认简历模板。
     */
    @Autowired
    public ResumeTemplateRenderer(ResumeTemplateFieldMapper fieldMapper, ResourceLoader resourceLoader) {
        this(fieldMapper, resourceLoader, DEFAULT_TEMPLATE_LOCATION);
    }

    /**
     * 支持测试或后续扩展指定模板资源位置。
     */
    public ResumeTemplateRenderer(ResumeTemplateFieldMapper fieldMapper,
                                  ResourceLoader resourceLoader,
                                  String templateLocation) {
        this.fieldMapper = fieldMapper;
        this.resourceLoader = resourceLoader;
        this.templateLocation = StrUtil.blankToDefault(templateLocation, DEFAULT_TEMPLATE_LOCATION);
    }

    /**
     * 将简历版本映射为模板字段并渲染 Markdown。
     */
    public String render(ResumeVersionDO version) {
        return render(fieldMapper.map(version));
    }

    /**
     * 使用轻量占位符替换渲染 Markdown，不引入额外模板运行时。
     */
    public String render(Map<String, ?> fields) {
        String template = loadTemplate();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = fields == null ? null : fields.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 从资源目录读取 Markdown 模板。
     */
    private String loadTemplate() {
        Resource resource = resourceLoader.getResource(templateLocation);
        if (!resource.exists()) {
            throw new ServiceException("简历模板资源不存在：" + templateLocation);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ServiceException("简历模板资源读取失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }
}
