package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "staybooking.admin.host=admin.localhost")
@AutoConfigureMockMvc
class ApproverBookingSearchApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentService payments;

    @Test
    void approverCanFindPaymentPendingAndPaidBookingsOnOperatorDomain() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity approver = users.save(new UserEntity("search-approver-" + tag, "hash", "Approver", null,
            "search-approver-" + tag + "@example.test", null, UserRole.ROLE_APPROVER));
        UserEntity admin = users.save(new UserEntity("search-admin-" + tag, "hash", "Admin", null,
            "search-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("search-guest-" + tag, "hash", "Guest",
            "search-guest-" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Search stay " + tag,
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 2));
        BookingEntity unpaid = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        BookingEntity paid = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(5), LocalDate.now().plusDays(6), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        payments.completeDemo(guest.getUserId(), paid.getBookingId());
        String path = "/api/v1/approver/bookings/search";
        String token = "Bearer " + jwt.createAccessToken(approver);

        mvc.perform(get("http://localhost" + path).header("Authorization", token))
            .andExpect(status().isNotFound());
        mvc.perform(get("http://admin.localhost" + path).param("bookingId", unpaid.getBookingId().toString())
                .param("status", "PAYMENT_PENDING").header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].booking.bookingId").value(unpaid.getBookingId()))
            .andExpect(jsonPath("$.content[0].booking.status").value("PAYMENT_PENDING"));
        mvc.perform(get("http://admin.localhost" + path).param("status", "PENDING")
                .header("Authorization", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].booking.bookingId").value(paid.getBookingId()));
        mvc.perform(get("http://admin.localhost" + path).param("size", "101")
                .header("Authorization", token))
            .andExpect(status().isBadRequest());
        mvc.perform(get("http://admin.localhost" + path)
                .header("Authorization", "Bearer " + jwt.createAccessToken(guest)))
            .andExpect(status().isForbidden());
        mvc.perform(get("http://admin.localhost" + path).param("bookingId", paid.getBookingId().toString())
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }
}
