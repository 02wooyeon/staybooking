package com.kisia.staybooking.statistics;

import java.time.LocalDateTime;

public record TimedAmount(long amount, LocalDateTime occurredAt) {
}
