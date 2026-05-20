# 简历渲染与导出

## 适用场景

修改简历结构化对象、模板字段映射、Markdown/HTML/PDF/DOCX 导出、导出记录失效时先读本文件。

## 当前实现路径

- 渲染流水线：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeRenderPipeline.java`
- 字段映射：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeTemplateFieldMapper.java`
- 模板渲染：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/render/ResumeTemplateRenderer.java`
- 模板资源：`bootstrap/src/main/resources/templates/career-resume-template-v1.md`
- 导出服务：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/service/impl/CandidateProfileServiceImpl.java`
- 导出 VO：`bootstrap/src/main/java/com/nageoffer/ai/ragent/career/controller/vo/CareerResumeExportVO.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/ResumeRenderPipelineTest.java`
- 测试：`bootstrap/src/test/java/com/nageoffer/ai/ragent/career/service/CandidateProfileExportTest.java`

## 流水线

1. 校验结构化简历字段。
2. 将结构化对象映射为模板变量。
3. 使用模板生成 Markdown。
4. Markdown 转 HTML。
5. 根据请求格式生成 PDF 或 DOCX。
6. 记录导出格式、模板版本、内容类型、Trace 和校验结果。

## 质量要求

- 不要直接把 JSON 兜底拼成 Markdown。
- PDF/DOCX 必须从同一份模板输出，保证一次优化，多端交付。
- 中文字体或渲染失败时，要返回明确失败原因，不能影响 Markdown/HTML。
- 删除简历版本后，关联导出记录不能继续下载。

## 修改检查

- 新增字段时，同步字段映射、模板、字段校验和导出测试。
- 修改模板版本时，同步导出记录中的 `templateVersion`。
- 修改 PDF/DOCX 依赖时，跑 `ResumeRenderPipelineTest,CandidateProfileExportTest`。
