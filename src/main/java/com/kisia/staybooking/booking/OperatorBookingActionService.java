package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.cancellation.CancellationDecisionService;
import com.kisia.staybooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자와 승인자의 예약 처리 및 응답 생성을 하나의 트랜잭션에서 완료한다. */
@Service
public class OperatorBookingActionService {
    private final BookingApprovalService approvals;
    private final BookingCancellationService directCancellations;
    private final CancellationDecisionService cancellationDecisions;

    public OperatorBookingActionService(BookingApprovalService approvals,
                                        BookingCancellationService directCancellations,
                                        CancellationDecisionService cancellationDecisions) {
        this.approvals = approvals;
        this.directCancellations = directCancellations;
        this.cancellationDecisions = cancellationDecisions;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse process(Long actorId, Long bookingId,
                                   ApproverBookingController.ApproverBookingActionRequest request) {
        BookingEntity booking = switch (request.action()) {
            case ACCEPT -> approvals.accept(actorId, bookingId);
            case CANCEL -> directCancellations.cancel(actorId, bookingId,
                required(request.reasonType(), "reasonType"), requiredText(request.reason(), "reason"));
            case APPROVE_CANCELLATION -> cancellationDecisions.approve(actorId, bookingId,
                required(request.cancellationRequestId(), "cancellationRequestId"), request.reason()).getBooking();
            case REJECT_CANCELLATION -> cancellationDecisions.reject(actorId, bookingId,
                required(request.cancellationRequestId(), "cancellationRequestId"),
                requiredText(request.reason(), "reason")).getBooking();
        };
        // room/accommodation은 LAZY 관계이므로 트랜잭션이 끝나기 전에 응답으로 변환한다.
        return BookingResponse.from(booking);
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw invalid(field);
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field);
        return value;
    }

    private static ApiException invalid(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.", List.of(field));
    }
}
