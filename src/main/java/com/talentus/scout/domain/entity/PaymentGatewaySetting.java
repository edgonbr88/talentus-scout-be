package com.talentus.scout.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_gateway_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentGatewaySetting {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "pago_movil_enabled", nullable = false)
    @Builder.Default
    private Boolean pagoMovilEnabled = true;

    @Column(name = "pago_movil_bank", nullable = false, length = 100)
    private String pagoMovilBank;

    @Column(name = "pago_movil_phone", nullable = false, length = 50)
    private String pagoMovilPhone;

    @Column(name = "pago_movil_id_number", nullable = false, length = 50)
    private String pagoMovilIdNumber;

    @Column(name = "pago_movil_account_holder", nullable = false, length = 150)
    private String pagoMovilAccountHolder;

    @Column(name = "binance_manual_enabled", nullable = false)
    @Builder.Default
    private Boolean binanceManualEnabled = true;

    @Column(name = "binance_pay_id", length = 50)
    private String binancePayId;

    @Column(name = "binance_wallet_address", length = 150)
    private String binanceWalletAddress;

    @Column(name = "binance_network", length = 50)
    @Builder.Default
    private String binanceNetwork = "USDT (TRC-20 / BEP-20)";

    @Column(name = "binance_qr_image_url")
    private String binanceQrImageUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
