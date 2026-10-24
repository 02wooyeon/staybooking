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

  // 화면에서 계산한 값은 바로 보여 주는 참고값이고, 로그인 상태면 서버 견적(POST /bookings/quote)으로 바꾼다.
  var quoteSeq = 0;
  var soldOut = false;

  function updatePrice() {
    var count = nights();
    document.getElementById('booking-night-count').textContent = count ? count + '박' : '-';
    document.getElementById('booking-total-price').textContent = room && count
      ? formatPrice(room.price * count) : '-';
    refreshQuote(count);
  }

  async function refreshQuote(count) {
    var seq = ++quoteSeq;
    var guestCount = Number(guests.value);
    if (!room || !count || !Number.isInteger(guestCount) || guestCount < 1 || guestCount > room.maxGuests) {
      return;
    }
    try {
      var quote = await BookingApi.quoteBooking({
        roomId: roomId, checkIn: checkIn.value, checkOut: checkOut.value, guests: guestCount
      });
      if (seq !== quoteSeq) return; // 그사이 날짜를 바꿨으면 늦게 온 응답은 버린다
      document.getElementById('booking-room-total').textContent = formatPrice(quote.roomTotal);
      document.getElementById('booking-discount-amount').textContent = formatPrice(quote.discountAmount);
      document.getElementById('booking-total-price').textContent = formatPrice(quote.totalAmount);
      soldOut = !quote.available;
      submit.disabled = soldOut;
      if (soldOut) Ui.showAlert(alertBox, '선택한 날짜에 남은 객실이 없습니다. 다른 날짜를 선택해 주세요.', 'error');
      else Ui.hideAlert(alertBox);
    } catch (e) {
      // 로그인 전이거나 날짜가 맞지 않으면 참고값을 그대로 둔다 (예약할 때 서버가 다시 검사한다)
      if (seq === quoteSeq && soldOut) {
        soldOut = false;
        submit.disabled = false;
        Ui.hideAlert(alertBox);
      }
    }
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
      window.location.replace('/booking/my.html?created=' + encodeURIComponent(booking.bookingId));
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
