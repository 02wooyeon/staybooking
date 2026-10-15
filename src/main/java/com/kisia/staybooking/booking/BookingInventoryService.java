package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.kisia.staybooking.room.RoomEntity;
import org.springframework.stereotype.Service;

@Service
public class BookingInventoryService {
    private static final List<BookingStatus> CONFIRMED_STATUSES =
        List.of(BookingStatus.PENDING, BookingStatus.COMPLETED);

    private final BookingRepository bookings;

    public BookingInventoryService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    public void requireAvailability(RoomEntity lockedRoom, LocalDate checkIn, LocalDate checkOut,
                                    LocalDateTime nowUtc) {
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            long occupied = bookings.countOccupiedOnDate(lockedRoom.getRoomId(), day,
                CONFIRMED_STATUSES, BookingStatus.PAYMENT_PENDING, nowUtc);
            if (occupied >= lockedRoom.getTotalQuantity()) {
                throw new IllegalStateException("No rooms available on " + day);
            }
        }
    }
}
