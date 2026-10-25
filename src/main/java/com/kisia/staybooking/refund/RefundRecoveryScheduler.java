package com.kisia.staybooking.refund;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** DB01을 사용하는 서버에서만 중단된 자동 환불을 주기적으로 확인한다. */
@Configuration
@Profile("mysql")
@EnableScheduling
public class RefundRecoveryScheduler {
    private final RefundRecoveryService recovery;

    public RefundRecoveryScheduler(RefundRecoveryService recovery) {
        this.recovery = recovery;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void recover() {
        recovery.recover();
    }
}
