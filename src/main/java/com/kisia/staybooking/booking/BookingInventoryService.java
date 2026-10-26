package com.kisia.staybooking.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

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
        firstUnavailableDay(lockedRoom, checkIn, checkOut, nowUtc).ifPresent(day -> {
            throw new IllegalStateException("No rooms available on " + day);
        });
    }

    // 검색 화면용. 잠금 없이 조회하므로 실제 예약 시에는 requireAvailability로 다시 확인해야 한다.
    public boolean isAvailable(RoomEntity room, LocalDate checkIn, LocalDate checkOut, LocalDateTime nowUtc) {
        return firstUnavailableDay(room, checkIn, checkOut, nowUtc).isEmpty();
    }

    // 기간 중 가장 붐비는 날 기준으로 남은 객실 수. 화면 표시용이며 잠금 없이 조회한다.
    public int remainingUnits(RoomEntity room, LocalDate checkIn, LocalDate checkOut, LocalDateTime nowUtc) {
        long remaining = room.getTotalQuantity();
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            long occupied = bookings.countOccupiedOnDate(room.getRoomId(), day,
                CONFIRMED_STATUSES, BookingStatus.PAYMENT_PENDING, nowUtc);
            remaining = Math.min(remaining, room.getTotalQuantity() - occupied);
        }
        return (int) Math.max(remaining, 0);
    }

    // 관리자 수량 변경 시 기존 예약을 지킬 수 있는 최소 수량이다. 날짜별 점유의 최댓값을 구한다.
    public int maximumOccupiedUnitsFrom(RoomEntity room, LocalDate fromDate, LocalDateTime nowUtc) {
        TreeMap<LocalDate, Integer> changes = new TreeMap<>();
        for (BookingEntity booking : bookings.findFutureOccupyingBookings(room.getRoomId(), fromDate,
                CONFIRMED_STATUSES, BookingStatus.PAYMENT_PENDING, nowUtc)) {
            LocalDate start = booking.getCheckInDate().isBefore(fromDate) ? fromDate : booking.getCheckInDate();
            changes.merge(start, 1, Integer::sum);
            changes.merge(booking.getCheckOutDate(), -1, Integer::sum);
        }
        int occupied = 0;
        int maximum = 0;
        for (int change : changes.values()) {
            occupied += change;
            maximum = Math.max(maximum, occupied);
        }
        return maximum;
    }

    private Optional<LocalDate> firstUnavailableDay(RoomEntity room, LocalDate checkIn, LocalDate checkOut,
                                                    LocalDateTime nowUtc) {
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            long occupied = bookings.countOccupiedOnDate(room.getRoomId(), day,
                CONFIRMED_STATUSES, BookingStatus.PAYMENT_PENDING, nowUtc);
            if (occupied >= room.getTotalQuantity()) {
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }
}
