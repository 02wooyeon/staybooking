package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingInventoryService;
import com.kisia.staybooking.booking.BookingService;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import({BookingService.class, BookingInventoryService.class})
class BookingServiceTests {
    @Autowired private EntityManager entityManager;
    @Autowired private BookingService service;

    private Long userId;
    private Long roomId;
    private LocalDate firstNight;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity("guest01", "hash", "Guest", "guest@example.test");
        AccommodationEntity accommodation = new AccommodationEntity(
            "Stay A", "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0));
        RoomEntity room = new RoomEntity(accommodation, "Deluxe", 100_000, 2, 2);
        entityManager.persist(user);
        entityManager.persist(accommodation);
        entityManager.persist(room);
        entityManager.flush();
        userId = user.getUserId();
        roomId = room.getRoomId();
        firstNight = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(10);
    }

    @Test
    void allowsTwoUnitsButRejectsThirdOnTheSameNight() {
        BookingEntity first = service.create(userId, roomId, firstNight, firstNight.plusDays(2), 2);
        service.create(userId, roomId, firstNight.plusDays(1), firstNight.plusDays(3), 1);

        assertEquals(BookingStatus.PAYMENT_PENDING, first.getStatus());
        assertEquals(200_000, first.getTotalAmount());
        assertThrows(IllegalStateException.class,
            () -> service.create(userId, roomId, firstNight.plusDays(1), firstNight.plusDays(2), 1));

        // 첫날에는 한 객실만 점유 중이므로 추가 예약이 가능하다.
        service.create(userId, roomId, firstNight, firstNight.plusDays(1), 1);
    }

    @Test
    void rejectsOverCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> service.create(userId, roomId, firstNight, firstNight.plusDays(1), 3));
    }
}
