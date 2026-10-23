package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.file.FileService;
import com.kisia.staybooking.qna.QnaResponse;
import com.kisia.staybooking.qna.QnaService;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({QnaService.class, FileService.class})
class QnaServiceTests {
    @Autowired private EntityManager em;
    @Autowired private QnaService qna;

    @Test
    void secretQuestionsAreVisibleOnlyToOwnerAndAdmin() {
        UserEntity owner = user("owner", UserRole.ROLE_USER);
        UserEntity other = user("other", UserRole.ROLE_USER);
        UserEntity admin = user("admin", UserRole.ROLE_ADMIN);
        QnaResponse created = qna.create(owner.getUserId(), null, "booking", "예약 문의",
            "예약 내용을 확인 부탁드립니다.", true);

        assertTrue(qna.publicQuestions().isEmpty());
        assertEquals(created.qnaId(), qna.mine(owner.getUserId()).getFirst().qnaId());
        assertEquals(created.qnaId(), qna.detail(owner.getUserId(), created.qnaId()).qnaId());
        assertEquals(created.qnaId(), qna.detail(admin.getUserId(), created.qnaId()).qnaId());
        assertThrows(ApiException.class, () -> qna.detail(null, created.qnaId()));
        assertThrows(ApiException.class, () -> qna.detail(other.getUserId(), created.qnaId()));
    }

    @Test
    void adminCanAnswerReviseAndHideQuestion() {
        UserEntity owner = user("writer", UserRole.ROLE_USER);
        UserEntity admin = user("operator", UserRole.ROLE_ADMIN);
        QnaResponse created = qna.create(owner.getUserId(), null, "etc", "일반 문의",
            "답변을 기다리고 있습니다.", false);
        assertEquals(1, qna.publicQuestions().size());

        assertThrows(ApiException.class,
            () -> qna.answer(owner.getUserId(), created.qnaId(), "권한 없는 답변"));
        QnaResponse answered = qna.answer(admin.getUserId(), created.qnaId(), "첫 번째 답변");
        assertTrue(answered.answered());
        assertEquals("첫 번째 답변", answered.answer());
        assertEquals("수정된 답변", qna.answer(admin.getUserId(), created.qnaId(), "수정된 답변").answer());
        assertEquals(1, qna.publicQuestions().size());

        QnaResponse hidden = qna.hide(admin.getUserId(), created.qnaId(), "부적절한 내용");
        assertTrue(hidden.deleted());
        assertEquals("부적절한 내용", hidden.deletedReason());
        assertTrue(qna.publicQuestions().isEmpty());
        assertTrue(qna.mine(owner.getUserId()).isEmpty());
        assertThrows(ApiException.class, () -> qna.detail(owner.getUserId(), created.qnaId()));
        assertThrows(IllegalStateException.class,
            () -> qna.answer(admin.getUserId(), created.qnaId(), "숨김 문의 답변"));
    }

    @Test
    void linkedBookingMustBelongToQuestionAuthor() {
        UserEntity owner = user("guest", UserRole.ROLE_USER);
        UserEntity other = user("stranger", UserRole.ROLE_USER);
        AccommodationEntity accommodation = new AccommodationEntity("Test Stay", "Seoul",
            LocalTime.of(15, 0), LocalTime.of(11, 0));
        em.persist(accommodation);
        RoomEntity room = new RoomEntity(accommodation, "Standard", 100_000, 2, 1);
        em.persist(room);
        BookingEntity booking = new BookingEntity(owner, room, LocalDate.now().plusDays(3),
            LocalDate.now().plusDays(4), 1, 100_000, 0, LocalDateTime.now().plusHours(1));
        em.persist(booking);
        em.flush();

        assertThrows(ApiException.class, () -> qna.create(other.getUserId(), booking.getBookingId(),
            "booking", "예약 문의", "제 예약을 확인해 주세요.", true));
        assertEquals(booking.getBookingId(), qna.create(owner.getUserId(), booking.getBookingId(),
            "booking", "예약 문의", "제 예약을 확인해 주세요.", true).bookingId());
    }

    @Test
    void ownerMayEditBeforeAnswerAndDeleteButOthersCannot() {
        UserEntity owner = user("editor", UserRole.ROLE_USER);
        UserEntity other = user("intruder", UserRole.ROLE_USER);
        UserEntity admin = user("moderator", UserRole.ROLE_ADMIN);
        Long id = qna.create(owner.getUserId(), null, "etc", "첫 제목", "처음 작성한 문의 내용입니다.",
            true).qnaId();

        assertThrows(ApiException.class, () -> qna.update(other.getUserId(), id, null, "etc",
            "남의 수정", "다른 사람의 수정입니다.", false));
        QnaResponse changed = qna.update(owner.getUserId(), id, null, "payment", "바뀐 제목",
            "수정한 문의 내용입니다.", false);
        assertEquals("바뀐 제목", changed.title());
        assertEquals(1, qna.publicQuestions().size());
        qna.answer(admin.getUserId(), id, "답변 완료");
        assertThrows(IllegalStateException.class, () -> qna.update(owner.getUserId(), id, null,
            "etc", "다시 수정", "답변 이후 변경 시도입니다.", false));
        assertThrows(ApiException.class, () -> qna.delete(other.getUserId(), id));
        qna.delete(owner.getUserId(), id);
        assertTrue(qna.publicQuestions().isEmpty());
        assertThrows(ApiException.class, () -> qna.detail(owner.getUserId(), id));
    }

    private UserEntity user(String loginId, UserRole role) {
        UserEntity user = new UserEntity(loginId, "hash", loginId, null,
            loginId + "@example.test", null, role);
        em.persist(user);
        em.flush();
        return user;
    }
}
