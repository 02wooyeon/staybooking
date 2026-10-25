-- DB01 마일리지 이력 테이블 생성용 1회 수동 SQL.
-- users.mileage_balance 는 이미 있으므로 건드리지 않는다.
-- 백업과 현재 스키마 재확인 후에만 실행한다. MySQL DDL은 ROLLBACK으로 되돌릴 수 없다.

CREATE TABLE mileage_history (
    mileage_history_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    booking_id         BIGINT       NULL,
    type               VARCHAR(20)  NOT NULL,
    amount             BIGINT       NOT NULL,
    balance_after      BIGINT       NOT NULL,
    description        VARCHAR(255) NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (mileage_history_id),
    KEY idx_mileage_history_user_created (user_id, created_at),
    KEY idx_mileage_history_booking_type (booking_id, type),
    CONSTRAINT fk_mileage_history_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_mileage_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (booking_id)
);

-- 확인용 조회
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'mileage_history'
ORDER BY ORDINAL_POSITION;
