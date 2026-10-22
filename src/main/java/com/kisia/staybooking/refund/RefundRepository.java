package com.kisia.staybooking.refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
public interface RefundRepository extends JpaRepository<RefundEntity,Long>{
 long countByStatus(RefundStatus status);
 List<RefundEntity> findAllByOrderByCreatedAtDesc();
 boolean existsByBookingBookingId(Long bookingId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select r from RefundEntity r where r.refundId = :id")
 Optional<RefundEntity> findByIdForUpdate(@Param("id") Long id);
}
