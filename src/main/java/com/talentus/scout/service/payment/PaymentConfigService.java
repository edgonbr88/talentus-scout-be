package com.talentus.scout.service.payment;

import com.talentus.scout.domain.entity.PaymentGatewaySetting;
import com.talentus.scout.domain.entity.SubscriptionPlan;
import com.talentus.scout.repository.PaymentGatewaySettingRepository;
import com.talentus.scout.repository.SubscriptionPlanRepository;
import com.talentus.scout.web.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfigService {

    private final BcvRateService bcvRateService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentGatewaySettingRepository paymentGatewaySettingRepository;

    @Value("${talentus.payments.pago-movil.bank-name:Banco Nacional de Crédito (BNC)}")
    private String defaultBankName;

    @Value("${talentus.payments.pago-movil.phone-number:04141234567}")
    private String defaultPhoneNumber;

    @Value("${talentus.payments.pago-movil.id-number:J-50123456-7}")
    private String defaultIdNumber;

    @Value("${talentus.payments.pago-movil.account-holder:Talentus Scout C.A.}")
    private String defaultAccountHolder;

    @Value("${talentus.payments.binance.pay-id:283918273}")
    private String defaultBinancePayId;

    @Value("${talentus.payments.binance.wallet-address:TLsV5289FjK93kdJ8193jd93kd9}")
    private String defaultBinanceWalletAddress;

    @Value("${talentus.payments.binance.network:USDT (TRC-20 / BEP-20)}")
    private String defaultBinanceNetwork;

    @Value("${talentus.payments.binance.qr-image-url:/assets/images/binance-qr-sample.png}")
    private String defaultBinanceQrImageUrl;

    @Transactional(readOnly = true)
    public PaymentConfigResponse getPaymentConfig() {
        BcvRateResponse bcv = bcvRateService.getOfficialRate();

        List<SubscriptionPlanDto> plans = subscriptionPlanRepository.findAllByIsActiveTrue().stream()
                .map(this::mapToDto)
                .toList();

        Optional<PaymentGatewaySetting> settingOpt = paymentGatewaySettingRepository.findSingleton();

        boolean pmEnabled = settingOpt.map(PaymentGatewaySetting::getPagoMovilEnabled).orElse(true);
        String bank = settingOpt.map(PaymentGatewaySetting::getPagoMovilBank).orElse(defaultBankName);
        String phone = settingOpt.map(PaymentGatewaySetting::getPagoMovilPhone).orElse(defaultPhoneNumber);
        String idNum = settingOpt.map(PaymentGatewaySetting::getPagoMovilIdNumber).orElse(defaultIdNumber);
        String holder = settingOpt.map(PaymentGatewaySetting::getPagoMovilAccountHolder).orElse(defaultAccountHolder);

        boolean binanceEnabled = settingOpt.map(PaymentGatewaySetting::getBinanceManualEnabled).orElse(true);
        String payId = settingOpt.map(PaymentGatewaySetting::getBinancePayId).orElse(defaultBinancePayId);
        String wallet = settingOpt.map(PaymentGatewaySetting::getBinanceWalletAddress).orElse(defaultBinanceWalletAddress);
        String network = settingOpt.map(PaymentGatewaySetting::getBinanceNetwork).orElse(defaultBinanceNetwork);
        String qrUrl = settingOpt.map(PaymentGatewaySetting::getBinanceQrImageUrl).orElse(defaultBinanceQrImageUrl);

        return PaymentConfigResponse.builder()
                .bcvRate(bcv.getRate())
                .bcvLastUpdated(bcv.getLastUpdated())
                .pagoMovilEnabled(pmEnabled)
                .bankName(bank)
                .phoneNumber(phone)
                .idNumber(idNum)
                .accountHolder(holder)
                .plans(plans)
                .binanceManualEnabled(binanceEnabled)
                .binancePayId(payId)
                .binanceWalletAddress(wallet)
                .binanceNetwork(network)
                .binanceQrImageUrl(qrUrl)
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentGatewaySettingsDto getGatewaySettings() {
        PaymentGatewaySetting setting = paymentGatewaySettingRepository.findSingleton()
                .orElseGet(this::createDefaultSetting);
        return mapToGatewayDto(setting);
    }

    @Transactional
    public PaymentGatewaySettingsDto updateGatewaySettings(UpdatePaymentGatewaySettingsRequest request) {
        PaymentGatewaySetting setting = paymentGatewaySettingRepository.findSingleton()
                .orElseGet(this::createDefaultSetting);

        setting.setPagoMovilEnabled(request.getPagoMovilEnabled());
        setting.setPagoMovilBank(request.getPagoMovilBank().trim());
        setting.setPagoMovilPhone(request.getPagoMovilPhone().trim());
        setting.setPagoMovilIdNumber(request.getPagoMovilIdNumber().trim());
        setting.setPagoMovilAccountHolder(request.getPagoMovilAccountHolder().trim());

        setting.setBinanceManualEnabled(request.getBinanceManualEnabled());
        if (request.getBinancePayId() != null) {
            setting.setBinancePayId(request.getBinancePayId().trim());
        }
        if (request.getBinanceWalletAddress() != null) {
            setting.setBinanceWalletAddress(request.getBinanceWalletAddress().trim());
        }
        if (request.getBinanceNetwork() != null) {
            setting.setBinanceNetwork(request.getBinanceNetwork().trim());
        }

        PaymentGatewaySetting saved = paymentGatewaySettingRepository.save(setting);
        log.info("Payment gateway settings updated successfully by administrator: ID={}", saved.getId());
        return mapToGatewayDto(saved);
    }

    @Transactional
    public PaymentGatewaySettingsDto updateBinanceQrUrl(String qrImageUrl) {
        PaymentGatewaySetting setting = paymentGatewaySettingRepository.findSingleton()
                .orElseGet(this::createDefaultSetting);
        setting.setBinanceQrImageUrl(qrImageUrl);
        PaymentGatewaySetting saved = paymentGatewaySettingRepository.save(setting);
        log.info("Binance QR image URL updated successfully: {}", qrImageUrl);
        return mapToGatewayDto(saved);
    }

    private PaymentGatewaySetting createDefaultSetting() {
        PaymentGatewaySetting defaultSetting = PaymentGatewaySetting.builder()
                .pagoMovilEnabled(true)
                .pagoMovilBank(defaultBankName)
                .pagoMovilPhone(defaultPhoneNumber)
                .pagoMovilIdNumber(defaultIdNumber)
                .pagoMovilAccountHolder(defaultAccountHolder)
                .binanceManualEnabled(true)
                .binancePayId(defaultBinancePayId)
                .binanceWalletAddress(defaultBinanceWalletAddress)
                .binanceNetwork(defaultBinanceNetwork)
                .binanceQrImageUrl(defaultBinanceQrImageUrl)
                .build();
        return paymentGatewaySettingRepository.save(defaultSetting);
    }

    private PaymentGatewaySettingsDto mapToGatewayDto(PaymentGatewaySetting s) {
        return PaymentGatewaySettingsDto.builder()
                .id(s.getId())
                .pagoMovilEnabled(s.getPagoMovilEnabled())
                .pagoMovilBank(s.getPagoMovilBank())
                .pagoMovilPhone(s.getPagoMovilPhone())
                .pagoMovilIdNumber(s.getPagoMovilIdNumber())
                .pagoMovilAccountHolder(s.getPagoMovilAccountHolder())
                .binanceManualEnabled(s.getBinanceManualEnabled())
                .binancePayId(s.getBinancePayId())
                .binanceWalletAddress(s.getBinanceWalletAddress())
                .binanceNetwork(s.getBinanceNetwork())
                .binanceQrImageUrl(s.getBinanceQrImageUrl())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private SubscriptionPlanDto mapToDto(SubscriptionPlan plan) {
        return SubscriptionPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .priceUsd(plan.getPriceUsd())
                .durationDays(plan.getDurationDays())
                .maxVideos(plan.getMaxVideos())
                .hasRadarViews(plan.getHasRadarViews())
                .hasPdfCv(plan.getHasPdfCv())
                .build();
    }
}
