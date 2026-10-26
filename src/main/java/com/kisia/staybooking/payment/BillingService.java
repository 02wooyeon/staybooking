package com.kisia.staybooking.payment;

import java.util.List;
import java.util.UUID;

import com.kisia.staybooking.auth.AttemptLimiter;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 등록(PAYMENT_004)과 등록 카드 결제(PAYMENT_005).
 *  - 카드 정보는 토스 카드 등록 창에만 입력하고, 서버는 authKey로 빌링키를 발급받아 암호화해 저장한다.
 *  - 등록 카드로 결제할 때는 계정 비밀번호를 다시 확인한다. 토큰을 훔친 사람이 결제하지 못하게 한다.
 *  - 결제 기록은 일반 토스 결제(paymentMethod=TOSS)와 같아서 환불·대사가 그대로 동작한다.
 */
@Service
public class BillingService {
    private static final int MAX_PASSWORD_FAILURES = 5;

    private final BillingCardRepository cards;
    private final BillingCustomerRepository customers;
    private final BillingKeyCipher cipher;
    private final TossPaymentGateway gateway;
    private final PaymentService payments;
    private final TossPaymentConfirmationService confirmations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter limiter;

    public BillingService(BillingCardRepository cards, BillingCustomerRepository customers, BillingKeyCipher cipher,
                          TossPaymentGateway gateway, PaymentService payments,
                          TossPaymentConfirmationService confirmations, UserRepository users,
                          PasswordEncoder passwordEncoder, AttemptLimiter limiter) {
        this.cards = cards;
        this.customers = customers;
        this.cipher = cipher;
        this.gateway = gateway;
        this.payments = payments;
        this.confirmations = confirmations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
    }

    /** 카드 등록 창을 열 때 쓰는 내 customerKey. 없으면 무작위로 만든다. */
    @Transactional
    public String customerKey(Long userId) {
        requireBilling();
        activeUser(userId);
        return customers.findById(userId)
            .orElseGet(() -> customers.save(new BillingCustomerEntity(userId,
                "CUST-" + UUID.randomUUID().toString().replace("-", ""))))
            .getCustomerKey();
    }

    /** 카드 등록 창이 돌려준 authKey로 빌링키를 발급하고 저장한다. 이미 등록한 카드가 있으면 바꾼다. */
    public CardResponse register(Long userId, String customerKey, String authKey) {
        requireBilling();
        UserEntity user = activeUser(userId);
        // 다른 회원의 customerKey로 발급받은 카드를 내 계정에 붙이지 못하게 한다.
        String mine = customers.findById(userId).map(BillingCustomerEntity::getCustomerKey).orElse(null);
        if (mine == null || !mine.equals(customerKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_CUSTOMER_MISMATCH", "카드 등록 정보가 올바르지 않습니다.");
        }
        TossPaymentGateway.BillingIssue issued = gateway.issueBillingKey(authKey, customerKey);
        if (!customerKey.equals(issued.customerKey())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "BILLING_REGISTER_FAILED", "카드를 등록하지 못했습니다.");
        }
        cards.findByUserUserId(userId).ifPresent(old -> {
            cards.delete(old);
            cards.flush();
        });
        BillingCardEntity saved = cards.saveAndFlush(new BillingCardEntity(user, customerKey,
            cipher.encrypt(issued.billingKey()), shorten(issued.cardCompany()), shorten(issued.cardNumber())));
        return CardResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> mine(Long userId) {
        return cards.findByUserUserId(userId).map(CardResponse::from).stream().toList();
    }

    @Transactional
    public void delete(Long userId, Long billingCardId) {
        cards.delete(ownedCard(userId, billingCardId));
    }

    /** 등록 카드로 예약 결제. 비밀번호를 5번 틀리면 15분 동안 이 기능을 막는다. */
    public PaymentEntity pay(Long userId, Long billingCardId, Long bookingId, String password) {
        requireBilling();
        String key = "billing-pay:" + userId;
        if (limiter.isLocked(key)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "비밀번호를 여러 번 틀려 잠시 등록 카드 결제를 쓸 수 없습니다. 15분 후 다시 시도해 주세요.");
        }
        UserEntity user = activeUser(userId);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            limiter.recordFailure(key, MAX_PASSWORD_FAILURES);
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호가 올바르지 않습니다.",
                List.of("password"));
        }
        limiter.reset(key);
        BillingCardEntity card = ownedCard(userId, billingCardId);
        PaymentEntity prepared = payments.prepare(userId, bookingId, "TOSS");
        return confirmations.payWithBillingKey(userId, prepared.getOrderId(),
            cipher.decrypt(card.getBillingKeyEncrypted()), card.getCustomerKey(), "스테이북 예약 " + bookingId);
    }

    private BillingCardEntity ownedCard(Long userId, Long billingCardId) {
        return cards.findByBillingCardIdAndUserUserId(billingCardId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "등록된 카드를 찾을 수 없습니다."));
    }

    private UserEntity activeUser(Long userId) {
        return users.findById(userId)
            .filter(u -> u.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
    }

    private void requireBilling() {
        if (!gateway.billingEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "BILLING_NOT_CONFIGURED",
                "카드 등록 결제를 사용할 수 없습니다.");
        }
    }

    private static String shorten(String value) {
        return value == null ? null : value.length() > 30 ? value.substring(0, 30) : value;
    }

    // 빌링키·customerKey는 화면에 내보내지 않는다.
    public record CardResponse(Long billingCardId, String cardCompany, String cardNumber) {
        static CardResponse from(BillingCardEntity card) {
            return new CardResponse(card.getBillingCardId(), card.getCardCompany(), card.getCardNumberMasked());
        }
    }
}
