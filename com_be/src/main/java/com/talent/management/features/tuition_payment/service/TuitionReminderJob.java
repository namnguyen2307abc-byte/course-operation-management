package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.repository.InvoiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TuitionReminderJob {
    private final InvoiceRepository invoices;
    private final TuitionNotificationService notices;
    private final InstallmentGatewayService gateway;

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void refresh() {
        for (Long id : invoices.findReminderInvoiceIds()) {
            try {
                notices.generate(id);
                Long noticeId = notices.pendingQrNotice(id);
                if (noticeId != null) {
                    var intent = gateway.createForReminder(id);
                    notices.attachQr(noticeId, intent.id());
                }
            } catch (RuntimeException ex) {
                log.error("Could not refresh tuition reminders for invoice {}", id, ex);
            }
        }
    }
}
