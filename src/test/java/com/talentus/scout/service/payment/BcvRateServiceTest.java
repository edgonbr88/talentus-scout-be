package com.talentus.scout.service.payment;

import com.talentus.scout.web.dto.BcvRateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BcvRateServiceTest {

    private RestTemplate restTemplate;
    private BcvRateService bcvRateService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        bcvRateService = new BcvRateService(
                restTemplate,
                "https://ve.dolarapi.com/v1/dolares/oficial",
                new BigDecimal("45.50"),
                360
        );
    }

    @Test
    void getOfficialRate_ApiSuccess_ReturnsExternalRate() {
        Map<String, Object> body = Map.of("promedio", 46.12);
        when(restTemplate.getForEntity(eq("https://ve.dolarapi.com/v1/dolares/oficial"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        BcvRateResponse response = bcvRateService.getOfficialRate();

        assertNotNull(response);
        assertEquals(new BigDecimal("46.12"), response.getRate());
        assertEquals("BCV_DOLARAPI", response.getSource());
        assertFalse(response.isFallback());
    }

    @Test
    void getOfficialRate_ApiError_ReturnsFallbackRate() {
        when(restTemplate.getForEntity(eq("https://ve.dolarapi.com/v1/dolares/oficial"), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        BcvRateResponse response = bcvRateService.getOfficialRate();

        assertNotNull(response);
        assertEquals(new BigDecimal("45.50"), response.getRate());
        assertEquals("FALLBACK_CONFIG", response.getSource());
        assertTrue(response.isFallback());
    }

    @Test
    void getOfficialRate_UsesMemoryCache_DoesNotCallApiAgain() {
        Map<String, Object> body = Map.of("promedio", 46.50);
        when(restTemplate.getForEntity(eq("https://ve.dolarapi.com/v1/dolares/oficial"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        BcvRateResponse response1 = bcvRateService.getOfficialRate();
        BcvRateResponse response2 = bcvRateService.getOfficialRate();

        assertEquals(0, new BigDecimal("46.50").compareTo(response1.getRate()));
        assertEquals(0, new BigDecimal("46.50").compareTo(response2.getRate()));
        verify(restTemplate, times(1)).getForEntity(anyString(), eq(Map.class));
    }
}
