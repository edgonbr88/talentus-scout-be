package com.talentus.scout.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "talentus.payments.vexpay")
public class VexPayProperties {

    private String apiUrl = "https://api.vexpay.app";
    private String apiKey = "";
    private String webhookSecret = "";
    private long webhookToleranceSeconds = 300L;
    private String successUrl = "http://localhost/dashboard?payment=vexpay_return";
    private String cancelUrl = "http://localhost/dashboard?payment=vexpay_cancelled";
    private boolean reconcileEnabled = true;
    private long reconcileIntervalMs = 300000L;
    private long reconcileLookbackHours = 48L;
    private boolean livemode = false;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isEffectiveLivemode() {
        if (apiKey != null && !apiKey.isBlank()) {
            if (apiKey.startsWith("vk_live_")) {
                return true;
            }
            if (apiKey.startsWith("vk_test_")) {
                return false;
            }
        }
        return livemode;
    }
}
