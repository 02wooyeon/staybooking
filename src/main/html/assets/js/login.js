/* 로그인 화면 (AUTH_001, AUTH_002, AUTH_003) */
(function () {
  'use strict';

  var form = document.getElementById('login-form');
  var email = document.getElementById('email');
  var password = document.getElementById('password');
  var submit = document.getElementById('login-submit');
  var alertBox = document.getElementById('login-alert');
  var params = new URLSearchParams(window.location.search);
  var next = window.BookingApi.safeNext(params.get('next'));

  Ui.bindPasswordToggles();

  // 회원가입 직후 (SIGNUP_004)
  if (params.get('signup') === 'done') {
    Ui.showAlert(alertBox, '회원가입이 완료되었습니다. 로그인해 주세요.', 'success');
  }

  // 이미 로그인한 상태면 바로 이동
  window.BookingApi.me().then(function (me) {
    if (me) window.location.replace(next);
  }).catch(function () { /* 로그인 화면 유지 */ });

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    Ui.hideAlert(alertBox);

    var emailValue = email.value.trim();
    var ok = true;
    if (!emailValue) {
      Ui.setFieldError(email, '이메일을 입력해 주세요.');
      ok = false;
    } else if (!email.checkValidity()) {
      Ui.setFieldError(email, '이메일 형식이 올바르지 않습니다.');
      ok = false;
    } else {
      Ui.setFieldError(email, '');
    }
    if (!password.value) {
      Ui.setFieldError(password, '비밀번호를 입력해 주세요.');
      ok = false;
    } else {
      Ui.setFieldError(password, '');
    }
    if (!ok) return;

    Ui.setLoading(submit, true, '로그인 중...');
    try {
      await window.BookingApi.login(emailValue, password.value);
      window.location.replace(next);
    } catch (e) {
      // 서버가 계정 존재 여부를 숨긴 일반 메시지를 주므로 그대로 보여 준다
      Ui.showAlert(alertBox, e.message, e.code === 'LOGIN_LOCKED' ? 'warning' : 'error');
      password.value = '';
      password.focus();
      Ui.setLoading(submit, false);
    }
  });
})();
