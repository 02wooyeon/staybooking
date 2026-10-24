package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    long countByStatus(BookingStatus status);
    @Query("select b.createdAt from BookingEntity b "
        + "where b.createdAt >= :fromUtc and b.createdAt < :toUtc")
    List<LocalDateTime> findCreatedTimesInRange(@Param("fromUtc") LocalDateTime fromUtc,
                                                 @Param("toUtc") LocalDateTime toUtc);
    @Query("select b.approvedAt from BookingEntity b "
        + "where b.approvedAt >= :fromUtc and b.approvedAt < :toUtc")
    List<LocalDateTime> findApprovedTimesInRange(@Param("fromUtc") LocalDateTime fromUtc,
                                                  @Param("toUtc") LocalDateTime toUtc);
    @Query("select b.cancelledAt from BookingEntity b "
        + "where b.cancelledAt >= :fromUtc and b.cancelledAt < :toUtc")
    List<LocalDateTime> findCancelledTimesInRange(@Param("fromUtc") LocalDateTime fromUtc,
                                                   @Param("toUtc") LocalDateTime toUtc);
    List<BookingEntity> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"room", "room.accommodation"})
    List<BookingEntity> findByStatusOrderByCreatedAtAsc(BookingStatus status);

    @EntityGraph(attributePaths = {"room", "room.accommodation"})
    Page<BookingEntity> findAllByOrderByCreatedAtDescBookingIdDesc(Pageable pageable);
    @EntityGraph(attributePaths = {"room", "room.accommodation"})
    Page<BookingEntity> findByStatusOrderByCreatedAtDescBookingIdDesc(BookingStatus status, Pageable pageable);
    @EntityGraph(attributePaths = {"room", "room.accommodation"})
    Page<BookingEntity> findByBookingIdOrderByCreatedAtDescBookingIdDesc(Long bookingId, Pageable pageable);
    @EntityGraph(attributePaths = {"room", "room.accommodation"})
    Page<BookingEntity> findByBookingIdAndStatusOrderByCreatedAtDescBookingIdDesc(
        Long bookingId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "room.accommodation"})
    @Query("""
        select b from BookingEntity b
        where (:bookingId is null or b.bookingId = :bookingId)
          and (:status is null or b.status = :status)
          and ((:cancellationRequested = true and exists (
                select r.cancellationRequestId from CancellationRequestEntity r
                where r.booking = b and r.status = :requestedStatus))
            or (:cancellationRequested = false and not exists (
                select r.cancellationRequestId from CancellationRequestEntity r
                where r.booking = b and r.status = :requestedStatus)))
        order by b.createdAt desc, b.bookingId desc
        """)
    Page<BookingEntity> findByPendingCancellation(@Param("bookingId") Long bookingId,
                                                  @Param("status") BookingStatus status,
                                                  @Param("cancellationRequested") boolean cancellationRequested,
                                                  @Param("requestedStatus") CancellationRequestStatus requestedStatus,
                                                  Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.bookingId = :bookingId")
    Optional<BookingEntity> findByIdForUpdate(@Param("bookingId") Long bookingId);

    @Query("""
        select count(b) from BookingEntity b
        where b.room.roomId = :roomId
          and b.checkInDate <= :stayDate and b.checkOutDate > :stayDate
          and (b.status in :confirmedStatuses
               or (b.status = :paymentPending and b.paymentExpiresAt > :now))
        """)
    long countOccupiedOnDate(@Param("roomId") Long roomId,
                             @Param("stayDate") LocalDate stayDate,
                             @Param("confirmedStatuses") List<BookingStatus> confirmedStatuses,
                             @Param("paymentPending") BookingStatus paymentPending,
                             @Param("now") LocalDateTime now);
}
