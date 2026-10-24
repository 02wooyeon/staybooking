package com.kisia.staybooking.refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.LocalDateTime;
import com.kisia.staybooking.statistics.TimedAmount;
public interface RefundRepository extends JpaRepository<RefundEntity,Long>{
 @Override
 @EntityGraph(attributePaths={"booking","processedBy"})
 Optional<RefundEntity> findById(Long id);
 long countByStatus(RefundStatus status);
 @Query("select coalesce(sum(r.amount), 0) from RefundEntity r where r.status = :status")
 long sumAmountByStatus(@Param("status") RefundStatus status);
 @Query("select coalesce(sum(r.amount), 0) from RefundEntity r "
     + "where r.status = :status and r.processedAt >= :fromUtc and r.processedAt < :toUtc")
 long sumProcessedAmountByStatusAndRange(@Param("status") RefundStatus status,
     @Param("fromUtc") LocalDateTime fromUtc, @Param("toUtc") LocalDateTime toUtc);
 @Query("select coalesce(sum(r.amount), 0) from RefundEntity r "
     + "where r.status = :status and r.createdAt >= :fromUtc and r.createdAt < :toUtc")
 long sumCreatedAmountByStatusAndRange(@Param("status") RefundStatus status,
     @Param("fromUtc") LocalDateTime fromUtc, @Param("toUtc") LocalDateTime toUtc);
 @Query("select new com.kisia.staybooking.statistics.TimedAmount(r.amount, r.processedAt) "
     + "from RefundEntity r where r.status = :status "
     + "and r.processedAt >= :fromUtc and r.processedAt < :toUtc")
 List<TimedAmount> findProcessedAmountsInRange(@Param("status") RefundStatus status,
     @Param("fromUtc") LocalDateTime fromUtc, @Param("toUtc") LocalDateTime toUtc);
 @Query("select new com.kisia.staybooking.statistics.TimedAmount(r.amount, r.createdAt) "
     + "from RefundEntity r where r.status = :status "
     + "and r.createdAt >= :fromUtc and r.createdAt < :toUtc")
 List<TimedAmount> findCreatedAmountsInRange(@Param("status") RefundStatus status,
     @Param("fromUtc") LocalDateTime fromUtc, @Param("toUtc") LocalDateTime toUtc);
 @EntityGraph(attributePaths={"booking","processedBy"})
 List<RefundEntity> findAllByOrderByCreatedAtDesc();
 @EntityGraph(attributePaths={"booking","processedBy"})
 Page<RefundEntity> findAllByOrderByCreatedAtDescRefundIdDesc(Pageable pageable);
 @EntityGraph(attributePaths={"booking","processedBy"})
 Page<RefundEntity> findByStatusOrderByCreatedAtDescRefundIdDesc(RefundStatus status, Pageable pageable);
 @EntityGraph(attributePaths={"booking","processedBy"})
 Page<RefundEntity> findByBookingBookingIdOrderByCreatedAtDescRefundIdDesc(Long bookingId, Pageable pageable);
 @EntityGraph(attributePaths={"booking","processedBy"})
 Page<RefundEntity> findByBookingBookingIdAndStatusOrderByCreatedAtDescRefundIdDesc(
     Long bookingId, RefundStatus status, Pageable pageable);
 boolean existsByBookingBookingId(Long bookingId);
 List<RefundEntity> findByBookingBookingIdIn(Collection<Long> bookingIds);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from RefundEntity r where r.refundId = :id")
 Optional<RefundEntity> findByIdForUpdate(@Param("id") Long id);
}
