/*
 * 예약 생성 화면
 *
 * BOOKING_001
 * BOOKING_002
 *
 * POST /api/v1/bookings
 *
 * 현재는 백엔드 연동 전이므로
 * mock 데이터로 화면 동작만 확인한다.
 */

(function () {
  'use strict';


  /*
   * 실제 예약 API가 완성되면 false
   */
  var USE_MOCK_DATA = true;


  /*
   * 상세 페이지에서 사용한 객실 mock과
   * 동일한 ID를 사용한다.
   */
  var MOCK_ROOMS = {

    101: {
      accommodationId: 1,
      accommodationName: '스테이 강남',
      address: '서울특별시 강남구',

      roomId: 101,
      roomName: '스탠다드 더블',

      price: 90000,
      maxGuests: 2,

      image: null
    },


    102: {
      accommodationId: 1,
      accommodationName: '스테이 강남',
      address: '서울특별시 강남구',

      roomId: 102,
      roomName: '디럭스 트윈',

      price: 130000,
      maxGuests: 3,

      image: null
    },


    201: {
      accommodationId: 2,
      accommodationName: '서울 시티 호텔',
      address: '서울특별시 중구',

      roomId: 201,
      roomName: '스탠다드 싱글',

      price: 75000,
      maxGuests: 1,

      image: null
    },


    202: {
      accommodationId: 2,
      accommodationName: '서울 시티 호텔',
      address: '서울특별시 중구',

      roomId: 202,
      roomName: '프리미엄 더블',

      price: 145000,
      maxGuests: 2,

      image: null
    },


    301: {
      accommodationId: 3,
      accommodationName: '한강 스테이',
      address: '서울특별시 마포구',

      roomId: 301,
      roomName: '리버뷰 더블',

      price: 150000,
      maxGuests: 2,

      image: null
    },


    302: {
      accommodationId: 3,
      accommodationName: '한강 스테이',
      address: '서울특별시 마포구',

      roomId: 302,
      roomName: '리버뷰 패밀리',

      price: 220000,
      maxGuests: 4,

      image: null
    }

  };


  var form =
    document.getElementById(
      'booking-form'
    );

  var alertBox =
    document.getElementById(
      'booking-alert'
    );

  var backLink =
    document.getElementById(
      'booking-back-link'
    );

  var accommodationNameEl =
    document.getElementById(
      'booking-accommodation-name'
    );

  var roomNameEl =
    document.getElementById(
      'booking-room-name'
    );

  var addressEl =
    document.getElementById(
      'booking-address'
    );

  var capacityEl =
    document.getElementById(
      'booking-capacity'
    );

  var roomImageBox =
    document.getElementById(
      'booking-room-image'
    );

  var checkIn =
    document.getElementById(
      'booking-check-in'
    );

  var checkOut =
    document.getElementById(
      'booking-check-out'
    );

  var guests =
    document.getElementById(
      'booking-guests'
    );

  var nightPriceEl =
    document.getElementById(
      'booking-night-price'
    );

  var nightCountEl =
    document.getElementById(
      'booking-night-count'
    );

  var totalPriceEl =
    document.getElementById(
      'booking-total-price'
    );

  var submitButton =
    document.getElementById(
      'booking-submit-button'
    );


  Ui.renderHeader();


  /*
   * 상세 페이지에서 전달된 값
   *
   * /booking/create.html
   * ?accommodationId=1
   * &roomId=101
   */
  var params =
    new URLSearchParams(
      window.location.search
    );

  var accommodationId =
    params.get(
      'accommodationId'
    );

  var roomId =
    params.get(
      'roomId'
    );


  if (
    !isValidId(
      accommodationId
    ) ||
    !isValidId(
      roomId
    )
  ) {

    showError(
      '잘못된 예약 접근입니다.'
    );

    disableForm();

    return;
  }


  /*
   * 이전 페이지 이동 링크
   */
  backLink.href =
    '/accommodation/detail.html?id=' +
    encodeURIComponent(
      accommodationId
    );


  var room =
    MOCK_ROOMS[
      Number(roomId)
    ];


  if (
    !room ||
    room.accommodationId !==
      Number(
        accommodationId
      )
  ) {

    showError(
      '선택한 객실 정보를 찾을 수 없습니다.'
    );

    disableForm();

    return;
  }


  /*
   * 선택한 객실 표시
   */
  renderRoom(
    room
  );


  /*
   * 날짜 최소값
   */
  setMinimumDates();


  /*
   * URL에 검색조건이 넘어온 경우도 대비
   */
  if (
    params.get('checkIn')
  ) {
    checkIn.value =
      params.get('checkIn');
  }

  if (
    params.get('checkOut')
  ) {
    checkOut.value =
      params.get('checkOut');
  }

  if (
    params.get('guests')
  ) {
    guests.value =
      params.get('guests');
  }


  /*
   * 날짜 / 인원이 바뀔 때
   * 예상 금액 재계산
   */
  checkIn.addEventListener(
    'change',
    function () {

      hideAlert();

      if (
        checkIn.value
      ) {

        checkOut.min =
          getNextDate(
            checkIn.value
          );

        if (
          checkOut.value &&
          checkOut.value <=
            checkIn.value
        ) {

          checkOut.value =
            '';
        }
      }

      updatePrice();
    }
  );


  checkOut.addEventListener(
    'change',
    function () {

      hideAlert();

      updatePrice();
    }
  );


  guests.addEventListener(
    'input',
    function () {

      hideAlert();

      updatePrice();
    }
  );


  /*
   * 예약 제출
   */
  form.addEventListener(
    'submit',
    async function (event) {

      event.preventDefault();

      hideAlert();


      if (
        !validateBooking(
          room
        )
      ) {
        return;
      }


      /*
       * 클라이언트에서 계산한 totalPrice는
       * 실제 예약 API에 authoritative 값으로
       * 보내지 않는다.
       *
       * 서버가 DB 가격을 기준으로
       * 다시 계산해야 하기 때문.
       */
      var requestBody = {

        roomId:
          Number(
            roomId
          ),

        checkIn:
          checkIn.value,

        checkOut:
          checkOut.value,

        guests:
          Number(
            guests.value
          )
      };


      if (
        USE_MOCK_DATA
      ) {

        alert(
          '예약 요청 화면 테스트가 완료되었습니다.\n\n' +
          '객실: ' +
          room.roomName +
          '\n체크인: ' +
          checkIn.value +
          '\n체크아웃: ' +
          checkOut.value +
          '\n인원: ' +
          guests.value +
          '명'
        );

        return;
      }


      /*
       * 실제 BOOK-01
       * POST /api/v1/bookings
       */
      try {

        setSubmitting(
          true
        );


        var response =
          await fetch(
            '/api/v1/bookings',
            {
              method:
                'POST',

              headers: {
                'Content-Type':
                  'application/json',

                Accept:
                  'application/json'
              },

              credentials:
                'same-origin',

              body:
                JSON.stringify(
                  requestBody
                )
            }
          );


        if (
          response.status ===
          401
        ) {

          showError(
            '로그인이 필요합니다.'
          );

          return;
        }


        if (
          response.status ===
          400
        ) {

          showError(
            '예약 정보를 다시 확인해 주세요.'
          );

          return;
        }


        if (
          response.status ===
          409
        ) {

          showError(
            '해당 일정에 예약 가능한 객실이 없습니다.'
          );

          return;
        }


        if (
          !response.ok
        ) {

          throw new Error(
            '예약 생성 실패'
          );
        }


        var data =
          await response.json();


        if (
          data &&
          data.data
        ) {

          data =
            data.data;
        }


        /*
         * 응답에 bookingId가 있다고 가정.
         * 실제 백엔드 응답 DTO가 정해지면
         * 여기만 맞추면 된다.
         */
        if (
          data &&
          data.bookingId
        ) {

          window.location.href =
            '/booking/my.html';

        } else {

          window.location.href =
            '/booking/my.html';
        }


      } catch (error) {

        console.error(
          error
        );

        showError(
          '예약 처리 중 오류가 발생했습니다.'
        );

      } finally {

        setSubmitting(
          false
        );
      }

    }
  );


  updatePrice();


  function renderRoom(
    roomData
  ) {

    accommodationNameEl.textContent =
      roomData
        .accommodationName;

    roomNameEl.textContent =
      roomData
        .roomName;

    addressEl.textContent =
      roomData
        .address;

    capacityEl.textContent =
      '최대 ' +
      roomData.maxGuests +
      '명';


    guests.max =
      roomData.maxGuests;


    nightPriceEl.textContent =
      formatPrice(
        roomData.price
      ) +
      '원';


    roomImageBox.textContent =
      '';


    if (
      roomData.image
    ) {

      var image =
        document.createElement(
          'img'
        );

      image.src =
        roomData.image;

      image.alt =
        roomData.roomName +
        ' 객실 사진';

      roomImageBox.append(
        image
      );

    } else {

      var placeholder =
        document.createElement(
          'div'
        );

      placeholder.className =
        'image-placeholder';

      placeholder.textContent =
        '객실 이미지';

      roomImageBox.append(
        placeholder
      );
    }
  }


  /*
   * 프론트 1차 검증
   *
   * 실제 보안 검증은
   * 서버에서도 반드시 다시 수행.
   */
  function validateBooking(
    roomData
  ) {

    if (
      !checkIn.value ||
      !checkOut.value
    ) {

      showError(
        '체크인과 체크아웃 날짜를 입력해 주세요.'
      );

      return false;
    }


    if (
      checkOut.value <=
        checkIn.value
    ) {

      showError(
        '체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.'
      );

      return false;
    }


    var guestValue =
      Number(
        guests.value
      );


    if (
      !Number.isInteger(
        guestValue
      ) ||
      guestValue < 1
    ) {

      showError(
        '인원수를 확인해 주세요.'
      );

      return false;
    }


    if (
      guestValue >
        roomData.maxGuests
    ) {

      showError(
        '선택한 객실의 최대 수용 인원은 ' +
        roomData.maxGuests +
        '명입니다.'
      );

      return false;
    }


    return true;
  }


  /*
   * 예상 금액 계산
   *
   * 화면 표시용일 뿐,
   * 실제 결제/예약 금액은 서버 계산값을 사용.
   */
  function updatePrice() {

    if (
      !checkIn.value ||
      !checkOut.value ||
      checkOut.value <=
        checkIn.value
    ) {

      nightCountEl.textContent =
        '-';

      totalPriceEl.textContent =
        '-';

      return;
    }


    var nights =
      calculateNights(
        checkIn.value,
        checkOut.value
      );


    nightCountEl.textContent =
      nights +
      '박';


    totalPriceEl.textContent =
      formatPrice(
        room.price *
        nights
      ) +
      '원';
  }


  function calculateNights(
    start,
    end
  ) {

    var startDate =
      new Date(
        start +
        'T00:00:00'
      );

    var endDate =
      new Date(
        end +
        'T00:00:00'
      );


    var milliseconds =
      endDate.getTime() -
      startDate.getTime();


    return Math.round(
      milliseconds /
      (
        1000 *
        60 *
        60 *
        24
      )
    );
  }


  function setMinimumDates() {

    var today =
      new Date();

    var yyyy =
      today.getFullYear();

    var mm =
      String(
        today.getMonth() + 1
      ).padStart(
        2,
        '0'
      );

    var dd =
      String(
        today.getDate()
      ).padStart(
        2,
        '0'
      );


    var todayString =
      yyyy +
      '-' +
      mm +
      '-' +
      dd;


    checkIn.min =
      todayString;

    checkOut.min =
      todayString;
  }


  function getNextDate(
    value
  ) {

    var date =
      new Date(
        value +
        'T00:00:00'
      );


    date.setDate(
      date.getDate() +
      1
    );


    var yyyy =
      date.getFullYear();

    var mm =
      String(
        date.getMonth() +
        1
      ).padStart(
        2,
        '0'
      );

    var dd =
      String(
        date.getDate()
      ).padStart(
        2,
        '0'
      );


    return (
      yyyy +
      '-' +
      mm +
      '-' +
      dd
    );
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

    alertBox.textContent =
      message;

    alertBox.hidden =
      false;
  }


  function hideAlert() {

    alertBox.textContent =
      '';

    alertBox.hidden =
      true;
  }


  function disableForm() {

    Array.from(
      form.elements
    ).forEach(
      function (element) {

        element.disabled =
          true;
      }
    );
  }


  function setSubmitting(
    submitting
  ) {

    submitButton.disabled =
      submitting;

    submitButton.textContent =
      submitting
        ? '예약 처리 중...'
        : '예약 요청';
  }

})();