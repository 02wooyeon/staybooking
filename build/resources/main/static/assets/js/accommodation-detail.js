/*
 * 숙소 상세 화면
 *
 * ACCOM_003
 * GET /api/v1/accommodations/{accommodationId}
 */

(function () {
  'use strict';


  var USE_MOCK_DATA = true;


  var MOCK_ACCOMMODATIONS = {

    1: {

      accommodationId: 1,

      name:
        '스테이 강남',

      address:
        '서울특별시 강남구',

      description:
        '강남 중심에 위치해 대중교통과 주변 상권을 이용하기 좋은 숙소입니다. 편안한 객실과 필요한 편의시설을 제공하며 서울 여행과 비즈니스 이용 모두에 적합합니다.',

      checkInTime:
        '15:00',

      checkOutTime:
        '11:00',

      status:
        'ACTIVE',

      images: [
        null,
        null,
        null,
        null
      ],

      amenities: [
        '무료 Wi-Fi',
        '에어컨',
        '주차 가능',
        '금연 객실',
        '짐 보관',
        '엘리베이터'
      ],

      rules: [
        '객실 내 흡연은 금지되어 있습니다.',
        '반려동물 동반은 불가능합니다.',
        '체크인 가능 시간 이후 입실해 주세요.'
      ],

      rooms: [

        {
          roomId: 101,

          name:
            '스탠다드 더블',

          image:
            null,

          price:
            90000,

          maxGuests:
            2,

          availableCount:
            3,

          status:
            'AVAILABLE'
        },

        {
          roomId: 102,

          name:
            '디럭스 트윈',

          image:
            null,

          price:
            130000,

          maxGuests:
            3,

          availableCount:
            1,

          status:
            'AVAILABLE'
        }

      ]
    },


    2: {

      accommodationId: 2,

      name:
        '서울 시티 호텔',

      address:
        '서울특별시 중구',

      description:
        '서울 도심의 주요 관광지와 대중교통 이용이 편리한 호텔입니다.',

      checkInTime:
        '15:00',

      checkOutTime:
        '11:00',

      status:
        'ACTIVE',

      images: [
        null,
        null,
        null,
        null
      ],

      amenities: [
        '무료 Wi-Fi',
        '조식',
        '엘리베이터',
        '24시간 프런트'
      ],

      rules: [
        '객실 내 흡연은 금지되어 있습니다.',
        '객실 최대 인원을 초과할 수 없습니다.'
      ],

      rooms: [

        {
          roomId: 201,

          name:
            '스탠다드 싱글',

          image:
            null,

          price:
            75000,

          maxGuests:
            1,

          availableCount:
            2,

          status:
            'AVAILABLE'
        },

        {
          roomId: 202,

          name:
            '프리미엄 더블',

          image:
            null,

          price:
            145000,

          maxGuests:
            2,

          availableCount:
            0,

          status:
            'SOLD_OUT'
        }

      ]
    },


    3: {

      accommodationId: 3,

      name:
        '한강 스테이',

      address:
        '서울특별시 마포구',

      description:
        '한강과 가까워 산책과 서울 여행을 함께 즐길 수 있는 숙소입니다.',

      checkInTime:
        '16:00',

      checkOutTime:
        '11:00',

      status:
        'ACTIVE',

      images: [
        null,
        null,
        null,
        null
      ],

      amenities: [
        '무료 Wi-Fi',
        '한강 전망',
        '에어컨',
        '짐 보관'
      ],

      rules: [
        '밤 10시 이후에는 소음을 줄여 주세요.',
        '객실 내 파티는 제한됩니다.'
      ],

      rooms: [

        {
          roomId: 301,

          name:
            '리버뷰 더블',

          image:
            null,

          price:
            150000,

          maxGuests:
            2,

          availableCount:
            2,

          status:
            'AVAILABLE'
        },

        {
          roomId: 302,

          name:
            '리버뷰 패밀리',

          image:
            null,

          price:
            220000,

          maxGuests:
            4,

          availableCount:
            1,

          status:
            'AVAILABLE'
        }

      ]
    }

  };


  var alertBox =
    document.getElementById(
      'detail-alert'
    );

  var loading =
    document.getElementById(
      'detail-loading'
    );

  var detail =
    document.getElementById(
      'accommodation-detail'
    );

  var nameEl =
    document.getElementById(
      'detail-name'
    );

  var addressEl =
    document.getElementById(
      'detail-address'
    );

  var descriptionEl =
    document.getElementById(
      'detail-description'
    );

  var checkInEl =
    document.getElementById(
      'detail-check-in'
    );

  var checkOutEl =
    document.getElementById(
      'detail-check-out'
    );

  var statusEl =
    document.getElementById(
      'detail-status'
    );

  var amenityList =
    document.getElementById(
      'detail-amenities'
    );

  var ruleList =
    document.getElementById(
      'detail-rules'
    );

  var roomList =
    document.getElementById(
      'room-list'
    );

  var roomCount =
    document.getElementById(
      'room-count'
    );

  var mainImageBox =
    document.getElementById(
      'detail-main-image'
    );

  var subImageBox =
    document.getElementById(
      'detail-sub-images'
    );


  Ui.renderHeader();


  var params =
    new URLSearchParams(
      window.location.search
    );

  var accommodationId =
    params.get('id');


  if (
    !isValidId(
      accommodationId
    )
  ) {

    showError(
      '잘못된 숙소 정보입니다.'
    );

    return;
  }


  loadAccommodation(
    Number(
      accommodationId
    )
  );


  async function loadAccommodation(
    id
  ) {

    try {

      var data;


      if (USE_MOCK_DATA) {

        data =
          MOCK_ACCOMMODATIONS[
            id
          ];

        if (!data) {

          showError(
            '존재하지 않는 숙소입니다.'
          );

          return;
        }

      } else {

        var response =
          await fetch(
            '/api/v1/accommodations/' +
            encodeURIComponent(
              id
            ),
            {
              method: 'GET',

              headers: {
                Accept:
                  'application/json'
              },

              credentials:
                'same-origin',

              cache:
                'no-store'
            }
          );


        if (
          response.status ===
          404
        ) {

          showError(
            '존재하지 않는 숙소입니다.'
          );

          return;
        }


        if (!response.ok) {

          throw new Error(
            '숙소 상세 조회 실패'
          );
        }


        data =
          await response.json();


        if (
          data &&
          data.data &&
          !data.accommodationId
        ) {

          data =
            data.data;
        }
      }


      renderAccommodation(
        data
      );


    } catch (error) {

      console.error(
        error
      );

      showError(
        '숙소 정보를 불러오는 중 오류가 발생했습니다.'
      );
    }
  }


  function renderAccommodation(
    accommodation
  ) {

    loading.hidden =
      true;

    detail.hidden =
      false;


    renderGallery(
      accommodation.images ||
      []
    );


    nameEl.textContent =
      accommodation.name ||
      '숙소 이름 없음';


    addressEl.textContent =
      accommodation.address ||
      '주소 정보 없음';


    descriptionEl.textContent =
      accommodation.description ||
      '숙소 설명이 없습니다.';


    checkInEl.textContent =
      formatTime(
        accommodation
          .checkInTime
      );


    checkOutEl.textContent =
      formatTime(
        accommodation
          .checkOutTime
      );


    var active =
      accommodation.status ===
      'ACTIVE';


    statusEl.textContent =
      active
        ? '예약 가능'
        : '이용 불가';


    statusEl.classList.toggle(
      'inactive',
      !active
    );


    renderTextList(
      amenityList,
      accommodation.amenities,
      '등록된 편의시설이 없습니다.'
    );


    renderTextList(
      ruleList,
      accommodation.rules,
      '등록된 이용 규칙이 없습니다.'
    );


    renderRooms(
      accommodation.rooms ||
      []
    );
  }


  function renderGallery(
    images
  ) {

    mainImageBox.textContent =
      '';

    subImageBox.textContent =
      '';


    if (
      images[0]
    ) {

      var mainImage =
        document.createElement(
          'img'
        );

      mainImage.src =
        images[0];

      mainImage.alt =
        '숙소 대표 사진';

      mainImageBox.append(
        mainImage
      );

    } else {

      mainImageBox.append(
        createPlaceholder(
          '숙소 대표 이미지'
        )
      );
    }


    for (
      var i = 1;
      i <= 4;
      i++
    ) {

      var box =
        document.createElement(
          'div'
        );

      box.className =
        'hotel-gallery-sub';


      if (
        images[i]
      ) {

        var image =
          document.createElement(
            'img'
          );

        image.src =
          images[i];

        image.alt =
          '숙소 추가 사진';

        box.append(image);

      } else {

        box.append(
          createPlaceholder(
            '추가 이미지'
          )
        );
      }


      subImageBox.append(
        box
      );
    }
  }


  function renderTextList(
    element,
    values,
    emptyMessage
  ) {

    element.textContent =
      '';


    if (
      !Array.isArray(
        values
      ) ||
      values.length === 0
    ) {

      var empty =
        document.createElement(
          'li'
        );

      empty.textContent =
        emptyMessage;

      element.append(
        empty
      );

      return;
    }


    values.forEach(
      function (value) {

        var item =
          document.createElement(
            'li'
          );

        item.textContent =
          value;

        element.append(
          item
        );
      }
    );
  }


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
            '/booking/create.html' +
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
  }


  function createPlaceholder(
    text
  ) {

    var placeholder =
      document.createElement(
        'div'
      );

    placeholder.className =
      'image-placeholder';

    placeholder.textContent =
      text;

    return placeholder;
  }


  function isValidId(
    value
  ) {

    return (
      value &&
      /^[1-9]\d*$/.test(
        value
      )
    );
  }


  function formatTime(
    value
  ) {

    if (!value) {
      return '-';
    }

    return String(
      value
    ).slice(
      0,
      5
    );
  }


  function formatPrice(
    value
  ) {

    return Number(
      value
    ).toLocaleString(
      'ko-KR'
    );
  }


  function showError(
    message
  ) {

    loading.hidden =
      true;

    detail.hidden =
      true;

    alertBox.textContent =
      message;

    alertBox.hidden =
      false;
  }

})();