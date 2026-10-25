package com.kisia.staybooking.user;

import java.time.LocalDateTime;

/**
 * 관리자 회원 상세. 회원 기본 정보에 예약·결제·환불·활동 집계를 더한다.
 * 금액은 로컬 DB 기준 집계이며 결제대행사 정산액이 아니다.
 */
public record AdminMemberDetail(
    Long userId,
    String loginId,
    String name,
    String nickname,
    String email,
    String phone,
    UserRole role,
    UserStatus status,
    long mileageBalance,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    BookingCounts bookings,
    PaymentTotals payments,
    Activity activity
) {
    /** 현재 상태별 예약 건수. */
    public record BookingCounts(long paymentPending, long approvalPending, long completed, long cancelled) { }

    /**
     * grossPaidAmount 는 환불된 건의 원결제액까지 포함하고, refundedAmount 는 환불이 끝난 금액,
     * pendingRefundAmount 는 아직 끝나지 않은 환불 금액이다.
     */
    public record PaymentTotals(long paidCount, long grossPaidAmount, long refundedAmount,
                                long pendingRefundAmount) { }

    /** 후기·문의 활동. hidden 은 관리자가 숨긴 건이다. */
    public record Activity(long reviewCount, long hiddenReviewCount, long questionCount,
                           long unansweredQuestionCount) { }
}
