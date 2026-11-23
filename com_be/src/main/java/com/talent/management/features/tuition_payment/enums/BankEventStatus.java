package com.talent.management.features.tuition_payment.enums;

public enum BankEventStatus {
    RECONCILE,
    POSTED,
    RESOLVED;

    public String code() {
        return name();
    }
}
