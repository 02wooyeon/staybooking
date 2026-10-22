package com.kisia.staybooking.refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RefundRepository extends JpaRepository<RefundEntity,Long>{
 long countByStatus(RefundStatus status);
 List<RefundEntity> findAllByOrderByCreatedAtDesc();
}
