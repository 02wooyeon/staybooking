/* 실제 예약·토스 결제 API를 사용하는 결제 화면. */
(function () {
  'use strict';

  var page = document.body.dataset.page;
  var params = new URLSearchParams(window.location.search);
  var orderStorageKey = 'staybookingPendingTossOrder';
  var bookingStorageKey = 'staybookingPendingTossBooking';

  Ui.renderHeader();

  function $(id) { return document.getElementById(id); }
  function validId(value) { return /^[1-9]\d{0,18}$/.test(String(value || '')); }
  function validOrder(value) { return /^[A-Za-z0-9_-]{6,64}$/.test(String(value || '')); }
  function money(value) { return Number(value).toLocaleString('ko-KR') + '원'; }
  function expiry(value) { return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : value + 'Z'); }
  function stored(key) { try { return sessionStorage.getItem(key); } catch (e) { return null; } }
  function remember(key, value) { try { sessionStorage.setItem(key, String(value)); } catch (e) { /* 선택적 저장 */ } }
  function forget(key) { try { sessionStorage.removeItem(key); } catch (e) { /* 선택적 저장 */ } }

  function renderRows(id, rows) {
    var list = $(id);
    list.textContent = '';
    rows.forEach(function (row) {
      var item = document.createElement('div');
      if (row[2]) item.className = row[2];
      var label = document.createElement('dt');
      var value = document.createElement('dd');
      label.textContent = row[0];
      value.textContent = row[1];
      item.append(label, value);
      list.append(item);
    });
  }

  function stayRows(booking, amount, orderId) {
    var rows = [
      ['예약번호', String(booking.bookingId)],
      ['숙소 · 객실', booking.accommodationName + ' · ' + booking.roomName]
    ];
    if (orderId) rows.push(['결제 주문번호', orderId]);
    rows.push(
      ['일정', booking.checkIn + ' ~ ' + booking.checkOut + ' · ' + booking.guests + '명'],
      ['결제 금액', money(amount), 'total']
    );
    return rows;
  }

  function showError(message) {
    var target = $('pay-error-message');
    if (target) target.textContent = message;
    document.querySelectorAll('[data-state]').forEach(function (section) {
      section.hidden = section.dataset.state !== 'error';
    });
  }

  function loginIfNeeded(error) {
    if (error && error.status === 401) {
      window.location.replace('/auth/login.html?next=' + encodeURIComponent(
        window.location.pathname + window.location.search));
      return true;
    }
    return false;
  }

  async function pending() {
    var bookingId = params.get('bookingId');
    if (!validId(bookingId)) return showError('예약 번호가 올바르지 않습니다.');
    try {
      var results = await Promise.all([
        BookingApi.getMyBooking(bookingId), BookingApi.getPaymentCapabilities()
      ]);
      var booking = results[0];
      var capabilities = results[1];
      if (booking.status !== 'PAYMENT_PENDING') {
        if (booking.status === 'PENDING' || booking.status === 'COMPLETED') {
          window.location.replace('/booking/payment/complete.html?bookingId=' + encodeURIComponent(bookingId));
        } else {
          showError('현재 결제할 수 없는 예약입니다.');
        }
        return;
      }
      if (!capabilities.tossEnabled || !capabilities.tossClientKey) {
        return showError('토스페이먼츠 테스트 키가 아직 설정되지 않았습니다.');
      }
      if (typeof TossPayments !== 'function') {
        return showError('결제창을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
      var deadline = expiry(booking.paymentExpiresAt);
      if (Number.isNaN(deadline.getTime()) || Date.now() >= deadline.getTime()) {
        return showError('결제 기한이 지났습니다. 내 예약에서 상태를 확인해 주세요.');
      }
      var payment = await BookingApi.preparePayment(booking.bookingId, 'TOSS');
      renderRows('pay-summary', stayRows(booking, payment.amount, payment.orderId));
      document.querySelectorAll('[data-state]').forEach(function (section) {
        section.hidden = section.dataset.state !== 'ready';
      });

      // 결제 전 예약 취소 API가 없으므로 화면에서 취소 완료를 흉내 내지 않는다.
      $('pay-cancel-button').hidden = true;
      var timer = $('pay-timer');
      var button = $('pay-button');
      var interval = setInterval(function () {
        var seconds = Math.max(0, Math.ceil((deadline.getTime() - Date.now()) / 1000));
        timer.textContent = '남은 시간 ' + String(Math.floor(seconds / 60)).padStart(2, '0')
          + ':' + String(seconds % 60).padStart(2, '0');
        if (seconds === 0) {
          clearInterval(interval);
          button.disabled = true;
          showError('결제 기한이 지났습니다. 내 예약에서 상태를 확인해 주세요.');
        }
      }, 1000);

      var widgets = TossPayments(capabilities.tossClientKey).widgets({ customerKey: payment.orderId });
      await widgets.setAmount({ currency: 'KRW', value: payment.amount });
      await Promise.all([
        widgets.renderPaymentMethods({ selector: '#payment-method' }),
        widgets.renderAgreement({ selector: '#payment-agreement' })
      ]);
      if (Date.now() >= deadline.getTime()) return;
      button.disabled = false;
      button.addEventListener('click', async function () {
        button.disabled = true;
        remember(orderStorageKey, payment.orderId);
        remember(bookingStorageKey, booking.bookingId);
        try {
          await widgets.requestPayment({
            orderId: payment.orderId,
            orderName: (booking.accommodationName + ' ' + booking.roomName).slice(0, 100),
            successUrl: window.location.origin + '/booking/payment/success.html',
            failUrl: window.location.origin + '/booking/payment/fail.html'
          });
        } catch (error) {
          button.disabled = false;
          showError('결제창을 열지 못했습니다. 내 예약에서 다시 시도해 주세요.');
        }
      });
    } catch (error) {
      if (!loginIfNeeded(error)) showError('예약 또는 결제 정보를 불러오지 못했습니다.');
    }
  }

  async function success() {
    var paymentKey = params.get('paymentKey');
    var orderId = params.get('orderId') || stored(orderStorageKey);
    var amount = Number(params.get('amount'));
    window.history.replaceState(null, '', window.location.pathname);
    if (!validOrder(orderId) || (paymentKey && (!Number.isSafeInteger(amount) || amount < 1))) {
      return successError('결제 정보가 올바르지 않습니다. 내 예약에서 상태를 확인해 주세요.', false);
    }
    remember(orderStorageKey, orderId);
    async function verify() {
      $('pay-retry').hidden = true;
      try {
        var result;
        if (paymentKey) result = await BookingApi.confirmTossPayment(orderId, paymentKey, amount);
        else result = await BookingApi.recoverTossPayment(orderId);
        if (result.status !== 'PAID') throw new Error('Payment is not complete');
        forget(orderStorageKey);
        forget(bookingStorageKey);
        window.location.replace('/booking/payment/complete.html?bookingId=' + encodeURIComponent(result.bookingId));
      } catch (error) {
        if (loginIfNeeded(error)) return;
        try {
          var recovered = await BookingApi.recoverTossPayment(orderId);
          if (recovered.status === 'PAID') {
            forget(orderStorageKey);
            forget(bookingStorageKey);
            window.location.replace('/booking/payment/complete.html?bookingId='
              + encodeURIComponent(recovered.bookingId));
            return;
          }
        } catch (recoveryError) { /* 상태를 확정할 수 없으면 재확인을 안내한다. */ }
        successError('승인 결과를 아직 확인하지 못했습니다. 잠시 후 다시 확인하거나 내 예약을 확인해 주세요.', true);
      }
    }
    $('pay-retry').addEventListener('click', verify);
    await verify();
  }

  function successError(message, canRetry) {
    $('pay-title').textContent = '결제 상태를 확인할 수 없습니다';
    $('pay-step-verify').textContent = message;
    $('pay-retry').hidden = !canRetry;
  }

  async function complete() {
    var bookingId = params.get('bookingId');
    if (!validId(bookingId)) return showError('예약 번호가 올바르지 않습니다.');
    try {
      var booking = await BookingApi.getMyBooking(bookingId);
      if (booking.status === 'PAYMENT_PENDING') {
        window.location.replace('/booking/payment/pay.html?bookingId=' + encodeURIComponent(bookingId));
        return;
      }
      if (booking.status !== 'PENDING' && booking.status !== 'COMPLETED') {
        return showError('결제 완료 상태를 확인할 수 없습니다.');
      }
      var history = await BookingApi.getPaidPaymentsForBooking(bookingId);
      var paid = history.content || [];
      if (paid.length !== 1 || String(paid[0].bookingId) !== String(booking.bookingId)
          || !paid[0].orderId) {
        return showError('결제 내역을 확인할 수 없습니다. 내 예약에서 상태를 확인해 주세요.');
      }
      $('pay-complete-subtitle').textContent = booking.status === 'COMPLETED'
        ? '예약 승인이 완료되었습니다.' : '결제가 완료되어 예약 승인 대기 중입니다.';
      renderRows('pay-summary', stayRows(booking, paid[0].amount, paid[0].orderId));
      document.querySelectorAll('[data-state]').forEach(function (section) {
        section.hidden = section.dataset.state !== 'ready';
      });
    } catch (error) {
      if (!loginIfNeeded(error)) showError('예약 정보를 불러오지 못했습니다.');
    }
  }

  async function fail() {
    var rawCode = params.get('code') || '';
    var code = /^[A-Z0-9_]{1,50}$/.test(rawCode) ? rawCode : 'UNKNOWN';
    var orderId = params.get('orderId') || stored(orderStorageKey);
    var bookingId = stored(bookingStorageKey);
    window.history.replaceState(null, '', window.location.pathname);
    $('pay-fail-code').textContent = '오류 코드: ' + code;
    $('pay-fail-message').textContent = code === 'PAY_PROCESS_CANCELED'
      ? '결제창을 닫았습니다.' : '결제 요청이 완료되지 않았습니다.';
    $('pay-fail-sub').textContent = '청구 여부가 확실하지 않다면 내 예약에서 결제 상태를 확인해 주세요.';
    $('pay-summary').hidden = true;
    if (validOrder(orderId)) {
      try {
        var recovered = await BookingApi.recoverTossPayment(orderId);
        if (recovered.status === 'PAID') {
          window.location.replace('/booking/payment/complete.html?bookingId='
            + encodeURIComponent(recovered.bookingId));
          return;
        }
      } catch (error) { /* PG에서 승인된 기록이 없거나 조회할 수 없는 상태다. */ }
    }
    if (!validId(bookingId)) return;
    try {
      var booking = await BookingApi.getMyBooking(bookingId);
      if (booking.status === 'PAYMENT_PENDING' && Date.now() < expiry(booking.paymentExpiresAt).getTime()) {
        $('pay-retry-link').href = '/booking/payment/pay.html?bookingId=' + encodeURIComponent(bookingId);
        $('pay-retry-link').hidden = false;
      }
    } catch (error) { /* 내 예약에서 다시 상태를 확인할 수 있다. */ }
  }

  ({ pending: pending, success: success, complete: complete, fail: fail }[page] || function () {})();
})();
