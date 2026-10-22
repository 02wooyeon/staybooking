package com.kisia.staybooking.qna;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QnaRepository extends JpaRepository<QnaEntity, Long> {
    @Query("select count(q) from QnaEntity q where q.deleted = false and not exists "
        + "(select a from QnaAnswerEntity a where a.qna = q)")
    long countVisibleUnanswered();
    List<QnaEntity> findByUserUserIdAndDeletedFalseOrderByCreatedAtDesc(Long userId);
    List<QnaEntity> findBySecretFalseAndDeletedFalseOrderByCreatedAtDesc();
    List<QnaEntity> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QnaEntity q where q.qnaId = :id")
    Optional<QnaEntity> findByIdForUpdate(@Param("id") Long id);
}
