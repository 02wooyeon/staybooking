/*
 * 회원 관리 (ADMIN_003)
 *   GET   /api/v1/admin/members/search?userId=&role=&status=&q=&page=&size=
 *   PATCH /api/v1/admin/members/{userId}/status  { status: ACTIVE | SUSPENDED }
 *   PATCH /api/v1/admin/members/{userId}/role    { role: ROLE_USER | ROLE_APPROVER }
 * 서버 규칙: 본인 계정 정지·권한 변경 불가, 관리자 권한은 변경 불가.
 * 정지·권한 변경 시 서버가 그 회원의 토큰을 폐기해 즉시 로그아웃된다.
 */
(function () {
  'use strict';

  var API = '/api/v1/admin/members';
  var alertBox = document.getElementById('page-alert');
  var tbody = document.getElementById('list-body');
  var pager = document.getElementById('pagination');
  var filters = null;
  var me = null;

  AdminUi.init({ page: 'members', roles: ['ROLE_ADMIN'] }).then(function (user) {
    if (!user) return;
    me = user;
    filters = AdminUi.bindFilters(document.getElementById('filter-form'),
      { q: '', role: '', status: '', userId: '', page: 0 }, load);
  });

  async function load(query) {
    AdminUi.hideMessage(alertBox);
    if (query.userId && !AdminUi.isPositiveId(query.userId)) {
      AdminUi.showMessage(alertBox, '회원 ID는 1 이상의 숫자로 입력해 주세요.');
      return;
    }
    try {
      var page = await AdminUi.api('GET', API + '/search' + AdminUi.buildQuery({
        userId: query.userId, role: query.role, status: query.status, q: query.q,
        page: query.page, size: AdminUi.DEFAULT_PAGE_SIZE
      }));
      render(page);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }

  function render(page) {
    AdminUi.renderTable(tbody, page.content || [], [
      { label: '회원 ID', render: function (m) { return m.userId; } },
      { label: '이름', render: function (m) {
        var wrap = document.createElement('span');
        wrap.className = 'admin-stack';
        var name = document.createElement('strong');
        name.textContent = m.name;
        wrap.append(name);
        if (m.nickname) {
          var nick = document.createElement('small');
          nick.className = 'admin-cell-sub';
          nick.textContent = m.nickname;
          wrap.append(nick);
        }
        return wrap;
      } },
      { label: '이메일', render: function (m) { return m.email; } },
      { label: '역할', render: function (m) { return AdminUi.badge('role', m.role); } },
      { label: '상태', render: function (m) { return AdminUi.badge('userStatus', m.status); } },
      { label: '마일리지', render: function (m) { return Number(m.mileageBalance || 0).toLocaleString('ko-KR') + ' P'; } },
      { label: '관리', render: actions }
    ], '조건에 맞는 회원이 없습니다.');
    AdminUi.renderPagination(pager, page, filters.go);
  }

  function actions(m) {
    if (m.userId === me.userId) return note('본인 계정');
    if (m.role === 'ROLE_ADMIN') return note('관리자 계정');

    var buttons = [];
    if (m.status === 'ACTIVE') {
      buttons.push(AdminUi.actionButton('정지', function (b) { changeStatus(m, 'SUSPENDED', b); }, 'danger'));
    } else if (m.status === 'SUSPENDED') {
      buttons.push(AdminUi.actionButton('정지 해제', function (b) { changeStatus(m, 'ACTIVE', b); }));
    }
    if (m.status !== 'WITHDRAWN') {
      if (m.role === 'ROLE_USER') {
        buttons.push(AdminUi.actionButton('승인자로 지정', function (b) { changeRole(m, 'ROLE_APPROVER', b); }));
      } else if (m.role === 'ROLE_APPROVER') {
        buttons.push(AdminUi.actionButton('일반 회원으로', function (b) { changeRole(m, 'ROLE_USER', b); }));
      }
    }
    return buttons.length ? AdminUi.group(buttons) : note('-');
  }

  function note(text) {
    var span = document.createElement('small');
    span.className = 'admin-cell-sub';
    span.textContent = text;
    return span;
  }

  function who(m) {
    return m.name + ' (' + m.email + ')';
  }

  async function changeStatus(m, status, button) {
    var suspend = status === 'SUSPENDED';
    var answer = await AdminUi.confirmAction({
      title: suspend ? '회원 정지' : '정지 해제',
      message: suspend
        ? who(m) + ' 계정을 정지합니다. 로그인 중인 세션도 모두 끊깁니다.'
        : who(m) + ' 계정의 정지를 해제합니다.',
      confirmLabel: suspend ? '정지' : '해제',
      danger: suspend
    });
    if (!answer.confirmed) return;
    send(API + '/' + encodeURIComponent(m.userId) + '/status', { status: status },
      suspend ? '회원을 정지했습니다.' : '정지를 해제했습니다.', button);
  }

  async function changeRole(m, role, button) {
    var toApprover = role === 'ROLE_APPROVER';
    var answer = await AdminUi.confirmAction({
      title: toApprover ? '승인자 지정' : '승인자 해제',
      message: toApprover
        ? who(m) + '을(를) 승인자로 지정합니다. 승인자는 예약 승인·취소 처리를 할 수 있습니다.'
        : who(m) + '의 승인자 권한을 해제하고 일반 회원으로 바꿉니다.',
      confirmLabel: toApprover ? '지정' : '해제'
    });
    if (!answer.confirmed) return;
    send(API + '/' + encodeURIComponent(m.userId) + '/role', { role: role },
      toApprover ? '승인자로 지정했습니다.' : '일반 회원으로 변경했습니다.', button);
  }

  async function send(url, body, successMessage, button) {
    button.disabled = true;
    try {
      await AdminUi.api('PATCH', url, body);
      await filters.reload();
      AdminUi.showMessage(alertBox, successMessage, 'success');
    } catch (e) {
      button.disabled = false;
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }
})();
