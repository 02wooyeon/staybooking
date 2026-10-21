package com.kisia.staybooking.payment;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse prepare(@AuthenticationPrincipal AuthenticatedUser me,
                                   @Valid @RequestBody PaymentPrepareRequest request) {
        return PaymentResponse.from(payments.prepare(me.userId(), request.bookingId(), request.paymentMethod()));
    }

    @PostMapping("/complete")
    public PaymentResponse complete(@AuthenticationPrincipal AuthenticatedUser me,
                                    @Valid @RequestBody PaymentCompleteRequest request) {
        return PaymentResponse.from(payments.complete(me.userId(), request.orderId(), request.paymentKey(),
            request.amount()));
    }

    public record PaymentPrepareRequest(@NotNull @Positive Long bookingId,
                                        @NotBlank @Size(max = 30) String paymentMethod) { }

    public record PaymentCompleteRequest(@NotBlank @Size(max = 100) String orderId,
                                         @NotBlank @Size(max = 255) String paymentKey,
                                         @Positive long amount) { }

    public record PaymentResponse(Long paymentId, Long bookingId, String orderId, long amount, String status) {
        static PaymentResponse from(PaymentEntity payment) {
            return new PaymentResponse(payment.getPaymentId(), payment.getBooking().getBookingId(),
                payment.getOrderId(), payment.getPaidAmount(), payment.getStatus().name());
        }
    }
}
