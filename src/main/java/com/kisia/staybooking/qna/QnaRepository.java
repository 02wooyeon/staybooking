package com.kisia.staybooking.qna;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    @Query("select q from QnaEntity q where "
        + "(:userId is null or q.user.userId = :userId) and "
        + "(:answered is null or (:answered = true and exists "
        + "(select a from QnaAnswerEntity a where a.qna = q)) or "
        + "(:answered = false and not exists "
        + "(select a from QnaAnswerEntity a where a.qna = q))) and "
        + "(:deleted is null or q.deleted = :deleted) and "
        + "(:q is null or lower(q.title) like lower(concat('%', :q, '%'))) "
        + "order by q.createdAt desc, q.qnaId desc")
    Page<QnaEntity> searchForAdmin(@Param("userId") Long userId,
                                   @Param("answered") Boolean answered,
                                   @Param("deleted") Boolean deleted,
                                   @Param("q") String q, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QnaEntity q where q.qnaId = :id")
    Optional<QnaEntity> findByIdForUpdate(@Param("id") Long id);
}
