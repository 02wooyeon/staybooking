package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.cancellation.CancellationDecisionService;
import com.kisia.staybooking.cancellation.CancellationRequestEntity;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 전체 예약 조회·처리 API. 실제 상태 전이는 승인자와 공통 서비스를 사용한다. */
@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {
    private final ApproverBookingQueryService queries;
    private final AdminBookingSearchService search;
    private final BookingApprovalService approvals;
    private final BookingCancellationService directCancellations;
    private final CancellationDecisionService cancellationDecisions;

    public AdminBookingController(ApproverBookingQueryService queries, AdminBookingSearchService search,
                                  BookingApprovalService approvals,
                                  BookingCancellationService directCancellations,
                                  CancellationDecisionService cancellationDecisions) {
        this.queries = queries;
        this.search = search;
        this.approvals = approvals;
        this.directCancellations = directCancellations;
        this.cancellationDecisions = cancellationDecisions;
    }

    @GetMapping
    public List<ApproverBookingResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                               @RequestParam(defaultValue = "PENDING") BookingStatus status) {
        return queries.list(me.userId(), status);
    }

    @GetMapping("/search")
    public PageResponse<ApproverBookingResponse> search(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @ModelAttribute AdminBookingSearchRequest request) {
        return search.search(me.userId(), request);
    }

    @PatchMapping("/{bookingId}")
    public BookingResponse process(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable @Positive Long bookingId,
                                   @Valid @RequestBody ApproverBookingController.ApproverBookingActionRequest request) {
        return switch (request.action()) {
            case ACCEPT -> BookingResponse.from(approvals.accept(me.userId(), bookingId));
            case CANCEL -> BookingResponse.from(directCancellations.cancel(me.userId(), bookingId,
                required(request.reasonType(), "reasonType"), requiredText(request.reason(), "reason")));
            case APPROVE_CANCELLATION -> BookingResponse.from(approve(me.userId(), bookingId,
                required(request.cancellationRequestId(), "cancellationRequestId"), request.reason()));
            case REJECT_CANCELLATION -> BookingResponse.from(reject(me.userId(), bookingId,
                required(request.cancellationRequestId(), "cancellationRequestId"), requiredText(request.reason(), "reason")));
        };
    }

    private BookingEntity approve(Long actorId, Long bookingId, Long requestId, String reason) {
        CancellationRequestEntity request = cancellationDecisions.approve(actorId, bookingId, requestId, reason);
        return request.getBooking();
    }

    private BookingEntity reject(Long actorId, Long bookingId, Long requestId, String reason) {
        CancellationRequestEntity request = cancellationDecisions.reject(actorId, bookingId, requestId, reason);
        return request.getBooking();
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
