package com.talentus.scout.service.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "talentus.payments.vexpay.reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class VexPayReconciliationScheduler {

    private final VexPayService vexPayService;

    @Scheduled(
            fixedDelayString = "${talentus.payments.vexpay.reconcile-interval-ms:300000}",
            initialDelayString = "${talentus.payments.vexpay.reconcile-initial-delay-ms:60000}"
    )
    public void scheduledReconciliation() {
        try {
            vexPayService.reconcilePendingTransactions();
        } catch (Exception e) {
            log.warn("Error en job programado de conciliación VexPay: {}", e.getMessage());
        }
    }
}
