package com.talentus.scout.web.controller;

import com.talentus.scout.service.payment.PaymentConfigService;
import com.talentus.scout.service.storage.FileStorageService;
import com.talentus.scout.web.dto.PaymentGatewaySettingsDto;
import com.talentus.scout.web.dto.UpdatePaymentGatewaySettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/payments/gateway-settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentGatewayConfigController {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp"
    );
    private static final long MAX_QR_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final PaymentConfigService paymentConfigService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public ResponseEntity<PaymentGatewaySettingsDto> getGatewaySettings() {
        return ResponseEntity.ok(paymentConfigService.getGatewaySettings());
    }

    @PutMapping
    public ResponseEntity<PaymentGatewaySettingsDto> updateGatewaySettings(
            @Valid @RequestBody UpdatePaymentGatewaySettingsRequest request
    ) {
        return ResponseEntity.ok(paymentConfigService.updateGatewaySettings(request));
    }

    @PostMapping("/binance-qr")
    public ResponseEntity<Map<String, String>> uploadBinanceQr(
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo de código QR no puede estar vacío"));
        }

        if (file.getSize() > MAX_QR_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo excede el tamaño máximo permitido de 5MB"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato de archivo inválido. Solo se admiten imágenes PNG, JPG o WEBP"));
        }

        String qrImageUrl = fileStorageService.storeFile(file, "payments");
        paymentConfigService.updateBinanceQrUrl(qrImageUrl);

        log.info("Binance QR code successfully uploaded and updated by admin: {}", qrImageUrl);
        return ResponseEntity.ok(Map.of("qrImageUrl", qrImageUrl));
    }
}
