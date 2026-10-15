# MySQL 연결 준비

[← 가이드 홈](README.md) · [DB 스키마](DATABASE.md)

현재 코드의 `mysql` 프로필은 DB 접속 정보를 **환경변수**에서 읽습니다. 저장소에 비밀번호를 넣지 않습니다. 아직 초기화된 DB01에 연결하거나 계정을 다시 만들지는 않았습니다.

## DB01 준비

DB01에서 `staybooking` 데이터베이스와 애플리케이션 전용 계정을 준비합니다. WAS01의 DB망 주소는 `192.168.30.20`, DB01은 `192.168.30.30`으로 설계했습니다. 이 주소가 바뀌었다면 실제 네트워크 설정을 먼저 확인합니다.

```sql
CREATE DATABASE staybooking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'staybooking_app'@'192.168.30.20' IDENTIFIED BY '<DB01에서 직접 정한 비밀번호>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON staybooking.* TO 'staybooking_app'@'192.168.30.20';
```

초기 개발 중에는 Hibernate가 누락된 테이블을 만들 수 있도록 `ddl-auto=update`를 사용합니다. 테이블과 FK를 검토해 확정하면 `STAYBOOKING_DDL_AUTO=validate`로 바꿉니다. 기존 컬럼 삭제나 데이터 이관은 `update`가 대신 처리하지 않으므로, 변경 시 SQL과 백업 계획을 별도로 검토합니다.

## WAS01 실행 설정

WAS01에서 실행할 프로세스의 환경변수에 아래 값을 설정합니다. 비밀번호는 Git이나 채팅에 올리지 않습니다.

```text
SPRING_PROFILES_ACTIVE=mysql
STAYBOOKING_DB_URL=jdbc:mysql://192.168.30.30:3306/staybooking
STAYBOOKING_DB_USER=staybooking_app
STAYBOOKING_DB_PASSWORD=<DB01에서 정한 비밀번호>
STAYBOOKING_DDL_AUTO=update
```

Tomcat 서비스에 WAR를 배포할 때는 **Tomcat systemd 서비스의 환경변수**로 주입해야 합니다. Mac IDE에만 입력하면 WAS01 프로세스에는 전달되지 않습니다. 설정 후 WAS01에서 애플리케이션 로그의 DB 연결 성공 여부와 DB01의 `SHOW TABLES FROM staybooking;` 결과를 함께 확인합니다. `users`, `accommodations`, `rooms`, `bookings`, `payments`, `booking_cancellation_requests`가 생성 대상입니다.

로컬 자동 테스트는 별도 H2 설정을 사용합니다. 위 접속 값 없이도 `./gradlew test`가 실행됩니다.
