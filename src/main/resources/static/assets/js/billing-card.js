/*
 * 카드 등록(PAYMENT_004)
 *  - 설정 화면: 등록 카드 보기·삭제, "카드 등록"을 누르면 토스 카드 등록 창(requestBillingAuth)을 연다
 *  - /payment/billing-success.html: 토스가 돌려준 authKey로 서버에 빌링키 발급을 요청한다
 * 카드번호 등 카드 정보는 토스 창에만 입력하고 이 화면과 서버는 받지 않는다.
 */
(function () {
  'use strict';

  var TOSS_SDK = 'https://js.tosspayments.com/v2/standard';

  function loadSdk() {
    if (typeof TossPayments === 'function') return Promise.resolve();
    return new Promise(function (resolve, reject) {
      var script = document.createElement('script');
      script.src = TOSS_SDK;
      script.onload = resolve;
      script.onerror = function () { reject(new Error('결제 모듈을 불러오지 못했습니다.')); };
      document.head.append(script);
    });
  }

  function cardLabel(card) {
    return (card.cardCompany || '카드') + ' ' + (card.cardNumber || '');
  }

  /* ---------- 카드 등록 결과 화면 ---------- */

  if (document.body.dataset.billingPage === 'success') {
    var params = new URLSearchParams(window.location.search);
    var authKey = params.get('authKey');
    var customerKey = params.get('customerKey');
    var message = document.getElementById('billing-message');
    // authKey가 주소창·방문 기록에 남지 않게 바로 지운다
    window.history.replaceState(null, '', window.location.pathname);
    if (!authKey || !customerKey) {
      message.textContent = '카드 등록 정보가 올바르지 않습니다. 설정에서 다시 시도해 주세요.';
      return;
    }
    BookingApi.registerBillingCard(customerKey, authKey).then(function (card) {
      message.textContent = cardLabel(card) + ' 카드가 등록되었습니다.';
      setTimeout(function () { window.location.replace('/profile/setting.html#profile'); }, 1200);
    }, function (e) {
      message.textContent = e.message;
    });
    return;
  }

  /* ---------- 설정 화면 ---------- */

  var section = document.getElementById('billing-card');
  if (!section) return;
  var alertBox = document.getElementById('billing-alert');
  var current = document.getElementById('billing-current');
  var registerButton = document.getElementById('billing-register');
  var deleteButton = document.getElementById('billing-delete');
  var card = null;

  function render() {
    current.hidden = !card;
    current.textContent = card ? '등록된 카드: ' + cardLabel(card) : '';
    deleteButton.hidden = !card;
    registerButton.textContent = card ? '다른 카드로 변경' : '카드 등록';
  }

  async function init() {
    try {
      var capabilities = await BookingApi.getPaymentCapabilities();
      if (!capabilities.billingEnabled) return; // 개별 연동 키가 아니면 카드 등록을 보여 주지 않는다
      section.hidden = false;
      var cards = await BookingApi.getBillingCards();
      card = cards.length ? cards[0] : null;
      render();
      registerButton.addEventListener('click', function () { openRegister(capabilities.tossClientKey); });
    } catch (e) {
      if (e.status !== 401) Ui.showAlert(alertBox, e.message, 'error');
    }
  }

  async function openRegister(clientKey) {
    Ui.hideAlert(alertBox);
    registerButton.disabled = true;
    try {
      var customer = await BookingApi.getBillingCustomerKey();
      await loadSdk();
      await TossPayments(clientKey).payment({ customerKey: customer.customerKey }).requestBillingAuth({
        method: 'CARD',
        successUrl: window.location.origin + '/payment/billing-success.html',
        failUrl: window.location.origin + '/payment/fail.html'
      });
    } catch (e) {
      // 사용자가 창을 닫으면 USER_CANCEL이 온다
      if (e.code !== 'USER_CANCEL') Ui.showAlert(alertBox, e.message || '카드 등록 창을 열지 못했습니다.', 'error');
    } finally {
      registerButton.disabled = false;
    }
  }

  deleteButton.addEventListener('click', async function () {
    if (!card || !window.confirm('등록된 카드를 삭제할까요?')) return;
    deleteButton.disabled = true;
    try {
      await BookingApi.deleteBillingCard(card.billingCardId);
      card = null;
      render();
      Ui.showAlert(alertBox, '카드를 삭제했습니다.', 'success');
    } catch (e) {
      Ui.showAlert(alertBox, e.message, 'error');
    } finally {
      deleteButton.disabled = false;
    }
  });

  init();
})();
