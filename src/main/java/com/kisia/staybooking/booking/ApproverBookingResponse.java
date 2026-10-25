package com.kisia.staybooking.booking;

import java.time.LocalDateTime;

import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.refund.RefundSummary;
import com.kisia.staybooking.user.UserEntity;

/** 승인 화면에서 예약 처리 대상과 대기 중인 취소 요청을 함께 보여주는 응답 모델. */
public record ApproverBookingResponse(
    BookingResponse booking,
    PendingCancellationRequest cancellationRequest,
    DecisionHistory decisionHistory
) {
    static ApproverBookingResponse from(BookingEntity booking, CancellationRequestEntity request,
                                        RefundSummary refund) {
        return new ApproverBookingResponse(BookingResponse.from(booking, request != null, refund),
            request == null ? null : PendingCancellationRequest.from(request),
            DecisionHistory.from(booking));
    }

    public record PendingCancellationRequest(Long cancellationRequestId, String reasonType,
                                             String reasonDetail) {
        static PendingCancellationRequest from(CancellationRequestEntity request) {
            return new PendingCancellationRequest(request.getCancellationRequestId(),
                request.getReasonType().name(), request.getReasonDetail());
        }
    }

    /** 관리자·승인자에게만 노출되는 예약 처리 이력. 공개 예약 응답에는 포함하지 않는다. */
    public record DecisionHistory(Long approvedByUserId, LocalDateTime approvedAt,
                                  Long completedByUserId, LocalDateTime completedAt,
                                  Long cancelledByUserId, LocalDateTime cancelledAt,
                                  CancellationSource cancelSource, CancellationReasonType cancelReasonType,
                                  String cancelReason) {
        static DecisionHistory from(BookingEntity booking) {
            return new DecisionHistory(userId(booking.getApprovedBy()), booking.getApprovedAt(),
                userId(booking.getCompletedBy()), booking.getCompletedAt(),
                userId(booking.getCancelledBy()), booking.getCancelledAt(),
                booking.getCancelSource(), booking.getCancelReasonType(), booking.getCancelReason());
        }

        private static Long userId(UserEntity user) {
            return user == null ? null : user.getUserId();
        }
    }
}
