/*
 * 회원가입 화면 (SIGNUP_001, SIGNUP_002, SIGNUP_004)
 * 클라이언트 검증은 사용 편의용이고, 실제 검증은 서버(SignupRequest)가 다시 한다.
 * 정규식은 서버 DTO와 같게 맞춰 둔다.
 */
(function () {
  'use strict';

  var PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,20}$/;
  var NAME_RE = /^[가-힣A-Za-z ]{2,20}$/;
  var PHONE_RE = /^01[016789]-?\d{3,4}-?\d{4}$/;

  var form = document.getElementById('signup-form');
  var alertBox = document.getElementById('signup-alert');
  var submit = document.getElementById('signup-submit');
  var f = {
    email: document.getElementById('email'),
    checkEmail: document.getElementById('check-email'),
    emailStatus: document.getElementById('email-status'),
    password: document.getElementById('password'),
    passwordConfirm: document.getElementById('password-confirm'),
    name: document.getElementById('name'),
    phone: document.getElementById('phone'),
    agreeAll: document.getElementById('agree-all'),
    agreeTerms: document.getElementById('agree-terms'),
    agreePrivacy: document.getElementById('agree-privacy'),
    agreeMarketing: document.getElementById('agree-marketing')
  };
  var rules = {
    length: document.querySelector('[data-rule="length"]'),
    mix: document.querySelector('[data-rule="mix"]'),
    space: document.querySelector('[data-rule="space"]'),
    email: document.querySelector('[data-rule="email"]')
  };

  // 중복 확인을 통과한 이메일. 이메일을 바꾸면 다시 확인해야 한다
  var checkedEmail = null;

  Ui.bindPasswordToggles();

  /* ---------- 이메일 ---------- */

  function normalizedEmail() {
    return f.email.value.trim().toLowerCase();
  }

  function setEmailStatus(message, type) {
    f.emailStatus.textContent = message;
    f.emailStatus.className = 'field-status ' + (type || '');
    f.emailStatus.hidden = !message;
  }

  f.email.addEventListener('input', function () {
    checkedEmail = null;
    setEmailStatus('');
    Ui.setFieldError(f.email, '');
    updatePasswordRules();
  });

  f.checkEmail.addEventListener('click', checkEmail);

  async function checkEmail() {
    var value = normalizedEmail();
    if (!value) {
      Ui.setFieldError(f.email, '이메일을 입력해 주세요.');
      return false;
    }
    if (!f.email.checkValidity() || value.length > 100) {
      Ui.setFieldError(f.email, '이메일 형식이 올바르지 않습니다.');
      return false;
    }
    Ui.setFieldError(f.email, '');
    Ui.setLoading(f.checkEmail, true, '확인 중');
    try {
      var res = await window.BookingApi.checkEmail(value);
      if (res.available) {
        checkedEmail = value;
        setEmailStatus('사용할 수 있는 이메일입니다.', 'ok');
        return true;
      }
      checkedEmail = null;
      Ui.setFieldError(f.email, '이미 사용 중인 이메일입니다.');
      return false;
    } catch (e) {
      Ui.setFieldError(f.email, e.message);
      return false;
    } finally {
      Ui.setLoading(f.checkEmail, false);
    }
  }

  /* ---------- 비밀번호 ---------- */

  function passwordChecks(pw) {
    var email = normalizedEmail();
    var emailId = email.indexOf('@') > 0 ? email.slice(0, email.indexOf('@')) : '';
    return {
      length: pw.length >= 8 && pw.length <= 20,
      mix: /[A-Za-z]/.test(pw) && /\d/.test(pw) && /[^A-Za-z0-9\s]/.test(pw),
      space: pw.length > 0 && !/\s/.test(pw),
      // 서버 규칙: 이메일 아이디(4자 이상)를 비밀번호에 포함할 수 없음
      email: pw.length > 0 && !(emailId.length >= 4 && pw.toLowerCase().indexOf(emailId) !== -1)
    };
  }

  function updatePasswordRules() {
    var checks = passwordChecks(f.password.value);
    Object.keys(rules).forEach(function (key) {
      rules[key].classList.toggle('ok', checks[key]);
    });
  }

  f.password.addEventListener('input', function () {
    updatePasswordRules();
    Ui.setFieldError(f.password, '');
    if (f.passwordConfirm.value) validatePasswordConfirm();
  });
  f.passwordConfirm.addEventListener('input', validatePasswordConfirm);

  function validatePassword() {
    var pw = f.password.value;
    var checks = passwordChecks(pw);
    if (!pw) return fail(f.password, '비밀번호를 입력해 주세요.');
    if (!PASSWORD_RE.test(pw) || !checks.email) return fail(f.password, '비밀번호 규칙을 확인해 주세요.');
    return pass(f.password);
  }

  function validatePasswordConfirm() {
    if (!f.passwordConfirm.value) return fail(f.passwordConfirm, '비밀번호를 한 번 더 입력해 주세요.');
    if (f.passwordConfirm.value !== f.password.value) return fail(f.passwordConfirm, '비밀번호가 일치하지 않습니다.');
    return pass(f.passwordConfirm);
  }

  /* ---------- 이름·연락처 ---------- */

  function validateName() {
    var v = f.name.value.trim();
    if (!v) return fail(f.name, '이름을 입력해 주세요.');
    if (!NAME_RE.test(v)) return fail(f.name, '이름은 한글 또는 영문 2~20자로 입력해 주세요.');
    return pass(f.name);
  }

  // 숫자만 남기고 010-1234-5678 형태로 자동 하이픈
  f.phone.addEventListener('input', function () {
    var d = f.phone.value.replace(/\D/g, '').slice(0, 11);
    if (d.length > 7) {
      f.phone.value = d.slice(0, 3) + '-' + d.slice(3, d.length - 4) + '-' + d.slice(d.length - 4);
    } else if (d.length > 3) {
      f.phone.value = d.slice(0, 3) + '-' + d.slice(3);
    } else {
      f.phone.value = d;
    }
    Ui.setFieldError(f.phone, '');
  });

  function validatePhone() {
    var v = f.phone.value.trim();
    if (!v) return fail(f.phone, '휴대폰 번호를 입력해 주세요.');
    if (!PHONE_RE.test(v)) return fail(f.phone, '휴대폰 번호 형식이 올바르지 않습니다.');
    return pass(f.phone);
  }

  // 입력 중에 오류 문구를 지워 두어야, 다음 칸을 누를 때 레이아웃이 밀려 클릭이 빗나가지 않는다
  f.name.addEventListener('input', function () { Ui.setFieldError(f.name, ''); });
  f.name.addEventListener('blur', function () { if (f.name.value) validateName(); });
  f.phone.addEventListener('blur', function () { if (f.phone.value) validatePhone(); });

  /* ---------- 약관 ---------- */

  var agreeItems = [f.agreeTerms, f.agreePrivacy, f.agreeMarketing];

  f.agreeAll.addEventListener('change', function () {
    agreeItems.forEach(function (item) { item.checked = f.agreeAll.checked; });
    document.getElementById('agree-error').hidden = true;
  });
  agreeItems.forEach(function (item) {
    item.addEventListener('change', function () {
      f.agreeAll.checked = agreeItems.every(function (i) { return i.checked; });
      document.getElementById('agree-error').hidden = true;
    });
  });

  function validateAgreements() {
    var ok = f.agreeTerms.checked && f.agreePrivacy.checked;
    document.getElementById('agree-error').hidden = ok;
    return ok;
  }

  /* ---------- 제출 ---------- */

  function fail(input, message) { Ui.setFieldError(input, message); return false; }
  function pass(input) { Ui.setFieldError(input, ''); return true; }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    Ui.hideAlert(alertBox);

    var results = [
      validatePassword(),
      validatePasswordConfirm(),
      validateName(),
      validatePhone(),
      validateAgreements()
    ];
    var emailOk = checkedEmail === normalizedEmail() || await checkEmail();

    if (!emailOk || results.indexOf(false) !== -1) {
      var firstInvalid = form.querySelector('[aria-invalid="true"]');
      if (firstInvalid) firstInvalid.focus();
      Ui.showAlert(alertBox, '입력하신 내용을 다시 확인해 주세요.', 'error');
      return;
    }

    Ui.setLoading(submit, true, '가입 중...');
    try {
      await window.BookingApi.signup({
        email: normalizedEmail(),
        password: f.password.value,
        passwordConfirm: f.passwordConfirm.value,
        name: f.name.value.trim(),
        phone: f.phone.value.trim(),
        agreeTerms: f.agreeTerms.checked,
        agreePrivacy: f.agreePrivacy.checked,
        agreeMarketing: f.agreeMarketing.checked
      });
      window.location.replace('/login.html?signup=done');
    } catch (e) {
      if (e.code === 'DUPLICATE_EMAIL') {
        checkedEmail = null;
        setEmailStatus('');
        Ui.setFieldError(f.email, e.message);
        f.email.focus();
      }
      Ui.showAlert(alertBox, e.message, 'error');
      Ui.setLoading(submit, false);
    }
  });
})();
