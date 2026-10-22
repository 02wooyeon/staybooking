package com.kisia.staybooking.refund;
import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.user.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;import jakarta.validation.constraints.NotNull;import org.springframework.web.bind.annotation.*;import java.time.*;import java.util.*;
@RestController @RequestMapping("/api/v1/admin/refunds") public class AdminRefundController{
 private final RefundRepository refunds; private final UserRepository users; public AdminRefundController(RefundRepository refunds,UserRepository users){this.refunds=refunds;this.users=users;}
 @GetMapping public List<Summary> list(){return refunds.findAllByOrderByCreatedAtDesc().stream().map(Summary::from).toList();}
 @Transactional
 @PatchMapping("/{id}") public Summary process(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable Long id,@Valid @RequestBody ProcessRequest r){var refund=refunds.findById(id).orElseThrow(()->new IllegalArgumentException("Refund not found"));var admin=users.findById(me.userId()).orElseThrow();refund.process(admin,r.status(),LocalDateTime.now(ZoneOffset.UTC));return Summary.from(refund);}
 public record ProcessRequest(@NotNull RefundStatus status){}
 public record Summary(Long refundId,Long bookingId,long amount,RefundStatus status,String reason,LocalDateTime processedAt){static Summary from(RefundEntity r){return new Summary(r.getRefundId(),r.getBooking().getBookingId(),r.getAmount(),r.getStatus(),r.getReason(),r.getProcessedAt());}}
}
