package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.util.List;

import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentRepository payments;
    private final PaymentHistoryService history;
    private final PaymentReconciliationService reconciliation;
    public AdminPaymentController(PaymentRepository payments, PaymentHistoryService history,
                                  PaymentReconciliationService reconciliation) {
        this.payments = payments;
        this.history = history;
        this.reconciliation = reconciliation;
    }
    @GetMapping
    public List<PaymentSummary> list() { return payments.findAllByOrderByCreatedAtDesc().stream().map(PaymentSummary::from).toList(); }
    @GetMapping("/search")
    public PageResponse<PaymentHistoryService.PaymentHistory> search(@Valid @ModelAttribute AdminPaymentSearchRequest request) {
        return history.adminSearch(request);
    }
    @GetMapping("/{paymentId}")
    public PaymentHistoryService.PaymentHistory detail(@PathVariable @Positive Long paymentId) {
        return history.adminDetail(paymentId);
    }
    @GetMapping("/{paymentId}/reconciliation")
    public PaymentReconciliationService.Report reconciliation(@PathVariable @Positive Long paymentId) {
        return reconciliation.check(paymentId);
    }
    public record PaymentSummary(Long paymentId, Long bookingId, String orderId, String paymentMethod,
                                 long paidAmount, String status, LocalDateTime paidAt) {
        static PaymentSummary from(PaymentEntity payment) { return new PaymentSummary(payment.getPaymentId(), payment.getBooking().getBookingId(), payment.getOrderId(), payment.getPaymentMethod(), payment.getPaidAmount(), payment.getStatus().name(), payment.getPaidAt()); }
    }
}
