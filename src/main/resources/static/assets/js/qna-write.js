/* 1:1 문의 작성·수정: 서버 저장, 관련 예약은 로그인한 사용자의 예약만 표시.
 * 사진은 POST /api/v1/uploads/Images 로 먼저 올린 뒤 받은 파일 ID를 문의에 연결한다. */
(function () {
  'use strict';

  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp'];
  var ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];
  var IMAGE_URL_RE = /^\/api\/v1\/uploads\/Images\/(\d{1,19})$/;

  var form = document.getElementById('inquiry-form');
  var alertBox = document.getElementById('inquiry-alert');
  var submit = document.getElementById('inquiry-submit');
  var fields = {
    category: document.getElementById('inquiry-category'),
    booking: document.getElementById('inquiry-booking'),
    title: document.getElementById('inquiry-title'),
    content: document.getElementById('inquiry-content'),
    count: document.getElementById('inquiry-count'),
    secret: document.getElementById('inquiry-secret'),
    image: document.getElementById('inquiry-image')
  };
  var imageText = document.getElementById('inquiry-image-text');
  var preview = document.getElementById('inquiry-image-preview');
  var previewImg = document.getElementById('inquiry-image-preview-img');
  var IMAGE_HINT = imageText.textContent;
  var newImageFile = null;
  var existingImageId = null;
  var editId = new URLSearchParams(window.location.search).get('edit');
  var editable = !editId;

  function error(input, message) {
    Ui.setFieldError(input, message);
    return false;
  }

  function validate() {
    var categoryOk = fields.category.value || error(fields.category, '문의 유형을 선택해 주세요.');
    if (categoryOk) Ui.setFieldError(fields.category, '');
    var title = fields.title.value.trim();
    var titleOk = title.length >= 2 && title.length <= 100;
    Ui.setFieldError(fields.title, titleOk ? '' : '제목은 2~100자로 입력해 주세요.');
    var content = fields.content.value.trim();
    var contentOk = content.length >= 10 && content.length <= 2000;
    Ui.setFieldError(fields.content, contentOk ? '' : '내용은 10~2000자로 입력해 주세요.');
    return Boolean(categoryOk && titleOk && contentOk);
  }

  fields.category.addEventListener('change', function () { Ui.setFieldError(fields.category, ''); });
  fields.title.addEventListener('input', function () { Ui.setFieldError(fields.title, ''); });
  fields.content.addEventListener('input', function () {
    fields.count.textContent = fields.content.value.length + ' / 2000';
    Ui.setFieldError(fields.content, '');
  });

  /* 사진 첨부 (후기 화면과 같은 규칙. 서버가 다시 검사한다) */

  function showPreview(src, label) {
    previewImg.src = src;
    imageText.textContent = label;
    preview.hidden = false;
  }

  function clearImage() {
    newImageFile = null;
    existingImageId = null;
    fields.image.value = '';
    imageText.textContent = IMAGE_HINT;
    preview.hidden = true;
    previewImg.removeAttribute('src');
  }

  function isImageSignature(b) {
    var jpeg = b[0] === 0xFF && b[1] === 0xD8 && b[2] === 0xFF;
    var png = b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47;
    var webp = b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46
      && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50;
    return jpeg || png || webp;
  }

  function readAs(file, method) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(reader.result); };
      reader.onerror = reject;
      reader[method](file);
    });
  }

  fields.image.addEventListener('change', async function () {
    var file = fields.image.files[0];
    Ui.setFieldError(fields.image, '');
    if (!file) return;

    var name = file.name;
    var ext = name.lastIndexOf('.') > 0 ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '';
    var message = '';
    if (ALLOWED_EXT.indexOf(ext) === -1 || ALLOWED_MIME.indexOf(file.type) === -1) {
      message = 'jpg, png, webp 사진만 첨부할 수 있습니다.';
    } else if (name.split('.').length > 2 || name.indexOf('\0') !== -1) {
      message = '파일 이름을 확인해 주세요.'; // shell.jsp.png 같은 이중 확장자 차단
    } else if (file.size > MAX_FILE_SIZE) {
      message = '5MB 이하의 사진만 첨부할 수 있습니다.';
    } else {
      try {
        var head = new Uint8Array(await readAs(file.slice(0, 12), 'readAsArrayBuffer'));
        if (!isImageSignature(head)) message = '올바른 사진 파일이 아닙니다.';
      } catch (e) {
        message = '사진을 읽을 수 없습니다.';
      }
    }
    if (message) {
      clearImage();
      Ui.setFieldError(fields.image, message);
      return;
    }
    try {
      showPreview(await readAs(file, 'readAsDataURL'), name);
      newImageFile = file;
      existingImageId = null;
    } catch (e) {
      clearImage();
      Ui.setFieldError(fields.image, '사진을 읽을 수 없습니다.');
    }
  });

  document.getElementById('inquiry-image-remove').addEventListener('click', function () {
    clearImage();
    Ui.setFieldError(fields.image, '');
  });

  async function load() {
    var me = await Ui.renderHeader();
    if (!me) {
      window.location.replace('/auth/login.html?next=' + encodeURIComponent(
        window.location.pathname + window.location.search));
      return;
    }
    try {
      var bookings = await BookingApi.getMyBookings();
      bookings.forEach(function (booking) {
        var option = document.createElement('option');
        option.value = String(booking.bookingId);
        option.textContent = booking.accommodationName + ' · ' + booking.checkIn + ' ~ ' + booking.checkOut;
        fields.booking.append(option);
      });
    } catch (e) {
      Ui.showAlert(alertBox, '관련 예약 목록을 불러오지 못했습니다. 예약 선택 없이 문의할 수 있습니다.', 'error');
    }

    if (!editId) return;
    if (!/^\d+$/.test(editId)) {
      Ui.showAlert(alertBox, '수정할 문의를 찾을 수 없습니다.', 'error');
      submit.disabled = true;
      return;
    }
    try {
      var q = await BookingApi.getQna(editId);
      if (q.userId !== me.userId || q.answered) {
        throw new Error('답변 전의 내 문의만 수정할 수 있습니다.');
      }
      fields.category.value = q.category;
      fields.booking.value = q.bookingId == null ? '' : String(q.bookingId);
      fields.title.value = q.title;
      fields.content.value = q.content;
      fields.secret.checked = q.secret;
      fields.count.textContent = q.content.length + ' / 2000';
      var match = q.image && IMAGE_URL_RE.exec(q.image);
      if (match) {
        existingImageId = Number(match[1]);
        showPreview(q.image, '등록된 사진');
      }
      document.querySelector('h1').textContent = '1:1 문의 수정';
      submit.textContent = '문의 수정';
      editable = true;
    } catch (e) {
      Ui.showAlert(alertBox, e.message, 'error');
      submit.disabled = true;
    }
  }

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    Ui.hideAlert(alertBox);
    if (!editable || !validate()) {
      Ui.showAlert(alertBox, '입력하신 내용을 다시 확인해 주세요.', 'error');
      return;
    }
    var body = {
      category: fields.category.value,
      bookingId: fields.booking.value ? Number(fields.booking.value) : null,
      title: fields.title.value.trim(),
      content: fields.content.value.trim(),
      secret: fields.secret.checked,
      imageFileId: existingImageId
    };
    Ui.setLoading(submit, true, editId ? '수정 중...' : '등록 중...');
    try {
      if (newImageFile) {
        var uploaded = await BookingApi.uploadImage(newImageFile);
        body.imageFileId = uploaded.fileId;
        // 다시 제출할 때 같은 사진을 또 올리지 않도록 올린 파일 ID를 기억한다
        newImageFile = null;
        existingImageId = uploaded.fileId;
      }
      if (editId) await BookingApi.updateQna(editId, body);
      else await BookingApi.createQna(body);
      window.location.replace('/qna.html?inquiry=' + (editId ? 'updated' : 'done'));
    } catch (e) {
      Ui.showAlert(alertBox, e.message, 'error');
      Ui.setLoading(submit, false);
    }
  });

  load();
})();
