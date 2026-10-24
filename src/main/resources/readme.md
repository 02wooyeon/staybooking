# 10-24 수정 내용
## 관리자 화면
- 리뷰 관리 페이지(reviews.html, admin-reviews.js)
- 숙소 등록 때 사진 같이 올리기
- 숙소 정보와 객실을 맨 아래 저장 하나로 저장
- 편의시설 체크박스, 이용 규칙 칸 생성
- 숙소 상태 필터 추가
- 뒤로가기 중복 등록 방지
- 승인자 로그인 이동 수정
- 숨김 버튼 CSS 수정
- 대시보드 리뷰 바로가기
- accommodation-detail.js: 예약하기가 quote.html로 가게 바꿈

## 백엔드
- 숙소 편의시설·이용 규칙 저장(Java 5개 파일)

## 기타
- static/booking/payment/ 폴더 삭제 및 booking-payment-live.js·booking.js·payment.js, 안 쓰는 CSS 삭제
- static/booking/create.html은 quote.html로 수정했으니 사용 자제, 삭제 요망

-----------------------------------------

# 10-23
## resource/admin-static/ 경로에 관리자 전용 도메인 추가
- 대시보드(index.html)
- 예약 관리(booking.html)
- 회원 관리(member.html)
- 숙소,객실 관리(accommodation.html)
- qna 관리(qna.html)
- 결제, 환불 (payments.html)
관리자 전용 페이지 생성

## 해야할일
- resource/admin-static/accommodation.html에서 숙소 등록 시 이미지를 같이 올릴 수 있도록 수정
- 리뷰 관리 페이지 생성 필요
- 승인자가 예약 승인, 처리 기능이 있는지 확인 필요

----------------------------------------

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

----------------------------------------

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