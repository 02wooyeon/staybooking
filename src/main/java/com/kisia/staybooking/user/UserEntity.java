package com.kisia.staybooking.user;

import java.util.Objects;

import com.kisia.staybooking.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id"),
    @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
@Check(constraints = "mileage_balance >= 0")
public class UserEntity extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role = UserRole.ROLE_USER;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "mileage_balance", nullable = false)
    @ColumnDefault("0")
    private long mileageBalance;

    @Column(name = "token_version", nullable = false)
    @ColumnDefault("0")
    private long tokenVersion;

    protected UserEntity() {
    }

    public UserEntity(String loginId, String passwordHash, String name, String email) {
        this.loginId = Objects.requireNonNull(loginId);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
    }

    public UserEntity(String loginId, String passwordHash, String name, String nickname,
                      String email, String phone) {
        this(loginId, passwordHash, name, email);
        this.nickname = nickname;
        this.phone = phone;
    }

    public UserEntity(String loginId, String passwordHash, String name, String nickname,
                      String email, String phone, UserRole role) {
        this(loginId, passwordHash, name, nickname, email, phone);
        this.role = Objects.requireNonNull(role);
    }

    public Long getUserId() { return userId; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public long getMileageBalance() { return mileageBalance; }
    public long getTokenVersion() { return tokenVersion; }
    public void changeStatus(UserStatus status) {
        UserStatus next = Objects.requireNonNull(status);
        if (this.status != next) {
            this.status = next;
            tokenVersion++;
        }
    }
}
