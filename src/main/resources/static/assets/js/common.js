/* 화면 공통: 헤더 로그인 상태, 메시지 표시, 비밀번호 보기 토글 */
(function () {
  'use strict';

  /** 사용자 입력·서버 응답은 항상 textContent로 넣어 XSS를 막는다 (innerHTML 금지) */
  function showAlert(el, message, type) {
    el.textContent = message;
    el.className = 'alert alert-' + (type || 'error');
    el.hidden = false;
  }

  function hideAlert(el) {
    el.hidden = true;
    el.textContent = '';
  }

  function setFieldError(input, message) {
    var field = input.closest('.field');
    var help = field && field.querySelector('.field-error');
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    if (help) {
      help.textContent = message || '';
      help.hidden = !message;
    }
  }

  function setLoading(button, loading, text) {
    if (loading) {
      button.dataset.label = button.textContent;
      button.textContent = text || '처리 중...';
      button.disabled = true;
    } else {
      button.textContent = button.dataset.label || button.textContent;
      button.disabled = false;
    }
  }

  function bindPasswordToggles() {
    document.querySelectorAll('[data-toggle-password]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var input = document.getElementById(btn.getAttribute('data-toggle-password'));
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.textContent = show ? '숨기기' : '보기';
        btn.setAttribute('aria-pressed', show ? 'true' : 'false');
      });
    });
  }

  /** 헤더 오른쪽: 비로그인이면 로그인/회원가입, 로그인이면 이름과 로그아웃 */
  async function renderHeader() {
    var nav = document.getElementById('header-nav');
    if (!nav) return null;
    var me = null;
    try { me = await window.BookingApi.me(); } catch (e) { /* 비로그인으로 표시 */ }

    nav.textContent = '';
    if (me) {
      var name = document.createElement('span');
      name.className = 'header-user';
      name.textContent = me.name + '님';
      var logout = document.createElement('button');
      logout.type = 'button';
      logout.className = 'btn-link';
      logout.textContent = '로그아웃';
      logout.addEventListener('click', async function () {
        logout.disabled = true;
        try { await window.BookingApi.logout(); } catch (e) { /* 쿠키는 서버가 만료 처리 */ }
        window.location.href = '/';
      });
      nav.append(name, logout);
    } else {
      var login = document.createElement('a');
      login.href = '/auth/login.html';
      login.textContent = '로그인';
      var signup = document.createElement('a');
      signup.href = '/auth/signup.html';
      signup.className = 'btn-small';
      signup.textContent = '회원가입';
      nav.append(login, signup);
    }
    nav.append(createHeaderMenu(me));
    return me;
  }

  var MENU_ITEMS = [
    { href: '/booking/my.html', label: '예약 정보' },
    { href: '/qna.html', label: '고객센터' },
    { href: '/profile/setting.html', label: '설정' }
  ];

  /** 헤더 맨 오른쪽 메뉴 버튼: 누르면 예약 정보·고객센터·설정 링크가 펼쳐진다 */
  function createHeaderMenu(me) {
    var wrap = document.createElement('div');
    wrap.className = 'header-menu';

    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'header-menu-button';
    button.setAttribute('aria-label', '메뉴');
    button.setAttribute('aria-haspopup', 'true');
    button.setAttribute('aria-expanded', 'false');
    button.setAttribute('aria-controls', 'header-menu-list');
    var icon = document.createElement('img');
    icon.src = '/assets/img/menu.svg';
    icon.alt = '';
    icon.width = 24;
    icon.height = 24;
    button.append(icon);

    var list = document.createElement('ul');
    list.id = 'header-menu-list';
    list.className = 'header-menu-list';
    list.hidden = true;
    var menuItems = me && me.role === 'ROLE_ADMIN'
      ? MENU_ITEMS.concat({ href: '/admin/payment-reconciliation.html', label: '결제 점검' })
      : MENU_ITEMS;
    menuItems.forEach(function (item) {
      var li = document.createElement('li');
      var link = document.createElement('a');
      link.href = item.href;
      link.textContent = item.label;
      if (window.location.pathname === item.href) link.setAttribute('aria-current', 'page');
      li.append(link);
      list.append(li);
    });

    function setOpen(open) {
      list.hidden = !open;
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    button.addEventListener('click', function () { setOpen(list.hidden); });
    document.addEventListener('click', function (event) {
      if (!wrap.contains(event.target)) setOpen(false);
    });
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !list.hidden) {
        setOpen(false);
        button.focus();
      }
    });

    wrap.append(button, list);
    return wrap;
  }

  /**
   * 예약번호 표시: 서버가 만든 16자리(영문 대문자+숫자)를 4자리씩 끊어 보여 준다
   * 예) SXZLHLT79U565WUE → SXZL-HLT7-9U56-5WUE
   */
  function formatBookingNumber(value) {
    var code = String(value || '');
    return /^[A-Z0-9]{16}$/.test(code) ? code.match(/.{4}/g).join('-') : code;
  }

  window.Ui = {
    showAlert: showAlert,
    hideAlert: hideAlert,
    setFieldError: setFieldError,
    setLoading: setLoading,
    bindPasswordToggles: bindPasswordToggles,
    renderHeader: renderHeader,
    formatBookingNumber: formatBookingNumber
  };
})();
