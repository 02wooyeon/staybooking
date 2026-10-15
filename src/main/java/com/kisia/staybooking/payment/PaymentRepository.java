package com.kisia.staybooking.payment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByBookingBookingIdAndStatus(Long bookingId, PaymentStatus status);
}
