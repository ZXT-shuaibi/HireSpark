package com.nageoffer.ai.ragent.career.service.demeanor;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiFaceDetectProvider;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiFaceDetectRequest;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiFaceDetectResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(XunfeiFaceDetectProvider.class)
public class XunfeiDemeanorFaceDetectProvider implements DemeanorFaceDetectProvider {

    private final XunfeiFaceDetectProvider xunfeiFaceDetectProvider;

    @Override
    public DemeanorFaceSignal detect(DemeanorFaceDetectRequest request) {
        XunfeiFaceDetectResult result = xunfeiFaceDetectProvider.detect(new XunfeiFaceDetectRequest(
                request.imageBase64(),
                StrUtil.blankToDefault(request.imageFormat(), "png"),
                request.traceId()));
        return new DemeanorFaceSignal(result.provider(), result.sid(), result.faceCount(),
                result.dominantEmotion(), result.averageConfidence());
    }
}
