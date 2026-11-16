package com.talentus.scout.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "talentus.oauth")
public class OAuthProperties {

    private boolean mockEnabled = true;
    private boolean verifySignature = true;

    private ProviderConfig google = ProviderConfig.builder()
            .jwksUri("https://www.googleapis.com/oauth2/v3/certs")
            .build();

    private ProviderConfig microsoft = ProviderConfig.builder()
            .jwksUri("https://login.microsoftonline.com/common/discovery/v2.0/keys")
            .build();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProviderConfig {
        @Builder.Default
        private String clientId = "";
        private String jwksUri;
    }
}
