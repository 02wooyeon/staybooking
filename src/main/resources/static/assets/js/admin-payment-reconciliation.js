/* 관리자 토스 결제 대조 결과: 조회만 하며 상태는 변경하지 않는다. */
(function () {
  'use strict';

  var list = document.getElementById('reconciliation-list');
  var empty = document.getElementById('reconciliation-empty');
  var alertBox = document.getElementById('reconciliation-alert');
  var previous = document.getElementById('reconciliation-prev');
  var next = document.getElementById('reconciliation-next');
  var retry = document.getElementById('reconciliation-retry');
  var pageLabel = document.getElementById('reconciliation-page-label');
  var currentPage = 0;
  var totalPages = 0;
  var loading = false;
  var results = {
    MATCHED: '일치', REVIEW_REQUIRED: '검토 필요',
    LOOKUP_FAILED: '조회 실패', NOT_APPLICABLE: '해당 없음'
  };
  var statuses = {
    READY: '결제 대기', PAID: '결제 완료', PARTIALLY_REFUNDED: '부분 환불',
    REFUNDED: '환불 완료', FAILED: '결제 실패',
    DONE: '결제 완료', IN_PROGRESS: '진행 중', CANCELED: '취소',
    PARTIAL_CANCELED: '부분 취소', ABORTED: '중단', EXPIRED: '만료',
    NOT_FOUND: '주문 없음', LOOKUP_FAILED: '조회 실패'
  };

  function cell(row, value, className) {
    var td = document.createElement('td');
    td.textContent = value;
    if (className) td.className = className;
    row.append(td);
  }

  function render(report) {
    var row = document.createElement('tr');
    cell(row, String(report.bookingId));
    cell(row, report.orderId, 'reconciliation-order');
    cell(row, statuses[report.localStatus] || report.localStatus);
    cell(row, statuses[report.providerStatus] || report.providerStatus);
    var amount = Number(report.localAmount).toLocaleString('ko-KR') + '원';
    if (report.providerAmount != null && report.providerAmount !== report.localAmount) {
      amount += ' / 토스 ' + Number(report.providerAmount).toLocaleString('ko-KR') + '원';
    }
    cell(row, amount);
    cell(row, results[report.result] || report.result,
      'reconciliation-result' + (report.result === 'REVIEW_REQUIRED' ? ' review'
        : report.result === 'LOOKUP_FAILED' ? ' failed' : ''));
    list.append(row);
  }

  async function loadPage(page) {
    if (loading || page < 0) return;
    loading = true;
    previous.disabled = next.disabled = retry.disabled = true;
    Ui.hideAlert(alertBox);
    try {
      var response = await BookingApi.getAdminReconciliationPage(page);
      list.textContent = '';
      (response.content || []).forEach(render);
      currentPage = page;
      totalPages = response.totalPages;
      empty.hidden = response.totalElements !== 0;
      pageLabel.textContent = (currentPage + 1) + '페이지';
      previous.disabled = currentPage === 0;
      next.disabled = currentPage + 1 >= totalPages;
    } catch (error) {
      if (error.status === 401) {
        window.location.replace('/auth/login.html?next='
          + encodeURIComponent('/admin/payment-reconciliation.html'));
        return;
      }
      Ui.showAlert(alertBox, error.status === 403 ? '관리자만 결제 점검 결과를 볼 수 있습니다.'
        : (error.message || '결제 대조 결과를 불러오지 못했습니다.'), 'error');
      previous.disabled = currentPage === 0;
      next.disabled = currentPage + 1 >= totalPages;
    } finally {
      loading = false;
      retry.disabled = false;
    }
  }

  previous.addEventListener('click', function () { loadPage(currentPage - 1); });
  next.addEventListener('click', function () { loadPage(currentPage + 1); });
  retry.addEventListener('click', function () { loadPage(currentPage); });
  Ui.renderHeader().then(function (me) {
    if (!me) {
      window.location.replace('/auth/login.html?next='
        + encodeURIComponent('/admin/payment-reconciliation.html'));
      return;
    }
    if (me.role !== 'ROLE_ADMIN') {
      Ui.showAlert(alertBox, '관리자만 결제 점검 결과를 볼 수 있습니다.', 'error');
      return;
    }
    loadPage(0);
  }).catch(function () {
    Ui.showAlert(alertBox, '로그인 정보를 확인하지 못했습니다.', 'error');
  });
})();
