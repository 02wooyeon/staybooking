/*
 * 관리자 로그인 (관리자 도메인 전용)
 *
 * 로그인 API는 일반 로그인과 같다 (POST /api/v1/auth/login).
 * 로그인 쿠키(Refresh Token)는 도메인별로 저장되므로, 일반 사이트에서 로그인한 상태가
 * 관리자 도메인으로 넘어오지 않는다 (세션 분리).
 *
 * 로그인에 성공해도 관리자·승인자가 아니면 바로 로그아웃시켜
 * 일반 회원의 세션이 관리자 도메인에 남지 않게 한다.
 * (실제 권한 확인은 서버가 API마다 다시 한다)
 */
(function () {
  'use strict';

  var form = document.getElementById('login-form');
  var email = document.getElementById('email');
  var password = document.getElementById('password');
  var toggle = document.getElementById('password-toggle');
  var submit = document.getElementById('login-submit');
  var alertBox = document.getElementById('login-alert');

  var DEFAULT_PAGE = '/index.html';
  var params = new URLSearchParams(window.location.search);
  // 같은 사이트 주소만 허용 (오픈 리다이렉트 방지)
  var next = window.BookingApi.safeNext(params.get('next'));
  if (next === '/' || next.indexOf('/login.html') === 0) next = DEFAULT_PAGE;

  function isOperator(me) {
    return Boolean(me) && AdminUi.OPERATOR_ROLES.indexOf(me.role) !== -1;
  }

  function showAlert(message, type) {
    alertBox.textContent = message;
    alertBox.className = 'alert alert-' + (type || 'error');
    alertBox.hidden = false;
  }

  function setFieldError(input, message) {
    var help = input.closest('.field').querySelector('.field-error');
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    help.textContent = message || '';
    help.hidden = !message;
  }

  function setLoading(loading) {
    submit.disabled = loading;
    submit.textContent = loading ? '로그인 중...' : '로그인';
  }

  toggle.addEventListener('click', function () {
    var show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.textContent = show ? '숨기기' : '보기';
    toggle.setAttribute('aria-pressed', show ? 'true' : 'false');
  });

  // 이미 관리자로 로그인한 상태면 바로 이동
  window.BookingApi.me().then(function (me) {
    if (isOperator(me)) window.location.replace(next);
  }).catch(function () { /* 로그인 화면 유지 */ });

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    alertBox.hidden = true;

    var emailValue = email.value.trim();
    var ok = true;
    if (!emailValue || !email.checkValidity()) {
      setFieldError(email, emailValue ? '이메일 형식이 올바르지 않습니다.' : '이메일을 입력해 주세요.');
      ok = false;
    } else {
      setFieldError(email, '');
    }
    if (!password.value) {
      setFieldError(password, '비밀번호를 입력해 주세요.');
      ok = false;
    } else {
      setFieldError(password, '');
    }
    if (!ok) return;

    setLoading(true);
    try {
      await window.BookingApi.login(emailValue, password.value);
      var me = await window.BookingApi.me();

      if (!isOperator(me)) {
        // 관리자·승인자가 아니면 세션을 남기지 않는다
        try { await window.BookingApi.logout(); } catch (e) { /* 무시 */ }
        showAlert('관리자 권한이 있는 계정으로 로그인해 주세요.');
        password.value = '';
        password.focus();
        setLoading(false);
        return;
      }

      window.location.replace(next);
    } catch (e) {
      // 서버가 계정 존재 여부를 숨긴 일반 메시지를 준다
      showAlert(e.message, e.code === 'LOGIN_LOCKED' ? 'warning' : 'error');
      password.value = '';
      password.focus();
      setLoading(false);
    }
  });
})();
