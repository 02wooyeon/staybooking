package com.kisia.staybooking.review;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

// docs/architecture/DATABASE.md의 reviews, review_files. 예약 1건당 후기 1개이며 삭제는 soft delete.
@Entity
@Table(name = "reviews", indexes = {
    @Index(name = "idx_reviews_accommodation_deleted_created", columnList = "accommodation_id,is_deleted,created_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_reviews_booking", columnNames = "booking_id")
})
@Check(constraints = "rating BETWEEN 1 AND 5")
public class ReviewEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reviews_user"))
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reviews_booking"))
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_reviews_accommodation"))
    private AccommodationEntity accommodation;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_reason", length = 255)
    private String deletedReason;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "review_files",
        joinColumns = @JoinColumn(name = "review_id", foreignKey = @ForeignKey(name = "fk_review_files_review")),
        inverseJoinColumns = @JoinColumn(name = "file_id", foreignKey = @ForeignKey(name = "fk_review_files_file")))
    private Set<FileEntity> files = new LinkedHashSet<>();

    protected ReviewEntity() {
    }

    public ReviewEntity(UserEntity user, BookingEntity booking, int rating, String content, Set<FileEntity> files) {
        this.user = Objects.requireNonNull(user);
        this.booking = Objects.requireNonNull(booking);
        this.accommodation = booking.getRoom().getAccommodation();
        update(rating, content, files);
    }

    public Long getReviewId() { return reviewId; }
    public UserEntity getUser() { return user; }
    public BookingEntity getBooking() { return booking; }
    public AccommodationEntity getAccommodation() { return accommodation; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public boolean isDeleted() { return deleted; }
    public String getDeletedReason() { return deletedReason; }
    public Set<FileEntity> getFiles() { return files; }

    public void update(int rating, String content, Set<FileEntity> files) {
        if (deleted) throw new IllegalStateException("Deleted review cannot be edited");
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be 1 to 5");
        this.rating = rating;
        this.content = Objects.requireNonNull(content);
        this.files.clear();
        this.files.addAll(files);
    }

    public void hide(String reason) {
        if (deleted) throw new IllegalStateException("Review is already deleted");
        deleted = true;
        deletedReason = Objects.requireNonNull(reason);
    }
}
