/*
 * 카드 등록(PAYMENT_004)
 *  - 결제 페이지(toss-payment.js)가 BillingCard.openRegister로 토스 카드 등록 창(requestBillingAuth)을 연다
 *  - /payment/billing-success.html: 토스가 돌려준 authKey로 서버에 빌링키 발급을 요청하고 결제 페이지로 돌아간다
 * 카드번호 등 카드 정보는 토스 창에만 입력하고 이 화면과 서버는 받지 않는다.
 */
(function () {
  'use strict';

  function cardLabel(card) {
    return (card.cardCompany || '카드') + ' ' + (card.cardNumber || '');
  }

  // 돌아갈 결제 페이지. 예약 번호는 숫자만 받아 다른 주소로 보내지 못하게 한다.
  function checkoutUrl(bookingId) {
    return /^\d{1,19}$/.test(String(bookingId || ''))
      ? '/payment/checkout.html?bookingId=' + bookingId : '/booking/my.html';
  }

  window.BillingCard = {
    label: cardLabel,

    /** 토스 카드 등록 창을 연다. 등록이 끝나면 billing-success.html을 거쳐 같은 결제 페이지로 돌아온다. */
    openRegister: async function (clientKey, bookingId) {
      var customer = await BookingApi.getBillingCustomerKey();
      await TossPayments(clientKey).payment({ customerKey: customer.customerKey }).requestBillingAuth({
        method: 'CARD',
        successUrl: window.location.origin + '/payment/billing-success.html?bookingId=' + encodeURIComponent(bookingId),
        failUrl: window.location.origin + checkoutUrl(bookingId)
      });
    }
  };

  if (document.body.dataset.billingPage !== 'success') return;

  var params = new URLSearchParams(window.location.search);
  var authKey = params.get('authKey');
  var customerKey = params.get('customerKey');
  var back = checkoutUrl(params.get('bookingId'));
  var message = document.getElementById('billing-message');
  document.getElementById('billing-back').href = back;
  // authKey가 주소창·방문 기록에 남지 않게 바로 지운다
  window.history.replaceState(null, '', window.location.pathname);
  if (!authKey || !customerKey) {
    message.textContent = '카드 등록 정보가 올바르지 않습니다. 결제 페이지에서 다시 시도해 주세요.';
    return;
  }
  BookingApi.registerBillingCard(customerKey, authKey).then(function (card) {
    message.textContent = cardLabel(card) + ' 카드가 등록되었습니다. 결제 페이지로 돌아갑니다.';
    setTimeout(function () { window.location.replace(back); }, 1200);
  }, function (e) {
    message.textContent = e.message;
  });
})();
