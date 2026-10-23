package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

// 리뷰·업로드·회원정보·견적 테스트가 함께 쓰는 데이터 준비 도구
@TestComponent
class TestFixtures {
    static final String PASSWORD = "Passw0rd!";
    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final AtomicLong SEQ = new AtomicLong();

    private final UserRepository users;
    private final AccommodationRepository accommodations;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider jwt;

    TestFixtures(UserRepository users, AccommodationRepository accommodations, RoomRepository rooms,
                 BookingRepository bookings, JdbcTemplate jdbc, PasswordEncoder passwords, JwtTokenProvider jwt) {
        this.users = users;
        this.accommodations = accommodations;
        this.rooms = rooms;
        this.bookings = bookings;
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    static String uniqueEmail(String prefix) {
        return prefix + System.nanoTime() % 1_000_000 + "n" + SEQ.incrementAndGet() + "@example.test";
    }

    UserEntity user(String name) {
        return user(name, UserRole.ROLE_USER);
    }

    UserEntity user(String name, UserRole role) {
        String email = uniqueEmail("u");
        return users.save(new UserEntity(email, passwords.encode(PASSWORD), name, null, email, "010-1234-5678", role));
    }

    String bearer(UserEntity user) {
        return "Bearer " + jwt.createAccessToken(users.findById(user.getUserId()).orElseThrow());
    }

    RoomEntity room(long price, int capacity, int quantity) {
        AccommodationEntity accommodation = accommodations.save(new AccommodationEntity(
            "Stay " + SEQ.incrementAndGet(), "부산 동래구", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        return rooms.save(new RoomEntity(accommodation, "Standard", price, capacity, quantity));
    }

    // 상태와 날짜를 직접 정한 예약. 지난 날짜·승인 완료 상태는 서비스로는 만들 수 없어 DB에 바로 반영한다.
    BookingEntity booking(UserEntity user, RoomEntity room, LocalDate checkIn, LocalDate checkOut,
                          BookingStatus status) {
        BookingEntity booking = bookings.save(new BookingEntity(user, room, checkIn, checkOut, 1,
            room.getPrice(), 0, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15)));
        jdbc.update("update bookings set status = ? where booking_id = ?", status.name(), booking.getBookingId());
        return booking;
    }

    BookingEntity finishedStay(UserEntity user, RoomEntity room) {
        LocalDate today = LocalDate.now(KOREA);
        return booking(user, room, today.minusDays(3), today.minusDays(1), BookingStatus.COMPLETED);
    }
}
