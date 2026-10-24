package com.kisia.staybooking.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    long countByStatus(UserStatus status);

    @Query("""
        select u from UserEntity u
        where (:userId is null or u.userId = :userId)
          and (:role is null or u.role = :role)
          and (:status is null or u.status = :status)
          and (:q is null or lower(u.loginId) = lower(:q) or lower(u.email) = lower(:q))
        order by u.createdAt desc, u.userId desc
        """)
    Page<UserEntity> searchForAdmin(@Param("userId") Long userId, @Param("role") UserRole role,
                                     @Param("status") UserStatus status, @Param("q") String q,
                                     Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.userId = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") Long userId);
    Optional<UserEntity> findByEmail(String email);
    List<UserEntity> findByNameAndStatus(String name, UserStatus status);

    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);
}
