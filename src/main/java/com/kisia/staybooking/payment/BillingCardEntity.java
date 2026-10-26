package com.kisia.staybooking.payment;

import java.util.Objects;

import com.kisia.staybooking.common.AuditedEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * PAYMENT_004 등록 카드. 빌링키는 BillingKeyCipher로 암호화한 값만 저장하고, 화면에는 카드사와 가린 카드번호만 준다.
 * 카드번호·유효기간 등 카드 정보는 토스 카드 등록 창에만 입력되고 이 서버로 오지 않는다.
 */
@Entity
@Table(name = "billing_cards", uniqueConstraints =
    @UniqueConstraint(name = "uk_billing_cards_user", columnNames = "user_id"))
public class BillingCardEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "billing_card_id")
    private Long billingCardId;

    // 회원당 카드 1장. 새로 등록하면 이전 카드를 지우고 바꾼다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_billing_cards_user"))
    private UserEntity user;

    @Column(name = "customer_key", nullable = false, length = 64)
    private String customerKey;

    @Column(name = "billing_key_encrypted", nullable = false, length = 512)
    private String billingKeyEncrypted;

    @Column(name = "card_company", length = 30)
    private String cardCompany;

    @Column(name = "card_number_masked", length = 30)
    private String cardNumberMasked;

    protected BillingCardEntity() {
    }

    public BillingCardEntity(UserEntity user, String customerKey, String billingKeyEncrypted,
                             String cardCompany, String cardNumberMasked) {
        this.user = Objects.requireNonNull(user);
        this.customerKey = Objects.requireNonNull(customerKey);
        this.billingKeyEncrypted = Objects.requireNonNull(billingKeyEncrypted);
        this.cardCompany = cardCompany;
        this.cardNumberMasked = cardNumberMasked;
    }

    public Long getBillingCardId() { return billingCardId; }
    public UserEntity getUser() { return user; }
    public String getCustomerKey() { return customerKey; }
    public String getBillingKeyEncrypted() { return billingKeyEncrypted; }
    public String getCardCompany() { return cardCompany; }
    public String getCardNumberMasked() { return cardNumberMasked; }
}
