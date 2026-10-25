package com.kisia.staybooking.mileage;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import com.kisia.staybooking.booking.BookingEntity;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * docs/architecture/DATABASE.md의 mileage_history. 잔액(users.mileage_balance)과 같은 트랜잭션에서 남긴다.
 * 이미 지나간 적립·사용 기록은 고치지 않으므로 수정 시각을 두지 않는다.
 */
@Entity
@Table(name = "mileage_history", indexes = {
    @Index(name = "idx_mileage_history_user_created", columnList = "user_id,created_at"),
    @Index(name = "idx_mileage_history_booking_type", columnList = "booking_id,type")
})
public class MileageHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mileage_history_id")
    private Long mileageHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mileage_history_user"))
    private UserEntity user;

    // 관리자 수동 조정처럼 예약과 무관한 기록도 있어 비워 둘 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", foreignKey = @ForeignKey(name = "fk_mileage_history_booking"))
    private BookingEntity booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MileageType type;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MileageHistoryEntity() {
    }

    MileageHistoryEntity(UserEntity user, BookingEntity booking, MileageType type, long amount,
                         long balanceAfter, String description) {
        this.user = Objects.requireNonNull(user);
        this.booking = booking;
        this.type = Objects.requireNonNull(type);
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getMileageHistoryId() { return mileageHistoryId; }
    public UserEntity getUser() { return user; }
    public BookingEntity getBooking() { return booking; }
    public MileageType getType() { return type; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
