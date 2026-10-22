package com.kisia.staybooking.review;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReviewEntity r where r.reviewId = :reviewId")
    Optional<ReviewEntity> findByIdForUpdate(@Param("reviewId") Long reviewId);

    Page<ReviewEntity> findByAccommodationAccommodationIdAndDeletedFalse(Long accommodationId, Pageable pageable);

    long countByAccommodationAccommodationIdAndDeletedFalse(Long accommodationId);

    @Query("""
        select avg(r.rating) from ReviewEntity r
        where r.accommodation.accommodationId = :accommodationId and r.deleted = false
        """)
    Double averageRating(@Param("accommodationId") Long accommodationId);

    List<ReviewEntity> findByUserUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);

    Page<ReviewEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByBookingBookingId(Long bookingId);

    boolean existsByFilesFileId(Long fileId);

    boolean existsByFilesFileIdAndDeletedFalse(Long fileId);

    @Query("""
        select count(r) > 0 from ReviewEntity r join r.files f
        where f.fileId = :fileId and r.reviewId <> :reviewId
        """)
    boolean fileUsedByOtherReview(@Param("fileId") Long fileId, @Param("reviewId") Long reviewId);

    // 그 숙소에서 이용을 마쳤고(체크아웃 지남) 아직 후기를 쓰지 않은 내 예약. 오래된 것부터 쓰게 한다.
    @Query("""
        select b from BookingEntity b
        where b.user.userId = :userId
          and b.room.accommodation.accommodationId = :accommodationId
          and b.status = :status
          and b.checkOutDate <= :today
          and not exists (select r from ReviewEntity r where r.booking = b)
        order by b.checkOutDate asc
        """)
    List<BookingEntity> findReviewableBookings(@Param("userId") Long userId,
                                               @Param("accommodationId") Long accommodationId,
                                               @Param("status") BookingStatus status,
                                               @Param("today") LocalDate today);
}
