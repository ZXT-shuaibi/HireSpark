package com.nageoffer.ai.ragent.user.service.auth;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ragent.auth.sms", name = "provider", havingValue = "aliyun")
public class AliyunSmsVerificationSender implements SmsVerificationSender {

    private final SmsVerificationProperties properties;

    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    public void send(String phone, String code, SmsVerificationPurpose purpose) {
        SmsVerificationProperties.Aliyun aliyun = properties.getAliyun();
        if (StrUtil.hasBlank(aliyun.getAccessKeyId(), aliyun.getAccessKeySecret(),
                aliyun.getSignName(), aliyun.getTemplateCode())) {
            throw new ServiceException("阿里云短信配置不完整");
        }
        Map<String, String> params = new TreeMap<>();
        params.put("Action", "SendSms");
        params.put("Version", "2017-05-25");
        params.put("RegionId", aliyun.getRegionId());
        params.put("Format", "JSON");
        params.put("AccessKeyId", aliyun.getAccessKeyId());
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
        params.put("PhoneNumbers", phone);
        params.put("SignName", aliyun.getSignName());
        params.put("TemplateCode", aliyun.getTemplateCode());
        params.put("TemplateParam", "{\"code\":\"" + code + "\"}");
        params.put("Signature", sign(params, aliyun.getAccessKeySecret()));

        FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
        params.forEach(form::add);
        Request request = new Request.Builder().url(aliyun.getEndpoint()).post(form.build()).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseBody = body == null ? "" : body.string();
            if (!response.isSuccessful() || !responseBody.contains("\"Code\":\"OK\"")) {
                throw new ServiceException("阿里云短信发送失败: " + responseBody);
            }
        } catch (Exception ex) {
            if (ex instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException("阿里云短信发送失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String sign(Map<String, String> params, String accessKeySecret) {
        StringBuilder canonicalized = new StringBuilder();
        params.forEach((key, value) -> {
            if (!canonicalized.isEmpty()) {
                canonicalized.append("&");
            }
            canonicalized.append(percentEncode(key)).append("=").append(percentEncode(value));
        });
        String stringToSign = "POST&%2F&" + percentEncode(canonicalized.toString());
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ServiceException("阿里云短信签名失败", ex, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
