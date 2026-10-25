package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.cancellation.CancellationDecisionService;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 관리자와 승인자의 예약 처리 및 응답 생성을 하나의 트랜잭션에서 완료한다. */
@Service
public class OperatorBookingActionService {
    private final BookingApprovalService approvals;
    private final BookingCancellationService directCancellations;
    private final CancellationDecisionService cancellationDecisions;
    private final RefundRepository refunds;
    private final TransactionTemplate transaction;

    public OperatorBookingActionService(BookingApprovalService approvals,
                                        BookingCancellationService directCancellations,
                                        CancellationDecisionService cancellationDecisions,
                                        RefundRepository refunds, PlatformTransactionManager manager) {
        this.approvals = approvals;
        this.directCancellations = directCancellations;
        this.cancellationDecisions = cancellationDecisions;
        this.refunds = refunds;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public BookingResponse process(Long actorId, Long bookingId,
                                   ApproverBookingController.ApproverBookingActionRequest request) {
        BookingResponse response = transaction.execute(status -> {
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
            // LAZY 관계는 트랜잭션 안에서 읽되, 환불 상태는 커밋 후에 확인한다.
            return BookingResponse.from(booking);
        });
        if (response != null && response.status() == BookingStatus.CANCELLED) {
            RefundSummary refund = refunds.findSummariesByBookingIdIn(List.of(bookingId))
                .stream().findFirst().orElse(null);
            return response.withRefund(refund);
        }
        return response;
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
