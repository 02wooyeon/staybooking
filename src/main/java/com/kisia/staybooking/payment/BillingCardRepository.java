package com.kisia.staybooking.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCardRepository extends JpaRepository<BillingCardEntity, Long> {
    Optional<BillingCardEntity> findByUserUserId(Long userId);

    Optional<BillingCardEntity> findByBillingCardIdAndUserUserId(Long billingCardId, Long userId);
}
