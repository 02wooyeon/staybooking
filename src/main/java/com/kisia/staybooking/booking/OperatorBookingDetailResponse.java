package com.kisia.staybooking.booking;

import java.time.LocalDateTime;
import java.util.List;

import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;

/** 운영자가 한 예약의 상태와 취소 요청 처리 이력을 함께 확인할 때 사용한다. */
public record OperatorBookingDetailResponse(
    ApproverBookingResponse summary,
    List<CancellationRequestDecision> cancellationRequests
) {
    public record CancellationRequestDecision(
        Long cancellationRequestId,
        CancellationRequestStatus status,
        Long requestedByUserId,
        CancellationReasonType reasonType,
        String reasonDetail,
        LocalDateTime requestedAt,
        Long decidedByUserId,
        String decisionReason,
        LocalDateTime decidedAt
    ) {
        static CancellationRequestDecision from(CancellationRequestEntity request) {
            return new CancellationRequestDecision(request.getCancellationRequestId(), request.getStatus(),
                request.getRequestedBy().getUserId(), request.getReasonType(), request.getReasonDetail(),
                request.getCreatedAt(), request.getDecidedBy() == null ? null : request.getDecidedBy().getUserId(),
                request.getDecisionReason(), request.getDecidedAt());
        }
    }
}
