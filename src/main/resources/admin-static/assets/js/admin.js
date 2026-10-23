/*
 * 관리자·승인자 화면 공통 (ADMIN_001 ~ ADMIN_006, APPROVER_001)
 *
 * 이 파일은 관리자 도메인 전용 폴더(src/main/resources/admin-static)에 있고,
 * 관리자 도메인에서는 이 폴더가 사이트 루트(/)가 된다.
 * 공통 파일(/assets/js/api.js, /assets/css/style.css, /assets/img/menu.svg)은
 * 빌드할 때 static 폴더에서 복사해 온다. (일반 화면의 common.js 는 쓰지 않는다)
 *
 * - 권한 확인: 페이지를 그리기 전에 내 정보(role)를 확인한다.
 *   화면에서 막는 것은 편의 기능이고, 실제 차단은 서버(SecurityConfig: /api/v1/admin/** → ROLE_ADMIN)가 한다.
 * - API 호출: BookingApi.authed 로 토큰을 붙이고 401 → 로그인, 403 → 권한 없음으로 처리한다.
 * - 화면에 넣는 값은 모두 textContent 로만 넣는다.
 *   관리자 화면은 회원 이름·문의 내용·취소 사유 같은 "사용자가 입력한 값"을 관리자가 보는 곳이라
 *   innerHTML 을 쓰면 저장형 XSS 로 관리자 계정이 탈취될 수 있다.
 */
(function () {
  'use strict';

  /*
   * 왼쪽 메뉴. ready: false 인 화면은 아직 만들지 않아 "준비 중"으로 표시한다.
   * roles: 메뉴를 볼 수 있는 역할
   */
  var NAV_ITEMS = [
    { key: 'dashboard', href: '/index.html', label: '대시보드', roles: ['ROLE_ADMIN'], ready: true },
    { key: 'bookings', href: '/bookings.html', label: '예약 관리', roles: ['ROLE_ADMIN'], ready: false },
    { key: 'members', href: '/members.html', label: '회원 관리', roles: ['ROLE_ADMIN'], ready: false },
    { key: 'accommodations', href: '/accommodations.html', label: '숙소·객실 관리', roles: ['ROLE_ADMIN'], ready: false },
    { key: 'qna', href: '/qna.html', label: 'Q&A 관리', roles: ['ROLE_ADMIN'], ready: false },
    { key: 'payments', href: '/payments.html', label: '결제·환불', roles: ['ROLE_ADMIN'], ready: false }
  ];

  // 관리자 도메인을 쓸 수 있는 역할
  var OPERATOR_ROLES = ['ROLE_ADMIN', 'ROLE_APPROVER'];

  var ROLE_LABELS = {
    ROLE_USER: '일반 회원',
    ROLE_APPROVER: '승인자',
    ROLE_ADMIN: '관리자'
  };

  var DEFAULT_PAGE_SIZE = 20;

  function $(id) { return document.getElementById(id); }

  function loginUrl() {
    return '/login.html?next=' + encodeURIComponent(window.location.pathname + window.location.search);
  }

  /* ---------- 권한 확인 · 레이아웃 ---------- */

  function showDenied(message) {
    $('admin-loading').hidden = true;
    $('admin-content').hidden = true;
    $('admin-denied-message').textContent = message;
    $('admin-denied').hidden = false;
  }

  async function logout() {
    try { await window.BookingApi.logout(); } catch (e) { /* 쿠키는 서버가 만료 처리 */ }
    window.location.replace('/login.html');
  }

  // 관리자 헤더: 이름 + 로그아웃 (일반 화면의 메뉴는 넣지 않는다)
  function renderHeader(me) {
    var nav = $('header-nav');
    nav.textContent = '';
    var name = document.createElement('span');
    name.className = 'header-user';
    name.textContent = me.name + '님';
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn-link';
    button.textContent = '로그아웃';
    button.addEventListener('click', function () {
      button.disabled = true;
      logout();
    });
    nav.append(name, button);
  }

  function renderNav(page, role) {
    var list = $('admin-nav');
    list.textContent = '';
    NAV_ITEMS.forEach(function (item) {
      if (item.roles.indexOf(role) === -1) return;
      var li = document.createElement('li');
      if (item.ready) {
        var link = document.createElement('a');
        link.href = item.href;
        link.textContent = item.label;
        if (item.key === page) link.setAttribute('aria-current', 'page');
        li.append(link);
      } else {
        var span = document.createElement('span');
        span.className = 'admin-nav-disabled';
        span.textContent = item.label;
        var badge = document.createElement('small');
        badge.textContent = '준비 중';
        span.append(badge);
        li.append(span);
      }
      list.append(li);
    });
  }

  /**
   * 관리자 페이지 시작: 헤더를 그리고 역할을 확인한 뒤 메뉴와 본문을 보여 준다.
   * @param {{ page: string, roles: string[] }} options
   * @returns {Promise<object|null>} 권한이 있으면 내 정보, 없으면 null
   */
  async function init(options) {
    // 권한 없음 화면의 "다른 계정으로 로그인"
    var relogin = $('admin-relogin');
    if (relogin) relogin.addEventListener('click', function () {
      relogin.disabled = true;
      logout();
    });

    var me = null;
    try { me = await window.BookingApi.me(); } catch (e) { /* 아래에서 로그인 화면으로 */ }

    if (!me) {
      window.location.replace(loginUrl());
      return null;
    }
    renderHeader(me);

    if (options.roles.indexOf(me.role) === -1) {
      showDenied('이 화면은 ' + options.roles.map(function (r) { return ROLE_LABELS[r] || r; }).join('·')
        + ' 계정만 사용할 수 있습니다.');
      return null;
    }

    renderNav(options.page, me.role);
    $('admin-user-name').textContent = me.name;
    $('admin-user-role').textContent = ROLE_LABELS[me.role] || me.role;
    $('admin-loading').hidden = true;
    $('admin-content').hidden = false;
    return me;
  }

  /* ---------- API 호출 ---------- */

  /**
   * 관리자 API 호출. 401 이면 로그인 화면으로, 403 이면 권한 없음 화면으로 바꾼다.
   * 그 밖의 오류는 호출한 쪽에서 메시지를 보여 줄 수 있도록 그대로 던진다.
   */
  async function api(method, url, body) {
    try {
      return await window.BookingApi.authed(method, url, body);
    } catch (e) {
      if (e.status === 401) {
        window.location.replace(loginUrl());
      } else if (e.status === 403) {
        showDenied('이 작업을 수행할 권한이 없습니다.');
      }
      throw e;
    }
  }

  // 값이 있는 항목만 골라 ?a=1&b=2 형태로 만든다
  function buildQuery(params) {
    var query = new URLSearchParams();
    Object.keys(params).forEach(function (key) {
      var value = params[key];
      if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
    });
    var text = query.toString();
    return text ? '?' + text : '';
  }

  /* ---------- 주소의 검색 조건 ---------- */

  /**
   * 주소(?status=PENDING&page=2)에서 검색 조건을 읽는다. 없는 값은 defaults 를 쓴다.
   * page 는 0 이상 정수만 허용한다.
   */
  function readQuery(defaults) {
    var current = new URLSearchParams(window.location.search);
    var result = {};
    Object.keys(defaults).forEach(function (key) {
      var value = current.get(key);
      result[key] = value === null ? defaults[key] : value;
    });
    if ('page' in result) {
      var page = Number(result.page);
      result.page = Number.isInteger(page) && page >= 0 ? page : 0;
    }
    return result;
  }

  // 검색 조건을 주소에 반영한다 (새로고침·공유해도 같은 화면)
  function writeQuery(params) {
    window.history.replaceState(null, '', window.location.pathname + buildQuery(params));
  }

  /* ---------- 표 · 페이지 이동 ---------- */

  /**
   * 표 본문을 그린다.
   * columns: [{ label, render(row) → 문자열 | Node }]
   * 문자열은 textContent 로 넣으므로 사용자 입력이 HTML 로 해석되지 않는다.
   */
  function renderTable(tbody, rows, columns, emptyMessage) {
    tbody.textContent = '';
    if (!rows.length) {
      var emptyRow = document.createElement('tr');
      var emptyCell = document.createElement('td');
      emptyCell.colSpan = columns.length;
      emptyCell.className = 'admin-table-empty';
      emptyCell.textContent = emptyMessage || '조회된 내용이 없습니다.';
      emptyRow.append(emptyCell);
      tbody.append(emptyRow);
      return;
    }
    rows.forEach(function (row) {
      var tr = document.createElement('tr');
      columns.forEach(function (column) {
        var td = document.createElement('td');
        td.dataset.label = column.label; // 모바일에서 카드형으로 보일 때 쓰는 제목
        var value = column.render(row);
        if (value instanceof Node) td.append(value);
        else td.textContent = value === null || value === undefined || value === '' ? '-' : String(value);
        tr.append(td);
      });
      tbody.append(tr);
    });
  }

  /**
   * 페이지 이동 (서버 PageResponse: { content, page, size, totalElements, totalPages })
   * onChange(page) 는 0 부터 시작하는 페이지 번호를 받는다.
   */
  function renderPagination(container, pageResponse, onChange) {
    container.textContent = '';
    var total = pageResponse.totalPages || 0;
    var current = pageResponse.page || 0;

    var info = document.createElement('p');
    info.className = 'admin-pagination-info';
    info.textContent = '총 ' + (pageResponse.totalElements || 0).toLocaleString('ko-KR') + '건';
    container.append(info);
    if (total <= 1) return;

    var nav = document.createElement('nav');
    nav.className = 'admin-pagination';
    nav.setAttribute('aria-label', '페이지');

    function addButton(label, page, options) {
      var button = document.createElement('button');
      button.type = 'button';
      button.textContent = label;
      if (options && options.ariaLabel) button.setAttribute('aria-label', options.ariaLabel);
      if (page === current) button.setAttribute('aria-current', 'page');
      button.disabled = page < 0 || page >= total || page === current;
      button.addEventListener('click', function () { onChange(page); });
      nav.append(button);
    }

    addButton('‹', current - 1, { ariaLabel: '이전 페이지' });
    // 현재 페이지 앞뒤 2쪽씩
    var start = Math.max(0, current - 2);
    var end = Math.min(total - 1, current + 2);
    for (var p = start; p <= end; p++) addButton(String(p + 1), p);
    addButton('›', current + 1, { ariaLabel: '다음 페이지' });
    container.append(nav);
  }

  /* ---------- 확인 창 ---------- */

  /**
   * 직접 취소·회원 정지·권한 변경처럼 되돌리기 어려운 작업 전에 띄우는 확인 창.
   * options: { title, message, confirmLabel, danger, reasonLabel, reasonRequired, reasonMaxLength }
   * @returns {Promise<{ confirmed: boolean, reason: string }>}
   */
  function confirmAction(options) {
    return new Promise(function (resolve) {
      var dialog = document.createElement('dialog');
      dialog.className = 'admin-dialog';
      dialog.setAttribute('aria-labelledby', 'admin-dialog-title');

      var form = document.createElement('form');
      form.method = 'dialog';
      form.noValidate = true;

      var title = document.createElement('h2');
      title.id = 'admin-dialog-title';
      title.textContent = options.title;
      var message = document.createElement('p');
      message.className = 'admin-dialog-message';
      message.textContent = options.message;
      form.append(title, message);

      var reason = null;
      var error = document.createElement('p');
      error.className = 'field-error';
      error.hidden = true;
      if (options.reasonLabel) {
        var label = document.createElement('label');
        label.className = 'field-label';
        label.htmlFor = 'admin-dialog-reason';
        label.textContent = options.reasonLabel + (options.reasonRequired ? '' : ' (선택)');
        reason = document.createElement('textarea');
        reason.id = 'admin-dialog-reason';
        reason.className = 'form-textarea admin-dialog-reason';
        reason.maxLength = options.reasonMaxLength || 255;
        reason.rows = 3;
        form.append(label, reason, error);
      }

      var actions = document.createElement('div');
      actions.className = 'admin-dialog-actions';
      var cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'btn-outline';
      cancel.textContent = '취소';
      var ok = document.createElement('button');
      ok.type = 'submit';
      ok.className = options.danger ? 'btn-primary btn-danger' : 'btn-primary';
      ok.textContent = options.confirmLabel || '확인';
      actions.append(cancel, ok);
      form.append(actions);
      dialog.append(form);
      document.body.append(dialog);

      function close(result) {
        dialog.close();
        dialog.remove();
        resolve(result);
      }

      cancel.addEventListener('click', function () { close({ confirmed: false, reason: '' }); });
      dialog.addEventListener('cancel', function (event) { // Esc
        event.preventDefault();
        close({ confirmed: false, reason: '' });
      });
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        var text = reason ? reason.value.trim() : '';
        if (reason && options.reasonRequired && !text) {
          error.textContent = options.reasonLabel + '을(를) 입력해 주세요.';
          error.hidden = false;
          reason.setAttribute('aria-invalid', 'true');
          reason.focus();
          return;
        }
        close({ confirmed: true, reason: text });
      });

      dialog.showModal();
      (reason || cancel).focus();
    });
  }

  /* ---------- 표시 형식 ---------- */

  function formatPrice(value) {
    return Number(value || 0).toLocaleString('ko-KR') + '원';
  }

  // "2026-10-23T05:12:30" → "2026-10-23 05:12"
  function formatDateTime(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
  }

  // 상태값을 색 있는 배지로 (labels: { STATUS: '표시 이름' }, tones: { STATUS: 'ok|warn|error|muted' })
  function statusBadge(status, labels, tones) {
    var badge = document.createElement('span');
    badge.className = 'admin-badge ' + ((tones && tones[status]) || 'muted');
    badge.textContent = (labels && labels[status]) || status || '-';
    return badge;
  }

  window.AdminUi = {
    DEFAULT_PAGE_SIZE: DEFAULT_PAGE_SIZE,
    OPERATOR_ROLES: OPERATOR_ROLES,
    ROLE_LABELS: ROLE_LABELS,
    logout: logout,
    init: init,
    api: api,
    buildQuery: buildQuery,
    readQuery: readQuery,
    writeQuery: writeQuery,
    renderTable: renderTable,
    renderPagination: renderPagination,
    confirmAction: confirmAction,
    formatPrice: formatPrice,
    formatDateTime: formatDateTime,
    statusBadge: statusBadge
  };
})();
