package com.kisia.staybooking.user;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    long countByStatus(UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.userId = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") Long userId);
    Optional<UserEntity> findByEmail(String email);

    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
