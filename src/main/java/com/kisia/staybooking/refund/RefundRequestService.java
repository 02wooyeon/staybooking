package com.kisia.staybooking.refund;

import java.time.ZoneId;
import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

/** 예약 취소와 같은 트랜잭션에서 환불 검토 건을 만든다. */
@Service
public class RefundRequestService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final ApplicationEventPublisher events;

    public RefundRequestService(PaymentRepository payments, RefundRepository refunds,
                                ApplicationEventPublisher events) {
        this.payments = payments;
        this.refunds = refunds;
        this.events = events;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BookingEntity booking) {
        if (refunds.existsByBookingBookingId(booking.getBookingId())) {
            throw new IllegalStateException("Refund request already exists");
        }
        var paid = payments.findByBookingBookingIdAndStatus(booking.getBookingId(), PaymentStatus.PAID);
        if (paid.size() != 1) throw new IllegalStateException("Expected exactly one paid payment");
        PaymentEntity payment = paid.getFirst();
        LocalDate cancellationDate = booking.getCancelledAt().atZone(ZoneId.of("UTC"))
            .withZoneSameInstant(KOREA).toLocalDate();
        RefundQuote quote = RefundPolicy.quote(booking.getCancelReasonType(), booking.getCheckInDate(),
            cancellationDate, payment.getPaidAmount(), booking.getUsedMileage());
        RefundEntity refund = refunds.save(new RefundEntity(booking, quote.refundAmount(), booking.getCancelReason()));
        events.publishEvent(new RefundCreated(refund.getRefundId()));
    }

    public record RefundCreated(Long refundId) { }
}
