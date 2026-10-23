/* 본인 결제 내역만 서버에서 페이지 단위로 조회한다. */
(function () {
  'use strict';

  var list = document.getElementById('payment-history-list');
  var empty = document.getElementById('payment-history-empty');
  var more = document.getElementById('payment-history-more');
  var alertBox = document.getElementById('payment-history-alert');
  var statusLabels = {
    READY: '결제 대기', PAID: '결제 완료', PARTIALLY_REFUNDED: '부분 환불',
    REFUNDED: '환불 완료', FAILED: '결제 실패'
  };
  var refundLabels = { REQUESTED: '환불 검토 중', APPROVED: '환불 승인', REJECTED: '환불 반려' };
  var nextPage = 0;
  var loading = false;

  function detail(dl, label, value) {
    var row = document.createElement('div');
    var title = document.createElement('dt');
    var text = document.createElement('dd');
    title.textContent = label;
    text.textContent = value;
    row.append(title, text);
    dl.append(row);
  }

  function amount(value) { return Number(value).toLocaleString('ko-KR') + '원'; }

  function paidAt(value) {
    if (!value) return '-';
    var date = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : value + 'Z');
    return Number.isNaN(date.getTime()) ? '-' : date.toLocaleString('ko-KR');
  }

  function render(payment) {
    var card = document.createElement('article');
    card.className = 'payment-history-card';
    var heading = document.createElement('div');
    heading.className = 'payment-history-heading';
    var title = document.createElement('h2');
    title.textContent = '예약번호 ' + payment.bookingId;
    var status = document.createElement('span');
    status.className = 'payment-history-status';
    status.textContent = statusLabels[payment.status] || payment.status;
    heading.append(title, status);
    var details = document.createElement('dl');
    details.className = 'payment-history-details';
    detail(details, '결제 주문번호', payment.orderId);
    detail(details, '결제 금액', amount(payment.amount));
    detail(details, '결제 일시', paidAt(payment.paidAt));
    if (payment.refundStatus) {
      detail(details, '환불 상태', refundLabels[payment.refundStatus] || payment.refundStatus);
      detail(details, '환불 금액', amount(payment.refundAmount));
    }
    card.append(heading, details);
    list.append(card);
  }

  async function loadPage() {
    if (loading) return;
    loading = true;
    more.disabled = true;
    Ui.hideAlert(alertBox);
    try {
      var result = await BookingApi.getMyPaymentsPage(nextPage, 20);
      (result.content || []).forEach(render);
      empty.hidden = result.totalElements !== 0;
      nextPage += 1;
      more.hidden = nextPage >= result.totalPages;
    } catch (error) {
      if (error.status === 401) {
        window.location.replace('/auth/login.html?next=' + encodeURIComponent('/payment/history.html'));
        return;
      }
      Ui.showAlert(alertBox, error.message || '결제 내역을 불러오지 못했습니다.', 'error');
    } finally {
      loading = false;
      more.disabled = false;
    }
  }

  more.addEventListener('click', loadPage);
  Ui.renderHeader().then(function (me) {
    if (!me) {
      window.location.replace('/auth/login.html?next=' + encodeURIComponent('/payment/history.html'));
      return;
    }
    loadPage();
  }).catch(function () {
    Ui.showAlert(alertBox, '로그인 정보를 확인하지 못했습니다.', 'error');
  });
})();
