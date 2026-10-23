package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.kisia.staybooking.accommodation.AccommodationStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.room.RoomStatus;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final UserRepository users;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final BookingInventoryService inventory;

    public BookingService(UserRepository users, RoomRepository rooms, BookingRepository bookings,
                          BookingInventoryService inventory) {
        this.users = users;
        this.rooms = rooms;
        this.bookings = bookings;
        this.inventory = inventory;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingEntity create(Long userId, Long roomId, LocalDate checkIn, LocalDate checkOut,
                                int guests) {
        validateStay(checkIn, checkOut);
        UserEntity user = bookingUser(userId);
        RoomEntity room = rooms.findByIdForUpdate(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        validateRoom(room, guests);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        long total = totalAmount(room, nights);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        inventory.requireAvailability(room, checkIn, checkOut, nowUtc);
        BookingEntity booking = new BookingEntity(user, room, checkIn, checkOut, guests,
            total, 0, nowUtc.plusMinutes(15));
        return bookings.save(booking);
    }

    // PAYMENT_006. 예약 생성과 같은 검사·계산을 쓰므로 화면에 보인 금액과 실제 예약 금액이 같다.
    // 금액은 클라이언트 값을 받지 않고 서버의 객실 가격으로만 계산한다. 잠금 없이 조회만 한다.
    @Transactional(readOnly = true)
    public BookingQuoteResponse quote(Long userId, Long roomId, LocalDate checkIn, LocalDate checkOut,
                                      int guests) {
        validateStay(checkIn, checkOut);
        bookingUser(userId);
        RoomEntity room = rooms.findById(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        validateRoom(room, guests);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        long roomTotal = totalAmount(room, nights);
        int availableCount = inventory.remainingUnits(room, checkIn, checkOut, LocalDateTime.now(ZoneOffset.UTC));
        return new BookingQuoteResponse(room.getAccommodation().getAccommodationId(),
            room.getAccommodation().getName(), room.getRoomId(), room.getName(), checkIn, checkOut, nights,
            guests, room.getPrice(), roomTotal, 0, roomTotal, availableCount, availableCount > 0);
    }

    private static void validateStay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)
                || checkIn.isBefore(LocalDate.now(KOREA))) {
            throw new IllegalArgumentException("Invalid stay dates");
        }
    }

    private UserEntity bookingUser(Long userId) {
        UserEntity user = users.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserRole.ROLE_USER || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Only active users can book");
        }
        return user;
    }

    private static void validateRoom(RoomEntity room, int guests) {
        if (room.getStatus() != RoomStatus.ACTIVE
                || room.getAccommodation().getStatus() != AccommodationStatus.ACTIVE
                || guests < 1 || guests > room.getCapacity()) {
            throw new IllegalArgumentException("Room is unavailable or exceeds capacity");
        }
    }

    // 지금은 1박 요금 × 숙박일 수. 할인·세금이 생기면 이곳만 바꾸면 견적과 예약에 함께 반영된다.
    private static long totalAmount(RoomEntity room, long nights) {
        return Math.multiplyExact(room.getPrice(), nights);
    }
}
