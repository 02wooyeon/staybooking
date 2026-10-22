package com.kisia.staybooking.payment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findAllByOrderByCreatedAtDesc();
    List<PaymentEntity> findByBookingBookingIdAndStatus(Long bookingId, PaymentStatus status);

    Optional<PaymentEntity> findFirstByBookingBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId,
                                                                                        PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.orderId = :orderId")
    Optional<PaymentEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
