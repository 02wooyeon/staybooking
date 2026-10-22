package com.kisia.staybooking.payment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    long countByStatus(PaymentStatus status);
    @Query("select coalesce(sum(p.paidAmount), 0) from PaymentEntity p where p.status in :statuses")
    long sumPaidAmountByStatusIn(@Param("statuses") List<PaymentStatus> statuses);
    @EntityGraph(attributePaths = "booking")
    List<PaymentEntity> findAllByOrderByCreatedAtDesc();
    Page<PaymentEntity> findAllByOrderByCreatedAtDescPaymentIdDesc(Pageable pageable);
    Page<PaymentEntity> findByStatusOrderByCreatedAtDescPaymentIdDesc(PaymentStatus status, Pageable pageable);
    Page<PaymentEntity> findByBookingBookingIdOrderByCreatedAtDescPaymentIdDesc(Long bookingId, Pageable pageable);
    Page<PaymentEntity> findByBookingBookingIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
        Long bookingId, PaymentStatus status, Pageable pageable);
    List<PaymentEntity> findByBookingUserUserIdOrderByCreatedAtDesc(Long userId);
    List<PaymentEntity> findByBookingBookingIdAndStatus(Long bookingId, PaymentStatus status);

    Optional<PaymentEntity> findFirstByBookingBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId,
                                                                                        PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.orderId = :orderId")
    Optional<PaymentEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
