-- DB01에 아직 적용하지 않은 숙소 이미지 연결 테이블.
-- 적용 전 DB 백업과 현재 테이블 존재 여부를 확인한다.
CREATE TABLE accommodation_files (
    accommodation_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (accommodation_id, file_id),
    UNIQUE KEY uk_accommodation_files_file (file_id),
    CONSTRAINT fk_accommodation_files_accommodation
        FOREIGN KEY (accommodation_id) REFERENCES accommodations (accommodation_id),
    CONSTRAINT fk_accommodation_files_file
        FOREIGN KEY (file_id) REFERENCES files (file_id)
);
