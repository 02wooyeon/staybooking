package com.kisia.staybooking;

import java.time.LocalDate;

import com.kisia.staybooking.booking.BookingEntity;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.payment.PaymentEntity;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.refund.RefundEntity;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.room.RoomEntity;
import com.kisia.staybooking.user.UserEntity;
import com.kisia.staybooking.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFixtures.class)
class AdminMemberDetailApiTests {
    @Autowired private MockMvc mvc;
    @Autowired private TestFixtures fixtures;
    @Autowired private PaymentRepository payments;
    @Autowired private RefundRepository refunds;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void detailSummarisesBookingsPaymentsAndRefundsOfOneMember() throws Exception {
        RoomEntity room = fixtures.room(100_000, 2, 5);
        UserEntity member = fixtures.user("홍길동");
        UserEntity other = fixtures.user("남의계정");
        UserEntity admin = fixtures.user("관리자", UserRole.ROLE_ADMIN);
        LocalDate today = LocalDate.now(TestFixtures.KOREA);

        fixtures.booking(member, room, today.plusDays(1), today.plusDays(2), BookingStatus.PAYMENT_PENDING);
        fixtures.booking(member, room, today.plusDays(3), today.plusDays(4), BookingStatus.PENDING);
        BookingEntity paid = fixtures.booking(member, room, today.plusDays(5), today.plusDays(6),
            BookingStatus.COMPLETED);
        BookingEntity cancelled = fixtures.booking(member, room, today.plusDays(7), today.plusDays(8),
            BookingStatus.CANCELLED);
        // 다른 회원의 예약·결제는 집계에 들어가면 안 된다.
        BookingEntity others = fixtures.booking(other, room, today.plusDays(9), today.plusDays(10),
            BookingStatus.COMPLETED);

        pay(paid, 100_000, PaymentStatus.PAID);
        pay(cancelled, 80_000, PaymentStatus.REFUNDED);
        pay(others, 70_000, PaymentStatus.PAID);
        refund(cancelled, 80_000, RefundStatus.REFUNDED);
        refund(others, 70_000, RefundStatus.REFUNDED);

        mvc.perform(get("/api/v1/admin/members/" + member.getUserId()))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/members/" + member.getUserId())
                .header("Authorization", fixtures.bearer(member)))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/members/" + member.getUserId())
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(member.getUserId()))
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.role").value(UserRole.ROLE_USER.name()))
            .andExpect(jsonPath("$.bookings.paymentPending").value(1))
            .andExpect(jsonPath("$.bookings.approvalPending").value(1))
            .andExpect(jsonPath("$.bookings.completed").value(1))
            .andExpect(jsonPath("$.bookings.cancelled").value(1))
            .andExpect(jsonPath("$.payments.paidCount").value(1))
            // 환불된 건의 원결제액도 결제 실적에 포함한다.
            .andExpect(jsonPath("$.payments.grossPaidAmount").value(180_000))
            .andExpect(jsonPath("$.payments.refundedAmount").value(80_000))
            .andExpect(jsonPath("$.payments.pendingRefundAmount").value(0))
            .andExpect(jsonPath("$.activity.reviewCount").value(0))
            .andExpect(jsonPath("$.activity.questionCount").value(0))
            // 비밀번호 해시는 어떤 경우에도 내보내지 않는다.
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/v1/admin/members/999999999")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(get("/api/v1/admin/members/0")
                .header("Authorization", fixtures.bearer(admin)))
            .andExpect(status().isBadRequest());
    }

    private void pay(BookingEntity booking, long amount, PaymentStatus status) {
        PaymentEntity payment = payments.save(new PaymentEntity(booking,
            "order-" + booking.getBookingId() + "-" + System.nanoTime(), "CARD", amount));
        jdbc.update("update payments set status = ?, paid_at = current_timestamp where payment_id = ?",
            status.name(), payment.getPaymentId());
    }

    private void refund(BookingEntity booking, long amount, RefundStatus status) {
        RefundEntity refund = refunds.save(new RefundEntity(booking, amount, "테스트"));
        jdbc.update("update refunds set status = ?, processed_at = current_timestamp where refund_id = ?",
            status.name(), refund.getRefundId());
    }
}
