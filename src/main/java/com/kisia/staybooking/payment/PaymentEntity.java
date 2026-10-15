package com.kisia.staybooking.payment;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_booking_status", columnList = "booking_id,status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"),
    @UniqueConstraint(name = "uk_payments_payment_key", columnNames = "payment_key")
})
@Check(constraints = "paid_amount >= 0")
public class PaymentEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_payments_booking"))
    private BookingEntity booking;

    @Column(name = "payment_key", length = 255, unique = true)
    private String paymentKey;

    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.READY;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    protected PaymentEntity() {
    }

    public PaymentEntity(BookingEntity booking, String orderId, String paymentMethod, long paidAmount) {
        this.booking = Objects.requireNonNull(booking);
        this.orderId = Objects.requireNonNull(orderId);
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        if (paidAmount < 0) throw new IllegalArgumentException("Paid amount must be nonnegative");
        this.paidAmount = paidAmount;
    }

    public Long getPaymentId() { return paymentId; }
    public BookingEntity getBooking() { return booking; }
    public String getPaymentKey() { return paymentKey; }
    public String getOrderId() { return orderId; }
    public String getPaymentMethod() { return paymentMethod; }
    public long getPaidAmount() { return paidAmount; }
    public PaymentStatus getStatus() { return status; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
