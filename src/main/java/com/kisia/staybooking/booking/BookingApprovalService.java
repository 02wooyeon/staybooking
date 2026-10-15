package com.kisia.staybooking.booking;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.kisia.staybooking.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingApprovalService {
    private final UserRepository users;
    private final BookingRepository bookings;
    private final PaymentRepository payments;

    public BookingApprovalService(UserRepository users, BookingRepository bookings,
                                  PaymentRepository payments) {
        this.users = users;
        this.bookings = bookings;
        this.payments = payments;
    }

    @Transactional
    public BookingEntity accept(Long actorId, Long bookingId) {
        UserEntity actor = users.findById(actorId)
            .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        if (actor.getStatus() != UserStatus.ACTIVE ||
                (actor.getRole() != UserRole.ROLE_APPROVER && actor.getRole() != UserRole.ROLE_ADMIN)) {
            throw new IllegalStateException("Approval permission required");
        }
        BookingEntity booking = bookings.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Booking is not awaiting approval");
        }
        List<PaymentEntity> paid = payments.findByBookingBookingIdAndStatus(bookingId, PaymentStatus.PAID);
        if (paid.size() != 1 || paid.getFirst().getPaidAt() == null ||
                Math.addExact(paid.getFirst().getPaidAmount(), booking.getUsedMileage())
                    != booking.getTotalAmount()) {
            throw new IllegalStateException("Exactly one matching verified payment is required");
        }
        booking.accept(actor, LocalDateTime.now(ZoneOffset.UTC));
        return booking;
    }
}
