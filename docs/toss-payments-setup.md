# 토스페이먼츠 테스트 결제 설정

이 프로젝트는 토스페이먼츠 JavaScript SDK v2의 **주문서형 결제**를 사용한다. 현재 구현은 테스트 키(`test_gck_`/`test_gsk_`)만 활성화하며, 실제 금전 거래를 만드는 라이브 키는 지원하지 않는다.

1. [토스페이먼츠 개발자센터의 API 키](https://developers.tosspayments.com/my/api-keys)에서 **주문서형·결제창형 연동 키**의 테스트 클라이언트 키와 테스트 시크릿 키를 같은 상점의 한 쌍으로 확인한다. 문서용 키보다 팀 상점의 테스트 키를 사용하면 설정과 테스트 결제 내역을 확인하기 쉽다.
2. 상점관리자에서 주문서형 결제 UI를 활성화한다. 현재 구현은 `DONE`이 즉시 돌아오는 결제만 완료 처리하므로, **가상계좌는 결제 UI에서 제외**한다. 가상계좌를 쓰려면 입금 웹훅, 상태 조회, 만료·환불 처리를 먼저 구현해야 한다.
3. WAS 실행 환경에 아래 두 환경변수를 설정한다. 시크릿 키는 프런트엔드 파일, 설정 파일, Git, 메신저에 넣지 않는다.

   ```bash
   export STAYBOOKING_TOSS_CLIENT_KEY='test_gck_...'
   export STAYBOOKING_TOSS_SECRET_KEY='test_gsk_...'
   ```

4. 서버를 재시작하고 로그인한 뒤 예약을 생성한다. **내 예약 → 결제하기**에서 SDK 주문서를 열고 테스트 결제를 진행한다. 토스 인증이 성공하면 `/payment/success.html`이 서버의 `/api/v1/payments/toss/confirm`을 호출한다. 서버는 저장된 주문번호·금액과 사용자 소유권을 검사한 다음 토스 승인 API를 호출한다. 승인 응답이 `DONE`일 때만 예약이 `PENDING`(승인 대기)으로 바뀐다. 승인 응답이 유실되면 같은 주문번호로 토스 결제를 조회하며, 동일한 승인 요청을 다시 보내도 결제를 중복 기록하지 않는다.
5. 결제 요청에는 브라우저가 토스 SDK에 접근할 수 있어야 하고, WAS에서는 `https://api.tosspayments.com`으로 나가는 HTTPS 연결이 필요하다. 성공·실패 URL은 사용자가 실제 접속하는 같은 사이트 주소를 사용한다.

로그인한 사용자는 `GET /api/v1/payments/me`로 자신의 결제 시도와 환불 처리 상태를 조회할 수 있다. 각 항목에는 `paymentId`, `bookingId`, `orderId`, `paymentMethod`, `amount`, `status`, `paidAt`, `refundId`, `refundStatus`, `refundAmount`가 들어간다. 환불 요청이 없으면 환불 필드는 `null`이다. 이 조회 API는 토스 `paymentKey`와 시크릿 키를 응답에 포함하지 않는다. 프런트엔드의 내 예약 화면에서 이 API를 연결하면 결제 완료·환불 요청·환불 승인 상태를 표시할 수 있다.

관리자는 `GET /api/v1/admin/payments/search`에서 `bookingId`, `status`, `page`, `size`로 결제·환불 상태를 함께 검색할 수 있다. `page`는 0부터, `size`는 1~100이며 기본값은 각각 0과 20이다. 응답은 `content`, `page`, `size`, `totalElements`, `totalPages` 형태다. 기존 `GET /api/v1/admin/payments`의 목록 응답은 유지한다.

환불 검토 목록도 `GET /api/v1/admin/refunds/search`에서 같은 방식으로 `bookingId`, `status`(`REQUESTED`, `APPROVED`, `REJECTED`), `page`, `size`를 받을 수 있다. 기존 `GET /api/v1/admin/refunds`와 `PATCH /api/v1/admin/refunds/{id}`의 응답 형식은 유지한다.

토스 테스트 키가 설정된 환경에서 관리자는 `GET /api/v1/admin/payments/{paymentId}/reconciliation`으로 로컬 결제·환불 상태와 토스 주문번호 조회 결과를 비교할 수 있다. `result`가 `MATCHED`면 조회 시점의 두 기록이 일치하고, `REVIEW_REQUIRED`면 결제·환불 내역과 객실 재고를 직접 확인해야 한다. 로컬 모의 결제는 `NOT_APPLICABLE`이다. 이 API는 조회만 수행하며 토스 결제 키를 반환하거나 결제·예약·환불 상태를 자동으로 바꾸지 않는다. 토스 조회가 실패하면 성공으로 간주하지 않고 오류를 반환한다.

테스트 결제도 서버와 브라우저가 키를 읽어야 하므로, 팀원마다 키를 직접 환경변수로 설정한다. 실제 키 값은 보고서나 이 문서에 적지 않는다. 기존 **로컬 테스트 결제** 버튼은 토스 키가 설정되지 않은 로컬 환경에서만 화면에 표시된다.

예약 취소 시 환불 요청을 생성하고, 관리자가 승인하면 토스 테스트 취소 API를 호출한다. 환불 요청 번호를 멱등키로 사용해 재시도 시 중복 취소를 막는다. 예약 결제 가능 시간이 지난 뒤 토스에서는 승인됐지만 DB에는 저장되지 않은 건은 자동으로 예약을 완료하지 않는다. 이 경우 다른 예약이 이미 객실을 점유했을 수 있으므로 토스 결제 내역과 객실 재고를 확인해 수동으로 처리해야 한다.

운영 전에는 라이브 키 활성화와 함께 가상계좌 입금 웹훅(사용할 경우), 만료 후 불일치 결제의 관리 절차, 정산 검증을 추가해야 한다. 현재 코드에서 라이브 키는 의도적으로 비활성화돼 있다.

참고: [주문서형 결제 가이드](https://docs.tosspayments.com/guides/v2/payment-widget/integration), [결제 승인 API](https://docs.tosspayments.com/reference), [API 키](https://docs.tosspayments.com/reference/using-api/api-keys).
