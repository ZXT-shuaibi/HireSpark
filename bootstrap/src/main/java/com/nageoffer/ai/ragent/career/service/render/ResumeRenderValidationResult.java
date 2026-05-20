package com.nageoffer.ai.ragent.career.service.render;

import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Builder
public record ResumeRenderValidationResult(boolean valid,
                                           List<String> missingFields,
                                           String templateVersion,
                                           String contentType,
                                           List<String> warnings,
                                           String traceId,
                                           boolean rendererEnabled,
                                           String disabledReason,
                                           String renderEngine,
                                           String fontFamily,
                                           String pdfFontFamily,
                                           List<String> fontResourceLocations,
                                           Map<String, Object> fontStrategy) {

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", valid);
        payload.put("missingFields", missingFields == null ? List.of() : missingFields);
        payload.put("templateVersion", templateVersion == null ? "" : templateVersion);
        payload.put("contentType", contentType == null ? "" : contentType);
        payload.put("warnings", warnings == null ? List.of() : warnings);
        payload.put("traceId", traceId == null ? "" : traceId);
        payload.put("rendererEnabled", rendererEnabled);
        payload.put("disabledReason", disabledReason == null ? "" : disabledReason);
        payload.put("renderEngine", renderEngine == null ? "" : renderEngine);
        payload.put("fontFamily", fontFamily == null ? "" : fontFamily);
        payload.put("pdfFontFamily", pdfFontFamily == null ? "" : pdfFontFamily);
        payload.put("fontResourceLocations", fontResourceLocations == null ? List.of() : fontResourceLocations);
        payload.put("fontStrategy", fontStrategy == null ? Map.of() : fontStrategy);
        return payload;
    }
}
