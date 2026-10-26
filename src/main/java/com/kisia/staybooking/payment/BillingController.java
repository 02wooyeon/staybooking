package com.kisia.staybooking.payment;

import java.util.List;

import com.kisia.staybooking.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// 엑셀 API 명세서 PAY-04 (POST /api/v1/payments/billing-keys), PAY-05 (POST .../billing-keys/{id}/pay)
// 경로의 {id}는 빌링키가 아니라 우리 DB의 등록 카드 번호다. 빌링키는 응답에 나가지 않는다.
@RestController
@RequestMapping("/api/v1/payments/billing-keys")
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    @PostMapping("/customer")
    public CustomerKeyResponse customerKey(@AuthenticationPrincipal AuthenticatedUser me) {
        return new CustomerKeyResponse(billing.customerKey(me.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillingService.CardResponse register(@AuthenticationPrincipal AuthenticatedUser me,
                                                @Valid @RequestBody RegisterRequest request) {
        return billing.register(me.userId(), request.customerKey(), request.authKey());
    }

    @GetMapping
    public List<BillingService.CardResponse> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return billing.mine(me.userId());
    }

    @DeleteMapping("/{billingCardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable @Positive Long billingCardId) {
        billing.delete(me.userId(), billingCardId);
    }

    @PostMapping("/{billingCardId}/pay")
    public PaymentController.PaymentResponse pay(@AuthenticationPrincipal AuthenticatedUser me,
                                                 @PathVariable @Positive Long billingCardId,
                                                 @Valid @RequestBody PayRequest request) {
        return PaymentController.PaymentResponse.from(
            billing.pay(me.userId(), billingCardId, request.bookingId(), request.password()));
    }

    public record CustomerKeyResponse(String customerKey) { }

    public record RegisterRequest(@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_\\-=.@]+$") String customerKey,
                                  @NotBlank @Size(max = 300) @Pattern(regexp = "^[A-Za-z0-9_\\-=.]+$") String authKey) { }

    public record PayRequest(@NotNull @Positive Long bookingId, @NotBlank @Size(max = 100) String password) { }
}
