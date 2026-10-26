/* 토스페이먼츠 결제: 서버의 주문번호와 금액을 그대로 사용한다.
 * 서버가 알려 주는 tossMode에 따라 결제위젯(WIDGET) 또는 결제창(PAYMENT_WINDOW)으로 연다. */
(function () {
  'use strict';
  var page = document.body.dataset.paymentPage;
  var params = new URLSearchParams(window.location.search);

  if (page === 'fail') {
    var code = params.get('code');
    document.getElementById('payment-message').textContent = code === 'PAY_PROCESS_CANCELED'
      ? '결제가 취소되었습니다.' : '결제 요청이 실패했습니다. 내 예약에서 다시 시도해 주세요.';
    window.history.replaceState(null, '', window.location.pathname);
    return;
  }

  if (page === 'success') {
    var pendingOrderKey = 'staybookingPendingTossOrder';
    var paymentKey = params.get('paymentKey');
    var orderId = params.get('orderId');
    if (!orderId) {
      try { orderId = sessionStorage.getItem(pendingOrderKey); } catch (e) { /* 저장소 없이 진행 */ }
    }
    var amount = Number(params.get('amount'));
    var message = document.getElementById('payment-message');
    var retry = document.getElementById('payment-retry');
    window.history.replaceState(null, '', window.location.pathname);
    if (!orderId || (paymentKey && (!Number.isSafeInteger(amount) || amount < 1))) {
      message.textContent = '결제 정보가 올바르지 않습니다. 내 예약에서 상태를 확인해 주세요.';
      return;
    }
    try { sessionStorage.setItem(pendingOrderKey, orderId); } catch (e) { /* 저장소 없이 진행 */ }
    function completed() {
      try { sessionStorage.removeItem(pendingOrderKey); } catch (e) { /* 저장소 없이 진행 */ }
      message.textContent = '결제가 완료되어 예약이 승인 대기 상태가 됐습니다.';
    }
    async function confirm() {
      retry.hidden = true;
      message.textContent = '결제 승인 결과를 확인하고 있습니다.';
      try {
        if (paymentKey) {
          await BookingApi.confirmTossPayment(orderId, paymentKey, amount);
          completed();
          return;
        }
      } catch (e) { /* 승인 결과가 유실됐을 수 있으므로 아래에서 주문을 재조회한다. */ }
      try {
        var recovered = await BookingApi.recoverTossPayment(orderId);
        if (recovered.status === 'PAID') {
          completed();
          return;
        }
      } catch (recoveryError) { /* 아래에서 재시도 방법을 안내한다. */ }
      message.textContent = '결제 승인 상태를 확인하지 못했습니다. 내 예약을 확인하거나 다시 시도해 주세요.';
      retry.hidden = false;
    }
    retry.addEventListener('click', confirm);
    confirm();
    return;
  }

  if (page !== 'checkout') return;
  var bookingId = Number(params.get('bookingId'));
  var button = document.getElementById('payment-button');
  var alertBox = document.getElementById('payment-alert');
  if (!Number.isSafeInteger(bookingId) || bookingId < 1) {
    Ui.showAlert(alertBox, '예약 번호가 올바르지 않습니다.', 'error');
    return;
  }

  async function init() {
    try {
      var results = await Promise.all([
        BookingApi.getPaymentCapabilities(),
        BookingApi.getMyBooking(bookingId)
      ]);
      var capabilities = results[0];
      var booking = results[1];
      if (!capabilities.tossEnabled || !capabilities.tossClientKey) {
        throw new Error('토스페이먼츠 결제 설정이 아직 준비되지 않았습니다.');
      }
      if (booking.status !== 'PAYMENT_PENDING' || Date.now() >= Date.parse(booking.paymentExpiresAt + 'Z')) {
        throw new Error('이 예약은 현재 결제할 수 없습니다.');
      }
      if (typeof TossPayments !== 'function') {
        throw new Error('결제 화면을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      }
      var payment = await BookingApi.preparePayment(bookingId, 'TOSS');
      document.getElementById('payment-stay').textContent = booking.accommodationName + ' · ' + booking.roomName;
      document.getElementById('payment-dates').textContent = booking.checkIn + ' ~ ' + booking.checkOut;
      document.getElementById('payment-amount').textContent = Number(payment.amount).toLocaleString('ko-KR') + '원';
      var request = {
        orderId: payment.orderId,
        orderName: (booking.accommodationName + ' ' + booking.roomName).slice(0, 100),
        successUrl: window.location.origin + '/payment/success.html',
        failUrl: window.location.origin + '/payment/fail.html'
      };
      var tossPayments = TossPayments(capabilities.tossClientKey);
      var startPayment;
      if (capabilities.tossMode === 'PAYMENT_WINDOW') {
        // 결제창: 결제 수단 선택·약관은 토스 결제창에서 한다. 금액은 서버가 준 값만 쓴다.
        document.getElementById('payment-method').hidden = true;
        document.getElementById('payment-agreement').hidden = true;
        var paymentWindow = tossPayments.payment({ customerKey: payment.orderId });
        startPayment = function () {
          return paymentWindow.requestPayment(Object.assign({
            method: 'CARD',
            amount: { currency: 'KRW', value: payment.amount }
          }, request));
        };
      } else {
        var widgets = tossPayments.widgets({ customerKey: payment.orderId });
        await widgets.setAmount({ currency: 'KRW', value: payment.amount });
        await Promise.all([
          widgets.renderPaymentMethods({ selector: '#payment-method' }),
          widgets.renderAgreement({ selector: '#payment-agreement' })
        ]);
        startPayment = function () { return widgets.requestPayment(request); };
      }
      button.disabled = false;
      button.addEventListener('click', async function () {
        button.disabled = true;
        try {
          await startPayment();
        } catch (e) {
          button.disabled = false;
          Ui.showAlert(alertBox, '결제 요청을 시작하지 못했습니다. 다시 시도해 주세요.', 'error');
        }
      });
    } catch (e) {
      if (e.status === 401) {
        window.location.replace('/auth/login.html?next=' + encodeURIComponent(
          window.location.pathname + window.location.search));
        return;
      }
      Ui.showAlert(alertBox, e.message, 'error');
    }
  }
  init();
})();
