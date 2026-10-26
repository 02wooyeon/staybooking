-- DB01에 아직 적용하지 않은 객실 이미지 연결 테이블.
-- 적용 전 DB 백업과 현재 테이블 존재 여부를 확인한다.
CREATE TABLE room_files (
    room_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (room_id, file_id),
    UNIQUE KEY uk_room_files_file (file_id),
    CONSTRAINT fk_room_files_room
        FOREIGN KEY (room_id) REFERENCES rooms (room_id),
    CONSTRAINT fk_room_files_file
        FOREIGN KEY (file_id) REFERENCES files (file_id)
);
