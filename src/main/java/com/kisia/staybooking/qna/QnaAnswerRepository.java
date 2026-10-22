package com.kisia.staybooking.qna;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QnaAnswerRepository extends JpaRepository<QnaAnswerEntity, Long> {
    Optional<QnaAnswerEntity> findByQnaQnaId(Long qnaId);
}
