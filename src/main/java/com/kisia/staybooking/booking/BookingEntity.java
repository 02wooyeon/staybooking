package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.room.RoomEntity;
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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_room_dates", columnList = "room_id,status,check_in_date,check_out_date"),
    @Index(name = "idx_bookings_user_created", columnList = "user_id,created_at")
})
@Check(constraints = "check_out_date > check_in_date AND guests >= 1 "
    + "AND total_amount >= 0 AND used_mileage >= 0 AND used_mileage <= total_amount")
public class BookingEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long bookingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_bookings_user"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_bookings_room"))
    private RoomEntity room;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "guests", nullable = false)
    private int guests;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'PAYMENT_PENDING'")
    private BookingStatus status = BookingStatus.PAYMENT_PENDING;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "used_mileage", nullable = false)
    @ColumnDefault("0")
    private long usedMileage;

    @Column(name = "payment_expires_at", nullable = false)
    private LocalDateTime paymentExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", foreignKey = @ForeignKey(name = "fk_bookings_approved_by"))
    private UserEntity approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by", foreignKey = @ForeignKey(name = "fk_bookings_completed_by"))
    private UserEntity completedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancel_source", length = 20)
    private CancellationSource cancelSource;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "cancel_reason_type", length = 30)
    private CancellationReasonType cancelReasonType;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", foreignKey = @ForeignKey(name = "fk_bookings_cancelled_by"))
    private UserEntity cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    protected BookingEntity() {
    }

    public BookingEntity(UserEntity user, RoomEntity room, LocalDate checkInDate, LocalDate checkOutDate,
                         int guests, long totalAmount, long usedMileage, LocalDateTime paymentExpiresAt) {
        this.user = Objects.requireNonNull(user);
        this.room = Objects.requireNonNull(room);
        this.checkInDate = Objects.requireNonNull(checkInDate);
        this.checkOutDate = Objects.requireNonNull(checkOutDate);
        this.paymentExpiresAt = Objects.requireNonNull(paymentExpiresAt);
        if (!checkOutDate.isAfter(checkInDate) || guests < 1 || totalAmount < 0
                || usedMileage < 0 || usedMileage > totalAmount) {
            throw new IllegalArgumentException("Booking dates, guests, or amounts are invalid");
        }
        this.guests = guests;
        this.totalAmount = totalAmount;
        this.usedMileage = usedMileage;
    }

    public Long getBookingId() { return bookingId; }
    public UserEntity getUser() { return user; }
    public RoomEntity getRoom() { return room; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public int getGuests() { return guests; }
    public BookingStatus getStatus() { return status; }
    public long getTotalAmount() { return totalAmount; }
    public long getUsedMileage() { return usedMileage; }
    public LocalDateTime getPaymentExpiresAt() { return paymentExpiresAt; }
    public UserEntity getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public UserEntity getCompletedBy() { return completedBy; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public CancellationSource getCancelSource() { return cancelSource; }
    public CancellationReasonType getCancelReasonType() { return cancelReasonType; }
    public String getCancelReason() { return cancelReason; }
    public UserEntity getCancelledBy() { return cancelledBy; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }

    public void accept(UserEntity actor, LocalDateTime nowUtc) {
        if (status != BookingStatus.PENDING) {
            throw new IllegalStateException("Only paid bookings awaiting approval can be accepted");
        }
        approvedBy = Objects.requireNonNull(actor);
        approvedAt = Objects.requireNonNull(nowUtc);
        completedBy = actor;
        completedAt = nowUtc;
        status = BookingStatus.COMPLETED;
    }
}
