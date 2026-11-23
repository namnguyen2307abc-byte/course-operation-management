package com.talent.management.features.tuition_payment.job;

import com.talent.management.features.tuition_payment.repository.TuitionIntentRepository;
import com.talent.management.features.tuition_payment.service.PaymentIntentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankTransferSyncJob {
    private final TuitionIntentRepository intents;
    private final PaymentIntentService payments;

    @Scheduled(fixedDelay = 45000, initialDelay = 45000)
    public void synchronize() {
        // No database transaction is held while requesting the payment gateway.
        for (Long id :
                intents.findBankSyncIds(LocalDateTime.now().minusDays(1), PageRequest.of(0, 50))) {
            try {
                payments.refreshFromBank(id);
            } catch (RuntimeException ex) {
                log.warn(
                        "Bank synchronization could not complete for intent {}: {}",
                        id,
                        ex.getClass().getSimpleName());
            }
        }
    }
}
