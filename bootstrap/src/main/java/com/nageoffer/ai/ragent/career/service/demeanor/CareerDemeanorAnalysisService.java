package com.nageoffer.ai.ragent.career.service.demeanor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
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

    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties) {
        this(properties, (CareerDemeanorAnalysisProvider) null);
    }

    @Autowired
    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties,
                                         ObjectProvider<CareerDemeanorAnalysisProvider> provider) {
        this(properties, provider == null ? null : provider.getIfAvailable());
    }

    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties,
                                         CareerDemeanorAnalysisProvider provider) {
        this.properties = properties == null ? new CareerDemeanorAnalysisProperties() : properties;
        this.provider = provider;
    }

    public CareerDemeanorAnalysisResult analyze(CareerDemeanorAnalysisRequest request) {
        if (!properties.isEnabled()) {
            return disabled("DISABLED");
        }
        if (request == null || !request.consentGranted()) {
            return disabled("CONSENT_REQUIRED");
        }
        if (provider != null && hasImage(request)) {
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
        try {
            CareerDemeanorAnalysisProviderResult providerResult = provider.analyze(
                    new CareerDemeanorAnalysisProviderRequest(
                            request.sessionId(),
                            request.imageUrl(),
                            request.imageBase64(),
                            request.sampledAt(),
                            request.observations() == null ? List.of() : request.observations()));
            int compositeScore = normalizeScore(providerResult == null ? null : providerResult.compositeScore());
            double confidence = BigDecimal.valueOf(compositeScore / 100D)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            List<String> signals = new ArrayList<>();
            if (providerResult != null && providerResult.signals() != null) {
                providerResult.signals().stream()
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .distinct()
                        .forEach(signals::add);
            }
            signals.add("composite-score:" + compositeScore);
            return new CareerDemeanorAnalysisResult(true, "AUXILIARY_READY", false, confidence,
                    signals, properties.getLimitations(), properties.getRetentionPolicy());
        } catch (Exception ex) {
            return new CareerDemeanorAnalysisResult(false, "PROVIDER_UNAVAILABLE", false, 0D,
                    List.of(providerFailureMessage(ex)), properties.getLimitations(), properties.getRetentionPolicy());
        }
    }

    private boolean hasImage(CareerDemeanorAnalysisRequest request) {
        return request != null && (StrUtil.isNotBlank(request.imageUrl()) || StrUtil.isNotBlank(request.imageBase64()));
    }

    private int normalizeScore(Integer score) {
        if (score == null) {
            throw new ServiceException("Demeanor provider response missing composite score");
        }
        return Math.max(0, Math.min(100, score));
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
