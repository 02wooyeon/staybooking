package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ROLE_APPROVER와 ROLE_ADMIN이 함께 쓰는 예약 처리 API. */
@RestController
@RequestMapping("/api/v1/approver/bookings")
public class ApproverBookingController {
    private final OperatorBookingActionService actions;
    private final ApproverBookingQueryService queries;
    private final AdminBookingSearchService search;

    public ApproverBookingController(OperatorBookingActionService actions, ApproverBookingQueryService queries,
                                     AdminBookingSearchService search) {
        this.actions = actions;
        this.queries = queries;
        this.search = search;
    }

    @GetMapping
    public List<ApproverBookingResponse> list(
        @AuthenticationPrincipal AuthenticatedUser me,
        @RequestParam(defaultValue = "PENDING") BookingStatus status
    ) {
        return queries.list(me.userId(), status);
    }

    @GetMapping("/search")
    public PageResponse<ApproverBookingResponse> search(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @ModelAttribute AdminBookingSearchRequest request) {
        return search.searchForOperator(me.userId(), request);
    }

    @GetMapping("/{bookingId}")
    public OperatorBookingDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser me,
                                                @PathVariable @Positive Long bookingId) {
        return queries.detail(me.userId(), bookingId);
    }

    @PatchMapping("/{bookingId}")
    public BookingResponse process(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable @Positive Long bookingId,
                                   @Valid @RequestBody ApproverBookingActionRequest request) {
        return actions.process(me.userId(), bookingId, request);
    }

    public record ApproverBookingActionRequest(@NotNull Action action, Long cancellationRequestId,
                                               CancellationReasonType reasonType, String reason) { }

    public enum Action {
        ACCEPT, CANCEL, APPROVE_CANCELLATION, REJECT_CANCELLATION
    }
}
