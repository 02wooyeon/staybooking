/* 객실 예약: 표시 가격은 참고용, 실제 금액과 재고는 서버가 다시 계산한다. */
(function () {
  'use strict';

  var params = new URLSearchParams(window.location.search);
  var accommodationId = Number(params.get('accommodationId'));
  var roomId = Number(params.get('roomId'));
  var alertBox = document.getElementById('booking-alert');
  var submit = document.getElementById('booking-submit-button');
  var form = document.getElementById('booking-form');
  var checkIn = document.getElementById('booking-check-in');
  var checkOut = document.getElementById('booking-check-out');
  var guests = document.getElementById('booking-guests');
  var room = null;

  if (!Number.isSafeInteger(accommodationId) || accommodationId < 1 ||
      !Number.isSafeInteger(roomId) || roomId < 1) {
    Ui.showAlert(alertBox, '잘못된 예약 접근입니다.', 'error');
    submit.disabled = true;
    return;
  }

  document.getElementById('booking-back-link').href =
    '/accommodation/detail.html?id=' + encodeURIComponent(accommodationId);
  submit.disabled = true;
  var today = new Date();
  var todayString = today.getFullYear() + '-' + String(today.getMonth() + 1).padStart(2, '0') + '-'
    + String(today.getDate()).padStart(2, '0');
  checkIn.min = todayString;
  checkOut.min = todayString;
  checkIn.value = params.get('checkIn') || '';
  checkOut.value = params.get('checkOut') || '';
  guests.value = params.get('guests') || '1';

  function formatPrice(value) {
    return Number(value).toLocaleString('ko-KR') + '원';
  }

  function nights() {
    if (!checkIn.value || !checkOut.value || checkOut.value <= checkIn.value) return 0;
    return Math.round((Date.parse(checkOut.value + 'T00:00:00Z')
      - Date.parse(checkIn.value + 'T00:00:00Z')) / 86400000);
  }

  function updatePrice() {
    var count = nights();
    document.getElementById('booking-night-count').textContent = count ? count + '박' : '-';
    document.getElementById('booking-total-price').textContent = room && count
      ? formatPrice(room.price * count) : '-';
  }

  checkIn.addEventListener('change', function () {
    if (checkIn.value) {
      var next = new Date(checkIn.value + 'T00:00:00Z');
      next.setUTCDate(next.getUTCDate() + 1);
      checkOut.min = next.toISOString().slice(0, 10);
      if (checkOut.value && checkOut.value <= checkIn.value) checkOut.value = '';
    }
    updatePrice();
  });
  checkOut.addEventListener('change', updatePrice);
  guests.addEventListener('input', updatePrice);

  async function load() {
    Ui.renderHeader();
    try {
      var stay = new URLSearchParams();
      if (checkIn.value && checkOut.value) {
        stay.set('checkIn', checkIn.value);
        stay.set('checkOut', checkOut.value);
      }
      var accommodation = await BookingApi.getAccommodation(accommodationId, stay);
      room = (accommodation.rooms || []).find(function (candidate) {
        return candidate.roomId === roomId;
      });
      if (!room) throw new Error('선택한 객실 정보를 찾을 수 없습니다.');
      document.getElementById('booking-accommodation-name').textContent = accommodation.name;
      document.getElementById('booking-room-name').textContent = room.name;
      document.getElementById('booking-address').textContent = accommodation.address;
      document.getElementById('booking-capacity').textContent = '최대 ' + room.maxGuests + '명';
      document.getElementById('booking-night-price').textContent = formatPrice(room.price);
      document.getElementById('booking-room-image').textContent = '객실 이미지 준비 중';
      guests.max = room.maxGuests;
      submit.disabled = false;
      updatePrice();
    } catch (e) {
      Ui.showAlert(alertBox, e.message, 'error');
    }
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    Ui.hideAlert(alertBox);
    var count = nights();
    var guestCount = Number(guests.value);
    if (!room || !count || checkIn.value < todayString ||
        !Number.isInteger(guestCount) || guestCount < 1 || guestCount > room.maxGuests) {
      Ui.showAlert(alertBox, '날짜와 인원수를 다시 확인해 주세요.', 'error');
      return;
    }
    Ui.setLoading(submit, true, '예약 중...');
    try {
      var booking = await BookingApi.createBooking({
        roomId: roomId,
        checkIn: checkIn.value,
        checkOut: checkOut.value,
        guests: guestCount
      });
      var destination = '/booking/my.html?created=' + encodeURIComponent(booking.bookingId);
      try {
        var capabilities = await BookingApi.getPaymentCapabilities();
        if (capabilities.tossEnabled && capabilities.tossClientKey &&
            booking.status === 'PAYMENT_PENDING') {
          destination = '/booking/payment/pay.html?bookingId=' + encodeURIComponent(booking.bookingId);
        }
      } catch (capabilityError) {
        // 예약은 이미 생성되었다. 결제 설정 조회 실패로 다시 예약을 만들지 않도록 내 예약으로 이동한다.
      }
      window.location.replace(destination);
    } catch (e) {
      if (e.status === 401) {
        window.location.href = '/auth/login.html?next=' + encodeURIComponent(
          window.location.pathname + window.location.search);
        return;
      }
      Ui.showAlert(alertBox, e.message, 'error');
      Ui.setLoading(submit, false);
    }
  });

  load();
})();
