package com.kisia.staybooking.statistics;

/** 금액 정산과 구분되는 현재 상태별 운영 건수. */
public record AdminOverview(
    long activeMembers,
    long activeAccommodations,
    long activeRooms,
    long paymentPendingBookings,
    long approvalPendingBookings,
    long approvedBookings,
    long cancelledBookings,
    long paidPayments,
    long requestedRefunds,
    long unansweredQuestions
) { }
