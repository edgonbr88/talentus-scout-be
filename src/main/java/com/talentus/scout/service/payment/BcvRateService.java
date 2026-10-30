package com.talentus.scout.service.payment;

import com.talentus.scout.web.dto.BcvRateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class BcvRateService {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final BigDecimal fallbackRate;
    private final long cacheTtlMinutes;

    private BigDecimal cachedRate;
    private Instant lastUpdated;
    private boolean isCachedFallback;

    @org.springframework.beans.factory.annotation.Autowired
    public BcvRateService(
            @Value("${talentus.payments.bcv.api-url:https://ve.dolarapi.com/v1/dolares/oficial}") String apiUrl,
            @Value("${talentus.payments.bcv.fallback-rate:45.50}") BigDecimal fallbackRate,
            @Value("${talentus.payments.bcv.cache-ttl-minutes:360}") long cacheTtlMinutes
    ) {
        this(createDefaultRestTemplate(), apiUrl, fallbackRate, cacheTtlMinutes);
    }

    public BcvRateService(
            RestTemplate restTemplate,
            String apiUrl,
            BigDecimal fallbackRate,
            long cacheTtlMinutes
    ) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.fallbackRate = fallbackRate;
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    private static RestTemplate createDefaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new RestTemplate(factory);
    }

    public synchronized BcvRateResponse getOfficialRate() {
        Instant now = Instant.now();
        if (cachedRate != null && lastUpdated != null && Duration.between(lastUpdated, now).toMinutes() < cacheTtlMinutes) {
            log.debug("Retornando tasa BCV desde caché en memoria: {}", cachedRate);
            return BcvRateResponse.builder()
                    .rate(cachedRate)
                    .source(isCachedFallback ? "FALLBACK_CONFIG" : "BCV_DOLARAPI")
                    .lastUpdated(lastUpdated)
                    .fallback(isCachedFallback)
                    .build();
        }

        try {
            log.info("Consultando tasa oficial BCV desde proveedor externo: {}", apiUrl);
            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                Object promedioObj = body.get("promedio");
                if (promedioObj == null) {
                    promedioObj = body.get("price");
                }

                if (promedioObj != null) {
                    BigDecimal rate = new BigDecimal(promedioObj.toString());
                    this.cachedRate = rate;
                    this.lastUpdated = now;
                    this.isCachedFallback = false;
                    log.info("Tasa oficial BCV obtenida con éxito: {} Bs/$", rate);
                    return BcvRateResponse.builder()
                    .rate(rate)
                    .source("BCV_DOLARAPI")
                    .lastUpdated(lastUpdated)
                    .fallback(false)
                    .build();
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener la tasa BCV desde API externa ({}). Aplicando tasa de respaldo: {} Bs/$",
                    e.getMessage(), fallbackRate);
        }

        this.cachedRate = this.fallbackRate;
        this.lastUpdated = now;
        this.isCachedFallback = true;

        return BcvRateResponse.builder()
                .rate(this.cachedRate)
                .source("FALLBACK_CONFIG")
                .lastUpdated(this.lastUpdated)
                .fallback(true)
                .build();
    }
}
