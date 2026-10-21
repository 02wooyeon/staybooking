package com.kisia.staybooking.cancellation;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.user.UserEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "booking_cancellation_requests", indexes = {
    @Index(name = "idx_cancellation_requests_booking_status", columnList = "booking_id,status")
})
public class CancellationRequestEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancellation_request_id")
    private Long cancellationRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_cancellation_requests_booking"))
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false,
        foreignKey = @ForeignKey(name = "fk_cancellation_requests_requested_by"))
    private UserEntity requestedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_type", nullable = false, length = 30)
    private CancellationReasonType reasonType;

    @Column(name = "reason_detail", length = 255)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private CancellationRequestStatus status = CancellationRequestStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by", foreignKey = @ForeignKey(name = "fk_cancellation_requests_decided_by"))
    private UserEntity decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    protected CancellationRequestEntity() {
    }

    public CancellationRequestEntity(BookingEntity booking, UserEntity requestedBy,
                                     CancellationReasonType reasonType, String reasonDetail) {
        this.booking = Objects.requireNonNull(booking);
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.reasonType = Objects.requireNonNull(reasonType);
        if (reasonDetail != null && reasonDetail.length() > 255) {
            throw new IllegalArgumentException("Reason detail is too long");
        }
        this.reasonDetail = reasonDetail;
    }

    public Long getCancellationRequestId() { return cancellationRequestId; }
    public BookingEntity getBooking() { return booking; }
    public UserEntity getRequestedBy() { return requestedBy; }
    public CancellationReasonType getReasonType() { return reasonType; }
    public String getReasonDetail() { return reasonDetail; }
    public CancellationRequestStatus getStatus() { return status; }
    public UserEntity getDecidedBy() { return decidedBy; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getDecisionReason() { return decisionReason; }

    public void reject(UserEntity actor, LocalDateTime decidedAt, String reason) {
        if (status != CancellationRequestStatus.REQUESTED) {
            throw new IllegalStateException("Cancellation request has already been decided");
        }
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new IllegalArgumentException("A short rejection reason is required");
        }
        this.decidedBy = Objects.requireNonNull(actor);
        this.decidedAt = Objects.requireNonNull(decidedAt);
        this.decisionReason = reason;
        this.status = CancellationRequestStatus.REJECTED;
    }

    public void approve(UserEntity actor, LocalDateTime decidedAt, String reason) {
        if (status != CancellationRequestStatus.REQUESTED) {
            throw new IllegalStateException("Cancellation request has already been decided");
        }
        if (reason != null && reason.length() > 255) {
            throw new IllegalArgumentException("Decision reason is too long");
        }
        this.decidedBy = Objects.requireNonNull(actor);
        this.decidedAt = Objects.requireNonNull(decidedAt);
        this.decisionReason = reason;
        this.status = CancellationRequestStatus.APPROVED;
    }
}
