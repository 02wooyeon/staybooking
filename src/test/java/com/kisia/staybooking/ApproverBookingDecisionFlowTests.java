package com.kisia.staybooking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.jayway.jsonpath.JsonPath;
import com.kisia.staybooking.accommodation.AccommodationEntity;
import com.kisia.staybooking.accommodation.AccommodationRepository;
import com.kisia.staybooking.auth.JwtTokenProvider;
import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.cancellation.CancellationRequestRepository;
import com.kisia.staybooking.cancellation.CancellationRequestStatus;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.room.RoomRepository;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "staybooking.admin.host=admin.localhost",
    "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class ApproverBookingDecisionFlowTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @Autowired private UserRepository users;
    @Autowired private AccommodationRepository accommodations;
    @Autowired private RoomRepository rooms;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentService payments;
    @Autowired private CancellationRequestRepository requests;
    @Autowired private RefundRepository refunds;

    @Test
    void approverAcceptsBookingThenRejectsAndApprovesUserCancellationRequests() throws Exception {
        String tag = Long.toString(System.nanoTime());
        UserEntity approver = users.save(new UserEntity("decision-approver-" + tag, "hash", "Approver", null,
            "decision-approver-" + tag + "@example.test", null, UserRole.ROLE_APPROVER));
        UserEntity admin = users.save(new UserEntity("decision-admin-" + tag, "hash", "Admin", null,
            "decision-admin-" + tag + "@example.test", null, UserRole.ROLE_ADMIN));
        UserEntity guest = users.save(new UserEntity("decision-guest-" + tag, "hash", "Guest",
            "decision-guest-" + tag + "@example.test"));
        AccommodationEntity stay = accommodations.save(new AccommodationEntity("Decision stay " + tag,
            "Seoul", LocalTime.of(15, 0), LocalTime.of(11, 0)));
        RoomEntity room = rooms.save(new RoomEntity(stay, "Standard", 100_000, 2, 1));
        BookingEntity booking = bookings.save(new BookingEntity(guest, room,
            LocalDate.now().plusDays(10), LocalDate.now().plusDays(11), 1, 100_000, 0,
            LocalDateTime.now().plusMinutes(10)));
        payments.completeDemo(guest.getUserId(), booking.getBookingId());

        String operatorToken = "Bearer " + jwt.createAccessToken(approver);
        String guestToken = "Bearer " + jwt.createAccessToken(guest);
        String operatorPath = "/api/v1/approver/bookings/" + booking.getBookingId();
        String requestPath = "/api/v1/bookings/" + booking.getBookingId() + "/cancellation-requests";

        mvc.perform(patch("http://localhost" + operatorPath).header("Authorization", operatorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(patch("http://admin.localhost" + operatorPath).header("Authorization", guestToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("http://admin.localhost" + operatorPath).header("Authorization", operatorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(approver.getUserId(), bookings.findById(booking.getBookingId())
            .orElseThrow().getApprovedBy().getUserId());
        String searchPath = "http://admin.localhost/api/v1/approver/bookings/search";
        mvc.perform(get(searchPath).param("bookingId", booking.getBookingId().toString())
                .header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].decisionHistory.approvedByUserId")
                .value(approver.getUserId()))
            .andExpect(jsonPath("$.content[0].decisionHistory.completedByUserId")
                .value(approver.getUserId()));

        Long firstRequestId = requestCancellation(requestPath, guestToken);
        mvc.perform(get(searchPath)
                .param("bookingId", booking.getBookingId().toString())
                .param("cancellationRequested", "true").header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].cancellationRequest.cancellationRequestId")
                .value(firstRequestId));
        mvc.perform(patch("http://admin.localhost" + operatorPath).header("Authorization", operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REJECT_CANCELLATION\",\"cancellationRequestId\":"
                    + firstRequestId + ",\"reason\":\"사유 확인 필요\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(CancellationRequestStatus.REJECTED,
            requests.findById(firstRequestId).orElseThrow().getStatus());
        mvc.perform(get(searchPath).param("bookingId", booking.getBookingId().toString())
                .param("cancellationRequested", "true").header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));

        Long secondRequestId = requestCancellation(requestPath, guestToken);
        mvc.perform(get(searchPath).param("bookingId", booking.getBookingId().toString())
                .param("cancellationRequested", "true").header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("http://admin.localhost" + operatorPath).header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.cancellationRequest.cancellationRequestId").value(secondRequestId))
            .andExpect(jsonPath("$.cancellationRequests[0].status").value("REQUESTED"))
            .andExpect(jsonPath("$.cancellationRequests[1].status").value("REJECTED"))
            .andExpect(jsonPath("$.cancellationRequests[1].decidedByUserId").value(approver.getUserId()));
        mvc.perform(patch("http://admin.localhost" + operatorPath).header("Authorization", operatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE_CANCELLATION\",\"cancellationRequestId\":"
                    + secondRequestId + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.refundStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.refundAmount").value(100_000));
        assertEquals(BookingStatus.CANCELLED, bookings.findById(booking.getBookingId()).orElseThrow().getStatus());
        assertEquals(CancellationRequestStatus.APPROVED,
            requests.findById(secondRequestId).orElseThrow().getStatus());
        mvc.perform(get(searchPath).param("bookingId", booking.getBookingId().toString())
                .param("cancellationRequested", "true").header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        assertTrue(refunds.existsByBookingBookingId(booking.getBookingId()));
        mvc.perform(get(searchPath).param("bookingId", booking.getBookingId().toString())
                .header("Authorization", operatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].booking.refundStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.content[0].decisionHistory.cancelledByUserId")
                .value(approver.getUserId()))
            .andExpect(jsonPath("$.content[0].decisionHistory.cancelSource").value("USER_REQUEST"))
            .andExpect(jsonPath("$.content[0].decisionHistory.cancelReasonType").value("USER_FAULT"));
        String adminDetail = "http://admin.localhost/api/v1/admin/bookings/" + booking.getBookingId();
        mvc.perform(get(adminDetail).header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.booking.status").value("CANCELLED"))
            .andExpect(jsonPath("$.summary.booking.refundStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.summary.cancellationRequest").doesNotExist())
            .andExpect(jsonPath("$.summary.decisionHistory.cancelledByUserId").value(approver.getUserId()))
            .andExpect(jsonPath("$.cancellationRequests[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.cancellationRequests[0].decidedByUserId").value(approver.getUserId()))
            .andExpect(jsonPath("$.cancellationRequests[1].status").value("REJECTED"));
        mvc.perform(get(adminDetail).header("Authorization", guestToken))
            .andExpect(status().isForbidden());
        mvc.perform(get("http://localhost/api/v1/admin/bookings/" + booking.getBookingId())
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isNotFound());
        mvc.perform(get("http://admin.localhost/api/v1/admin/bookings/999999999")
                .header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
            .andExpect(status().isNotFound());
        mvc.perform(get("http://localhost/api/v1/bookings/" + booking.getBookingId())
                .header("Authorization", guestToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisionHistory").doesNotExist());
    }

    private Long requestCancellation(String path, String guestToken) throws Exception {
        String response = mvc.perform(post("http://localhost" + path).header("Authorization", guestToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonType\":\"USER_FAULT\",\"reasonDetail\":\"일정 변경\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.cancellationRequestId")).longValue();
    }
}
