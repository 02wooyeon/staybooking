package com.kisia.staybooking.qna;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.common.PageResponse;
import com.kisia.staybooking.file.FileEntity;
import com.kisia.staybooking.file.FileService;
import com.kisia.staybooking.review.ReviewRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QnaService {
    private final QnaRepository questions;
    private final QnaAnswerRepository answers;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final FileService files;
    private final ReviewRepository reviews;

    public QnaService(QnaRepository questions, QnaAnswerRepository answers,
                      UserRepository users, BookingRepository bookings, FileService files,
                      ReviewRepository reviews) {
        this.questions = questions;
        this.answers = answers;
        this.users = users;
        this.bookings = bookings;
        this.files = files;
        this.reviews = reviews;
    }

    // 사진 없이 쓰는 기존 호출용
    @Transactional
    public QnaResponse create(Long userId, Long bookingId, String category, String title,
                              String content, boolean secret) {
        return create(userId, bookingId, category, title, content, secret, null);
    }

    @Transactional
    public QnaResponse update(Long userId, Long qnaId, Long bookingId, String category, String title,
                              String content, boolean secret) {
        return update(userId, qnaId, bookingId, category, title, content, secret, null);
    }

    @Transactional
    public QnaResponse create(Long userId, Long bookingId, String category, String title,
                              String content, boolean secret, Long imageFileId) {
        UserEntity user = users.findById(userId).orElseThrow(QnaService::notFound);
        if (user.getStatus() != UserStatus.ACTIVE) throw forbidden();
        BookingEntity booking = ownedBooking(userId, bookingId);
        return response(questions.save(new QnaEntity(user, booking, category, title, content, secret,
            images(userId, imageFileId, null))));
    }

    @Transactional
    public QnaResponse update(Long userId, Long qnaId, Long bookingId, String category, String title,
                              String content, boolean secret, Long imageFileId) {
        QnaEntity question = ownedQuestion(userId, qnaId);
        if (answers.existsByQnaQnaId(qnaId)) {
            throw new IllegalStateException("Answered Q&A cannot be edited");
        }
        question.update(ownedBooking(userId, bookingId), category, title, content, secret,
            images(userId, imageFileId, qnaId));
        return response(question);
    }

    @Transactional
    public void delete(Long userId, Long qnaId) {
        ownedQuestion(userId, qnaId).hide("작성자 삭제");
    }

    @Transactional(readOnly = true)
    public List<QnaResponse> mine(Long userId) {
        return questions.findByUserUserIdAndDeletedFalseOrderByCreatedAtDesc(userId)
            .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<QnaResponse> publicQuestions() {
        return questions.findBySecretFalseAndDeletedFalseOrderByCreatedAtDesc()
            .stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public QnaResponse detail(Long actorId, Long qnaId) {
        QnaEntity question = questions.findById(qnaId).orElseThrow(QnaService::notFound);
        if (question.isDeleted()) throw notFound();
        if (question.isSecret() && !question.getUser().getUserId().equals(actorId)
            && !isAdmin(actorId)) throw notFound();
        return response(question);
    }

    @Transactional(readOnly = true)
    public List<QnaResponse> allForAdmin(Long adminId) {
        requireAdmin(adminId);
        return questions.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<QnaResponse> searchForAdmin(Long adminId, AdminQnaSearchRequest request) {
        requireAdmin(adminId);
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? 20 : request.size();
        String q = request.q() == null || request.q().isBlank() ? null : request.q().trim();
        Page<QnaEntity> result = questions.searchForAdmin(request.userId(), request.answered(),
            request.deleted(), q, PageRequest.of(page, size));
        return new PageResponse<>(result.getContent().stream().map(this::response).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public QnaResponse answer(Long adminId, Long qnaId, String content) {
        UserEntity admin = requireAdmin(adminId);
        QnaEntity question = questions.findByIdForUpdate(qnaId).orElseThrow(QnaService::notFound);
        if (question.isDeleted()) throw new IllegalStateException("Hidden Q&A cannot be answered");
        QnaAnswerEntity answer = answers.findByQnaQnaId(qnaId).orElse(null);
        if (answer == null) answers.save(new QnaAnswerEntity(question, admin, content));
        else answer.update(admin, content);
        return response(question);
    }

    @Transactional
    public QnaResponse hide(Long adminId, Long qnaId, String reason) {
        requireAdmin(adminId);
        QnaEntity question = questions.findByIdForUpdate(qnaId).orElseThrow(QnaService::notFound);
        question.hide(reason);
        return response(question);
    }

    private QnaResponse response(QnaEntity question) {
        return QnaResponse.from(question, answers.findByQnaQnaId(question.getQnaId()).orElse(null));
    }

    private QnaEntity ownedQuestion(Long userId, Long qnaId) {
        QnaEntity question = questions.findByIdForUpdate(qnaId).orElseThrow(QnaService::notFound);
        if (question.isDeleted() || !question.getUser().getUserId().equals(userId)) throw notFound();
        return question;
    }

    private BookingEntity ownedBooking(Long userId, Long bookingId) {
        if (bookingId == null) return null;
        BookingEntity booking = bookings.findById(bookingId).orElseThrow(QnaService::notFound);
        if (!booking.getUser().getUserId().equals(userId)) throw notFound();
        return booking;
    }

    // 본인이 올린 사진 중 다른 문의·후기에 쓰지 않은 것만 붙인다.
    // (공개 후기 사진을 비밀 문의에 다시 붙이는 식으로 공개 범위가 섞이지 않게 한다.)
    private Set<FileEntity> images(Long userId, Long imageFileId, Long qnaId) {
        Set<FileEntity> result = new LinkedHashSet<>();
        if (imageFileId == null) return result;
        FileEntity file = files.ownedFile(userId, imageFileId);
        boolean usedElsewhere = reviews.existsByFilesFileId(imageFileId) || (qnaId == null
            ? questions.existsByFilesFileId(imageFileId)
            : questions.fileUsedByOtherQna(imageFileId, qnaId));
        if (usedElsewhere) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_IN_USE", "이미 다른 글에 사용한 사진입니다.");
        }
        result.add(file);
        return result;
    }

    private UserEntity requireAdmin(Long adminId) {
        UserEntity admin = users.findById(adminId).orElseThrow(QnaService::notFound);
        if (admin.getStatus() != UserStatus.ACTIVE || admin.getRole() != UserRole.ROLE_ADMIN) throw forbidden();
        return admin;
    }

    private boolean isAdmin(Long actorId) {
        return actorId != null && users.findById(actorId)
            .filter(user -> user.getRole() == UserRole.ROLE_ADMIN && user.getStatus() == UserStatus.ACTIVE)
            .isPresent();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "문의를 찾을 수 없습니다.");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
    }
}
