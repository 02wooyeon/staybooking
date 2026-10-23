/* 아이디(이메일) 찾기 (AUTH_006). 서버는 일부를 가린 이메일만 돌려준다. */
(function () {
  'use strict';

  var NAME_RE = /^[가-힣A-Za-z ]{2,20}$/;
  var PHONE_RE = /^01[016789]-?\d{3,4}-?\d{4}$/;

  var form = document.getElementById('find-form');
  var alertBox = document.getElementById('find-alert');
  var submit = document.getElementById('find-submit');
  var nameInput = document.getElementById('name');
  var phoneInput = document.getElementById('phone');
  var result = document.getElementById('find-result');

  // 숫자만 남기고 010-1234-5678 형태로 자동 하이픈 (회원가입 화면과 같은 방식)
  phoneInput.addEventListener('input', function () {
    var d = phoneInput.value.replace(/\D/g, '').slice(0, 11);
    if (d.length > 7) {
      phoneInput.value = d.slice(0, 3) + '-' + d.slice(3, d.length - 4) + '-' + d.slice(d.length - 4);
    } else if (d.length > 3) {
      phoneInput.value = d.slice(0, 3) + '-' + d.slice(3);
    } else {
      phoneInput.value = d;
    }
    Ui.setFieldError(phoneInput, '');
  });
  nameInput.addEventListener('input', function () { Ui.setFieldError(nameInput, ''); });

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    Ui.hideAlert(alertBox);
    result.hidden = true;
    result.textContent = '';

    var name = nameInput.value.trim();
    var phone = phoneInput.value.trim();
    var ok = true;
    if (!NAME_RE.test(name)) {
      Ui.setFieldError(nameInput, '이름은 한글 또는 영문 2~20자로 입력해 주세요.');
      ok = false;
    }
    if (!PHONE_RE.test(phone)) {
      Ui.setFieldError(phoneInput, '휴대폰 번호 형식이 올바르지 않습니다.');
      ok = false;
    }
    if (!ok) return;

    Ui.setLoading(submit, true, '찾는 중...');
    try {
      var data = await BookingApi.findEmail({ name: name, phone: phone });
      var emails = Array.isArray(data.emails) ? data.emails : [];
      if (!emails.length) {
        Ui.showAlert(alertBox, '입력한 정보와 일치하는 계정이 없습니다.');
        return;
      }
      Ui.showAlert(alertBox, '가입한 이메일을 찾았습니다. 보안을 위해 일부만 보여 드립니다.', 'success');
      emails.forEach(function (email) {
        var li = document.createElement('li');
        li.textContent = email;
        result.append(li);
      });
      result.hidden = false;
    } catch (e) {
      Ui.showAlert(alertBox, e.message, e.code === 'TOO_MANY_REQUESTS' ? 'warning' : 'error');
    } finally {
      Ui.setLoading(submit, false);
    }
  });
})();
