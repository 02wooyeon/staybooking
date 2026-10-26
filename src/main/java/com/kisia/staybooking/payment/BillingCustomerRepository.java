package com.kisia.staybooking.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingCustomerRepository extends JpaRepository<BillingCustomerEntity, Long> {
}
