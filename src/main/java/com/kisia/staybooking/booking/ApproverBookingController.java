package com.kisia.staybooking.booking;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.cancellation.CancellationDecisionService;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ROLE_APPROVER와 ROLE_ADMIN이 함께 쓰는 예약 처리 API. */
@RestController
@RequestMapping("/api/v1/approver/bookings")
public class ApproverBookingController {
    private final BookingApprovalService approvals;
    private final BookingCancellationService directCancellations;
    private final CancellationDecisionService cancellationDecisions;

    public ApproverBookingController(BookingApprovalService approvals,
                                     BookingCancellationService directCancellations,
                                     CancellationDecisionService cancellationDecisions) {
        this.approvals = approvals;
        this.directCancellations = directCancellations;
        this.cancellationDecisions = cancellationDecisions;
    }

    @PatchMapping("/{bookingId}")
    public BookingResponse process(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable @Positive Long bookingId,
                                   @Valid @RequestBody ApproverBookingActionRequest request) {
        return switch (request.action()) {
            case ACCEPT -> BookingResponse.from(approvals.accept(me.userId(), bookingId));
            case CANCEL -> BookingResponse.from(directCancellations.cancel(me.userId(), bookingId,
                required(request.reasonType(), "reasonType"), requiredText(request.reason(), "reason")));
            case APPROVE_CANCELLATION -> BookingResponse.from(approveRequest(me.userId(), bookingId,
                required(request.cancellationRequestId(), "cancellationRequestId"), request.reason()));
            case REJECT_CANCELLATION -> BookingResponse.from(rejectRequest(me.userId(), bookingId,
                required(request.cancellationRequestId(), "cancellationRequestId"), requiredText(request.reason(), "reason")));
        };
    }

    private BookingEntity approveRequest(Long actorId, Long bookingId, Long requestId, String reason) {
        CancellationRequestEntity decision = cancellationDecisions.approve(actorId, bookingId, requestId, reason);
        return decision.getBooking();
    }

    private BookingEntity rejectRequest(Long actorId, Long bookingId, Long requestId, String reason) {
        CancellationRequestEntity decision = cancellationDecisions.reject(actorId, bookingId, requestId, reason);
        return decision.getBooking();
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.",
                java.util.List.of(field));
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "입력값이 올바르지 않습니다.",
                java.util.List.of(field));
        }
        return value;
    }

    public record ApproverBookingActionRequest(@NotNull Action action, Long cancellationRequestId,
                                               CancellationReasonType reasonType, String reason) { }

    public enum Action {
        ACCEPT, CANCEL, APPROVE_CANCELLATION, REJECT_CANCELLATION
    }
}
