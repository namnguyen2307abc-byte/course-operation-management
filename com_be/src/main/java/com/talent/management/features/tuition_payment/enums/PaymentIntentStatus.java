package com.talent.management.features.tuition_payment.enums;

/** Stable codes used by the existing database and API; no schema change required. */
public enum PaymentIntentStatus {
    CREATING,
    PENDING,
    PARTIALLY_PAID,
    UNCERTAIN,
    PAID,
    CANCELLED,
    FAILED,
    RECONCILE;

    public String code() {
        return name();
    }
}
