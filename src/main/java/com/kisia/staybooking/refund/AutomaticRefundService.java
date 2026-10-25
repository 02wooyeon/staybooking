package com.kisia.staybooking.refund;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.payment.TossPaymentGateway;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** 취소가 커밋된 뒤 PG 취소를 실행한다. PG 호출은 DB 트랜잭션 밖에서 수행한다. */
@Service
public class AutomaticRefundService {
    private static final Logger log = LoggerFactory.getLogger(AutomaticRefundService.class);
    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final UserRepository users;
    private final TossPaymentGateway gateway;
    private final TransactionTemplate transaction;

    public AutomaticRefundService(RefundRepository refunds, PaymentRepository payments,
                                  UserRepository users, TossPaymentGateway gateway,
                                  PlatformTransactionManager manager) {
        this.refunds = refunds;
        this.payments = payments;
        this.users = users;
        this.gateway = gateway;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundCreated(RefundRequestService.RefundCreated event) {
        try {
            settle(event.refundId());
        } catch (RuntimeException e) {
            // 예약 취소는 이미 커밋됐다. 오류를 숨기지 않고 환불 상태와 로그로 추적한다.
            log.error("Automatic refund could not start for refund {}", event.refundId(), e);
        }
    }

    public RefundEntity retry(Long adminId, Long refundId) {
        var admin = users.findById(adminId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다."));
        if (admin.getRole() != UserRole.ROLE_ADMIN || admin.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
        }
        settle(refundId);
        RefundEntity refund = refunds.findById(refundId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "환불 건을 찾을 수 없습니다."));
        if (refund.getStatus() != RefundStatus.REFUNDED) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_RETRY_FAILED",
                "환불 처리 결과를 확인하지 못했습니다. 환불 상태와 결제대행사 내역을 확인해 주세요.");
        }
        return refund;
    }

    void recover(Long refundId) {
        try {
            settle(refundId);
        } catch (ApiException e) {
            // 조회 후 다른 요청이 먼저 처리한 건은 다시 호출하지 않는다.
            if (!"REFUND_NOT_RETRYABLE".equals(e.getCode())) {
                log.error("Refund recovery could not start for refund {}", refundId, e);
            }
        } catch (RuntimeException e) {
            log.error("Refund recovery failed for refund {}", refundId, e);
        }
    }

    private void settle(Long refundId) {
        Work work;
        try {
            work = transaction.execute(status -> {
                RefundEntity refund = refunds.findByIdForUpdate(refundId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "환불 건을 찾을 수 없습니다."));
                try {
                    refund.start(LocalDateTime.now(ZoneOffset.UTC));
                } catch (IllegalStateException e) {
                    throw new ApiException(HttpStatus.CONFLICT, "REFUND_NOT_RETRYABLE", "재시도할 수 없는 환불 상태입니다.");
                }
                var paid = payments.findByBookingBookingIdAndStatus(
                    refund.getBooking().getBookingId(), PaymentStatus.PAID);
                if (paid.size() != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_REFUNDABLE", "환불 가능한 결제를 확인할 수 없습니다.");
                }
                PaymentEntity payment = paid.getFirst();
                if (refund.getAmount() < 0 || refund.getAmount() > payment.getPaidAmount()
                        || payment.getPaymentKey() == null || payment.getPaymentKey().isBlank()) {
                    throw new ApiException(HttpStatus.CONFLICT, "REFUND_AMOUNT_MISMATCH", "환불 금액이나 결제 키가 올바르지 않습니다.");
                }
                return new Work(refundId, refund.getAmount(), refund.getReason(), payment.getPaymentKey(),
                    payment.getOrderId(), payment.getPaidAmount(), "DEMO".equals(payment.getPaymentMethod()));
            });
        } catch (ApiException e) {
            if (!"REFUND_NOT_RETRYABLE".equals(e.getCode())) markFailed(refundId, e.getCode());
            throw e;
        }
        if (work == null) throw new IllegalStateException("Refund transaction returned no work");
        try {
            if (work.amount() > 0 && !work.demo()) {
                String reason = work.reason() == null || work.reason().isBlank() ? "예약 취소" : work.reason();
                var result = gateway.cancel(work.paymentKey(),
                    reason.length() > 200 ? reason.substring(0, 200) : reason,
                    work.amount(), work.refundId());
                String expected = work.amount() == work.paidAmount() ? "CANCELED" : "PARTIAL_CANCELED";
                if (!work.paymentKey().equals(result.paymentKey()) || !work.orderId().equals(result.orderId())
                        || result.totalAmount() != work.paidAmount()
                        || result.balanceAmount() != work.paidAmount() - work.amount()
                        || !expected.equals(result.status()) || result.cancels() == null
                        || result.cancels().stream().noneMatch(c -> c.cancelAmount() == work.amount())) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_CANCEL_MISMATCH",
                        "토스 결제 취소 결과가 환불 요청과 일치하지 않습니다.");
                }
            }
            transaction.executeWithoutResult(status -> {
                RefundEntity refund = refunds.findByIdForUpdate(refundId).orElseThrow();
                PaymentEntity payment = payments.findByBookingBookingIdAndStatus(
                    refund.getBooking().getBookingId(), PaymentStatus.PAID).stream().findFirst().orElseThrow();
                payment.refund(refund.getAmount());
                refund.refunded(LocalDateTime.now(ZoneOffset.UTC));
            });
        } catch (RuntimeException e) {
            String code = e instanceof ApiException api ? api.getCode() : "REFUND_PROCESSING_FAILED";
            markFailed(refundId, code);
            log.error("Automatic refund failed for refund {}", refundId, e);
        }
    }

    private void markFailed(Long refundId, String code) {
        try {
            transaction.executeWithoutResult(status -> {
                RefundEntity refund = refunds.findByIdForUpdate(refundId).orElseThrow();
                if (refund.getStatus() == RefundStatus.PROCESSING || refund.getStatus() == RefundStatus.REQUESTED) {
                    refund.failed(code);
                }
            });
        } catch (RuntimeException markFailure) {
            log.error("Could not record failed refund {}", refundId, markFailure);
        }
    }

    private record Work(Long refundId, long amount, String reason, String paymentKey,
                        String orderId, long paidAmount, boolean demo) { }
}
