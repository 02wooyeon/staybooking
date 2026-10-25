package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 커밋 후 이벤트를 놓쳤거나 서버가 PG 응답 도중 종료된 자동 환불 건을 복구한다. */
@Service
public class RefundRecoveryService {
    private static final int BATCH_SIZE = 25;
    private final RefundRepository refunds;
    private final AutomaticRefundService automaticRefunds;

    public RefundRecoveryService(RefundRepository refunds, AutomaticRefundService automaticRefunds) {
        this.refunds = refunds;
        this.automaticRefunds = automaticRefunds;
    }

    public int recover() {
        return recover(LocalDateTime.now(ZoneOffset.UTC));
    }

    int recover(LocalDateTime nowUtc) {
        List<Long> ids = refunds.findRecoveryCandidates(RefundStatus.REQUESTED, nowUtc.minusMinutes(1),
            RefundStatus.PROCESSING, nowUtc.minusMinutes(2), PageRequest.of(0, BATCH_SIZE));
        ids.forEach(automaticRefunds::recover);
        return ids.size();
    }
}
