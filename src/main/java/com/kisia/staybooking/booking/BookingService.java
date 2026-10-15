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
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)
                || checkIn.isBefore(LocalDate.now(KOREA))) {
            throw new IllegalArgumentException("Invalid stay dates");
        }
        UserEntity user = users.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserRole.ROLE_USER || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Only active users can book");
        }
        RoomEntity room = rooms.findByIdForUpdate(roomId)
            .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        if (room.getStatus() != RoomStatus.ACTIVE
                || room.getAccommodation().getStatus() != AccommodationStatus.ACTIVE
                || guests < 1 || guests > room.getCapacity()) {
            throw new IllegalArgumentException("Room is unavailable or exceeds capacity");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        long total = Math.multiplyExact(room.getPrice(), nights);
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        inventory.requireAvailability(room, checkIn, checkOut, nowUtc);
        BookingEntity booking = new BookingEntity(user, room, checkIn, checkOut, guests,
            total, 0, nowUtc.plusMinutes(15));
        return bookings.save(booking);
    }
}
