/*
 * 결제·환불 (ADMIN_005)
 *   GET   /api/v1/admin/payments/search?bookingId=&status=&page=&size=
 *   GET   /api/v1/admin/payments/{paymentId}/reconciliation   결제대행사 금액 대조
 *   GET   /api/v1/admin/refunds/search?bookingId=&status=&page=&size=
 *   POST /api/v1/admin/refunds/{refundId}/retry (대기·실패 건 재시도)
 * 예약 취소 확정 후 서버가 자동으로 결제 취소를 요청한다.
 * 탭은 주소로 구분한다: ?tab=payments (기본) / ?tab=refunds
 */
(function () {
  'use strict';

  var alertBox = document.getElementById('page-alert');
  var tab = new URLSearchParams(window.location.search).get('tab') === 'refunds' ? 'refunds' : 'payments';
  var filters = null;

  AdminUi.init({ page: 'payments', roles: ['ROLE_ADMIN'] }).then(function (me) {
    if (!me) return;
    document.getElementById('tab-' + tab).setAttribute('aria-current', 'page');
    document.getElementById(tab + '-view').hidden = false;
    if (tab === 'payments') {
      filters = AdminUi.bindFilters(document.getElementById('payment-filter'),
        { tab: 'payments', status: '', bookingId: '', page: 0 }, loadPayments);
    } else {
      filters = AdminUi.bindFilters(document.getElementById('refund-filter'),
        { tab: 'refunds', status: '', bookingId: '', page: 0 }, loadRefunds);
    }
  });

  function validBookingId(query) {
    if (query.bookingId && !AdminUi.isPositiveId(query.bookingId)) {
      AdminUi.showMessage(alertBox, '예약 ID는 1 이상의 숫자로 입력해 주세요.');
      return false;
    }
    return true;
  }

  /* ---------- 결제 내역 ---------- */

  async function loadPayments(query) {
    AdminUi.hideMessage(alertBox);
    if (!validBookingId(query)) return;
    try {
      var page = await AdminUi.api('GET', '/api/v1/admin/payments/search' + AdminUi.buildQuery({
        bookingId: query.bookingId, status: query.status, page: query.page, size: AdminUi.DEFAULT_PAGE_SIZE
      }));
      AdminUi.renderTable(document.getElementById('payment-body'), page.content || [], [
        { label: '결제 ID', render: function (p) { return p.paymentId; } },
        { label: '예약 ID', render: function (p) { return p.bookingId; } },
        { label: '주문번호', className: 'admin-wrap admin-break', render: function (p) { return p.orderId; } },
        { label: '결제 수단', render: function (p) { return p.paymentMethod; } },
        { label: '금액', render: function (p) { return AdminUi.formatPrice(p.amount); } },
        { label: '상태', render: function (p) { return AdminUi.badge('paymentStatus', p.status); } },
        { label: '결제 일시', render: function (p) { return AdminUi.formatDateTime(p.paidAt); } },
        { label: '환불', render: function (p) {
          if (!p.refundId) return '-';
          var amount = document.createElement('small');
          amount.className = 'admin-cell-sub';
          amount.textContent = AdminUi.formatPrice(p.refundAmount);
          return AdminUi.group([AdminUi.badge('refundStatus', p.refundStatus), amount], 'admin-badges');
        } },
        { label: '관리', render: function (p) {
          return AdminUi.actionButton('금액 대조', function (button) { reconcile(p, button); });
        } }
      ], '조건에 맞는 결제가 없습니다.');
      AdminUi.renderPagination(document.getElementById('payment-pagination'), page, filters.go);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }

  async function reconcile(p, button) {
    AdminUi.hideMessage(alertBox);
    button.disabled = true;
    try {
      var report = await AdminUi.api('GET', '/api/v1/admin/payments/' + encodeURIComponent(p.paymentId) + '/reconciliation');
      showReport(report);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    } finally {
      button.disabled = false;
    }
  }

  function showReport(r) {
    var list = document.getElementById('reconcile-list');
    list.textContent = '';
    var rows = [
      ['결과', AdminUi.badge('reconcile', r.result)],
      ['결제 ID · 예약 ID', r.paymentId + ' · ' + r.bookingId],
      ['주문번호', r.orderId],
      ['스테이북 상태', AdminUi.label('paymentStatus', r.localStatus)],
      ['결제대행사 상태', r.providerStatus || '-'],
      ['스테이북 금액', AdminUi.formatPrice(r.localAmount)],
      ['결제대행사 금액', r.providerAmount === null || r.providerAmount === undefined ? '-' : AdminUi.formatPrice(r.providerAmount)],
      ['결제대행사 잔액', r.providerBalanceAmount === null || r.providerBalanceAmount === undefined ? '-' : AdminUi.formatPrice(r.providerBalanceAmount)],
      ['확인 시각', AdminUi.formatDateTime(r.checkedAt)]
    ];
    rows.forEach(function (row) {
      var dt = document.createElement('dt');
      dt.textContent = row[0];
      var dd = document.createElement('dd');
      if (row[1] instanceof Node) dd.append(row[1]);
      else dd.textContent = row[1];
      list.append(dt, dd);
    });
    var card = document.getElementById('reconcile-result');
    card.hidden = false;
    card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  document.getElementById('reconcile-close').addEventListener('click', function () {
    document.getElementById('reconcile-result').hidden = true;
  });

  /* ---------- 환불 ---------- */

  async function loadRefunds(query) {
    AdminUi.hideMessage(alertBox);
    if (!validBookingId(query)) return;
    try {
      var page = await AdminUi.api('GET', '/api/v1/admin/refunds/search' + AdminUi.buildQuery({
        bookingId: query.bookingId, status: query.status, page: query.page, size: AdminUi.DEFAULT_PAGE_SIZE
      }));
      AdminUi.renderTable(document.getElementById('refund-body'), page.content || [], [
        { label: '환불 ID', render: function (r) { return r.refundId; } },
        { label: '예약 ID', render: function (r) { return r.bookingId; } },
        { label: '금액', render: function (r) { return AdminUi.formatPrice(r.amount); } },
        { label: '상태', render: function (r) { return AdminUi.badge('refundStatus', r.status); } },
        { label: '사유', className: 'admin-wrap', render: function (r) { return r.reason; } },
        { label: '처리', render: refundActions }
      ], '조건에 맞는 환불이 없습니다.');
      AdminUi.renderPagination(document.getElementById('refund-pagination'), page, filters.go);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }

  function refundActions(r) {
    if (r.status !== 'REQUESTED' && r.status !== 'FAILED' && r.status !== 'PROCESSING') {
      var done = document.createElement('small');
      done.className = 'admin-cell-sub';
      done.textContent = r.processedAt ? AdminUi.formatDateTime(r.processedAt) : '-';
      return done;
    }
    return AdminUi.group([
      AdminUi.actionButton('재시도', function (b) { retryRefund(r, b); }, 'primary')
    ]);
  }

  async function retryRefund(r, button) {
    var answer = await AdminUi.confirmAction({
      title: '환불 재시도',
      message: '환불 #' + r.refundId + '을(를) 같은 환불 번호로 재시도합니다. 처리 중인 건은 잠시 후 다시 시도해 주세요.',
      confirmLabel: '재시도',
      danger: true
    });
    if (!answer.confirmed) return;
    button.disabled = true;
    try {
      var result = await AdminUi.api('POST', '/api/v1/admin/refunds/' + encodeURIComponent(r.refundId) + '/retry');
      await filters.reload();
      AdminUi.showMessage(alertBox, result.status === 'REFUNDED'
        ? '환불이 완료됐습니다.' : '환불 상태를 다시 확인해 주세요.', result.status === 'REFUNDED' ? 'success' : 'error');
    } catch (e) {
      button.disabled = false;
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }
})();
