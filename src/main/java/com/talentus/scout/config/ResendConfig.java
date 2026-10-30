package com.talentus.scout.config;

import com.resend.Resend;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ResendConfig {

    @Value("${talentus.mail.api-key:}")
    private String apiKey;

    @Bean
    public Resend resendClient() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("RESEND_API_KEY no está configurada. El servicio de correo operará en modo simulador (Mock).");
            return null;
        }
        log.info("Cliente Resend inicializado exitosamente para envíos transaccionales.");
        return new Resend(apiKey.trim());
    }
}
