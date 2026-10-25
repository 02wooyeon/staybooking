# MySQL 연결 준비

[← 가이드 홈](README.md) · [DB 스키마](DATABASE.md)

현재 코드의 `mysql` 프로필은 DB 접속 정보를 **환경변수**에서 읽습니다. 저장소에 비밀번호를 넣지 않습니다. 2026-10-25 읽기 전용 확인에서 DB01의 `staybooking` DB와 14개 테이블을 확인했습니다. Spring Boot 애플리케이션의 현재 DB01 연결 상태는 이 확인만으로 판단할 수 없습니다.

## 팀원 개발 접속

기존 VPN을 통해 DB01에 SSH 로그인할 수 있는 팀원은 별도 DB 비밀번호 입력 없이 다음 명령으로 접속합니다.

```bash
ssh -p 2222 kisia@100.78.65.82
staybooking-db
```

위 SSH 명령의 `kisia`는 예시 사용자명입니다. 팀원이 DB01에 다른 SSH 계정으로 로그인한다면 그 계정명으로 바꿔도 됩니다.

`staybooking-db`는 DB01 내부에서 `staybooking_dev@localhost` 공용 개발 계정으로 `staybooking` DB를 엽니다. 읽기·쓰기와 개발 중 스키마 변경 권한을 이 DB에만 부여했습니다. 2026-10-15에 일반 SSH 사용자로 접속하고 임시 테이블의 생성·삽입·조회·수정·삭제를 검증했습니다. MySQL 포트의 외부 노출이나 기존 VPN 규칙은 바꾸지 않았습니다.

공용 계정의 접속 정보는 DB01의 `/etc/staybooking/dev-client.cnf`에 있으며 모든 DB01 SSH 사용자가 읽을 수 있습니다. 이는 팀 개발 중 접근을 단순화하기 위한 설정이므로, 보안 조치 단계에서 개인 계정과 최소 권한으로 전환합니다. 애플리케이션의 `staybooking_app` 계정은 이 개발 계정과 별도로 유지합니다.

## DB01 준비

DB01에서 `staybooking` 데이터베이스와 애플리케이션 전용 계정을 준비했습니다. WAS01의 DB망 주소는 `192.168.30.20`, DB01은 `192.168.30.30`입니다. 아래 SQL은 수행한 작업의 기록이며 **다시 실행할 필요가 없습니다**.

```sql
CREATE DATABASE staybooking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'staybooking_app'@'192.168.30.20' IDENTIFIED BY '<DB01에서 직접 정한 비밀번호>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON staybooking.* TO 'staybooking_app'@'192.168.30.20';
```

초기 개발 중에는 Hibernate가 누락된 테이블을 만들 수 있도록 `ddl-auto=update`를 사용합니다. 테이블과 FK를 검토해 확정하면 `STAYBOOKING_DDL_AUTO=validate`로 바꿉니다. 기존 컬럼 삭제나 데이터 이관은 `update`가 대신 처리하지 않으므로, 변경 시 SQL과 백업 계획을 별도로 검토합니다.

### 자동 환불 스키마 변경 준비

2026-10-25 DB01에서 `refunds.status`는 `enum('APPROVED','REJECTED','REQUESTED')`였고, `started_at`, `failure_code`, `auto_processing`은 없었습니다. `refunds` 행은 0건이었습니다. 새 자동 환불 코드를 DB01에 배포하기 전에 [수동 변경 SQL](../sql/2026-10-25-refunds-auto-processing.sql)을 적용해야 합니다. 이 SQL은 저장소에 **준비만** 했으며 DB01에서는 아직 실행하지 않았습니다.

적용 직전에 `SHOW CREATE TABLE refunds;`와 `SELECT COUNT(*) FROM refunds;`로 상태를 다시 확인하고 DB를 백업합니다. 다른 팀원이 그사이에 스키마를 변경했다면 SQL을 그대로 실행하지 말고 변경 내용을 먼저 비교합니다. 적용 후 SQL 하단의 컬럼 조회 결과에서 `status`가 `varchar(30)`이고 새 컬럼 3개가 보여야 합니다. MySQL DDL은 트랜잭션 취소로 되돌릴 수 없으므로 백업 파일을 별도로 보관합니다.
이 SQL은 `refunds` 테이블만 다룹니다. 전체 애플리케이션 배포 전에는 다른 테이블과 FK도 현재 Entity 기준으로 별도 검증해야 합니다.

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
