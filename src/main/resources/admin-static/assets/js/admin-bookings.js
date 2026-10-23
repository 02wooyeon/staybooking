/*
 * 예약 관리 (ADMIN_004)
 *   GET   /api/v1/admin/bookings/search?bookingId=&status=&page=&size=
 *   PATCH /api/v1/admin/bookings/{bookingId}
 *         { action: ACCEPT | CANCEL | APPROVE_CANCELLATION | REJECT_CANCELLATION,
 *           cancellationRequestId, reasonType, reason }
 * 상태 전이(승인 가능 여부, 취소 가능 여부)는 서버가 다시 검사하고, 거절되면 서버 문구를 보여 준다.
 */
(function () {
  'use strict';

  var API = '/api/v1/admin/bookings';
  var alertBox = document.getElementById('page-alert');
  var tbody = document.getElementById('list-body');
  var pager = document.getElementById('pagination');
  var filters = null;

  var REASON_CHOICES = Object.keys(AdminUi.LABELS.cancelReason).map(function (value) {
    return { value: value, label: AdminUi.LABELS.cancelReason[value] };
  });

  AdminUi.init({ page: 'bookings', roles: ['ROLE_ADMIN'] }).then(function (me) {
    if (!me) return;
    filters = AdminUi.bindFilters(document.getElementById('filter-form'),
      { status: '', bookingId: '', page: 0 }, load);
  });

  async function load(query) {
    AdminUi.hideMessage(alertBox);
    if (query.bookingId && !AdminUi.isPositiveId(query.bookingId)) {
      AdminUi.showMessage(alertBox, '예약 ID는 1 이상의 숫자로 입력해 주세요.');
      return;
    }
    try {
      var page = await AdminUi.api('GET', API + '/search' + AdminUi.buildQuery({
        bookingId: query.bookingId, status: query.status, page: query.page, size: AdminUi.DEFAULT_PAGE_SIZE
      }));
      render(page);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }

  function render(page) {
    AdminUi.renderTable(tbody, page.content || [], [
      { label: '예약 ID', render: function (r) { return r.booking.bookingId; } },
      { label: '숙소 · 객실', className: 'admin-wrap', render: function (r) {
        return stack(r.booking.accommodationName, r.booking.roomName);
      } },
      { label: '일정', render: function (r) { return r.booking.checkIn + ' ~ ' + r.booking.checkOut; } },
      { label: '인원', render: function (r) { return r.booking.guests + '명'; } },
      { label: '금액', render: function (r) { return AdminUi.formatPrice(r.booking.totalAmount); } },
      { label: '상태', render: function (r) {
        var nodes = [AdminUi.badge('bookingStatus', r.booking.status)];
        if (r.cancellationRequest || r.booking.cancellationRequested) {
          nodes.push(AdminUi.statusBadge('CANCEL_REQUEST', { CANCEL_REQUEST: '취소 신청' }, { CANCEL_REQUEST: 'warn' }));
        }
        if (r.booking.status === 'PAYMENT_PENDING' && r.booking.paymentExpiresAt) {
          var due = document.createElement('small');
          due.className = 'admin-cell-sub';
          due.textContent = '결제 기한 ' + AdminUi.formatDateTime(r.booking.paymentExpiresAt);
          nodes.push(due);
        }
        return AdminUi.group(nodes, 'admin-badges');
      } },
      { label: '처리', render: actions }
    ], '조건에 맞는 예약이 없습니다.');
    AdminUi.renderPagination(pager, page, filters.go);
  }

  // 위·아래 두 줄로 보여 주는 칸
  function stack(main, sub) {
    var wrap = document.createElement('span');
    wrap.className = 'admin-stack';
    var strong = document.createElement('strong');
    strong.textContent = main || '-';
    var small = document.createElement('small');
    small.className = 'admin-cell-sub';
    small.textContent = sub || '';
    wrap.append(strong, small);
    return wrap;
  }

  function actions(r) {
    var b = r.booking;
    var buttons = [];
    if (b.status === 'PENDING') {
      buttons.push(AdminUi.actionButton('승인', function (button) { accept(b, button); }, 'primary'));
    }
    if (r.cancellationRequest) {
      buttons.push(AdminUi.actionButton('취소 신청 승인', function (button) { approveCancellation(r, button); }));
      buttons.push(AdminUi.actionButton('취소 신청 거절', function (button) { rejectCancellation(r, button); }));
    }
    if (b.status !== 'CANCELLED') {
      buttons.push(AdminUi.actionButton('직접 취소', function (button) { cancel(b, button); }, 'danger'));
    }
    return buttons.length ? AdminUi.group(buttons) : '-';
  }

  function describe(b) {
    return '예약 #' + b.bookingId + ' (' + (b.accommodationName || '') + ' · ' + b.checkIn + ' ~ ' + b.checkOut + ')';
  }

  async function accept(b, button) {
    var answer = await AdminUi.confirmAction({
      title: '예약 승인',
      message: describe(b) + '을(를) 승인합니다.',
      confirmLabel: '승인'
    });
    if (answer.confirmed) send(b.bookingId, { action: 'ACCEPT' }, '예약을 승인했습니다.', button);
  }

  async function cancel(b, button) {
    var answer = await AdminUi.confirmAction({
      title: '예약 직접 취소',
      message: describe(b) + '을(를) 취소합니다. 결제 금액은 환불 정책에 따라 처리됩니다.',
      choiceLabel: '취소 사유 유형',
      choices: REASON_CHOICES,
      reasonLabel: '취소 사유',
      reasonRequired: true,
      confirmLabel: '예약 취소',
      danger: true
    });
    if (answer.confirmed) {
      send(b.bookingId, { action: 'CANCEL', reasonType: answer.choice, reason: answer.reason },
        '예약을 취소했습니다.', button);
    }
  }

  function requestSummary(r) {
    var req = r.cancellationRequest;
    return '신청 사유: ' + AdminUi.label('cancelReason', req.reasonType)
      + (req.reasonDetail ? ' - ' + req.reasonDetail : '');
  }

  async function approveCancellation(r, button) {
    var answer = await AdminUi.confirmAction({
      title: '취소 신청 승인',
      message: describe(r.booking) + '의 취소 신청을 승인하면 예약이 취소됩니다. ' + requestSummary(r),
      reasonLabel: '처리 메모',
      reasonRequired: false,
      confirmLabel: '취소 승인',
      danger: true
    });
    if (answer.confirmed) {
      send(r.booking.bookingId, {
        action: 'APPROVE_CANCELLATION',
        cancellationRequestId: r.cancellationRequest.cancellationRequestId,
        reason: answer.reason || null
      }, '취소 신청을 승인했습니다.', button);
    }
  }

  async function rejectCancellation(r, button) {
    var answer = await AdminUi.confirmAction({
      title: '취소 신청 거절',
      message: describe(r.booking) + '의 취소 신청을 거절합니다. ' + requestSummary(r),
      reasonLabel: '거절 사유',
      reasonRequired: true,
      confirmLabel: '거절'
    });
    if (answer.confirmed) {
      send(r.booking.bookingId, {
        action: 'REJECT_CANCELLATION',
        cancellationRequestId: r.cancellationRequest.cancellationRequestId,
        reason: answer.reason
      }, '취소 신청을 거절했습니다.', button);
    }
  }

  async function send(bookingId, body, successMessage, button) {
    button.disabled = true;
    try {
      await AdminUi.api('PATCH', API + '/' + encodeURIComponent(bookingId), body);
      await filters.reload();
      AdminUi.showMessage(alertBox, successMessage, 'success');
    } catch (e) {
      button.disabled = false;
      if (e.status >= 500) {
        // 서버가 처리 후 응답을 만들다 실패할 수 있다 → 실제 상태를 다시 불러와 보여 준다
        await filters.reload();
        AdminUi.showMessage(alertBox, '처리 결과를 확인하지 못했습니다. 목록에서 예약 상태를 확인해 주세요.', 'warning');
        return;
      }
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }
})();
