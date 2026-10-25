package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.CancellationReasonType;
import com.kisia.staybooking.cancellation.BookingCancellationService;
import com.kisia.staybooking.mileage.MileageHistoryRepository;
import com.kisia.staybooking.mileage.MileageType;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRepository;
import com.kisia.staybooking.user.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class MileageFlowApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private UserRepository users;
    @Autowired private MileageHistoryRepository history;
    @Autowired private BookingCancellationService cancellations;

    @Test
    void mileageIsDeductedOnPaymentAndRestoredOnCancellation() throws Exception {
        UserEntity guest = fixtures.user("마일리지 예약자");
        UserEntity admin = fixtures.user("마일리지 관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(50_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);

        grant(admin, guest, 50_000, "이벤트 보상");
        assertEquals(50_000, balanceOf(guest));

        // 예약 생성은 "쓰겠다"는 기록만 남기고 잔액을 건드리지 않는다.
        String created = mvc.perform(post("/api/v1/bookings")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(room, checkIn, 50_000)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalAmount").value(50_000))
            .andExpect(jsonPath("$.usedMileage").value(50_000))
            .andReturn().getResponse().getContentAsString();
        long bookingId = ((Number) JsonPath.read(created, "$.bookingId")).longValue();
        assertEquals(50_000, balanceOf(guest));
        assertTrue(history.findFirstByBookingBookingIdAndType(bookingId, MileageType.USE).isEmpty());

        // 결제(여기서는 전액 마일리지라 0원 결제)가 확정될 때 실제로 차감된다.
        mvc.perform(post("/api/v1/payments/free-complete")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":" + bookingId + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(0));
        assertEquals(0, balanceOf(guest));
        var used = history.findFirstByBookingBookingIdAndType(bookingId, MileageType.USE).orElseThrow();
        assertEquals(-50_000, used.getAmount());
        assertEquals(0, used.getBalanceAfter());

        // 같은 결제를 다시 요청해도 잔액은 한 번만 움직인다.
        mvc.perform(post("/api/v1/payments/free-complete")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingId\":" + bookingId + "}"))
            .andExpect(status().isOk());
        assertEquals(0, balanceOf(guest));

        cancellations.cancel(admin.getUserId(), bookingId,
            CancellationReasonType.ACCOMMODATION_FAULT, "운영자 취소");
        assertEquals(50_000, balanceOf(guest));
        var restored = history.findFirstByBookingBookingIdAndType(bookingId, MileageType.REFUND).orElseThrow();
        assertEquals(50_000, restored.getAmount());

        // 적립·사용·복원이 최신순으로 남는다.
        mvc.perform(get("/api/v1/admin/members/" + guest.getUserId() + "/mileage")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].type").value(MileageType.REFUND.name()))
            .andExpect(jsonPath("$.content[0].balanceAfter").value(50_000))
            .andExpect(jsonPath("$.content[1].type").value(MileageType.USE.name()))
            .andExpect(jsonPath("$.content[2].type").value(MileageType.EARN.name()));
    }

    @Test
    void bookingRejectsMileageOverTheBalanceOrTheTotal() throws Exception {
        UserEntity guest = fixtures.user("마일리지 부족 예약자");
        UserEntity admin = fixtures.user("마일리지 검증 관리자", UserRole.ROLE_ADMIN);
        RoomEntity room = fixtures.room(50_000, 2, 1);
        LocalDate checkIn = LocalDate.now(TestFixtures.KOREA).plusDays(5);

        // 잔액보다 많이 쓸 수 없다.
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(room, checkIn, 10_000)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MILEAGE_BALANCE_SHORT"));

        // 결제 총액보다 많이 쓸 수 없다.
        grant(admin, guest, 90_000, "이벤트 보상");
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(room, checkIn, 60_000)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_MILEAGE"));

        // 음수는 요청 형식에서 걸러진다.
        mvc.perform(post("/api/v1/bookings")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(room, checkIn, -1)))
            .andExpect(status().isBadRequest());

        // 견적에는 보유·사용 가능 금액과 실제 결제 금액이 함께 나온다.
        mvc.perform(post("/api/v1/bookings/quote")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(room, checkIn, 20_000)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(50_000))
            .andExpect(jsonPath("$.usedMileage").value(20_000))
            .andExpect(jsonPath("$.payAmount").value(30_000))
            .andExpect(jsonPath("$.mileageBalance").value(90_000))
            .andExpect(jsonPath("$.usableMileage").value(50_000));
    }

    @Test
    void administratorAdjustmentRequiresAdminRoleAndCannotGoNegative() throws Exception {
        UserEntity guest = fixtures.user("조정 대상");
        UserEntity admin = fixtures.user("조정 관리자", UserRole.ROLE_ADMIN);

        mvc.perform(post("/api/v1/admin/members/" + guest.getUserId() + "/mileage")
                .header("Authorization", fixtures.bearer(guest))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1000,\"description\":\"직접 적립\"}"))
            .andExpect(status().isForbidden());

        grant(admin, guest, 1_000, "이벤트 보상");
        // 보유액보다 많이 차감할 수 없다.
        mvc.perform(post("/api/v1/admin/members/" + guest.getUserId() + "/mileage")
                .header("Authorization", fixtures.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":-2000,\"description\":\"회수\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MILEAGE_BALANCE_SHORT"));
        // 0은 기록할 이유가 없다.
        mvc.perform(post("/api/v1/admin/members/" + guest.getUserId() + "/mileage")
                .header("Authorization", fixtures.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0,\"description\":\"변화 없음\"}"))
            .andExpect(status().isBadRequest());
        // 차감은 USE로 남는다.
        mvc.perform(post("/api/v1/admin/members/" + guest.getUserId() + "/mileage")
                .header("Authorization", fixtures.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":-400,\"description\":\"오지급 회수\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value(MileageType.USE.name()))
            .andExpect(jsonPath("$.balanceAfter").value(600));
        assertEquals(600, balanceOf(guest));

        mvc.perform(get("/api/v1/admin/members/999999999/mileage")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private void grant(UserEntity admin, UserEntity target, long amount, String description) throws Exception {
        mvc.perform(post("/api/v1/admin/members/" + target.getUserId() + "/mileage")
                .header("Authorization", fixtures.bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"description\":\"" + description + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value(MileageType.EARN.name()));
    }

    private static String bookingBody(RoomEntity room, LocalDate checkIn, long usedMileage) {
        return "{\"roomId\":" + room.getRoomId() + ",\"checkIn\":\"" + checkIn + "\",\"checkOut\":\""
            + checkIn.plusDays(1) + "\",\"guests\":1,\"usedMileage\":" + usedMileage + "}";
    }

    private long balanceOf(UserEntity user) {
        return users.findById(user.getUserId()).orElseThrow().getMileageBalance();
    }
}
