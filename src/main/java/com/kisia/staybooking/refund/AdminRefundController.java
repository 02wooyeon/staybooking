package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {
    private final RefundRepository refunds;
    private final AdminRefundService service;

    public AdminRefundController(RefundRepository refunds, AdminRefundService service) {
        this.refunds = refunds;
        this.service = service;
    }

    @GetMapping
    public List<Summary> list() {
        return refunds.findAllByOrderByCreatedAtDesc().stream().map(Summary::from).toList();
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

    @PatchMapping("/{id}")
    public Summary process(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                           @Valid @RequestBody ProcessRequest request) {
        return Summary.from(service.process(me.userId(), id, request.status()));
    }

    public record ProcessRequest(@NotNull RefundStatus status) { }

    public record Summary(Long refundId, Long bookingId, long amount, RefundStatus status,
                          String reason, Long processedBy, LocalDateTime processedAt) {
        static Summary from(RefundEntity refund) {
            return new Summary(refund.getRefundId(), refund.getBooking().getBookingId(),
                refund.getAmount(), refund.getStatus(), refund.getReason(),
                refund.getProcessedBy() == null ? null : refund.getProcessedBy().getUserId(),
                refund.getProcessedAt());
        }
    }
}
