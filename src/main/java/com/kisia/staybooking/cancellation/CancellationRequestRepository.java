package com.kisia.staybooking.cancellation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationRequestRepository extends JpaRepository<CancellationRequestEntity, Long> {
    boolean existsByBookingBookingIdAndStatus(Long bookingId, CancellationRequestStatus status);

    @Query("select r.booking.bookingId from CancellationRequestEntity r "
        + "where r.booking.bookingId in :ids and r.status = :status")
    List<Long> findBookingIdsWithStatus(@Param("ids") Collection<Long> ids,
                                        @Param("status") CancellationRequestStatus status);

    Optional<CancellationRequestEntity> findByBookingBookingIdAndStatus(Long bookingId,
                                                                          CancellationRequestStatus status);

    List<CancellationRequestEntity> findByBookingBookingIdOrderByCreatedAtDescCancellationRequestIdDesc(
        Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CancellationRequestEntity r where r.cancellationRequestId = :id")
    Optional<CancellationRequestEntity> findByIdForUpdate(@Param("id") Long id);
}
