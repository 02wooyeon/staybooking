package com.kisia.staybooking.refund;
public enum RefundStatus {
    REQUESTED, PROCESSING, REFUNDED, FAILED,
    // 기존 DB 행을 읽기 위한 이전 상태값. 새 환불 처리에서는 사용하지 않는다.
    APPROVED, REJECTED
}
