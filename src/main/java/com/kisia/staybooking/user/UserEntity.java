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
    /**
     * 마일리지 잔액을 더한다. 차감은 음수를 넣는다. 잔액은 0 미만이 될 수 없다(users 테이블 CHECK와 같은 규칙).
     * 이력(mileage_history)과 함께 같은 트랜잭션에서 바꾸도록 MileageService 에서만 호출한다.
     *
     * @return 변경 후 잔액
     */
    public long addMileage(long amount) {
        long next = Math.addExact(mileageBalance, amount);
        if (next < 0) throw new IllegalStateException("Mileage balance cannot be negative");
        mileageBalance = next;
        return next;
    }

    // PROFILE_002. 이름·이메일은 본인 확인 정보라 바꾸지 않는다.
    public void updateProfile(String nickname, String phone) {
        this.nickname = nickname;
        this.phone = phone;
    }
    // 비밀번호를 바꾸면 기존에 발급된 Access Token도 모두 쓸 수 없게 한다.
    public void changePassword(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
        tokenVersion++;
    }
    // PROFILE_003. 예약·결제 기록은 남기되 회원을 알아볼 수 있는 정보는 지운다.
    // 이메일·아이디는 유일해야 하므로 회원번호로 만든 값으로 바꾼다.
    public void withdraw(String unusablePasswordHash) {
        String anonymous = "withdrawn-" + userId + "@deleted.invalid";
        this.loginId = anonymous;
        this.email = anonymous;
        this.passwordHash = Objects.requireNonNull(unusablePasswordHash);
        this.name = "탈퇴회원";
        this.nickname = null;
        this.phone = null;
        changeStatus(UserStatus.WITHDRAWN);
    }
    public void changeAssignableRole(UserRole role) {
        if (this.role == UserRole.ROLE_ADMIN) {
            throw new IllegalStateException("Administrator role cannot be changed here");
        }
        if (role != UserRole.ROLE_USER && role != UserRole.ROLE_APPROVER) {
            throw new IllegalArgumentException("Only user and approver roles are assignable");
        }
        if (this.role != role) {
            this.role = role;
            tokenVersion++;
        }
    }
}
