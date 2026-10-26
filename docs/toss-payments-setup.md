# 토스페이먼츠 테스트 결제 설정

이 프로젝트는 토스페이먼츠 JavaScript SDK v2를 사용한다. 넣은 테스트 키 종류에 따라 결제 화면이 정해진다. 실제 금전 거래를 만드는 라이브 키는 지원하지 않는다.

| 키 종류 | 클라이언트 키 / 시크릿 키 | 결제 화면 |
|---|---|---|
| 결제위젯 연동 키 | `test_gck_` / `test_gsk_` | 주문서형 결제위젯 (결제 수단 목록이 결제 페이지 안에 나온다) |
| API 개별 연동 키 | `test_ck_` / `test_sk_` | 결제창 (결제하기를 누르면 토스 결제창이 열린다, 카드) |

계약 전 상점은 보통 API 개별 연동 키만 받으므로, 팀 상점 키로 결제·웹훅을 확인하려면 개별 연동 키를 쓴다. 두 종류를 섞으면(`test_ck_` + `test_gsk_` 등) 토스 결제는 꺼진다. 결제 승인·조회·취소 API는 두 방식이 같다. 화면 방식은 `GET /api/v1/payments/capabilities`의 `tossMode`(`WIDGET`, `PAYMENT_WINDOW`)로 내려준다.

1. [토스페이먼츠 개발자센터의 API 키](https://developers.tosspayments.com/my/api-keys)에서 **주문서형·결제창형 연동 키**의 테스트 클라이언트 키와 테스트 시크릿 키를 같은 상점의 한 쌍으로 확인한다. 문서용 키보다 팀 상점의 테스트 키를 사용하면 설정과 테스트 결제 내역을 확인하기 쉽다.
2. 상점관리자에서 주문서형 결제 UI를 활성화한다. 현재 구현은 `DONE`이 즉시 돌아오는 결제만 완료 처리하므로, **가상계좌는 결제 UI에서 제외**한다. 가상계좌를 쓰려면 입금 웹훅, 상태 조회, 만료·환불 처리를 먼저 구현해야 한다.
3. WAS 실행 환경에 아래 두 환경변수를 설정한다. 시크릿 키는 프런트엔드 파일, 설정 파일, Git, 메신저에 넣지 않는다.

   ```bash
   export STAYBOOKING_TOSS_CLIENT_KEY='test_gck_...'   # 또는 test_ck_...
   export STAYBOOKING_TOSS_SECRET_KEY='test_gsk_...'   # 또는 test_sk_...
   ```

4. 서버를 재시작하고 로그인한 뒤 예약을 생성한다. **내 예약 → 결제하기**에서 SDK 주문서를 열고 테스트 결제를 진행한다. 토스 인증이 성공하면 `/payment/success.html`이 서버의 `/api/v1/payments/toss/confirm`을 호출한다. 서버는 저장된 주문번호·금액과 사용자 소유권을 검사한 다음 토스 승인 API를 호출한다. 승인 응답이 `DONE`일 때만 예약이 `PENDING`(승인 대기)으로 바뀐다. 승인 응답이 유실되면 같은 주문번호로 토스 결제를 조회하며, 동일한 승인 요청을 다시 보내도 결제를 중복 기록하지 않는다.
5. 결제 요청에는 브라우저가 토스 SDK에 접근할 수 있어야 하고, WAS에서는 `https://api.tosspayments.com`으로 나가는 HTTPS 연결이 필요하다. 성공·실패 URL은 사용자가 실제 접속하는 같은 사이트 주소를 사용한다.

로그인한 사용자는 `GET /api/v1/payments/me`로 자신의 결제 시도와 환불 처리 상태를 조회할 수 있다. 각 항목에는 `paymentId`, `bookingId`, `orderId`, `paymentMethod`, `amount`, `status`, `paidAt`, `refundId`, `refundStatus`, `refundAmount`가 들어간다. 환불 요청이 없으면 환불 필드는 `null`이다. 이 조회 API는 토스 `paymentKey`와 시크릿 키를 응답에 포함하지 않는다. 내 예약 조회에도 `refundStatus`와 `refundAmount`가 포함되어 자동 환불의 처리 중·완료·실패 상태를 표시할 수 있다.
객실 요금이 0원인 예약은 토스 결제를 준비하지 않는다. 예약자 본인이 `POST /api/v1/payments/free-complete`에 `{"bookingId": 예약번호}`를 보내면 서버가 금액 0원인 `FREE` 결제 기록을 만들고 예약을 승인 대기로 전환한다. 같은 요청을 다시 보내면 기존 기록을 반환하며, 유료 예약에는 사용할 수 없다. 예약 생성·견적 요청에 `usedMileage`를 넣으면 그만큼을 뺀 금액만 PG로 결제하며, 전액을 마일리지로 치르는 예약도 결제 금액이 0원이라 같은 무료 완료 경로를 쓴다. 잔액은 예약 생성이 아니라 결제가 확정될 때 차감한다([마일리지 이력](architecture/DATABASE.md#mileage_history)).
승인자·관리자의 예약 조회와 예약 처리 PATCH 응답도 같은 `refundStatus`, `refundAmount`를 담는다. 취소 처리 후 응답은 커밋된 환불 기록을 다시 읽어 반환하므로, 결제 취소 결과가 확인됐으면 즉시 `REFUNDED`가 표시된다.

관리자는 `GET /api/v1/admin/payments/search`에서 `bookingId`, `status`, `page`, `size`로 결제·환불 상태를 함께 검색할 수 있다. `page`는 0부터, `size`는 1~100이며 기본값은 각각 0과 20이다. 응답은 `content`, `page`, `size`, `totalElements`, `totalPages` 형태다. 기존 `GET /api/v1/admin/payments`의 목록 응답은 유지한다.

자동 환불 목록은 `GET /api/v1/admin/refunds/search`에서 `bookingId`, `status`(`REQUESTED`, `PROCESSING`, `REFUNDED`, `FAILED`), `page`, `size`로 조회한다. 기존 `APPROVED`, `REJECTED`는 과거 데이터 조회용이다. 대기·실패 건 또는 30초 이상 처리 중인 건은 결제대행사 내역을 확인한 뒤 `POST /api/v1/admin/refunds/{id}/retry`로 같은 환불 번호를 재시도할 수 있다. 기존 수동 승인·반려 PATCH는 제거됐다.
환불 조회 응답의 `retryable`은 현재 시점에 재시도할 수 있는지, `failureCode`는 마지막 실패 원인을 나타낸다. 처리 중인 건은 시작 후 30초가 지나야 재시도 가능해진다. 이미 `REFUNDED`인 건에 같은 관리자 재시도 요청이 다시 오면 PG 취소를 반복하지 않고 완료 상태를 반환한다.
관리자는 재시도 전에 `GET /api/v1/admin/refunds/{id}/reconciliation`으로 해당 환불의 로컬 상태와 토스 결제 상태·잔액을 비교할 수 있다. `NO_REFUND_DUE`는 환불액 0원 건이 로컬에서 완료됐고 토스 결제 잔액이 그대로인 정상 상태다. `NO_CANCELLATION_RECORDED`는 미완료 환불 건에서 조회 시점에 PG 취소가 기록되지 않았다는 뜻이고, `PROVIDER_BALANCE_MATCHES`는 잔액이 환불 금액과 맞지만 이 환불 요청으로 처리됐다고 확정할 수 없다는 뜻이다. 로컬 환불·결제 상태가 서로 다르거나 로컬 환불 완료인데 PG 취소가 없으면 `REVIEW_REQUIRED`다. `REVIEW_REQUIRED`와 `LOOKUP_FAILED`는 결제대행사 내역을 직접 확인해야 한다. 조회 API는 상태를 변경하지 않는다. 관리자 재시도 중 PG 처리 결과를 확인하지 못하면 환불 상태와 실패 코드를 저장하고 HTTP 502(`REFUND_RETRY_FAILED`)를 반환한다.

토스 테스트 키가 설정된 환경에서 관리자는 `GET /api/v1/admin/payments/{paymentId}/reconciliation`으로 로컬 결제·환불 상태와 토스 주문번호 조회 결과를 비교할 수 있다. 목록 조회는 `GET /api/v1/admin/payments/reconciliation/search`를 사용한다. 응답의 `bookingStatus`는 예약 상태이며, 결제 완료 건에는 연결된 환불의 `refundId`, `refundStatus`, `refundAmount`가 포함된다. 환불이 없거나 결제 준비·실패 건이면 환불 필드는 `null`이다. PG 조회에 실패한 목록 행에도 로컬 환불 정보는 남긴다. `result`가 `MATCHED`면 조회 시점의 두 기록이 일치하고, `PROVIDER_CHARGED_NOT_RECORDED`는 토스에서 전액 결제됐지만 로컬 결제는 준비 상태이며, `CANCELLED_BOOKING_CHARGED`는 여기에 예약 취소까지 확인된 경우다. 두 경우 모두 결제·환불 내역과 객실 재고를 직접 확인해야 한다. 예약 취소 후 환불 기록이 없는 결제, 아직 결제 대기인 예약에 연결된 완료 결제, 활성 예약에 연결된 환불 완료 결제는 금액과 PG 상태가 맞아도 `REVIEW_REQUIRED`다. 이 값은 그 밖의 불일치에도 사용된다. 로컬 모의 결제는 `NOT_APPLICABLE`이다. 이 API는 조회만 수행하며 토스 결제 키를 반환하거나 결제·예약·환불 상태를 자동으로 바꾸지 않는다. 토스 조회가 실패하면 성공으로 간주하지 않고 오류를 반환한다.

관리자는 `GET /api/v1/admin/statistics/payment-summary`로 로컬 DB에 기록된 전체 기간의 결제·환불 금액을 조회할 수 있다. `grossPaidAmount`는 결제 완료 후 부분·전액 환불된 원결제액까지 포함하며 미완료 결제는 제외한다. 기존 응답 필드명 `approvedRefundAmount`는 실제 환불 완료액(`REFUNDED`, 과거 `APPROVED`)을 뜻하고, `requestedRefundAmount`는 미완료액(`REQUESTED`, `PROCESSING`, `FAILED`)이다. `netPaidAmount`는 원결제액에서 완료된 환불액을 뺀 값이다. 이 수치는 내부 운영 집계이며 토스의 실제 정산·입금액과 수수료를 검증한 결과가 아니다.

같은 API에 `from=YYYY-MM-DD&to=YYYY-MM-DD`를 함께 보내면 양 끝 날짜를 포함한 기간별 금액을 조회한다. 날짜는 UTC 기준이다. 결제액은 `paidAt`, 승인 환불액은 `processedAt`, 대기 환불 요청액은 `createdAt`이 해당 기간에 속하는 건만 합산한다. 조회 기간에 과거 결제의 환불만 처리됐다면 `netPaidAmount`가 음수일 수 있다. 날짜를 보내지 않으면 기존처럼 전체 기간을 조회하며, 한쪽 날짜만 보내거나 시작일이 종료일보다 늦으면 400 오류를 반환한다.

날짜별 추이는 `GET /api/v1/admin/statistics/payment-daily?from=YYYY-MM-DD&to=YYYY-MM-DD`로 조회한다. 시작일과 종료일은 필수이며 양 끝을 포함해 최대 31일이다. 응답은 날짜 오름차순 배열이며 결제·환불이 없는 날짜도 금액 0으로 포함한다. 각 날짜의 금액 기준은 기간별 요약과 동일하다.

테스트 결제도 서버와 브라우저가 키를 읽어야 하므로, 팀원마다 키를 직접 환경변수로 설정한다. 실제 키 값은 보고서나 이 문서에 적지 않는다. 기존 **로컬 테스트 결제** 버튼은 토스 키가 설정되지 않은 로컬 환경에서만 화면에 표시된다.

예약 취소가 DB에 커밋되면 서버가 토스 테스트 취소 API를 자동 호출한다. 결과가 확인될 때만 `REFUNDED`로 기록하고, 실패하거나 응답을 확인하지 못하면 `FAILED`로 기록해 관리자가 확인·재시도한다. 환불 요청 번호를 멱등키로 사용해 재시도 시 중복 취소를 막는다. PG 취소 호출 뒤 로컬 결제 행이 처음 확인한 결제와 달라지면 `REFUND_PAYMENT_CHANGED`로 기록하고 다른 결제를 환불 완료로 바꾸지 않는다. 이 경우 PG 취소 여부를 직접 대조해야 한다. 예약 결제 가능 시간이 지난 뒤 토스에서는 승인됐지만 DB에는 저장되지 않은 건은 자동으로 예약을 완료하지 않는다. 이 경우 다른 예약이 이미 객실을 점유했을 수 있으므로 토스 결제 내역과 객실 재고를 확인해 수동으로 처리해야 한다.
토스 취소 응답에서는 결제 키·주문 번호·금액·잔액·상태와 함께 `lastTransactionKey`가 `cancels`의 완료 거래 `transactionKey`와 일치하는지 확인한다. 일치하지 않으면 로컬 환불을 완료하지 않고 관리자 확인 대상으로 남긴다.
토스 승인 API 호출 중에는 예약·결제 행의 잠금을 유지하지 않는다. 승인 결과를 받은 뒤 예약을 먼저 잠그고 결제 상태를 다시 확인해, 그 사이 예약이 취소됐다면 로컬 결제 완료 처리를 거부한다. 토스 승인만 성공한 경우에는 관리자 결제 대조 API에서 불일치를 확인해 수동으로 처리해야 한다.
결제 준비 기록을 다시 사용할 때와 토스 승인 호출 전·로컬 확정 시점에는 `paid_amount = total_amount - used_mileage`를 확인한다. 저장된 결제 금액이 예약 금액과 다르면 `PAYMENT_AMOUNT_MISMATCH`를 반환하며 PG 승인 요청을 보내지 않는다.
환불 실행 시 예약 상태가 `CANCELLED`인지 다시 확인한다. 환불 행만 존재하고 예약이 활성 상태인 불일치 건은 PG 취소를 호출하지 않고 `BOOKING_NOT_CANCELLED`로 처리한다.
MySQL 프로필의 복구 작업은 1분마다 신규 자동 환불 중 1분 넘게 대기하거나 2분 넘게 처리 중인 건을 최대 25건 확인한다. 과거 수동 환불은 자동 복구 대상에서 제외한다. `FAILED`는 관리자 확인 후 재시도한다.

운영 전에는 라이브 키 활성화와 함께 가상계좌 입금 웹훅(사용할 경우), 만료 후 불일치 결제의 관리 절차, 정산 검증을 추가해야 한다. 현재 코드에서 라이브 키는 의도적으로 비활성화돼 있다.

참고: [주문서형 결제 가이드](https://docs.tosspayments.com/guides/v2/payment-widget/integration), [결제 승인 API](https://docs.tosspayments.com/reference), [API 키](https://docs.tosspayments.com/reference/using-api/api-keys).

## 결제 Webhook (PAYMENT_003)

`POST /api/v1/payments/webhook`이 토스 `PAYMENT_STATUS_CHANGED` 이벤트를 받는다. 본문에서는 `orderId`만 쓰고, 실제 상태·금액은 시크릿 키로 토스에 다시 조회해 확인한 뒤(승인 응답 유실 복구와 같은 검증) 완료 처리한다. 이미 완료된 주문은 그대로 두므로 같은 이벤트가 여러 번 와도 한 번만 반영된다.

토스 개발자센터 **웹훅** 메뉴(https://developers.tosspayments.com/my/webhooks)에서 서버의 공개 주소 + `/api/v1/payments/webhook`을 등록하고 `PAYMENT_STATUS_CHANGED`를 선택한다. 웹훅은 **등록한 상점의 키로 결제했을 때만** 오므로, 문서용 공용 키(`test_gck_docs_...`)로 결제하면 오지 않는다. 로컬에서 확인할 때는 `ngrok http 8080`으로 임시 공개 주소를 만들고, 확인이 끝나면 바로 끈다.
