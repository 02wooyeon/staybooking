# 10-19
## 추가 목록
우측 상단 매뉴에 예약 정보, 고객센터, 설정 추가

## 내 예약 정보
내 예약 상세 클릭 시 해당 숙소 페이지로 이동
예약번호는 16자리로 영문+숫자로 설정

## 고객센터
setting 페이지에 문의하기 추가

## 설정
비로그인 시 설정으로 접속.
로그인 시 내 정보 관리로 이동.
계정 정보에는 mock데이터 입력되어있음

-----------------------------------------
# 10-22
예약 정보 확인 페이지 booking/create.html -> booking/quote.html로 변경
하단에 이용자 정보, 마일리지 사용. 결제수단 입력 칸 생성
이용자 정보에 계정 이름, 연락처 정보를 기본으로 작성

booking/payment/ 디렉터리 내에 complete, fail, pay, success 구현
결제 완료: complete.html
결제 실패: fail.html
결재 대기: pay.html
결제 성공: success.html
결제 중(pay.html) -> 결제성공(success.html) -> 결제완료(complete.html) or 결제실패(fail.html)로 구현

후기 작성 페이지를 accommodation/review.html로 새 페이지를 생성
페이지 작성, 수정 시 해당 페이지로 이동
