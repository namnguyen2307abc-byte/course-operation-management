package com.talent.management.features.tuition_payment.enums;

public enum BankTransferReason {
    UNMATCHED_CONTENT, // Historical audit value.
    UNKNOWN_ORDER,
    OVERPAYMENT, // Historical audit value; new callbacks use AMOUNT_MISMATCH.
    AMOUNT_MISMATCH,
    STALE_PAYMENT_REQUEST,
    ALREADY_SETTLED,
    INVOICE_UNAVAILABLE
}
