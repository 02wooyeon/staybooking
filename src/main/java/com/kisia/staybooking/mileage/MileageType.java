package com.kisia.staybooking.mileage;

/** docs/architecture/DATABASE.md의 mileage_history.type. USE 금액은 음수로 기록한다. */
public enum MileageType {
    EARN,
    USE,
    REFUND
}
