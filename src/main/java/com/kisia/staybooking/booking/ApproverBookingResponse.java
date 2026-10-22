package com.kisia.staybooking.booking;

import com.kisia.staybooking.cancellation.CancellationRequestEntity;

/** 승인 화면에서 예약 처리 대상과 대기 중인 취소 요청을 함께 보여주는 응답 모델. */
public record ApproverBookingResponse(
    BookingResponse booking,
    PendingCancellationRequest cancellationRequest
) {
    static ApproverBookingResponse from(BookingEntity booking, CancellationRequestEntity request) {
        return new ApproverBookingResponse(BookingResponse.from(booking),
            request == null ? null : PendingCancellationRequest.from(request));
    }

    public record PendingCancellationRequest(Long cancellationRequestId, String reasonType,
                                             String reasonDetail) {
        static PendingCancellationRequest from(CancellationRequestEntity request) {
            return new PendingCancellationRequest(request.getCancellationRequestId(),
                request.getReasonType().name(), request.getReasonDetail());
        }
    }
}
