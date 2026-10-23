package com.kisia.staybooking.payment;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;
    private final TossPaymentConfirmationService tossConfirmations;
    private final TossPaymentGateway tossGateway;
    private final PaymentHistoryService history;
    private final boolean demoEnabled;

    public PaymentController(PaymentService payments, TossPaymentConfirmationService tossConfirmations,
                             TossPaymentGateway tossGateway, PaymentHistoryService history,
                             @Value("${staybooking.payment.demo-enabled:false}") boolean demoEnabled) {
        this.payments = payments;
        this.tossConfirmations = tossConfirmations;
        this.tossGateway = tossGateway;
        this.history = history;
        this.demoEnabled = demoEnabled && !tossGateway.isConfigured();
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
        requireDemoEnabled();
        return PaymentResponse.from(payments.complete(me.userId(), request.orderId(), request.paymentKey(),
            request.amount()));
    }

    @GetMapping("/capabilities")
    public PaymentCapabilities capabilities() {
        return new PaymentCapabilities(demoEnabled, tossGateway.isConfigured(), tossGateway.clientKey());
    }

    @GetMapping("/me")
    public List<PaymentHistoryService.PaymentHistory> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return history.mine(me.userId());
    }

    @GetMapping("/me/search")
    public PageResponse<PaymentHistoryService.PaymentHistory> mineSearch(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @ModelAttribute PaymentSearchRequest request) {
        return history.mineSearch(me.userId(), request);
    }

    @PostMapping("/toss/confirm")
    public PaymentResponse confirmToss(@AuthenticationPrincipal AuthenticatedUser me,
                                       @Valid @RequestBody PaymentCompleteRequest request) {
        return PaymentResponse.from(tossConfirmations.confirm(me.userId(), request.orderId(),
            request.paymentKey(), request.amount()));
    }

    @PostMapping("/demo-complete")
    public PaymentResponse completeDemo(@AuthenticationPrincipal AuthenticatedUser me,
                                        @Valid @RequestBody DemoCompleteRequest request) {
        requireDemoEnabled();
        return PaymentResponse.from(payments.completeDemo(me.userId(), request.bookingId()));
    }

    private void requireDemoEnabled() {
        if (!demoEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                "결제 서비스가 아직 연결되지 않았습니다.");
        }
    }

    public record PaymentCapabilities(boolean demoEnabled, boolean tossEnabled, String tossClientKey) { }

    public record DemoCompleteRequest(@NotNull @Positive Long bookingId) { }

    public record PaymentPrepareRequest(@NotNull @Positive Long bookingId,
                                        @NotBlank @Size(max = 30) String paymentMethod) { }

    public record PaymentCompleteRequest(@NotBlank @Size(max = 100) String orderId,
                                         @NotBlank @Size(max = 200) String paymentKey,
                                         @Positive long amount) { }

    public record PaymentResponse(Long paymentId, Long bookingId, String orderId, long amount, String status) {
        static PaymentResponse from(PaymentEntity payment) {
            return new PaymentResponse(payment.getPaymentId(), payment.getBooking().getBookingId(),
                payment.getOrderId(), payment.getPaidAmount(), payment.getStatus().name());
        }
    }
}
