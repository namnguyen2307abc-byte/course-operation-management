package com.talent.management.features.tuition_payment.service;

import com.talent.management.features.tuition_payment.dto.response.InstallmentResponses.NoticeView;

import java.util.List;

/** Application contract for TuitionNotificationService. */
public interface TuitionNotificationService {
    List<NoticeView> mine();

    void generate(Long invoiceId);

    Long pendingQrNotice(Long invoiceId);

    void attachQr(Long noticeId, Long intentId);

    void markRead(Long id);
}
