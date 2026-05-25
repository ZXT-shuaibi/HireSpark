package com.nageoffer.ai.ragent.user.service.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "ragent.auth.sms")
public class SmsVerificationProperties {

    private String provider = "logging";

    private Duration codeTtl = Duration.ofMinutes(5);

    private Duration ticketTtl = Duration.ofMinutes(10);

    private Duration resendCooldown = Duration.ofSeconds(60);

    private int maxSendPerWindow = 3;

    private Duration sendWindow = Duration.ofMinutes(10);

    private int maxVerifyFailures = 3;

    private Duration ipSendWindow = Duration.ofMinutes(10);

    private int maxIpSendPerWindow = 30;

    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Aliyun {

        private String endpoint = "https://dysmsapi.aliyuncs.com/";

        private String accessKeyId;

        private String accessKeySecret;

        private String signName;

        private String templateCode;

        private String regionId = "cn-hangzhou";
    }
}
