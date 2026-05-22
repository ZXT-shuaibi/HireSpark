package com.nageoffer.ai.ragent.career.service.demeanor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CareerDemeanorAnalysisService {

    private final CareerDemeanorAnalysisProperties properties;

    private final CareerDemeanorAnalysisProvider provider;

    private final DemeanorFaceDetectProvider faceDetectProvider;

    private final DemeanorNormalizationStrategy normalizationStrategy;

    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties) {
        this(properties, (CareerDemeanorAnalysisProvider) null, null, new DemeanorNormalizationStrategy());
    }

    @Autowired
    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties,
                                         ObjectProvider<CareerDemeanorAnalysisProvider> provider,
                                         ObjectProvider<DemeanorFaceDetectProvider> faceDetectProvider,
                                         ObjectProvider<DemeanorNormalizationStrategy> normalizationStrategy) {
        this(properties,
                provider == null ? null : provider.getIfAvailable(),
                faceDetectProvider == null ? null : faceDetectProvider.getIfAvailable(),
                normalizationStrategy == null ? null : normalizationStrategy.getIfAvailable());
    }

    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties,
                                         CareerDemeanorAnalysisProvider provider) {
        this(properties, provider, null, new DemeanorNormalizationStrategy());
    }

    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties,
                                         CareerDemeanorAnalysisProvider provider,
                                         DemeanorFaceDetectProvider faceDetectProvider,
                                         DemeanorNormalizationStrategy normalizationStrategy) {
        this.properties = properties == null ? new CareerDemeanorAnalysisProperties() : properties;
        this.provider = provider;
        this.faceDetectProvider = faceDetectProvider;
        this.normalizationStrategy = normalizationStrategy == null
                ? new DemeanorNormalizationStrategy()
                : normalizationStrategy;
    }

    public CareerDemeanorAnalysisResult analyze(CareerDemeanorAnalysisRequest request) {
        if (!properties.isEnabled()) {
            return disabled("DISABLED");
        }
        if (request == null || !request.consentGranted()) {
            return disabled("CONSENT_REQUIRED");
        }
        if (hasVisualAnalyzer(request)) {
            return analyzeWithProvider(request);
        }
        List<CareerDemeanorObservation> observations = request.observations() == null
                ? List.of()
                : request.observations();
        List<String> signals = observations.stream()
                .map(CareerDemeanorObservation::signal)
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(signals)) {
            return new CareerDemeanorAnalysisResult(true, "NO_SIGNAL", false, 0D,
                    List.of(), properties.getLimitations(), properties.getRetentionPolicy());
        }
        double confidence = observations.stream()
                .map(CareerDemeanorObservation::confidence)
                .filter(value -> value != null && value >= 0D)
                .mapToDouble(value -> Math.min(1D, value))
                .average()
                .orElse(0D);
        double rounded = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new CareerDemeanorAnalysisResult(true, "AUXILIARY_READY", false, rounded,
                signals, properties.getLimitations(), properties.getRetentionPolicy());
    }

    private CareerDemeanorAnalysisResult analyzeWithProvider(CareerDemeanorAnalysisRequest request) {
        CareerDemeanorAnalysisProviderResult providerResult = null;
        DemeanorFaceSignal faceSignal = null;
        List<String> failures = new ArrayList<>();
        if (provider != null && hasImage(request)) {
            try {
                providerResult = provider.analyze(
                        new CareerDemeanorAnalysisProviderRequest(
                                request.sessionId(),
                                request.imageUrl(),
                                request.imageBase64(),
                                request.sampledAt(),
                                request.observations() == null ? List.of() : request.observations()));
            } catch (Exception ex) {
                failures.add(providerFailureMessage(ex));
            }
        }
        if (faceDetectProvider != null && StrUtil.isNotBlank(request.imageBase64())) {
            try {
                faceSignal = faceDetectProvider.detect(new DemeanorFaceDetectRequest(
                        request.imageBase64(),
                        imageFormat(request.imageBase64()),
                        "career-demeanor-face-" + StrUtil.blankToDefault(request.sessionId(), "session")));
            } catch (Exception ex) {
                failures.add(providerFailureMessage(ex));
            }
        }
        if (providerResult == null && faceSignal == null) {
            if (!failures.isEmpty()) {
                return new CareerDemeanorAnalysisResult(false, "PROVIDER_UNAVAILABLE", false, 0D,
                        failures, properties.getLimitations(), properties.getRetentionPolicy());
            }
            return new CareerDemeanorAnalysisResult(true, "NO_SIGNAL", false, 0D,
                    List.of(), properties.getLimitations(), properties.getRetentionPolicy());
        }
        DemeanorNormalizedResult normalized = normalizationStrategy.normalize(
                providerResult,
                faceSignal,
                request.observations() == null ? List.of() : request.observations());
        return new CareerDemeanorAnalysisResult(true, "AUXILIARY_READY", false, normalized.confidence(),
                normalized.signals(), properties.getLimitations(), properties.getRetentionPolicy());
    }

    private boolean hasImage(CareerDemeanorAnalysisRequest request) {
        return request != null && (StrUtil.isNotBlank(request.imageUrl()) || StrUtil.isNotBlank(request.imageBase64()));
    }

    private boolean hasVisualAnalyzer(CareerDemeanorAnalysisRequest request) {
        return request != null && hasImage(request)
                && (provider != null || (faceDetectProvider != null && StrUtil.isNotBlank(request.imageBase64())));
    }

    private String imageFormat(String imageBase64) {
        String value = StrUtil.blankToDefault(imageBase64, "").trim();
        if (value.startsWith("data:image/")) {
            int slash = value.indexOf('/');
            int semicolon = value.indexOf(';');
            if (slash >= 0 && semicolon > slash) {
                String format = value.substring(slash + 1, semicolon);
                return "jpeg".equals(format) ? "jpg" : format;
            }
        }
        return "png";
    }

    private String providerFailureMessage(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        return StrUtil.blankToDefault(message, "Demeanor provider unavailable");
    }

    private CareerDemeanorAnalysisResult disabled(String status) {
        return new CareerDemeanorAnalysisResult(false, status, false, 0D,
                List.of(), Objects.requireNonNullElse(properties.getLimitations(), List.of()),
                properties.getRetentionPolicy());
    }
}
