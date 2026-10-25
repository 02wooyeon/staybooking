/* 내 예약: 서버 조회와 사용자 취소 신청 */
(function () {
  'use strict';

  var list = document.getElementById('my-booking-list');
  var alertBox = document.getElementById('my-booking-alert');
  var tabs = document.querySelectorAll('.my-booking-tab');
  var bookings = [];
  var demoPaymentEnabled = false;
  var tossPaymentEnabled = false;
  var selected = 'ALL';
  var statusLabels = {
    PAYMENT_PENDING: '결제 대기',
    PENDING: '승인 대기',
    COMPLETED: '예약 완료',
    CANCELLED: '예약 취소'
  };
  var statusClasses = {
    PAYMENT_PENDING: 'pending',
    PENDING: 'pending',
    COMPLETED: 'accepted',
    CANCELLED: 'cancelled'
  };

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      selected = tab.dataset.status;
      tabs.forEach(function (item) { item.classList.toggle('active', item === tab); });
      render();
    });
  });

  function info(label, value) {
    var box = document.createElement('div');
    var heading = document.createElement('span');
    var text = document.createElement('strong');
    heading.textContent = label;
    text.textContent = value;
    box.append(heading, text);
    return box;
  }

  function matches(booking) {
    if (selected === 'ALL') return true;
    if (selected === 'PENDING') {
      return booking.status === 'PAYMENT_PENDING' || booking.status === 'PENDING';
    }
    return booking.status === selected;
  }

  function paymentExpired(booking) {
    return booking.paymentExpiresAt && Date.now() >= Date.parse(booking.paymentExpiresAt + 'Z');
  }

  function render() {
    list.textContent = '';
    var visible = bookings.filter(matches);
    if (!visible.length) {
      var empty = document.createElement('p');
      empty.className = 'my-booking-empty';
      empty.textContent = '해당하는 예약 내역이 없습니다.';
      list.append(empty);
      return;
    }
    visible.forEach(function (booking) {
      var card = document.createElement('article');
      card.className = 'my-booking-card';
      var image = document.createElement('div');
      image.className = 'my-booking-image image-placeholder';
      image.textContent = '숙소 이미지 준비 중';

      var body = document.createElement('div');
      body.className = 'my-booking-info';
      var status = document.createElement('span');
      status.className = 'my-booking-status ' + (statusClasses[booking.status] || '');
      status.textContent = booking.status === 'PAYMENT_PENDING' && paymentExpired(booking)
        ? '결제 기한 만료' : (statusLabels[booking.status] || booking.status);
      var title = document.createElement('h2');
      title.textContent = booking.accommodationName;
      var room = document.createElement('p');
      room.className = 'my-booking-room-name';
      room.textContent = booking.roomName;
      var details = document.createElement('div');
      details.className = 'my-booking-detail-grid';
      details.append(info('체크인', booking.checkIn), info('체크아웃', booking.checkOut),
        info('인원', booking.guests + '명'), info('예약 ID', String(booking.bookingId)));
      if (booking.status === 'PAYMENT_PENDING' && booking.paymentExpiresAt) {
        details.append(info('결제 기한', new Date(booking.paymentExpiresAt + 'Z').toLocaleString('ko-KR')));
      }
      body.append(status, title, room, details);

      var actions = document.createElement('div');
      actions.className = 'my-booking-actions';
      var price = document.createElement('div');
      price.className = 'my-booking-price';
      price.append(info('예약 금액', Number(booking.totalAmount).toLocaleString('ko-KR') + '원'));
      var stay = document.createElement('button');
      stay.type = 'button';
      stay.className = 'my-booking-detail-button';
      stay.textContent = '숙소 상세';
      stay.addEventListener('click', function () {
        window.location.href = '/accommodation/detail.html?id=' + encodeURIComponent(booking.accommodationId);
      });
      actions.append(price, stay);

      var cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'my-booking-cancel-button';
      if (booking.status === 'PAYMENT_PENDING' && tossPaymentEnabled && !paymentExpired(booking)) {
        cancel.textContent = '결제하기';
        cancel.addEventListener('click', function () {
          window.location.href = '/payment/checkout.html?bookingId=' + encodeURIComponent(booking.bookingId);
        });
      } else if (booking.status === 'PAYMENT_PENDING' && demoPaymentEnabled && !paymentExpired(booking)) {
        cancel.textContent = '테스트 결제 (실제 청구 없음)';
        cancel.addEventListener('click', function () { completeDemoPayment(booking, cancel); });
      } else if (booking.status === 'PENDING' || booking.status === 'COMPLETED') {
        cancel.textContent = booking.cancellationRequested ? '취소 신청 접수' : '취소 신청';
        cancel.disabled = !!booking.cancellationRequested;
        if (!booking.cancellationRequested) {
          cancel.addEventListener('click', function () { requestCancellation(booking, cancel); });
        }
      } else {
        cancel.textContent = booking.status === 'PAYMENT_PENDING'
          ? (paymentExpired(booking) ? '결제 기한 만료' : '결제 서비스 준비 중') : '취소 완료';
        cancel.disabled = true;
      }
      actions.append(cancel);
      card.append(image, body, actions);
      list.append(card);
    });
  }

  async function completeDemoPayment(booking, button) {
    if (!window.confirm('개발용 테스트 결제입니다. 실제 결제나 청구는 발생하지 않습니다. 진행할까요?')) return;
    button.disabled = true;
    try {
      await BookingApi.completeDemoPayment(booking.bookingId);
      booking.status = 'PENDING';
      render();
      Ui.showAlert(alertBox, '테스트 결제가 완료되어 예약이 승인 대기 상태로 변경되었습니다.', 'success');
    } catch (e) {
      button.disabled = false;
      Ui.showAlert(alertBox, e.message, 'error');
    }
  }

  async function requestCancellation(booking, button) {
    var choice = window.prompt('취소 사유를 선택해 주세요. 1: 개인 사유, 2: 숙소 사유, 3: 불가항력', '1');
    if (choice === null) return;
    var types = { '1': 'USER_FAULT', '2': 'ACCOMMODATION_FAULT', '3': 'FORCE_MAJEURE' };
    if (!types[choice]) {
      Ui.showAlert(alertBox, '취소 사유 유형을 1, 2, 3 중에서 선택해 주세요.', 'error');
      return;
    }
    var detail = window.prompt('취소 신청 사유를 입력해 주세요. 승인자 또는 관리자가 검토합니다.');
    if (detail === null) return;
    detail = detail.trim();
    if (!detail || detail.length > 255) {
      Ui.showAlert(alertBox, '상세 사유를 1~255자로 입력해 주세요.', 'error');
      return;
    }
    button.disabled = true;
    try {
      await BookingApi.requestBookingCancellation(booking.bookingId, {
        reasonType: types[choice], reasonDetail: detail
      });
      button.textContent = '취소 신청 접수';
      Ui.showAlert(alertBox, '취소 신청을 접수했습니다. 승인자 또는 관리자의 결정을 기다려 주세요.', 'success');
    } catch (e) {
      button.disabled = false;
      Ui.showAlert(alertBox, e.message, 'error');
    }
  }

  async function load() {
    var me = await Ui.renderHeader();
    if (!me) {
      window.location.replace('/auth/login.html?next=' + encodeURIComponent('/booking/my.html'));
      return;
    }
    try {
      var results = await Promise.all([BookingApi.getMyBookings(), BookingApi.getPaymentCapabilities()]);
      bookings = results[0];
      demoPaymentEnabled = results[1].demoEnabled === true;
      tossPaymentEnabled = results[1].tossEnabled === true;
      render();
      if (new URLSearchParams(window.location.search).has('created')) {
        Ui.showAlert(alertBox, '예약이 접수되었습니다. 결제 후 승인 대기 상태로 넘어갑니다.', 'success');
        window.history.replaceState(null, '', window.location.pathname);
      }
    } catch (e) {
      Ui.showAlert(alertBox, e.message, 'error');
    }
  }

  load();
})();
