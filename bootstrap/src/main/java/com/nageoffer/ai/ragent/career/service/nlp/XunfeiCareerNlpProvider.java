package com.nageoffer.ai.ragent.career.service.nlp;

import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiNlpProvider;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiNlpRequest;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiNlpResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(XunfeiNlpProvider.class)
public class XunfeiCareerNlpProvider implements CareerNlpProvider {

    private final XunfeiNlpProvider xunfeiNlpProvider;

    @Override
    public CareerNlpAnalysisResult analyze(CareerNlpAnalysisRequest request) {
        XunfeiNlpResult result = xunfeiNlpProvider.analyze(new XunfeiNlpRequest(request.text(), request.traceId()));
        return new CareerNlpAnalysisResult(result.provider(), result.sid(),
                result.keywords(), result.entities(), result.sentiment());
    }
}
