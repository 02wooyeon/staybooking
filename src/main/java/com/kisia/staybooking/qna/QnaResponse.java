package com.kisia.staybooking.qna;

import java.time.LocalDateTime;

import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.UploadResponse;

public record QnaResponse(Long qnaId, Long userId, Long bookingId, String category, String title,
                          String content, boolean secret, boolean answered, String answer,
                          LocalDateTime createdAt, boolean deleted, String deletedReason, String image) {
    static QnaResponse from(QnaEntity qna, QnaAnswerEntity answer) {
        return new QnaResponse(qna.getQnaId(), qna.getUser().getUserId(),
            qna.getBooking() == null ? null : qna.getBooking().getBookingId(), qna.getCategory(),
            qna.getTitle(), qna.getContent(), qna.isSecret(), answer != null,
            answer == null ? null : answer.getContent(), qna.getCreatedAt(),
            qna.isDeleted(), qna.getDeletedReason(), qna.getFiles().stream().findFirst()
                .map(FileEntity::getFileId).map(UploadResponse::urlOf).orElse(null));
    }
}
