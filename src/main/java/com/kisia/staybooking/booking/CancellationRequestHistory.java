package com.kisia.staybooking.booking;

import java.time.LocalDateTime;

import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;

public record CancellationRequestHistory(
    Long cancellationRequestId,
    CancellationRequestStatus status,
    CancellationReasonType reasonType,
    String reasonDetail,
    LocalDateTime requestedAt,
    String decisionReason,
    LocalDateTime decidedAt
) {
    public static CancellationRequestHistory from(CancellationRequestEntity request) {
        return new CancellationRequestHistory(request.getCancellationRequestId(), request.getStatus(),
            request.getReasonType(), request.getReasonDetail(), request.getCreatedAt(),
            request.getDecisionReason(), request.getDecidedAt());
    }
}
