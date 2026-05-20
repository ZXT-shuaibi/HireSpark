package com.nageoffer.ai.ragent.career.service.demeanor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class CareerDemeanorAnalysisService {

    private final CareerDemeanorAnalysisProperties properties;

    public CareerDemeanorAnalysisService(CareerDemeanorAnalysisProperties properties) {
        this.properties = properties == null ? new CareerDemeanorAnalysisProperties() : properties;
    }

    public CareerDemeanorAnalysisResult analyze(CareerDemeanorAnalysisRequest request) {
        if (!properties.isEnabled()) {
            return disabled("DISABLED");
        }
        if (request == null || !request.consentGranted()) {
            return disabled("CONSENT_REQUIRED");
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

    private CareerDemeanorAnalysisResult disabled(String status) {
        return new CareerDemeanorAnalysisResult(false, status, false, 0D,
                List.of(), properties.getLimitations(), properties.getRetentionPolicy());
    }
}
