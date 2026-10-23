-- 로컬(local 프로필) 실행 때만 들어가는 샘플 데이터. 팀 DB(mysql 프로필)에는 들어가지 않는다.
-- 테스트 계정 비밀번호는 모두 Test1234! 이다. (실제 서비스 계정 아님)
--   admin@stay.test  관리자
--   guest@stay.test  일반 회원, 스테이 강남 숙박 완료(후기 작성 가능)
--   friend@stay.test 일반 회원, 스테이 강남 숙박 완료(후기 작성 가능)
INSERT INTO users (login_id, password_hash, name, nickname, email, phone, role, status, mileage_balance, token_version, created_at, updated_at) VALUES
 ('admin@stay.test', '$2a$10$i3/t0PU9aKU6EfC3yRFfl.slweo8aofUKKu.xh3Lfbtj2XBylN8Ja', '관리자', NULL, 'admin@stay.test', '010-1111-1111', 'ROLE_ADMIN', 'ACTIVE', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('guest@stay.test', '$2a$10$i3/t0PU9aKU6EfC3yRFfl.slweo8aofUKKu.xh3Lfbtj2XBylN8Ja', '홍길동', NULL, 'guest@stay.test', '010-2222-2222', 'ROLE_USER', 'ACTIVE', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('friend@stay.test', '$2a$10$i3/t0PU9aKU6EfC3yRFfl.slweo8aofUKKu.xh3Lfbtj2XBylN8Ja', '김스테이', '여행러', 'friend@stay.test', '010-3333-3333', 'ROLE_USER', 'ACTIVE', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO accommodations (name, address, description, check_in_time, check_out_time, status, created_at, updated_at) VALUES
 ('스테이 강남', '서울특별시 강남구 테헤란로 1', '강남 중심에 위치한 편안한 숙소입니다.', '15:00:00', '11:00:00', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('해운대 오션뷰', '부산광역시 해운대구 우동 100', '바다가 보이는 객실이 있는 숙소입니다.', '15:00:00', '11:00:00', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('온천동 한옥스테이', '부산광역시 동래구 온천동 50', '조용한 한옥 숙소입니다.', '16:00:00', '10:00:00', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO rooms (accommodation_id, name, description, price, capacity, total_quantity, status, created_at, updated_at) VALUES
 (1, '스탠다드 더블', '퀸 침대 1개', 90000, 2, 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (1, '디럭스 트윈', '싱글 침대 2개', 130000, 3, 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (2, '오션뷰 더블', '바다 전망', 150000, 2, 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, '한옥 온돌방', '온돌 2인실', 110000, 2, 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
-- 홍길동·김스테이: 스테이 강남에 지난주 숙박 완료(승인 완료 + 체크아웃 지남) → 후기 작성 가능
INSERT INTO bookings (user_id, room_id, check_in_date, check_out_date, guests, status, total_amount, used_mileage, payment_expires_at, created_at, updated_at) VALUES
 (2, 1, DATEADD('DAY', -7, CURRENT_DATE), DATEADD('DAY', -5, CURRENT_DATE), 2, 'COMPLETED', 180000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 (3, 1, DATEADD('DAY', -4, CURRENT_DATE), DATEADD('DAY', -3, CURRENT_DATE), 1, 'COMPLETED', 90000, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
