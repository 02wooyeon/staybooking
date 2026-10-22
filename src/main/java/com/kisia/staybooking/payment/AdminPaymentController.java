package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentRepository payments;
    public AdminPaymentController(PaymentRepository payments) { this.payments = payments; }
    @GetMapping
    public List<PaymentSummary> list() { return payments.findAllByOrderByCreatedAtDesc().stream().map(PaymentSummary::from).toList(); }
    public record PaymentSummary(Long paymentId, Long bookingId, String orderId, String paymentMethod,
                                 long paidAmount, String status, LocalDateTime paidAt) {
        static PaymentSummary from(PaymentEntity payment) { return new PaymentSummary(payment.getPaymentId(), payment.getBooking().getBookingId(), payment.getOrderId(), payment.getPaymentMethod(), payment.getPaidAmount(), payment.getStatus().name(), payment.getPaidAt()); }
    }
}
