package com.kisia.staybooking.user;

import java.util.List;

import com.kisia.staybooking.booking.BookingRepository;
import com.kisia.staybooking.booking.BookingStatus;
import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentRepository;
import com.kisia.staybooking.payment.PaymentStatus;
import com.kisia.staybooking.qna.QnaRepository;
import com.kisia.staybooking.refund.RefundRepository;
import com.kisia.staybooking.refund.RefundStatus;
import com.kisia.staybooking.review.ReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 회원 상세 조회. 금액 기준은 운영 통계(AdminStatisticsService)와 같게 맞춘다. */
@Service
public class AdminMemberQueryService {
    // 환불됐더라도 원결제액은 결제 실적으로 본다.
    private static final List<PaymentStatus> PAID_STATUSES =
        List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    // APPROVED는 자동 환불 이전에 쓰던 완료 상태다.
    private static final List<RefundStatus> SETTLED_REFUNDS =
        List.of(RefundStatus.REFUNDED, RefundStatus.APPROVED);
    private static final List<RefundStatus> PENDING_REFUNDS =
        List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING, RefundStatus.FAILED);

    private final UserRepository users;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final ReviewRepository reviews;
    private final QnaRepository questions;

    public AdminMemberQueryService(UserRepository users, BookingRepository bookings, PaymentRepository payments,
                                   RefundRepository refunds, ReviewRepository reviews, QnaRepository questions) {
        this.users = users;
        this.bookings = bookings;
        this.payments = payments;
        this.refunds = refunds;
        this.reviews = reviews;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public AdminMemberDetail detail(Long userId) {
        UserEntity user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
        return new AdminMemberDetail(user.getUserId(), user.getLoginId(), user.getName(), user.getNickname(),
            user.getEmail(), user.getPhone(), user.getRole(), user.getStatus(), user.getMileageBalance(),
            user.getCreatedAt(), user.getUpdatedAt(),
            new AdminMemberDetail.BookingCounts(
                bookings.countByUserUserIdAndStatus(userId, BookingStatus.PAYMENT_PENDING),
                bookings.countByUserUserIdAndStatus(userId, BookingStatus.PENDING),
                bookings.countByUserUserIdAndStatus(userId, BookingStatus.COMPLETED),
                bookings.countByUserUserIdAndStatus(userId, BookingStatus.CANCELLED)),
            new AdminMemberDetail.PaymentTotals(
                payments.countByBookingUserUserIdAndStatus(userId, PaymentStatus.PAID),
                payments.sumPaidAmountByUserAndStatusIn(userId, PAID_STATUSES),
                refunds.sumAmountByUserAndStatusIn(userId, SETTLED_REFUNDS),
                refunds.sumAmountByUserAndStatusIn(userId, PENDING_REFUNDS)),
            new AdminMemberDetail.Activity(
                reviews.countByUserUserId(userId),
                reviews.countByUserUserIdAndDeletedTrue(userId),
                questions.countByUserUserId(userId),
                questions.countVisibleUnansweredByUser(userId)));
    }
}
