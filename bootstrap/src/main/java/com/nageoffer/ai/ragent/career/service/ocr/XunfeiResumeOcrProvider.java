package com.nageoffer.ai.ragent.career.service.ocr;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiOcrProvider;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiOcrRequest;
import com.nageoffer.ai.ragent.career.service.xunfei.XunfeiOcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(XunfeiOcrProvider.class)
public class XunfeiResumeOcrProvider implements ResumeOcrProvider {

    private final XunfeiOcrProvider xunfeiOcrProvider;

    @Override
    public ResumeOcrResult recognize(ResumeOcrRequest request) {
        String base64 = Base64.getEncoder().encodeToString(request.imageBytes());
        XunfeiOcrResult result = xunfeiOcrProvider.recognize(new XunfeiOcrRequest(
                base64,
                StrUtil.blankToDefault(request.imageFormat(), "png"),
                request.traceId()));
        return new ResumeOcrResult(result.text(), result.lines(), result.provider(), result.sid());
    }
}
