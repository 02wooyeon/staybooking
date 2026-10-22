/* 1:1 문의 작성·수정: 서버 저장, 관련 예약은 로그인한 사용자의 예약만 표시 */
(function () {
  'use strict';

  var form = document.getElementById('inquiry-form');
  var alertBox = document.getElementById('inquiry-alert');
  var submit = document.getElementById('inquiry-submit');
  var fields = {
    category: document.getElementById('inquiry-category'),
    booking: document.getElementById('inquiry-booking'),
    title: document.getElementById('inquiry-title'),
    content: document.getElementById('inquiry-content'),
    count: document.getElementById('inquiry-count'),
    secret: document.getElementById('inquiry-secret')
  };
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
      secret: fields.secret.checked
    };
    Ui.setLoading(submit, true, editId ? '수정 중...' : '등록 중...');
    try {
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
