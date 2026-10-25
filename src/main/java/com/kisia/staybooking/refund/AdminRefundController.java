package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {
    private final RefundRepository refunds;
    private final AutomaticRefundService service;
    private final RefundReconciliationService reconciliation;

    public AdminRefundController(RefundRepository refunds, AutomaticRefundService service,
                                 RefundReconciliationService reconciliation) {
        this.refunds = refunds;
        this.service = service;
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public List<Summary> list() {
        return refunds.findAllByOrderByCreatedAtDesc().stream().map(Summary::from).toList();
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public Summary detail(@PathVariable @Positive Long id) {
        return Summary.from(refunds.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND",
                "환불 요청을 찾을 수 없습니다.")));
    }

    @GetMapping("/{id}/reconciliation")
    public RefundReconciliationService.Report reconciliation(@PathVariable @Positive Long id) {
        return reconciliation.check(id);
    }

    @GetMapping("/search")
    public PageResponse<Summary> search(@Valid @ModelAttribute RefundSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        PageRequest paging = PageRequest.of(page, size);
        Page<RefundEntity> result;
        if (request.bookingId() != null && request.status() != null) {
            result = refunds.findByBookingBookingIdAndStatusOrderByCreatedAtDescRefundIdDesc(
                request.bookingId(), request.status(), paging);
        } else if (request.bookingId() != null) {
            result = refunds.findByBookingBookingIdOrderByCreatedAtDescRefundIdDesc(request.bookingId(), paging);
        } else if (request.status() != null) {
            result = refunds.findByStatusOrderByCreatedAtDescRefundIdDesc(request.status(), paging);
        } else {
            result = refunds.findAllByOrderByCreatedAtDescRefundIdDesc(paging);
        }
        return new PageResponse<>(result.getContent().stream().map(Summary::from).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/{id}/retry")
    public Summary retry(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable @Positive Long id) {
        return Summary.from(service.retry(me.userId(), id));
    }

    public record Summary(Long refundId, Long bookingId, long amount, RefundStatus status,
                          String reason, Long processedBy, LocalDateTime processedAt,
                          LocalDateTime startedAt, String failureCode, boolean retryable) {
        static Summary from(RefundEntity refund) {
            return new Summary(refund.getRefundId(), refund.getBooking().getBookingId(),
                refund.getAmount(), refund.getStatus(), refund.getReason(),
                refund.getProcessedBy() == null ? null : refund.getProcessedBy().getUserId(),
                refund.getProcessedAt(), refund.getStartedAt(), refund.getFailureCode(),
                refund.canRetry(LocalDateTime.now(ZoneOffset.UTC)));
        }
    }
}
