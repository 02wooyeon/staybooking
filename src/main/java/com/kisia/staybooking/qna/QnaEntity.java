package com.kisia.staybooking.qna;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "qna", indexes = @Index(name = "idx_qna_secret_deleted_created", columnList = "is_secret,is_deleted,created_at"))
public class QnaEntity extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qna_id") private Long qnaId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_qna_user"))
    private UserEntity user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", foreignKey = @ForeignKey(name = "fk_qna_booking"))
    private BookingEntity booking;
    @Column(nullable = false, length = 20) private String category;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "is_secret", nullable = false) private boolean secret;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "deleted_reason", length = 255) private String deletedReason;

    protected QnaEntity() { }
    public QnaEntity(UserEntity user, BookingEntity booking, String category, String title,
                     String content, boolean secret) {
        this.user = user;
        this.booking = booking;
        this.category = category;
        this.title = title;
        this.content = content;
        this.secret = secret;
    }
    public Long getQnaId() { return qnaId; }
    public UserEntity getUser() { return user; }
    public BookingEntity getBooking() { return booking; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isSecret() { return secret; }
    public boolean isDeleted() { return deleted; }
    public String getDeletedReason() { return deletedReason; }

    public void update(BookingEntity booking, String category, String title, String content, boolean secret) {
        if (deleted) throw new IllegalStateException("Hidden Q&A cannot be edited");
        this.booking = booking;
        this.category = category;
        this.title = title;
        this.content = content;
        this.secret = secret;
    }

    public void hide(String reason) {
        if (deleted) throw new IllegalStateException("Q&A is already hidden");
        deleted = true;
        deletedReason = reason;
    }
}
