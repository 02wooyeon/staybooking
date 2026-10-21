/*
 * 마이페이지(설정 화면): 왼쪽 메뉴로 내 정보 관리 / 마일리지 / 설정을 전환한다.
 * 백엔드 연동 전이라 회원 정보·마일리지는 mock 데이터,
 * 일반 설정은 이 브라우저의 localStorage에만 저장한다.
 */
(function () {
  'use strict';

  var USE_MOCK_DATA = true;

  /*
   * 백엔드 연동 전 mock 데이터
   *
   * 실제 API:
   * GET /api/v1/members/me
   */
  var MOCK_PROFILE = {
    name: '홍길동',
    email: 'hong@example.com',
    nickname: '길동',
    phone: '010-1234-5678',
    createdAt: '2026-10-01'
  };

  /*
   * 실제 API: 미정 (mileage_histories 테이블 기준)
   * type: EARN(적립), USE(사용), REFUND(환불)
   */
  var MOCK_MILEAGE = {
    balance: 12500,
    histories: [
      { type: 'EARN', amount: 9000, description: '스테이 강남 숙박 완료 적립', createdAt: '2026-10-12' },
      { type: 'REFUND', amount: 5000, description: '한강 스테이 예약 취소 환불', createdAt: '2026-10-09' },
      { type: 'USE', amount: 5000, description: '한강 스테이 예약 결제', createdAt: '2026-10-08' },
      { type: 'EARN', amount: 3500, description: '부산 오션뷰 숙박 완료 적립', createdAt: '2026-09-21' }
    ]
  };

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

  if (USE_MOCK_DATA) renderProfile(MOCK_PROFILE);

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

    // 실제 API: PATCH /api/v1/members/me (연동 전에는 화면 메시지만 표시)
    Ui.setLoading(profileSubmit, true, '저장 중...');
    setTimeout(function () {
      Ui.setLoading(profileSubmit, false);
      Ui.showAlert(profileAlert, '회원 정보가 저장되었습니다.', 'success');
    }, 300);
  });

  /* ---------- 마일리지 ---------- */

  var historyList = document.getElementById('mileage-history');
  var historyEmpty = document.getElementById('mileage-empty');
  var filterButtons = document.querySelectorAll('[data-mileage-filter]');
  var mileage = USE_MOCK_DATA ? MOCK_MILEAGE : { balance: 0, histories: [] };

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

  document.getElementById('mileage-balance').textContent = mileage.balance.toLocaleString('ko-KR');
  document.getElementById('mileage-earned').textContent = formatPoint(sumBy('EARN'));
  document.getElementById('mileage-used').textContent = formatPoint(sumBy('USE'));
  renderMileageHistory('all');

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

  /* ---------- 회원 탈퇴 mock ---------- */

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


    /*
     * 실제 API 연동 시
     * 회원 탈퇴 API 호출로 교체
     */

    alert(
      '회원 탈퇴 mock 테스트가 완료되었습니다.'
    );
  }
);

})();
