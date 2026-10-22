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
public interface RefundRepository extends JpaRepository<RefundEntity,Long>{
 long countByStatus(RefundStatus status);
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
