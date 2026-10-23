/*
 * 마이페이지(설정 화면): 왼쪽 메뉴로 내 정보 관리 / 마일리지 / 설정을 전환한다.
 * 회원 정보와 현재 마일리지는 API에서 조회하고,
 * 일반 설정은 이 브라우저의 localStorage에만 저장한다.
 */
(function () {
  'use strict';

  var PHONE_RE = /^01[016789]-?\d{3,4}-?\d{4}$/;
  var NICKNAME_RE = /^[가-힣A-Za-z0-9]{2,20}$/;
  var MILEAGE_LABELS = { EARN: '적립', USE: '사용', REFUND: '환불' };

  var headerReady = Ui.renderHeader();

  /* ---------- 왼쪽 메뉴 전환 ---------- */

  var PANELS = ['profile', 'mileage', 'preferences'];
  var tabLinks = document.querySelectorAll('.settings-tabs a');
  var defaultPanel = 'profile';

  function showPanel(name) {
    if (PANELS.indexOf(name) === -1) name = defaultPanel;
    PANELS.forEach(function (panel) {
      document.getElementById('panel-' + panel).hidden = panel !== name;
    });
    tabLinks.forEach(function (link) {
      if (link.dataset.panel === name) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
  }

  window.addEventListener('hashchange', function () {
    showPanel(window.location.hash.slice(1));
  });

  // 주소에 #패널이 없으면 로그인 여부를 확인한 뒤 기본 패널을 연다 (비로그인: 설정)
  if (PANELS.indexOf(window.location.hash.slice(1)) !== -1) {
    showPanel(window.location.hash.slice(1));
  } else {
    headerReady.then(function (me) {
      defaultPanel = me ? 'profile' : 'preferences';
      showPanel(window.location.hash.slice(1));
    });
  }

  /* ---------- 내 정보 관리 ---------- */

  var profileForm = document.getElementById('profile-form');
  var profileAlert = document.getElementById('profile-alert');
  var profileSubmit = document.getElementById('profile-submit');
  var nicknameInput = document.getElementById('profile-nickname');
  var phoneInput = document.getElementById('profile-phone');

  function renderProfile(profile) {
    document.getElementById('profile-name').textContent = profile.name || '-';
    document.getElementById('profile-email').textContent = profile.email || '-';
    document.getElementById('profile-joined').textContent = profile.createdAt || '-';
    nicknameInput.value = profile.nickname || '';
    phoneInput.value = profile.phone || '';
  }

  // 숫자만 남기고 010-1234-5678 형태로 자동 하이픈
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
  nicknameInput.addEventListener('input', function () { Ui.setFieldError(nicknameInput, ''); });

  profileForm.addEventListener('submit', function (event) {
    event.preventDefault();
    Ui.hideAlert(profileAlert);

    var nickname = nicknameInput.value.trim();
    var phone = phoneInput.value.trim();
    var ok = true;
    if (nickname && !NICKNAME_RE.test(nickname)) {
      Ui.setFieldError(nicknameInput, '닉네임은 한글·영문·숫자 2~20자로 입력해 주세요.');
      ok = false;
    } else {
      Ui.setFieldError(nicknameInput, '');
    }
    if (!phone) {
      Ui.setFieldError(phoneInput, '휴대폰 번호를 입력해 주세요.');
      ok = false;
    } else if (!PHONE_RE.test(phone)) {
      Ui.setFieldError(phoneInput, '휴대폰 번호 형식이 올바르지 않습니다.');
      ok = false;
    } else {
      Ui.setFieldError(phoneInput, '');
    }
    if (!ok) return;

    saveProfile(nickname, phone);
  });

  // PATCH /api/v1/members/me. 이름·이메일은 바꿀 수 없고, 서버가 입력값을 다시 검사한다.
  async function saveProfile(nickname, phone) {
    Ui.setLoading(profileSubmit, true, '저장 중...');
    try {
      var updated = await BookingApi.updateMe({ nickname: nickname, phone: phone });
      renderProfile(updated);
      Ui.showAlert(profileAlert, '회원 정보가 저장되었습니다.', 'success');
    } catch (e) {
      Ui.showAlert(profileAlert, e.message, 'error');
    } finally {
      Ui.setLoading(profileSubmit, false);
    }
  }

  /* ---------- 마일리지 ---------- */

  var historyList = document.getElementById('mileage-history');
  var historyEmpty = document.getElementById('mileage-empty');
  var filterButtons = document.querySelectorAll('[data-mileage-filter]');
  var mileage = { balance: 0, histories: [] };

  function formatPoint(value) {
    return value.toLocaleString('ko-KR') + ' P';
  }

  function sumBy(type) {
    return mileage.histories
      .filter(function (h) { return h.type === type; })
      .reduce(function (total, h) { return total + h.amount; }, 0);
  }

  function renderMileageHistory(filter) {
    historyList.textContent = '';
    var items = mileage.histories.filter(function (h) {
      return filter === 'all' || h.type === filter;
    });
    items.forEach(function (h) {
      var li = document.createElement('li');

      var info = document.createElement('div');
      var desc = document.createElement('p');
      desc.className = 'mileage-desc';
      desc.textContent = h.description;
      var meta = document.createElement('p');
      meta.className = 'mileage-meta';
      meta.textContent = h.createdAt + ' · ' + MILEAGE_LABELS[h.type];
      info.append(desc, meta);

      var amount = document.createElement('p');
      amount.className = 'mileage-amount ' + (h.type === 'USE' ? 'minus' : 'plus');
      amount.textContent = (h.type === 'USE' ? '-' : '+') + formatPoint(h.amount);

      li.append(info, amount);
      historyList.append(li);
    });
    historyEmpty.hidden = items.length > 0;
  }

  function renderMileageSummary() {
    document.getElementById('mileage-balance').textContent = mileage.balance.toLocaleString('ko-KR');
    document.getElementById('mileage-earned').textContent = formatPoint(sumBy('EARN'));
    document.getElementById('mileage-used').textContent = formatPoint(sumBy('USE'));
    renderMileageHistory('all');
  }

  renderMileageSummary();

  headerReady.then(function (me) {
    if (!me) {
      Ui.showAlert(profileAlert, '로그인 후 내 정보를 확인할 수 있습니다.');
      profileSubmit.disabled = true;
      return;
    }
    renderProfile(me);
    mileage.balance = Number(me.mileageBalance || 0);
    renderMileageSummary();
  }).catch(function () {
    Ui.showAlert(profileAlert, '내 정보를 불러오지 못했습니다.');
  });

  filterButtons.forEach(function (button) {
    button.addEventListener('click', function () {
      filterButtons.forEach(function (b) {
        b.setAttribute('aria-pressed', b === button ? 'true' : 'false');
      });
      renderMileageHistory(button.dataset.mileageFilter);
    });
  });

  /* ---------- 설정 (localStorage) ---------- */

  var STORAGE_KEY = 'staybookSettings';
  var DEFAULTS = {
    language: 'ko',
    defaultSort: 'recommended'
  };

  var savedMessage = document.getElementById('settings-saved');
  var controls = document.querySelectorAll('[data-setting]');
  var hideTimer = null;

  function load() {
    var settings = Object.assign({}, DEFAULTS);
    try {
      var stored = JSON.parse(localStorage.getItem(STORAGE_KEY));
      if (stored && typeof stored === 'object') Object.assign(settings, stored);
    } catch (e) { /* 저장소를 못 쓰면 기본값 사용 */ }
    return settings;
  }

  function save(settings) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
      return true;
    } catch (e) {
      return false;
    }
  }

  function showSaved(ok) {
    savedMessage.textContent = ok ? '설정이 저장되었습니다.' : '이 브라우저에서는 설정을 저장할 수 없습니다.';
    savedMessage.hidden = false;
    clearTimeout(hideTimer);
    hideTimer = setTimeout(function () { savedMessage.hidden = true; }, 2000);
  }

  var settings = load();

  controls.forEach(function (control) {
    var key = control.dataset.setting;
    if (control.type === 'checkbox') {
      control.checked = Boolean(settings[key]);
    } else {
      control.value = settings[key];
      if (control.value !== settings[key]) control.value = DEFAULTS[key];
    }

    control.addEventListener('change', function () {
      settings[key] = control.type === 'checkbox' ? control.checked : control.value;
      showSaved(save(settings));
    });
  });

  /* ---------- 회원 탈퇴 ---------- */

var withdrawOpenButton =
  document.getElementById(
    'withdraw-open-button'
  );

var withdrawBox =
  document.getElementById(
    'withdraw-box'
  );

var withdrawCancelButton =
  document.getElementById(
    'withdraw-cancel-button'
  );

var withdrawSubmitButton =
  document.getElementById(
    'withdraw-submit-button'
  );

var withdrawAgree =
  document.getElementById(
    'withdraw-agree'
  );

var withdrawPassword =
  document.getElementById(
    'withdraw-password'
  );


withdrawOpenButton.addEventListener(
  'click',
  function () {

    withdrawBox.hidden = false;
  }
);


withdrawCancelButton.addEventListener(
  'click',
  function () {

    withdrawBox.hidden = true;

    withdrawAgree.checked = false;

    withdrawPassword.value = '';
  }
);


withdrawSubmitButton.addEventListener(
  'click',
  function () {

    if (!withdrawAgree.checked) {

      alert(
        '회원 탈퇴 안내에 동의해 주세요.'
      );

      return;
    }


    if (!withdrawPassword.value) {

      alert(
        '비밀번호를 입력해 주세요.'
      );

      withdrawPassword.focus();

      return;
    }


    var confirmed =
      confirm(
        '정말 회원 탈퇴하시겠습니까?'
      );

    if (!confirmed) {
      return;
    }

    withdraw(withdrawPassword.value);
  }
);

// DELETE /api/v1/members/me. 진행 중인 예약이 있거나 비밀번호가 틀리면 서버가 거절한다.
async function withdraw(password) {
  Ui.setLoading(withdrawSubmitButton, true, '탈퇴 처리 중...');
  try {
    await BookingApi.withdraw(password);
    alert('회원 탈퇴가 완료되었습니다. 그동안 이용해 주셔서 감사합니다.');
    window.location.replace('/');
  } catch (e) {
    alert(e.message);
    withdrawPassword.value = '';
    withdrawPassword.focus();
    Ui.setLoading(withdrawSubmitButton, false);
  }
}

})();
