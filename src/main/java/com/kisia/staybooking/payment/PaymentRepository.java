package com.kisia.staybooking.payment;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import com.kisia.staybooking.statistics.TimedAmount;

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
    @Query("select coalesce(sum(p.paidAmount), 0) from PaymentEntity p "
        + "where p.status in :statuses and p.paidAt >= :fromUtc and p.paidAt < :toUtc")
    long sumPaidAmountByStatusInAndPaidAtRange(@Param("statuses") List<PaymentStatus> statuses,
                                                @Param("fromUtc") LocalDateTime fromUtc,
                                                @Param("toUtc") LocalDateTime toUtc);
    @Query("select new com.kisia.staybooking.statistics.TimedAmount(p.paidAmount, p.paidAt) "
        + "from PaymentEntity p where p.status in :statuses "
        + "and p.paidAt >= :fromUtc and p.paidAt < :toUtc")
    List<TimedAmount> findPaidAmountsInRange(@Param("statuses") List<PaymentStatus> statuses,
                                             @Param("fromUtc") LocalDateTime fromUtc,
                                             @Param("toUtc") LocalDateTime toUtc);
    @EntityGraph(attributePaths = "booking")
    List<PaymentEntity> findAllByOrderByCreatedAtDesc();
    Page<PaymentEntity> findAllByOrderByCreatedAtDescPaymentIdDesc(Pageable pageable);
    Page<PaymentEntity> findByPaymentMethodOrderByCreatedAtDescPaymentIdDesc(String paymentMethod,
                                                                              Pageable pageable);
    Page<PaymentEntity> findByStatusOrderByCreatedAtDescPaymentIdDesc(PaymentStatus status, Pageable pageable);
    Page<PaymentEntity> findByBookingBookingIdOrderByCreatedAtDescPaymentIdDesc(Long bookingId, Pageable pageable);
    Page<PaymentEntity> findByBookingBookingIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
        Long bookingId, PaymentStatus status, Pageable pageable);
    @Query("select p from PaymentEntity p where p.orderId = :orderId "
        + "and (:bookingId is null or p.booking.bookingId = :bookingId) "
        + "and (:status is null or p.status = :status) "
        + "order by p.createdAt desc, p.paymentId desc")
    Page<PaymentEntity> searchByOrderIdForAdmin(@Param("orderId") String orderId,
                                                @Param("bookingId") Long bookingId,
                                                @Param("status") PaymentStatus status,
                                                Pageable pageable);
    List<PaymentEntity> findByBookingUserUserIdOrderByCreatedAtDesc(Long userId);
    Page<PaymentEntity> findByBookingUserUserIdOrderByCreatedAtDescPaymentIdDesc(Long userId,
                                                                                  Pageable pageable);
    Page<PaymentEntity> findByBookingUserUserIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
        Long userId, PaymentStatus status, Pageable pageable);
    Page<PaymentEntity> findByBookingUserUserIdAndBookingBookingIdOrderByCreatedAtDescPaymentIdDesc(
        Long userId, Long bookingId, Pageable pageable);
    Page<PaymentEntity> findByBookingUserUserIdAndBookingBookingIdAndStatusOrderByCreatedAtDescPaymentIdDesc(
        Long userId, Long bookingId, PaymentStatus status, Pageable pageable);
    List<PaymentEntity> findByBookingBookingIdAndStatus(Long bookingId, PaymentStatus status);

    Optional<PaymentEntity> findFirstByBookingBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId,
                                                                                        PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.orderId = :orderId")
    Optional<PaymentEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
