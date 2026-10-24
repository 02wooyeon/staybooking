package com.kisia.staybooking.booking;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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
    private final OperatorBookingActionService actions;

    public AdminBookingController(ApproverBookingQueryService queries, AdminBookingSearchService search,
                                  OperatorBookingActionService actions) {
        this.queries = queries;
        this.search = search;
        this.actions = actions;
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

    @GetMapping("/{bookingId}")
    public OperatorBookingDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser me,
                                                @PathVariable @Positive Long bookingId) {
        return queries.detail(me.userId(), bookingId);
    }

    @PatchMapping("/{bookingId}")
    public BookingResponse process(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable @Positive Long bookingId,
                                   @Valid @RequestBody ApproverBookingController.ApproverBookingActionRequest request) {
        return actions.process(me.userId(), bookingId, request);
    }
}
