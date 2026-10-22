/*
 * 1:1 문의 작성 화면 (QNA_002)
 * 클라이언트 검증은 사용 편의용이고, 실제 검증(HTML Escaping, 파일 시그니처 등)은 서버가 다시 한다.
 */
(function () {
  'use strict';

  var USE_MOCK_DATA = true;

  /*
   * 백엔드 연동 전 mock 데이터 (booking/my.html 과 같은 예약)
   *
   * 실제 API:
   * GET /api/v1/bookings/me
   */
  var MOCK_BOOKINGS = [
    { bookingId: 1001, bookingNumber: 'SXZLHLT79U565WUE', accommodationName: '스테이 강남', checkIn: '2026-11-18', checkOut: '2026-11-20' },
    { bookingId: 1002, bookingNumber: 'ZDACEAY966R36ZXQ', accommodationName: '한강 스테이', checkIn: '2026-09-10', checkOut: '2026-09-12' },
    { bookingId: 1003, bookingNumber: 'G6G4ST9Q4FYLW923', accommodationName: '서울 시티 호텔', checkIn: '2026-08-01', checkOut: '2026-08-02' }
  ];

  var TITLE_MIN = 2;
  var CONTENT_MIN = 10;
  var CONTENT_MAX = 2000;
  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp'];
  var ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];
  // shell.jsp.png 같은 이중 확장자 차단
  var DOUBLE_EXT_RE = /\.(jsp|jspx|php\d?|phtml|asp|aspx|exe|sh|bat|cmd|js|html?|svg)\./i;

  var form = document.getElementById('inquiry-form');
  var alertBox = document.getElementById('inquiry-alert');
  var submit = document.getElementById('inquiry-submit');
  var f = {
    category: document.getElementById('inquiry-category'),
    booking: document.getElementById('inquiry-booking'),
    title: document.getElementById('inquiry-title'),
    content: document.getElementById('inquiry-content'),
    count: document.getElementById('inquiry-count'),
    image: document.getElementById('inquiry-image'),
    imageText: document.getElementById('inquiry-image-text'),
    preview: document.getElementById('inquiry-preview'),
    previewImg: document.getElementById('inquiry-preview-img'),
    imageRemove: document.getElementById('inquiry-image-remove'),
    secret: document.getElementById('inquiry-secret')
  };
  var IMAGE_HINT = f.imageText.textContent;
  var previewUrl = null;

  var params =
  new URLSearchParams(
    window.location.search
  );

var editId =
  params.get('edit');

var editInquiry =
  null;


if (editId) {

  try {

    editInquiry =
      JSON.parse(
        sessionStorage.getItem(
          'staybookEditInquiry'
        )
      );

  } catch (e) {

    editInquiry = null;
  }

}

  Ui.renderHeader();

  /* ---------- 수정 모드 ---------- */

if (
  editId &&
  editInquiry &&
  String(editInquiry.qnaId) === String(editId)
) {

  document.querySelector('h1').textContent =
    '1:1 문의 수정';

  document.querySelector('.page-sub').textContent =
    '문의 내용을 수정한 뒤 저장해 주세요.';

  submit.textContent =
    '문의 수정';


  f.category.value =
    editInquiry.category || '';

  f.title.value =
    editInquiry.title || '';

  f.content.value =
    editInquiry.content || '';

  f.secret.checked =
    editInquiry.secret !== false;


  f.count.textContent =
    f.content.value.length +
    ' / ' +
    CONTENT_MAX;
}

  /* ---------- 관련 예약 ---------- */

  (USE_MOCK_DATA ? MOCK_BOOKINGS : []).forEach(function (b) {
    var option = document.createElement('option');
    option.value = String(b.bookingId);
    option.textContent = b.accommodationName + ' · ' + b.checkIn + ' ~ ' + b.checkOut
      + ' (' + Ui.formatBookingNumber(b.bookingNumber) + ')';
    f.booking.append(option);
  });

  /* ---------- 입력 검증 ---------- */

  function fail(input, message) { Ui.setFieldError(input, message); return false; }
  function pass(input) { Ui.setFieldError(input, ''); return true; }

  function validateCategory() {
    return f.category.value ? pass(f.category) : fail(f.category, '문의 유형을 선택해 주세요.');
  }

  function validateTitle() {
    var v = f.title.value.trim();
    if (!v) return fail(f.title, '제목을 입력해 주세요.');
    if (v.length < TITLE_MIN) return fail(f.title, '제목은 ' + TITLE_MIN + '자 이상 입력해 주세요.');
    return pass(f.title);
  }

  function validateContent() {
    var v = f.content.value.trim();
    if (!v) return fail(f.content, '문의 내용을 입력해 주세요.');
    if (v.length < CONTENT_MIN) return fail(f.content, '문의 내용은 ' + CONTENT_MIN + '자 이상 입력해 주세요.');
    return pass(f.content);
  }

  f.category.addEventListener('change', validateCategory);
  f.title.addEventListener('input', function () { Ui.setFieldError(f.title, ''); });
  f.content.addEventListener('input', function () {
    f.count.textContent = f.content.value.length + ' / ' + CONTENT_MAX;
    Ui.setFieldError(f.content, '');
  });

  /* ---------- 이미지 첨부 ---------- */

  // 파일 앞부분(매직 바이트)으로 실제 이미지인지 확인한다
  function readSignature(file) {
    return new Promise(function (resolve) {
      var reader = new FileReader();
      reader.onload = function () { resolve(new Uint8Array(reader.result)); };
      reader.onerror = function () { resolve(new Uint8Array(0)); };
      reader.readAsArrayBuffer(file.slice(0, 12));
    });
  }

  function isImageSignature(b) {
    var jpeg = b[0] === 0xFF && b[1] === 0xD8 && b[2] === 0xFF;
    var png = b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47;
    var webp = b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46
      && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50;
    return jpeg || png || webp;
  }

  async function checkImage(file) {
    var name = file.name;
    var ext = name.lastIndexOf('.') > 0 ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '';
    if (ALLOWED_EXT.indexOf(ext) === -1 || ALLOWED_MIME.indexOf(file.type) === -1) {
      return 'jpg, png, webp 이미지만 첨부할 수 있습니다.';
    }
    if (DOUBLE_EXT_RE.test(name) || name.indexOf('\0') !== -1) return '파일 이름을 확인해 주세요.';
    if (file.size > MAX_FILE_SIZE) return '5MB 이하의 이미지만 첨부할 수 있습니다.';
    if (!isImageSignature(await readSignature(file))) return '올바른 이미지 파일이 아닙니다.';
    return '';
  }

  function clearImage() {
    f.image.value = '';
    f.imageText.textContent = IMAGE_HINT;
    f.preview.hidden = true;
    f.previewImg.removeAttribute('src');
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = null;
  }

  f.image.addEventListener('change', async function () {
    var file = f.image.files[0];
    Ui.setFieldError(f.image, '');
    if (!file) { clearImage(); return; }

    var error = await checkImage(file);
    if (error) {
      clearImage();
      Ui.setFieldError(f.image, error);
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    previewUrl = URL.createObjectURL(file);
    f.previewImg.src = previewUrl;
    f.imageText.textContent = file.name;
    f.preview.hidden = false;
  });

  f.imageRemove.addEventListener('click', function () {
    clearImage();
    Ui.setFieldError(f.image, '');
  });

  /* ---------- 등록 ---------- */

  // 연동 전: 등록한 글을 이 탭의 sessionStorage에 두어 고객센터의 최근 내 문의에 보이게 한다
  function saveMockInquiry(inquiry) {
    try {
      var stored = JSON.parse(sessionStorage.getItem('staybookMockInquiries'));
      var list = Array.isArray(stored) ? stored : [];
      list.unshift(inquiry);
      sessionStorage.setItem('staybookMockInquiries', JSON.stringify(list.slice(0, 10)));
    } catch (e) { /* 저장소를 못 쓰면 목록에만 안 보임 */ }
  }

  function today() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    Ui.hideAlert(alertBox);

    var results = [validateCategory(), validateTitle(), validateContent()];
    if (results.indexOf(false) !== -1 || f.image.getAttribute('aria-invalid') === 'true') {
      var firstInvalid = form.querySelector('[aria-invalid="true"]');
      if (firstInvalid) firstInvalid.focus();
      Ui.showAlert(alertBox, '입력하신 내용을 다시 확인해 주세요.', 'error');
      return;
    }

    /*
     * 실제 API: POST /api/v1/qna
     * { category, bookingId, title, content, secret } + 이미지는 POST /api/v1/uploads/Images
     */
if (editId && editInquiry) {

  Ui.setLoading(
    submit,
    true,
    '수정 중...'
  );

  editInquiry.category =
    f.category.value;

  editInquiry.title =
    f.title.value.trim();

  editInquiry.content =
    f.content.value.trim();

  editInquiry.secret =
    f.secret.checked;

  /*
   * 실제 API 연동 시:
   * Q&A 수정 API 호출
   */

  sessionStorage.setItem(
    'staybookEditInquiry',
    JSON.stringify(editInquiry)
  );

  window.location.replace(
    '/qna.html?inquiry=updated'
  );

} else {

  Ui.setLoading(
    submit,
    true,
    '등록 중...'
  );

  saveMockInquiry({
    qnaId: Date.now(),
    category: f.category.value,
    title: f.title.value.trim(),
    content: f.content.value.trim(),
    secret: f.secret.checked,
    answered: false,
    createdAt: today(),
    owned: true
  });

  window.location.replace(
    '/qna.html?inquiry=done'
  );
}
  });
})();
