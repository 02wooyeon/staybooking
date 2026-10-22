/*
 * 결제 화면 (PAYMENT_001, PAYMENT_002)
 *
 *   결제 대기  /booking/payment/pay.html?bookingId=      예약 요약 + 남은 시간 + 결제하기
 *   결제 성공  /booking/payment/success.html?paymentKey=&orderId=&amount=
 *              PG가 성공으로 돌려보낸 직후. 서버 검증이 끝나야 완료로 이동한다.
 *   결제 완료  /booking/payment/complete.html?bookingId=
 *   결제 실패  /booking/payment/fail.html?code=&orderId=
 *
 * 백엔드·PG 연동 전이라 예약 화면(booking.js)이 sessionStorage에 남긴 mock 예약으로 동작한다.
 * PG(토스페이먼츠/포트원)를 정하면 아래 "실제 연동" 주석 자리만 바꾼다.
 */
(function () {
  'use strict';

  var USE_MOCK_DATA = true;

  var STORE_KEY = 'staybookMockPayments';
  var PAGE = document.body.dataset.page;

  var STATUS_LABELS = {
    PAYMENT_PENDING: '결제 대기',
    PENDING: '승인 대기',
    COMPLETED: '예약 완료',
    CANCELLED: '취소됨'
  };

  var METHOD_LABELS = {
    CARD: '카드',
    EASY_PAY: '간편 결제',
    MILEAGE: '마일리지 전액 결제'
  };

  var EASY_PAY_LABELS = {
    KAKAOPAY: '카카오페이',
    NAVERPAY: '네이버페이',
    TOSSPAY: '토스페이'
  };

  /*
   * PG 실패 코드 → 안내 문구
   * 주소의 message 값은 누구나 바꿔 보낼 수 있으므로 화면에 그대로 쓰지 않고,
   * 알려진 코드만 우리 문구로 바꿔 보여 준다. (코드 이름은 PG를 정한 뒤 맞춘다)
   */
  var FAIL_MESSAGES = {
    PAY_PROCESS_CANCELED: '결제를 취소하셨습니다.',
    USER_CANCEL: '결제를 취소하셨습니다.',
    PAY_PROCESS_ABORTED: '결제가 중단되었습니다. 다시 시도해 주세요.',
    REJECT_CARD_COMPANY: '카드사에서 결제를 승인하지 않았습니다. 다른 카드로 시도해 주세요.',
    EXCEED_MAX_CARD_LIMIT: '카드 한도가 초과되었습니다. 다른 결제수단으로 시도해 주세요.',
    INVALID_CARD_EXPIRATION: '카드 유효기간을 확인해 주세요.',
    VERIFY_FAILED: '결제 금액이 확인되지 않아 결제가 취소되었습니다.',
    INVALID_REQUEST: '잘못된 결제 요청입니다.',
    PAYMENT_EXPIRED: '결제 기한이 지나 예약이 취소되었습니다.'
  };
  var DEFAULT_FAIL_MESSAGE = '결제가 정상적으로 처리되지 않았습니다. 잠시 후 다시 시도해 주세요.';

  Ui.renderHeader();

  /* ---------- 공통 ---------- */

  function $(id) { return document.getElementById(id); }

  function params() { return new URLSearchParams(window.location.search); }

  function formatPrice(value) {
    return Number(value).toLocaleString('ko-KR') + '원';
  }

  function isValidId(value) {
    return /^[1-9]\d{0,18}$/.test(String(value || ''));
  }

  function isValidOrderId(value) {
    return /^[A-Za-z0-9_-]{6,64}$/.test(String(value || ''));
  }

  function loadStore() {
    try {
      var stored = JSON.parse(sessionStorage.getItem(STORE_KEY));
      return stored && typeof stored === 'object' ? stored : {};
    } catch (e) {
      return {};
    }
  }

  function saveBooking(booking) {
    var store = loadStore();
    store[booking.bookingId] = booking;
    try {
      sessionStorage.setItem(STORE_KEY, JSON.stringify(store));
    } catch (e) { /* 저장소를 못 쓰면 이 탭에서만 유지되지 않음 */ }
  }

  function findBooking(bookingId) {
    return loadStore()[bookingId] || null;
  }

  function findBookingByOrderId(orderId) {
    var store = loadStore();
    var keys = Object.keys(store);
    for (var i = 0; i < keys.length; i++) {
      if (store[keys[i]].orderId === orderId) return store[keys[i]];
    }
    return null;
  }

  function remainingMs(booking) {
    return new Date(booking.paymentExpiresAt).getTime() - Date.now();
  }

  function formatRemaining(ms) {
    var total = Math.max(0, Math.floor(ms / 1000));
    var m = Math.floor(total / 60);
    var s = total % 60;
    return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
  }

  function describeMethod(booking) {
    if (booking.paymentMethod === 'EASY_PAY') {
      return METHOD_LABELS.EASY_PAY + ' (' + (EASY_PAY_LABELS[booking.easyPayProvider] || '-') + ')';
    }
    return METHOD_LABELS[booking.paymentMethod] || '-';
  }

  // <dl class="pay-summary"> 에 [라벨, 값] 목록을 채운다 (값은 textContent로만 넣는다)
  function renderSummary(dl, rows) {
    dl.textContent = '';
    rows.forEach(function (row) {
      var div = document.createElement('div');
      if (row[2]) div.className = row[2];
      var dt = document.createElement('dt');
      dt.textContent = row[0];
      var dd = document.createElement('dd');
      if (row[1] instanceof Node) dd.append(row[1]);
      else dd.textContent = row[1];
      div.append(dt, dd);
      dl.append(div);
    });
  }

  function stayText(booking) {
    return booking.checkIn + ' ~ ' + booking.checkOut + ' (' + booking.nights + '박) · ' + booking.guests + '명';
  }

  // 문서에 한 번만 보이는 상태 영역을 고른다 (data-state="loading|ready|error" 등)
  function showState(name) {
    document.querySelectorAll('[data-state]').forEach(function (el) {
      el.hidden = el.dataset.state !== name;
    });
  }

  function showPageError(message) {
    $('pay-error-message').textContent = message;
    showState('error');
  }

  /* ---------- 결제 대기 ---------- */

  function initPending() {
    var bookingId = params().get('bookingId');
    if (!isValidId(bookingId)) {
      showPageError('잘못된 접근입니다.');
      return;
    }

    /*
     * 실제 연동: GET /api/v1/bookings/{bookingId} (본인 예약만, 서버가 확인)
     */
    var booking = USE_MOCK_DATA ? findBooking(bookingId) : null;
    if (!booking) {
      showPageError('예약 정보를 찾을 수 없습니다.');
      return;
    }
    if (booking.status !== 'PAYMENT_PENDING') {
      // 이미 결제했거나 취소된 예약
      window.location.replace(booking.status === 'CANCELLED'
        ? '/booking/my.html'
        : '/booking/payment/complete.html?bookingId=' + encodeURIComponent(booking.bookingId));
      return;
    }

    var badge = document.createElement('span');
    badge.className = 'pay-status-badge';
    badge.textContent = STATUS_LABELS[booking.status];

    renderSummary($('pay-summary'), [
      ['예약번호', Ui.formatBookingNumber(booking.bookingNumber)],
      ['숙소 · 객실', booking.accommodationName + ' · ' + booking.roomName],
      ['일정', stayText(booking)],
      ['결제수단', describeMethod(booking)],
      ['상태', badge],
      ['결제할 금액', formatPrice(booking.finalAmount), 'total']
    ]);
    showState('ready');

    var timer = $('pay-timer');
    var payButton = $('pay-button');
    var cancelButton = $('pay-cancel-button');

    function tick() {
      var left = remainingMs(booking);
      timer.textContent = '남은 시간 ' + formatRemaining(left);
      if (left <= 0) {
        clearInterval(interval);
        booking.status = 'CANCELLED';
        saveBooking(booking);
        window.location.replace('/booking/payment/fail.html?code=PAYMENT_EXPIRED&orderId='
          + encodeURIComponent(booking.orderId));
      }
    }
    var interval = setInterval(tick, 1000);
    tick();

    payButton.addEventListener('click', function () {
      payButton.disabled = true;
      payButton.textContent = '결제창 여는 중...';

      if (USE_MOCK_DATA) {
        // PG 결제창 대신 성공 리다이렉트를 흉내 낸다
        window.location.href = '/booking/payment/success.html'
          + '?paymentKey=' + encodeURIComponent('mock_' + Date.now())
          + '&orderId=' + encodeURIComponent(booking.orderId)
          + '&amount=' + encodeURIComponent(booking.finalAmount);
        return;
      }

      /*
       * 실제 연동
       * 1) POST /api/v1/payments/prepare { bookingId }
       *    → 서버가 DB 가격으로 계산한 { orderId, amount, orderName } (화면 금액을 쓰지 않는다)
       * 2) PG SDK 결제창 호출 (클라이언트 키만 사용, 시크릿 키는 서버에만)
       *    successUrl: location.origin + '/booking/payment/success.html'
       *    failUrl:    location.origin + '/booking/payment/fail.html'
       *    간편 결제면 booking.easyPayProvider를 SDK 옵션으로 넘긴다
       */
    });

    // 화면 시연용: PG 실패 리다이렉트 흉내
    var failDemo = $('pay-fail-demo');
    if (failDemo) {
      failDemo.hidden = !USE_MOCK_DATA;
      failDemo.addEventListener('click', function () {
        window.location.href = '/booking/payment/fail.html?code=REJECT_CARD_COMPANY&orderId='
          + encodeURIComponent(booking.orderId);
      });
    }

    var cancelBox = $('pay-cancel-confirm');
    cancelButton.addEventListener('click', function () {
      cancelBox.hidden = false;
      $('pay-cancel-yes').focus();
    });
    $('pay-cancel-no').addEventListener('click', function () {
      cancelBox.hidden = true;
      cancelButton.focus();
    });
    $('pay-cancel-yes').addEventListener('click', function () {
      /*
       * 실제 연동: 결제 전 예약 취소 API (서버가 본인·상태를 확인)
       */
      clearInterval(interval);
      booking.status = 'CANCELLED';
      saveBooking(booking);
      window.location.replace('/booking/my.html');
    });
  }

  /* ---------- 결제 성공 (서버 검증 중) ---------- */

  function initSuccess() {
    var p = params();
    var paymentKey = p.get('paymentKey');
    var orderId = p.get('orderId');
    var amount = Number(p.get('amount'));

    function fail(code) {
      window.location.replace('/booking/payment/fail.html?code=' + encodeURIComponent(code)
        + (isValidOrderId(orderId) ? '&orderId=' + encodeURIComponent(orderId) : ''));
    }

    if (!paymentKey || paymentKey.length > 200 || !isValidOrderId(orderId)
        || !Number.isInteger(amount) || amount < 0) {
      fail('INVALID_REQUEST');
      return;
    }

    var booking = USE_MOCK_DATA ? findBookingByOrderId(orderId) : null;

    // 새로고침 등으로 다시 들어온 경우: 이미 끝난 결제는 다시 처리하지 않는다
    if (booking && booking.status !== 'PAYMENT_PENDING') {
      window.location.replace('/booking/payment/complete.html?bookingId=' + encodeURIComponent(booking.bookingId));
      return;
    }

    if (USE_MOCK_DATA) {
      setTimeout(function () {
        $('pay-step-verify').className = 'done';
        $('pay-step-verify').textContent = '✓ 결제 금액·주문번호 확인';

        // 서버가 할 검증을 흉내: 주소의 금액이 예약 금액과 다르면 실패
        if (!booking || amount !== booking.finalAmount) {
          fail('VERIFY_FAILED');
          return;
        }
        booking.status = 'PENDING';
        booking.paidAt = new Date().toISOString();
        saveBooking(booking);
        window.location.replace('/booking/payment/complete.html?bookingId=' + encodeURIComponent(booking.bookingId));
      }, 1200);
      return;
    }

    /*
     * 실제 연동: POST /api/v1/payments/complete { paymentKey, orderId, amount }
     * 서버가 PG에 직접 결제 내역을 조회해 사전 등록 금액·주문번호와 비교한다.
     * 같은 orderId로 다시 와도 한 번만 처리되도록 서버에서 막아야 한다.
     */
    window.BookingApi.authed('POST', '/api/v1/payments/complete', {
      paymentKey: paymentKey,
      orderId: orderId,
      amount: amount
    }).then(function (result) {
      window.location.replace('/booking/payment/complete.html?bookingId=' + encodeURIComponent(result.bookingId));
    }).catch(function () {
      fail('VERIFY_FAILED');
    });
  }

  /* ---------- 결제 완료 ---------- */

  function initComplete() {
    var bookingId = params().get('bookingId');
    if (!isValidId(bookingId)) {
      showPageError('잘못된 접근입니다.');
      return;
    }

    /*
     * 실제 연동: GET /api/v1/bookings/{bookingId}
     */
    var booking = USE_MOCK_DATA ? findBooking(bookingId) : null;
    if (!booking) {
      showPageError('예약 정보를 찾을 수 없습니다.');
      return;
    }
    // 결제가 끝나지 않은 예약은 완료 화면을 보여 주지 않는다
    if (booking.status === 'PAYMENT_PENDING') {
      window.location.replace('/booking/payment/pay.html?bookingId=' + encodeURIComponent(booking.bookingId));
      return;
    }

    var rows = [
      ['예약번호', Ui.formatBookingNumber(booking.bookingNumber)],
      ['숙소 · 객실', booking.accommodationName + ' · ' + booking.roomName],
      ['일정', stayText(booking)],
      ['이용자', booking.guestName + ' (' + booking.guestPhone + ')'],
      ['결제수단', describeMethod(booking)]
    ];
    if (booking.usedMileage > 0) {
      rows.push(['마일리지 사용', '-' + formatPrice(booking.usedMileage)]);
    }
    rows.push(['결제 금액', formatPrice(booking.finalAmount), 'total']);
    renderSummary($('pay-summary'), rows);
    showState('ready');
  }

  /* ---------- 결제 실패 ---------- */

  function initFail() {
    var p = params();
    var rawCode = p.get('code') || '';
    var code = /^[A-Z0-9_]{1,50}$/.test(rawCode) ? rawCode : 'UNKNOWN';
    var orderId = p.get('orderId');

    $('pay-fail-message').textContent = FAIL_MESSAGES[code] || DEFAULT_FAIL_MESSAGE;
    $('pay-fail-code').textContent = '오류 코드: ' + code;

    var booking = USE_MOCK_DATA && isValidOrderId(orderId) ? findBookingByOrderId(orderId) : null;
    var retry = $('pay-retry-link');

    if (!booking) {
      $('pay-fail-sub').textContent = '결제 금액은 청구되지 않았습니다.';
      retry.hidden = true;
      $('pay-summary').hidden = true;
      return;
    }

    var left = remainingMs(booking);
    var canRetry = booking.status === 'PAYMENT_PENDING' && left > 0;

    $('pay-fail-sub').textContent = canRetry
      ? '결제 금액은 청구되지 않았습니다. 예약은 결제 대기 상태로 유지됩니다.'
      : '결제 금액은 청구되지 않았습니다. 결제 기한이 지나 예약이 취소되었습니다.';

    var expires = new Date(booking.paymentExpiresAt);
    renderSummary($('pay-summary'), [
      ['예약번호', Ui.formatBookingNumber(booking.bookingNumber)],
      ['결제 기한', canRetry
        ? String(expires.getHours()).padStart(2, '0') + ':' + String(expires.getMinutes()).padStart(2, '0')
          + '까지 (' + Math.ceil(left / 60000) + '분 남음)'
        : '만료']
    ]);

    retry.hidden = !canRetry;
    retry.href = '/booking/payment/pay.html?bookingId=' + encodeURIComponent(booking.bookingId);
  }

  ({
    pending: initPending,
    success: initSuccess,
    complete: initComplete,
    fail: initFail
  }[PAGE] || function () {})();
})();
