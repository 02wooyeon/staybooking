-- DB01 refunds 테이블용 1회 수동 변경 SQL.
-- 사전 확인(2026-10-25): status enum('APPROVED','REJECTED','REQUESTED'),
-- started_at / failure_code / auto_processing 없음, refunds 0행.
-- 백업과 현재 스키마 재확인 후에만 실행한다. MySQL DDL은 ROLLBACK으로 되돌릴 수 없다.

ALTER TABLE refunds
    MODIFY COLUMN status VARCHAR(30) NOT NULL,
    ADD COLUMN started_at DATETIME(6) NULL,
    ADD COLUMN failure_code VARCHAR(80) NULL,
    ADD COLUMN auto_processing BOOLEAN NULL;

-- 과거 수동 환불 행은 auto_processing을 NULL로 유지한다.
-- 새 애플리케이션이 생성하는 자동 환불 행만 TRUE를 저장한다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'refunds'
  AND COLUMN_NAME IN ('status', 'started_at', 'failure_code', 'auto_processing')
ORDER BY ORDINAL_POSITION;
