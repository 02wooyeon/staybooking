package com.kisia.staybooking.payment;

import java.util.Objects;

import com.kisia.staybooking.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 토스 자동결제의 customerKey. 토스 권고대로 회원번호·이메일처럼 추측 가능한 값 대신 무작위 값을 회원마다 하나 만든다.
 * 카드 등록 창이 돌려준 customerKey가 로그인한 회원의 것인지 이 표로 확인한다.
 */
@Entity
@Table(name = "billing_customers", uniqueConstraints =
    @UniqueConstraint(name = "uk_billing_customers_key", columnNames = "customer_key"))
public class BillingCustomerEntity extends AuditedEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "customer_key", nullable = false, length = 64)
    private String customerKey;

    protected BillingCustomerEntity() {
    }

    public BillingCustomerEntity(Long userId, String customerKey) {
        this.userId = Objects.requireNonNull(userId);
        this.customerKey = Objects.requireNonNull(customerKey);
    }

    public Long getUserId() { return userId; }
    public String getCustomerKey() { return customerKey; }
}
