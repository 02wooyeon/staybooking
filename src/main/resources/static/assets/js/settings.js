/*
 * 마이페이지(설정 화면): 왼쪽 메뉴로 내 정보 관리 / 내 활동 / 마일리지 / 설정을 전환한다.
 * 비로그인이면 설정 패널만 볼 수 있다. 내 정보 관리·내 활동·마일리지를 열면 로그인 화면으로 보내고,
 * 로그인 후 그 패널로 돌아온다.
 * 회원 정보와 현재 마일리지는 API에서 조회하고,
 * 일반 설정은 이 브라우저의 localStorage에만 저장한다.
 */
(function () {
  'use strict';

  var PHONE_RE = /^01[016789]-?\d{3,4}-?\d{4}$/;
  var NICKNAME_RE = /^[가-힣A-Za-z0-9]{2,20}$/;
  var PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,20}$/;
  var MILEAGE_LABELS = { EARN: '적립', USE: '사용', REFUND: '환불' };

  var headerReady = Ui.renderHeader();

  /* ---------- 왼쪽 메뉴 전환 ---------- */

  var PANELS = ['profile', 'activity', 'mileage', 'preferences'];
  var tabLinks = document.querySelectorAll('.settings-tabs a');
  var MEMBER_PANELS = ['profile', 'activity', 'mileage']; // 로그인해야 볼 수 있는 패널
  var defaultPanel = 'profile';
  var signedIn = null; // null: 로그인 확인 중 (그동안은 어떤 패널도 보여 주지 않는다)
  var shownPanel = null; // 지금 화면에 보이는 패널

  function showPanel(name) {
    if (PANELS.indexOf(name) === -1) name = defaultPanel;
    shownPanel = name;
    PANELS.forEach(function (panel) {
      document.getElementById('panel-' + panel).hidden = panel !== name;
    });
    if (name === 'activity') loadActivities();
    tabLinks.forEach(function (link) {
      if (link.dataset.panel === name) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
  }

  // 로그인 후 돌아올 주소(#패널)를 붙여 로그인 화면으로 보낸다.
  // 처음 들어올 때는 replace 로, 메뉴를 눌렀을 때는 뒤로 가기로 설정 화면에 돌아올 수 있게 이동한다.
  function goLogin(panel, fromMenu) {
    var url = '/auth/login.html?next=' + encodeURIComponent(window.location.pathname + '#' + panel);
    if (fromMenu) {
      // 로그인 화면에서 뒤로 가면 보던 패널로 돌아오도록, 방금 바뀐 주소를 원래 패널로 되돌려 둔다
      window.history.replaceState(null, '', window.location.pathname + '#' + (shownPanel || 'preferences'));
      window.location.href = url;
    } else {
      window.location.replace(url);
    }
  }

  function route(fromMenu) {
    var name = window.location.hash.slice(1);
    if (PANELS.indexOf(name) === -1) name = defaultPanel;
    if (!signedIn && MEMBER_PANELS.indexOf(name) !== -1) {
      goLogin(name, fromMenu);
      return;
    }
    showPanel(name);
  }

  window.addEventListener('hashchange', function () {
    if (signedIn !== null) route(true);
  });

  // 로그인 여부를 먼저 확인한다. 비로그인이면 기본 패널은 설정이고, 회원 전용 패널은 로그인 화면으로 보낸다.
  function decide(loggedIn) {
    signedIn = loggedIn;
    defaultPanel = loggedIn ? 'profile' : 'preferences';
    route(false);
  }
  headerReady.then(function (me) { decide(Boolean(me)); }, function () { decide(false); });

  /* ---------- 내 정보 관리 ---------- */

  var profileForm = document.getElementById('profile-form');
  var profileAlert = document.getElementById('profile-alert');
  var profileSubmit = document.getElementById('profile-submit');
  var nicknameInput = document.getElementById('profile-nickname');
  var phoneInput = document.getElementById('profile-phone');
  var currentPasswordInput = document.getElementById('profile-current-password');
  var currentProfile = null;

  function renderProfile(profile) {
    currentProfile = profile;
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
    if (!currentPasswordInput.value) {
      Ui.setFieldError(currentPasswordInput, '현재 비밀번호를 입력해 주세요.');
      ok = false;
    } else {
      Ui.setFieldError(currentPasswordInput, '');
    }
    if (!ok) return;

    saveProfile(nickname, phone, currentPasswordInput.value);
  });

  // PATCH /api/v1/members/me. 이름·이메일은 바꿀 수 없고, 서버가 현재 비밀번호와 입력값을 다시 검사한다.
  async function saveProfile(nickname, phone, currentPassword) {
    Ui.setLoading(profileSubmit, true, '저장 중...');
    try {
      var updated = await BookingApi.updateMe({
        nickname: nickname, phone: phone, currentPassword: currentPassword
      });
      renderProfile(updated);
      Ui.showAlert(profileAlert, '회원 정보가 저장되었습니다.', 'success');
    } catch (e) {
      Ui.showAlert(profileAlert, e.message, 'error');
    } finally {
      currentPasswordInput.value = '';
      Ui.setLoading(profileSubmit, false);
    }
  }

  /* ---------- 비밀번호 변경 ---------- */

  var passwordForm = document.getElementById('password-form');
  var passwordAlert = document.getElementById('password-alert');
  var passwordSubmit = document.getElementById('password-submit');
  var pwCurrent = document.getElementById('pw-current');
  var pwNew = document.getElementById('pw-new');
  var pwConfirm = document.getElementById('pw-confirm');

  passwordForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    Ui.hideAlert(passwordAlert);

    var ok = true;
    function check(input, valid, message) {
      Ui.setFieldError(input, valid ? '' : message);
      if (!valid) ok = false;
    }
    check(pwCurrent, pwCurrent.value.length > 0, '현재 비밀번호를 입력해 주세요.');
    check(pwNew, PASSWORD_RE.test(pwNew.value), '영문·숫자·특수문자를 포함해 8~20자로 입력해 주세요.');
    check(pwConfirm, pwConfirm.value === pwNew.value, '새 비밀번호가 일치하지 않습니다.');
    if (!ok || !currentProfile) return;

    Ui.setLoading(passwordSubmit, true, '변경 중...');
    try {
      // 서버는 닉네임·연락처도 같은 요청으로 받으므로 지금 값을 그대로 함께 보낸다.
      await BookingApi.updateMe({
        nickname: currentProfile.nickname || '',
        phone: currentProfile.phone || '',
        currentPassword: pwCurrent.value,
        newPassword: pwNew.value
      });
      // 비밀번호를 바꾸면 서버가 기존 토큰을 모두 폐기하므로 다시 로그인한다.
      await BookingApi.logout().catch(function () {});
      alert('비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요.');
      window.location.replace('/auth/login.html');
    } catch (e) {
      Ui.showAlert(passwordAlert, e.message, 'error');
      if (e.code === 'PASSWORD_MISMATCH') pwCurrent.focus();
      Ui.setLoading(passwordSubmit, false);
    } finally {
      pwCurrent.value = '';
    }
  });

  /* ---------- 내 활동 ---------- */

  // showPanel이 이 아래 코드보다 먼저 불릴 수 있어(#activity로 바로 들어온 경우) 요소는 함수 안에서 찾는다.
  var activitiesLoaded = false;

  function activityItem(href, title, meta) {
    var li = document.createElement('li');
    var a = document.createElement('a');
    a.href = href;
    var text = document.createElement('span');
    var strong = document.createElement('strong');
    strong.textContent = title;
    var small = document.createElement('small');
    small.className = 'muted';
    small.textContent = ' · ' + meta;
    text.append(strong, small);
    a.append(text);
    li.append(a);
    return li;
  }

  function fillList(listId, emptyId, countId, items) {
    var list = document.getElementById(listId);
    list.textContent = '';
    items.forEach(function (li) { list.append(li); });
    document.getElementById(emptyId).hidden = items.length > 0;
    document.getElementById(countId).textContent = '(' + items.length + ')';
  }

  /* 이용 내역: 승인 완료(COMPLETED)이고 체크아웃 날짜가 지난 내 예약 (후기 작성 조건과 같다) */

  // 오늘 날짜(한국 시간) YYYY-MM-DD
  function todayInKorea() {
    return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10);
  }

  // 서버가 준 사진 주소는 같은 사이트 경로(/...) 또는 https 만 쓴다
  function safeImageUrl(url) {
    var text = String(url || '');
    return /^\/(?!\/)/.test(text) || /^https:\/\//i.test(text) ? text : null;
  }

  function stayItem(stay) {
    var li = document.createElement('li');
    var a = document.createElement('a');
    a.className = 'stay-history-item';
    a.href = '/accommodation/detail.html?id=' + encodeURIComponent(stay.accommodationId);
    var thumb = document.createElement('div');
    thumb.className = 'stay-history-thumb';
    var placeholder = document.createElement('span');
    placeholder.textContent = '이미지 준비 중';
    thumb.append(placeholder);
    var text = document.createElement('div');
    text.className = 'stay-history-text';
    var name = document.createElement('strong');
    name.textContent = stay.accommodationName || '숙소';
    var meta = document.createElement('small');
    meta.className = 'muted';
    meta.textContent = stay.checkIn + ' ~ ' + stay.checkOut + (stay.roomName ? ' · ' + stay.roomName : '');
    text.append(name, meta);
    a.append(thumb, text);
    li.append(a);
    return { li: li, thumb: thumb };
  }

  // 숙소 상세의 첫 번째 사진을 대표 이미지로 쓴다. 사진이 없거나 볼 수 없는 숙소면 "이미지 준비 중"을 둔다.
  async function fillStayImage(accommodationId, thumbs) {
    try {
      var detail = await BookingApi.getAccommodation(accommodationId);
      var first = (detail.images || [])[0];
      var src = safeImageUrl(typeof first === 'string' ? first : first && first.url);
      if (!src) return;
      thumbs.forEach(function (thumb) {
        var img = document.createElement('img');
        img.src = src;
        img.alt = (detail.name || '숙소') + ' 대표 이미지';
        img.loading = 'lazy';
        thumb.textContent = '';
        thumb.append(img);
      });
    } catch (e) { /* 판매 중지된 숙소 등은 상세를 볼 수 없으므로 자리 표시를 그대로 둔다 */ }
  }

  async function loadStays() {
    var bookings = await BookingApi.getMyBookings();
    var today = todayInKorea();
    var stays = (bookings || []).filter(function (b) {
      return b.status === 'COMPLETED' && b.checkOut <= today;
    }).sort(function (x, y) { return x.checkOut < y.checkOut ? 1 : -1; }); // 최근에 이용한 순
    var thumbsByAccommodation = {};
    fillList('activity-stays', 'activity-stays-empty', 'activity-stay-count', stays.map(function (stay) {
      var item = stayItem(stay);
      (thumbsByAccommodation[stay.accommodationId] = thumbsByAccommodation[stay.accommodationId] || [])
        .push(item.thumb);
      return item.li;
    }));
    // 같은 숙소는 상세를 한 번만 불러온다
    Object.keys(thumbsByAccommodation).forEach(function (id) {
      fillStayImage(id, thumbsByAccommodation[id]);
    });
  }

  // GET /api/v1/members/me/activities. 제목·내용은 textContent로만 넣어 스크립트가 실행되지 않게 한다.
  async function loadActivities() {
    if (activitiesLoaded) return;
    var activityAlert = document.getElementById('activity-alert');
    Ui.hideAlert(activityAlert);
    try {
      var results = await Promise.all([BookingApi.getMyActivities(), loadStays()]);
      var data = results[0];
      activitiesLoaded = true;
      fillList('activity-reviews', 'activity-reviews-empty', 'activity-review-count',
        (data.reviews || []).map(function (r) {
          return activityItem('/accommodation/detail.html?id=' + encodeURIComponent(r.accommodationId),
            (r.accommodationName || '숙소') + ' ' + '★'.repeat(r.rating),
            r.createdAt + (r.content ? ' · ' + r.content.slice(0, 30) : ''));
        }));
      fillList('activity-qna', 'activity-qna-empty', 'activity-qna-count',
        (data.qna || []).filter(function (q) { return !q.deleted; }).map(function (q) {
          return activityItem('/qna/detail.html?id=' + encodeURIComponent(q.qnaId),
            q.title, String(q.createdAt || '').slice(0, 10) + ' · ' + (q.answered ? '답변 완료' : '답변 대기'));
        }));
    } catch (e) {
      Ui.showAlert(activityAlert, e.status === 401 ? '로그인 후 내 활동을 확인할 수 있습니다.' : e.message);
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
    if (!me) return; // 위에서 로그인 화면으로 보낸다
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
