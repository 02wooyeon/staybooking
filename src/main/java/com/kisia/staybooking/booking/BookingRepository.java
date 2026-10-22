package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    List<BookingEntity> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    List<BookingEntity> findByStatusOrderByCreatedAtAsc(BookingStatus status);

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
