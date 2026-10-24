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


  /*
   * 로그인 전 화면 확인용 계정 정보
   * (마이페이지 mock과 같은 값)
   *
   * 실제 API:
   * GET /api/v1/members/me
   * → name, phone, mileageBalance
   */
  var MOCK_PROFILE = {
    name: '홍길동',
    phone: '010-1234-5678',
    mileageBalance: 12500
  };


  // 회원가입·마이페이지와 같은 규칙
  var NAME_RE = /^[가-힣A-Za-z ]{2,20}$/;
  var PHONE_RE = /^01[016789]-?\d{3,4}-?\d{4}$/;


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

  var submitErrorEl =
    document.getElementById(
      'booking-submit-error'
    );

  var guestName =
    document.getElementById(
      'booking-guest-name'
    );

  var guestPhone =
    document.getElementById(
      'booking-guest-phone'
    );

  var mileageInput =
    document.getElementById(
      'booking-mileage'
    );

  var mileageAllButton =
    document.getElementById(
      'booking-mileage-all'
    );

  var mileageBalanceEl =
    document.getElementById(
      'booking-mileage-balance'
    );

  var mileageHelpEl =
    document.getElementById(
      'booking-mileage-help'
    );

  var mileageDiscountEl =
    document.getElementById(
      'booking-mileage-discount'
    );

  var finalPriceEl =
    document.getElementById(
      'booking-final-price'
    );

  var methodButtons =
    document.querySelectorAll(
      '.payment-method'
    );

  var easyPayButtons =
    document.querySelectorAll(
      '.easy-pay-option'
    );

  var cardPanel =
    document.getElementById(
      'payment-card-panel'
    );

  var easyPanel =
    document.getElementById(
      'payment-easy-panel'
    );

  var mileageOnlyNote =
    document.getElementById(
      'payment-mileage-only'
    );

  var cardNumber =
    document.getElementById(
      'booking-card-number'
    );

  var cardExpiry =
    document.getElementById(
      'booking-card-expiry'
    );

  var cardCvc =
    document.getElementById(
      'booking-card-cvc'
    );


  // 보유 마일리지와 선택한 결제수단
  var mileageBalance = 0;
  var paymentMethod = 'CARD';
  var easyPayProvider = null;


  var headerReady =
    Ui.renderHeader();


  /*
   * 상세 페이지에서 전달된 값
   *
   * /booking/quote.html
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
   * 이용자 정보·마일리지 기본값
   * 로그인 상태면 내 정보 API 값을, 아니면 mock 값을 사용한다.
   */
  headerReady.then(
    function (me) {

      var profile =
        me ||
        (USE_MOCK_DATA ? MOCK_PROFILE : null);

      if (!profile) {
        return;
      }

      // 사용자가 먼저 입력한 값은 덮어쓰지 않는다
      if (!guestName.value) {
        guestName.value =
          profile.name || '';
      }

      if (!guestPhone.value) {
        guestPhone.value =
          formatPhone(
            profile.phone || ''
          );
      }

      mileageBalance =
        Math.max(
          0,
          Number(profile.mileageBalance) || 0
        );

      mileageBalanceEl.textContent =
        formatPrice(
          mileageBalance
        ) +
        ' P';

      updatePrice();
    }
  );


  guestPhone.addEventListener(
    'input',
    function () {

      guestPhone.value =
        formatPhone(
          guestPhone.value
        );
    }
  );


  /*
   * 마일리지
   * 보유량과 숙박 금액 중 작은 값까지만 사용할 수 있다.
   */
  mileageInput.addEventListener(
    'input',
    function () {

      hideAlert();

      updatePrice();
    }
  );


  mileageInput.addEventListener(
    'blur',
    function () {

      mileageInput.value =
        getUsedMileage();
    }
  );


  mileageAllButton.addEventListener(
    'click',
    function () {

      mileageInput.value =
        getMaxMileage();

      updatePrice();
    }
  );


  /*
   * 결제수단 선택
   */
  methodButtons.forEach(
    function (button) {

      button.addEventListener(
        'click',
        function () {

          hideAlert();

          paymentMethod =
            button.dataset.method;

          setChecked(
            methodButtons,
            button
          );

          updatePaymentPanels();
        }
      );
    }
  );


  easyPayButtons.forEach(
    function (button) {

      button.addEventListener(
        'click',
        function () {

          hideAlert();

          easyPayProvider =
            button.dataset.provider;

          setChecked(
            easyPayButtons,
            button
          );
        }
      );
    }
  );


  // 0000-0000-0000-0000
  cardNumber.addEventListener(
    'input',
    function () {

      var digits =
        cardNumber.value
          .replace(/\D/g, '')
          .slice(0, 16);

      cardNumber.value =
        digits.replace(
          /(\d{4})(?=\d)/g,
          '$1-'
        );
    }
  );


  // MM/YY
  cardExpiry.addEventListener(
    'input',
    function () {

      var digits =
        cardExpiry.value
          .replace(/\D/g, '')
          .slice(0, 4);

      cardExpiry.value =
        digits.length > 2
          ? digits.slice(0, 2) + '/' + digits.slice(2)
          : digits;
    }
  );


  cardCvc.addEventListener(
    'input',
    function () {

      cardCvc.value =
        cardCvc.value
          .replace(/\D/g, '')
          .slice(0, 4);
    }
  );


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
          ),

        guestName:
          guestName.value.trim(),

        guestPhone:
          guestPhone.value.trim(),

        // 서버가 보유 마일리지·숙박 금액으로 다시 검증한다
        usedMileage:
          getUsedMileage(),

        paymentMethod:
          getFinalAmount() === 0
            ? 'MILEAGE'
            : paymentMethod,

        easyPayProvider:
          paymentMethod === 'EASY_PAY'
            ? easyPayProvider
            : null

        /*
         * 카드 번호·유효기간·CVC는 이 요청에 넣지 않는다.
         * 실제 연동 시 PG 결제창(SDK)이 카드사로 직접 보내고,
         * 서버는 PG 결제 결과만 검증한다. (PAY-01, PAY-02)
         */
      };


      if (
        USE_MOCK_DATA
      ) {

        /*
         * 연동 전: 서버 대신 예약을 만들어 이 탭에 저장하고 결제 단계로 넘어간다.
         * (카드 번호 등 카드 정보는 저장하지 않는다)
         */
        var mockBooking =
          createMockBooking(
            requestBody
          );

        window.location.href =
          mockBooking.status === 'PAYMENT_PENDING'
            ? '/booking/payment/pay.html?bookingId=' + encodeURIComponent(mockBooking.bookingId)
            : '/booking/payment/complete.html?bookingId=' + encodeURIComponent(mockBooking.bookingId);

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


    /* 이용자 정보 */

    if (
      !NAME_RE.test(
        guestName.value.trim()
      )
    ) {

      return fail(
        guestName,
        '이용자 성명은 한글 또는 영문 2~20자로 입력해 주세요.'
      );
    }


    if (
      !PHONE_RE.test(
        guestPhone.value.trim()
      )
    ) {

      return fail(
        guestPhone,
        '이용자 연락처를 010-1234-5678 형식으로 입력해 주세요.'
      );
    }


    /* 마일리지 */

    var mileageValue =
      Number(
        mileageInput.value || 0
      );

    if (
      !Number.isInteger(mileageValue) ||
      mileageValue < 0 ||
      mileageValue > getMaxMileage()
    ) {

      return fail(
        mileageInput,
        '마일리지는 0 ~ ' +
        formatPrice(
          getMaxMileage()
        ) +
        'P 사이로 입력해 주세요.'
      );
    }


    /* 결제수단 (마일리지로 전액 결제하면 생략) */

    if (
      getFinalAmount() === 0
    ) {
      return true;
    }


    if (
      paymentMethod === 'EASY_PAY'
    ) {

      if (!easyPayProvider) {

        return fail(
          easyPayButtons[0],
          '간편 결제 수단을 선택해 주세요.'
        );
      }

      return true;
    }


    if (
      !isValidCardNumber(
        cardNumber.value
      )
    ) {

      return fail(
        cardNumber,
        '카드 번호를 확인해 주세요.'
      );
    }


    if (
      !isValidExpiry(
        cardExpiry.value
      )
    ) {

      return fail(
        cardExpiry,
        '카드 유효기간을 확인해 주세요.'
      );
    }


    if (
      !/^\d{3,4}$/.test(
        cardCvc.value
      )
    ) {

      return fail(
        cardCvc,
        'CVC 3~4자리를 입력해 주세요.'
      );
    }


    return true;
  }


  // 오류 문구를 보여 주고 해당 칸으로 이동
  function fail(
    input,
    message
  ) {

    showError(
      message
    );

    input.focus();

    return false;
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

      updateMileage();

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

    updateMileage();
  }


  /*
   * 숙박 금액 (일정 미선택이면 null)
   */
  function getStayAmount() {

    if (
      !checkIn.value ||
      !checkOut.value ||
      checkOut.value <=
        checkIn.value
    ) {
      return null;
    }

    return (
      room.price *
      calculateNights(
        checkIn.value,
        checkOut.value
      )
    );
  }


  // 사용할 수 있는 최대 마일리지 = min(보유, 숙박 금액)
  function getMaxMileage() {

    var stay =
      getStayAmount();

    return stay === null
      ? mileageBalance
      : Math.min(
          mileageBalance,
          stay
        );
  }


  // 입력값을 0 ~ 최대 사용 가능 범위의 정수로 맞춘 값
  function getUsedMileage() {

    var value =
      Math.floor(
        Number(
          mileageInput.value
        ) || 0
      );

    return Math.min(
      Math.max(
        value,
        0
      ),
      getMaxMileage()
    );
  }


  function getFinalAmount() {

    var stay =
      getStayAmount();

    return stay === null
      ? null
      : stay - getUsedMileage();
  }


  /*
   * 마일리지 사용액·최종 금액 표시
   */
  function updateMileage() {

    var max =
      getMaxMileage();

    var used =
      getUsedMileage();

    var finalAmount =
      getFinalAmount();


    mileageInput.max =
      max;

    mileageAllButton.disabled =
      max === 0;

    mileageHelpEl.textContent =
      getStayAmount() === null
        ? '숙박 일정을 선택하면 사용할 수 있는 최대 마일리지가 계산됩니다.'
        : '최대 ' +
          formatPrice(max) +
          'P까지 사용할 수 있습니다.';

    mileageDiscountEl.textContent =
      used > 0
        ? '-' + formatPrice(used) + '원'
        : '0원';

    finalPriceEl.textContent =
      finalAmount === null
        ? '-'
        : formatPrice(finalAmount) + '원';

    updatePaymentPanels();
  }


  /*
   * 선택한 결제수단 입력 칸만 보여 준다.
   * 마일리지로 전액 결제하면 결제수단을 숨긴다.
   */
  function updatePaymentPanels() {

    var mileageOnly =
      getFinalAmount() === 0;

    cardPanel.hidden =
      mileageOnly ||
      paymentMethod !== 'CARD';

    easyPanel.hidden =
      mileageOnly ||
      paymentMethod !== 'EASY_PAY';

    mileageOnlyNote.hidden =
      !mileageOnly;

    methodButtons.forEach(
      function (button) {
        button.disabled =
          mileageOnly;
      }
    );
  }


  function setChecked(
    buttons,
    selected
  ) {

    buttons.forEach(
      function (button) {

        button.setAttribute(
          'aria-checked',
          button === selected
            ? 'true'
            : 'false'
        );
      }
    );
  }


  /*
   * 연동 전 mock 예약 (결제 화면 payment.js가 읽는다)
   * 실제로는 POST /api/v1/bookings 응답과 서버가 만든 값을 쓴다.
   */
  function createMockBooking(
    body
  ) {

    // 서버가 만들 예약번호와 같은 형식 (영문 대문자+숫자 16자리, 0·O·1·I 제외)
    var BOOKING_NUMBER_CHARS =
      'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

    var randomBytes =
      new Uint8Array(16);

    crypto.getRandomValues(
      randomBytes
    );

    var bookingNumber =
      Array.from(
        randomBytes,
        function (b) {
          return BOOKING_NUMBER_CHARS[b % 32];
        }
      ).join('');

    var bookingId =
      Date.now();

    var finalAmount =
      getFinalAmount();

    var booking = {
      bookingId: bookingId,
      bookingNumber: bookingNumber,
      orderId: 'ORDER_' + bookingId,
      accommodationName: room.accommodationName,
      roomName: room.roomName,
      checkIn: body.checkIn,
      checkOut: body.checkOut,
      nights: calculateNights(body.checkIn, body.checkOut),
      guests: body.guests,
      guestName: body.guestName,
      guestPhone: body.guestPhone,
      stayAmount: getStayAmount(),
      usedMileage: body.usedMileage,
      finalAmount: finalAmount,
      paymentMethod: body.paymentMethod,
      easyPayProvider: body.easyPayProvider,
      // 마일리지로 전액 결제하면 결제 단계 없이 승인 대기
      status: finalAmount === 0 ? 'PENDING' : 'PAYMENT_PENDING',
      paymentExpiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString()
    };

    try {
      var store =
        JSON.parse(
          sessionStorage.getItem('staybookMockPayments')
        ) || {};

      store[bookingId] =
        booking;

      sessionStorage.setItem(
        'staybookMockPayments',
        JSON.stringify(store)
      );
    } catch (e) { /* 저장소를 못 쓰면 결제 화면에서 예약을 찾지 못한다 */ }

    return booking;
  }


  // 010-1234-5678 형태로 자동 하이픈 (회원가입 화면과 같은 방식)
  function formatPhone(
    value
  ) {

    var d =
      String(value)
        .replace(/\D/g, '')
        .slice(0, 11);

    if (d.length > 7) {
      return d.slice(0, 3) + '-' + d.slice(3, d.length - 4) + '-' + d.slice(d.length - 4);
    }

    if (d.length > 3) {
      return d.slice(0, 3) + '-' + d.slice(3);
    }

    return d;
  }


  // 16자리 + Luhn 검사 (입력 실수 확인용, 실제 승인은 PG가 한다)
  function isValidCardNumber(
    value
  ) {

    var digits =
      value.replace(/\D/g, '');

    if (
      !/^\d{16}$/.test(digits)
    ) {
      return false;
    }

    var sum = 0;

    for (var i = 0; i < digits.length; i++) {

      var n =
        Number(
          digits[digits.length - 1 - i]
        );

      if (i % 2 === 1) {
        n *= 2;
        if (n > 9) n -= 9;
      }

      sum += n;
    }

    return sum % 10 === 0;
  }


  // MM/YY, 이번 달까지 유효
  function isValidExpiry(
    value
  ) {

    var match =
      /^(\d{2})\/(\d{2})$/.exec(value);

    if (!match) {
      return false;
    }

    var month =
      Number(match[1]);

    var year =
      2000 + Number(match[2]);

    if (
      month < 1 ||
      month > 12
    ) {
      return false;
    }

    var now =
      new Date();

    return (
      year > now.getFullYear() ||
      (
        year === now.getFullYear() &&
        month >= now.getMonth() + 1
      )
    );
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

    // 스크롤해도 보이도록 예약 요청 버튼 위에도 표시
    submitErrorEl.textContent =
      message;

    submitErrorEl.hidden =
      false;
  }


  function hideAlert() {

    alertBox.textContent =
      '';

    alertBox.hidden =
      true;

    submitErrorEl.textContent =
      '';

    submitErrorEl.hidden =
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