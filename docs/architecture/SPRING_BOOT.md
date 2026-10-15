# Spring Boot 구현 지도

[← 가이드 홈](README.md) · [DB 스키마](DATABASE.md) · [예약 흐름](BOOKING_FLOW.md)

## 패키지와 주요 클래스

```text
src/main/java/com/kisia/staybooking/
├── user/           UserEntity, UserRepository, UserRole, UserStatus, UserService
├── auth/           AuthController, AuthService, RefreshTokenEntity, RefreshTokenRepository
├── accommodation/  AccommodationEntity, AccommodationRepository, AccommodationStatus,
│                   AccommodationController, AccommodationService, AdminAccommodationController
├── room/           RoomEntity, RoomRepository, RoomStatus, RoomService, AdminRoomController
├── booking/        BookingEntity, BookingRepository, BookingStatus, CancellationSource,
│                   BookingController, BookingService, BookingInventoryService,
│                   ApproverBookingController, AdminBookingController, BookingApprovalService
├── cancellation/   CancellationRequestEntity, CancellationRequestRepository,
│                   CancellationRequestStatus, CancellationService
├── payment/        PaymentEntity, PaymentRepository, PaymentStatus, PaymentService,
│                   PaymentController, PgClient
├── refund/         RefundEntity, RefundRepository, RefundStatus, RefundReasonType, RefundService
├── mileage/        MileageHistoryEntity, MileageHistoryRepository, MileageType, MileageService
├── review/         ReviewEntity, ReviewRepository, ReviewController, ReviewService
├── qna/            QnaEntity, QnaAnswerEntity, repositories, controllers, services
├── file/           FileEntity, FileRepository, FileService
└── common/         SecurityConfig, JwtAuthenticationFilter, ApiExceptionHandler
```

| 계층 | 책임 | 넣지 말아야 할 것 |
|---|---|---|
| Entity + enum | DB 컬럼·관계·상태 이름 | 클라이언트 응답 형태 |
| Repository | 저장·조회·잠금 쿼리 | 권한 정책·환불 계산 |
| Service | 트랜잭션, 본인 확인, 상태 전이, 재고·금액 계산 | HTTP 응답 구성 |
| Controller + DTO | 요청 형식 검증, HTTP 응답 | 직접 DB 변경·금액 신뢰 |

승인자와 관리자의 예약 변경은 컨트롤러는 각각 두되, `BookingApprovalService`를 공유합니다. 관리자도 Accept를 수행할 수 있으므로 같은 상태 전이 규칙을 써야 합니다. 승인자에게 숙소·객실 CRUD 권한은 주지 않습니다.

## 핵심 API 계약

| 작업 | Method / Path | 역할 | 서버 핵심 검사 |
|---|---|---|---|
| 예약 생성 | `POST /api/v1/bookings` | USER | 날짜·정원·매일 재고·서버 가격 |
| 내 예약 목록·상세 | `GET /api/v1/bookings/me`, `GET /api/v1/bookings/{bookingId}` | USER | 상세는 예약자 본인만 |
| 결제 준비·완료 | `POST /api/v1/payments/prepare`, `POST /api/v1/payments/complete` | USER | PG 금액·주문번호·중복 성공 |
| 취소 신청 | `POST /api/v1/bookings/{bookingId}/cancellation-requests` | USER | 본인·취소 가능 상태·중복 신청 |
| 승인자 예약 목록 | `GET /api/v1/approver/bookings` | APPROVER, ADMIN | 상태별 필터·페이지네이션 |
| 예약 승인 | `PATCH /api/v1/approver/bookings/{bookingId}` | APPROVER, ADMIN | `action=ACCEPT` → 예약 `COMPLETED` |
| 취소 신청 판단 | `PATCH /api/v1/approver/bookings/{bookingId}/cancellation-requests/{requestId}` | APPROVER, ADMIN | `decision=APPROVE` 또는 `REJECT` |
| 직접 취소 | `POST /api/v1/approver/bookings/{bookingId}/cancel` | APPROVER, ADMIN | 사유·상태·환불액·처리자 기록 |
| 관리자 전체 예약 | `GET /api/v1/admin/bookings`, `PATCH /api/v1/admin/bookings/{bookingId}` | ADMIN | 조회·승인·취소; 같은 서비스 호출 |

관리자 PATCH에서 취소는 `action=CANCEL`과 사유를 받되, 내부에서는 직접 취소 서비스를 호출합니다. 사용자 취소는 기존 시트의 즉시 취소 경로 대신 **신청** 경로로 바꿉니다. DTO에 임의의 `status` 값을 받지 말고 허용된 `action`을 enum으로 받습니다.

## enum 목록

```text
UserRole                  ROLE_USER, ROLE_APPROVER, ROLE_ADMIN
UserStatus                ACTIVE, SUSPENDED, WITHDRAWN
AccommodationStatus       ACTIVE, INACTIVE
RoomStatus                ACTIVE, INACTIVE
BookingStatus             PAYMENT_PENDING, PENDING, COMPLETED, CANCELLED
CancellationSource        USER_REQUEST, APPROVER_DIRECT, ADMIN_DIRECT, SYSTEM
CancellationRequestStatus REQUESTED, APPROVED, REJECTED
PaymentStatus             READY, PAID, PARTIALLY_REFUNDED, REFUNDED, FAILED
RefundStatus              REQUESTED, PROCESSING, REFUNDED, FAILED
RefundReasonType          USER_FAULT, ACCOMMODATION_FAULT, FORCE_MAJEURE
MileageType               EARN, USE, REFUND
```

## 구현 순서

1. 2026-10-15에 기존 연동 검증용 DB를 백업 후 제거했다. 새 Entity 구현 전에 새 `staybooking` DB를 생성한다. 이전 `reservations`/`host_id` 구조를 재사용하지 않는다.
2. 팀 시트의 실제 ‘API 명세서’와 ‘테이블 명세서’를 이 가이드에 맞춘다.
3. `users` → `accommodations` → `rooms` → `bookings` Entity·enum·Repository를 먼저 만든다.
4. 객실 유형별 재고 잠금과 결제 전 승인 차단을 구현한다.
5. 취소 신청·직접 취소·환불·마일리지 기록을 이어 붙인다.
6. 리뷰·Q&A·파일·Refresh Token 기능을 붙인다.
7. MySQL의 FK/인덱스 확인, 서버 기동, `ddl-auto=validate`, 권한·상태·동시성 시나리오를 검증한다.

### 꼭 확인할 동작

- `total_quantity=2`인 객실 유형에 같은 날짜 예약 2건은 성공, 3번째는 거절.
- 결제하지 않은 예약은 Accept 불가. Accept와 동시에 예약은 `COMPLETED`. 숙박 이용 완료는 미설계.
- 일반 사용자는 승인·직접 취소 불가. 승인자는 숙소 관리 불가.
- 취소 신청만으로 예약 상태가 바뀌지 않음. 승인 또는 직접 취소 후에만 `CANCELLED`.
- 승인·완료·취소 처리자와 시각이 저장됨. 중복 환불·중복 마일리지 복원이 일어나지 않음.
