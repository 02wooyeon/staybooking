# 관리자 운영 통계

관리자(`ROLE_ADMIN`)는 `/api/v1/admin/statistics` 아래 API에서 운영 현황을 조회한다. 모든 날짜는 UTC 기준이다.

- `GET /overview`: 현재 상태별 회원·숙소·객실·예약·결제·환불·Q&A 건수. 과거 이벤트의 누적 건수가 아니다.
- `GET /payment-summary`: 전체 기간의 결제·환불 금액. `from=YYYY-MM-DD&to=YYYY-MM-DD`를 함께 보내면 해당 기간의 합계. 금액 기준과 토스 정산액과의 차이는 [토스 결제 설정](toss-payments-setup.md)을 참고한다.
- `GET /payment-daily?from=YYYY-MM-DD&to=YYYY-MM-DD`: 날짜별 결제·환불 금액.
- `GET /booking-daily?from=YYYY-MM-DD&to=YYYY-MM-DD`: 날짜별 예약 생성·승인·취소 건수.

두 일별 API는 시작일과 종료일을 필수로 받고 양 끝을 포함해 최대 31일을 조회한다. 거래나 예약 변동이 없는 날짜도 0으로 반환한다. 예약 생성은 `createdAt`, 승인은 `approvedAt`, 취소는 `cancelledAt`을 기준으로 집계한다. 승인 후 취소된 예약은 승인일과 취소일 양쪽에 각각 포함된다. 따라서 일별 승인·취소 건수를 현재 상태별 예약 건수와 합산하거나 동일한 값으로 해석하면 안 된다.
