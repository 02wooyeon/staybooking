package com.kisia.staybooking.cancellation;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationRequestRepository extends JpaRepository<CancellationRequestEntity, Long> {
    boolean existsByBookingBookingIdAndStatus(Long bookingId, CancellationRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CancellationRequestEntity r where r.cancellationRequestId = :id")
    Optional<CancellationRequestEntity> findByIdForUpdate(@Param("id") Long id);
}
