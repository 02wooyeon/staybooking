/* 숙소 상세 화면: /accommodation/detail.html?id={accommodationId} */
(function () {
  'use strict';

  var loading = document.getElementById('detail-loading');
  var alertBox = document.getElementById('detail-alert');
  var detail = document.getElementById('accommodation-detail');
  var params = new URLSearchParams(window.location.search);
  var accommodationId = Number(params.get('id'));

  Ui.renderHeader();

  if (!Number.isSafeInteger(accommodationId) || accommodationId < 1) {
    showFailure('조회할 숙소 정보가 올바르지 않습니다.');
    return;
  }

  loadDetail();

  async function loadDetail() {
    try {
      var response = await fetch('/api/v1/accommodations/' + encodeURIComponent(accommodationId), {
        headers: { Accept: 'application/json' },
        cache: 'no-store'
      });
      var data = await response.json().catch(function () { return null; });
      if (!response.ok) {
        throw new Error((data && data.message) || '숙소 정보를 불러오지 못했습니다.');
      }
      renderDetail(data);
      loading.hidden = true;
      detail.hidden = false;
    } catch (e) {
      showFailure(e.message || '숙소 정보를 불러오지 못했습니다.');
    }
  }

  function showFailure(message) {
    loading.hidden = true;
    Ui.showAlert(alertBox, message);
  }

  function renderDetail(data) {
    text('detail-status', data.status === 'ACTIVE' ? '예약 가능' : '운영 중지');
    text('detail-name', data.name);
    text('detail-address', data.address);
    text('detail-check-in', formatTime(data.checkInTime));
    text('detail-check-out', formatTime(data.checkOutTime));
    text('detail-description', data.description || '등록된 숙소 소개가 없습니다.');
    renderList('detail-amenities', data.amenities, '등록된 편의시설 정보가 없습니다.');
    renderList('detail-rules', data.rules, '등록된 이용 규칙이 없습니다.');
    renderGallery(data.images, data.name);
    renderRooms(data.rooms || []);
  }

  function text(id, value) {
    document.getElementById(id).textContent = value || '';
  }

<<<<<<< Updated upstream
  function renderList(id, values, emptyText) {
    var list = document.getElementById(id);
    list.textContent = '';
    (values && values.length ? values : [emptyText]).forEach(function (value) {
      var item = document.createElement('li');
      item.textContent = value;
      list.append(item);
    });
=======

  function renderRooms(
    rooms
  ) {

    roomList.textContent =
      '';

    roomCount.textContent =
      rooms.length +
      '개 객실';


    rooms.forEach(
      function (room) {

        var card =
          document.createElement(
            'article'
          );

        card.className =
          'hotel-room-card';


        /*
         * 왼쪽 객실 정보
         */

        var left =
          document.createElement(
            'div'
          );

        left.className =
          'hotel-room-left';


        var imageBox =
          document.createElement(
            'div'
          );

        imageBox.className =
          'hotel-room-image';


        if (room.image) {

          var roomImage =
            document.createElement(
              'img'
            );

          roomImage.src =
            room.image;

          roomImage.alt =
            room.name +
            ' 객실 사진';

          imageBox.append(
            roomImage
          );

        } else {

          imageBox.append(
            createPlaceholder(
              '객실 이미지'
            )
          );
        }


        var roomInfo =
          document.createElement(
            'div'
          );

        roomInfo.className =
          'hotel-room-info';


        var roomName =
          document.createElement(
            'h3'
          );

        roomName.textContent =
          room.name;


        var capacity =
          document.createElement(
            'p'
          );

        capacity.textContent =
          '최대 ' +
          room.maxGuests +
          '명';


        roomInfo.append(
          roomName,
          capacity
        );


        left.append(
          imageBox,
          roomInfo
        );


        /*
         * 가운데 예약 조건
         */

        var center =
          document.createElement(
            'div'
          );

        center.className =
          'hotel-room-center';


        var conditionTitle =
          document.createElement(
            'strong'
          );

        conditionTitle.textContent =
          '객실 예약 정보';


        var available =
          room.status !==
            'SOLD_OUT' &&
          room.availableCount !==
            0;


        var availability =
          document.createElement(
            'p'
          );


        if (available) {

          availability.textContent =
            '✓ 예약 가능 · 남은 객실 ' +
            room.availableCount +
            '개';

          availability.className =
            'hotel-room-available';

        } else {

          availability.textContent =
            '예약 마감';

          availability.className =
            'hotel-room-soldout';
        }


        var people =
          document.createElement(
            'p'
          );

        people.textContent =
          '성인 최대 ' +
          room.maxGuests +
          '명';


        center.append(
          conditionTitle,
          availability,
          people
        );


        /*
         * 오른쪽 가격/버튼
         */

        var right =
          document.createElement(
            'div'
          );

        right.className =
          'hotel-room-right';


        var priceLabel =
          document.createElement(
            'span'
          );

        priceLabel.textContent =
          '1박 기준';


        var price =
          document.createElement(
            'strong'
          );

        price.className =
          'hotel-room-price';

        price.textContent =
          formatPrice(
            room.price
          ) +
          '원';


        var bookingButton =
          document.createElement(
            'a'
          );

        bookingButton.className =
          'hotel-booking-button';


        if (available) {

          bookingButton.textContent =
            '예약하기';

          bookingButton.href =
            '/booking/quote.html' +
            '?accommodationId=' +
            encodeURIComponent(
              accommodationId
            ) +
            '&roomId=' +
            encodeURIComponent(
              room.roomId
            );

        } else {

          bookingButton.textContent =
            '예약 마감';

          bookingButton.classList.add(
            'disabled'
          );
        }


        right.append(
          priceLabel,
          price,
          bookingButton
        );


        card.append(
          left,
          center,
          right
        );


        roomList.append(
          card
        );
      }
    );
>>>>>>> Stashed changes
  }

  function renderGallery(images, name) {
    var main = document.getElementById('detail-main-image');
    var side = document.getElementById('detail-sub-images');
    main.textContent = '';
    side.textContent = '';
    if (!images || !images.length) {
      main.textContent = name + ' 이미지 준비 중';
      return;
    }
    main.append(image(images[0], name + ' 대표 이미지'));
    images.slice(1, 5).forEach(function (src) {
      var box = document.createElement('div');
      box.className = 'hotel-gallery-sub';
      box.append(image(src, name + ' 숙소 이미지'));
      side.append(box);
    });
  }

  function image(src, alt) {
    var element = document.createElement('img');
    element.src = src;
    element.alt = alt;
    return element;
  }

  function renderRooms(rooms) {
    var list = document.getElementById('room-list');
    text('room-count', rooms.length + '개 객실 유형');
    list.textContent = '';
    if (!rooms.length) {
      list.textContent = '등록된 객실이 없습니다.';
      return;
    }
    rooms.forEach(function (room) {
      var card = document.createElement('article');
      card.className = 'hotel-room-card';

      var left = document.createElement('div');
      left.className = 'hotel-room-left';
      var imageBox = document.createElement('div');
      imageBox.className = 'hotel-room-image';
      if (room.image) imageBox.append(image(room.image, room.name + ' 객실 이미지'));
      else imageBox.textContent = '객실 이미지 준비 중';
      var info = document.createElement('div');
      info.className = 'hotel-room-info';
      var name = document.createElement('h3');
      name.textContent = room.name;
      var description = document.createElement('p');
      description.textContent = room.description || '객실 설명이 등록되지 않았습니다.';
      info.append(name, description);
      left.append(imageBox, info);

      var center = document.createElement('div');
      center.className = 'hotel-room-center';
      var capacity = document.createElement('strong');
      capacity.textContent = '최대 ' + room.maxGuests + '명';
      var availability = document.createElement('p');
      var available = room.status === 'AVAILABLE' && room.availableCount > 0;
      availability.className = available ? 'hotel-room-available' : 'hotel-room-soldout';
      availability.textContent = available ? '예약 가능 ' + room.availableCount + '개' : '예약 마감';
      center.append(capacity, availability);

      var right = document.createElement('div');
      right.className = 'hotel-room-right';
      var label = document.createElement('span');
      label.textContent = '1박 기준';
      var price = document.createElement('strong');
      price.className = 'hotel-room-price';
      price.textContent = Number(room.price || 0).toLocaleString('ko-KR') + '원';
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'hotel-booking-button' + (available ? '' : ' disabled');
      button.textContent = available ? '예약 화면 준비 중' : '예약 마감';
      button.disabled = true;
      right.append(label, price, button);

      card.append(left, center, right);
      list.append(card);
    });
  }

  function formatTime(value) {
    return value ? String(value).slice(0, 5) : '-';
  }
})();
