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
    return me;
  }

  window.Ui = {
    showAlert: showAlert,
    hideAlert: hideAlert,
    setFieldError: setFieldError,
    setLoading: setLoading,
    bindPasswordToggles: bindPasswordToggles,
    renderHeader: renderHeader
  };
})();
