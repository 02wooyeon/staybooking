package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
