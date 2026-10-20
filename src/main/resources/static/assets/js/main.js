/*
 * 메인 화면
 *
 * - 로그인 상태 헤더
 * - 숙소 검색
 * - 숙소 미리보기
 */

(function () {
  'use strict';

  var greeting =
    document.getElementById('greeting');

  var form =
    document.getElementById('home-search-form');

  var keyword =
    document.getElementById('home-keyword');

  var checkIn =
    document.getElementById('home-check-in');

  var checkOut =
    document.getElementById('home-check-out');

  var guests =
    document.getElementById('home-guests');

  var alertBox =
    document.getElementById('home-search-alert');

  var previewList =
    document.getElementById('home-accommodation-list');


  var MOCK_ACCOMMODATIONS = [
    {
      accommodationId: 1,
      name: '스테이 강남',
      address: '서울특별시 강남구',
      description: '강남 중심에 위치한 편안한 숙소입니다.',
      checkInTime: '15:00',
      checkOutTime: '11:00',
      mainImage: null
    },

    {
      accommodationId: 2,
      name: '서울 시티 호텔',
      address: '서울특별시 중구',
      description: '서울 도심 여행에 적합한 숙소입니다.',
      checkInTime: '15:00',
      checkOutTime: '11:00',
      mainImage: null
    },

    {
      accommodationId: 3,
      name: '한강 스테이',
      address: '서울특별시 마포구',
      description: '한강과 가까운 숙소입니다.',
      checkInTime: '16:00',
      checkOutTime: '11:00',
      mainImage: null
    }
  ];


  /*
   * 공통 헤더
   */
  Promise.resolve(
    Ui.renderHeader()
  ).then(function (me) {

    if (me && greeting) {
      greeting.textContent =
        me.name + '님, 어디로 떠나볼까요?';
    }
  });


  setMinimumDates();

  /*
   * 첫 화면에서 숙소 목록 바로 표시
   */
  renderPreview(
    MOCK_ACCOMMODATIONS
  );


  /*
   * 메인 검색
   */
  form.addEventListener(
    'submit',
    function (event) {

      event.preventDefault();

      hideAlert();

      if (!validateSearch()) {
        return;
      }

      var params =
        new URLSearchParams();

      if (keyword.value.trim()) {
        params.set(
          'keyword',
          keyword.value.trim()
        );
      }

      if (checkIn.value) {
        params.set(
          'checkIn',
          checkIn.value
        );
      }

      if (checkOut.value) {
        params.set(
          'checkOut',
          checkOut.value
        );
      }

      params.set(
        'guests',
        guests.value || '1'
      );

      window.location.href =
        '/accommodation/list.html?' +
        params.toString();
    }
  );


  checkIn.addEventListener(
    'change',
    function () {

      if (!checkIn.value) {
        return;
      }

      checkOut.min =
        getNextDate(
          checkIn.value
        );

      if (
        checkOut.value &&
        checkOut.value <=
          checkIn.value
      ) {
        checkOut.value = '';
      }
    }
  );


  function validateSearch() {

    if (
      checkIn.value &&
      checkOut.value &&
      checkOut.value <=
        checkIn.value
    ) {

      showAlert(
        '체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.'
      );

      return false;
    }

    var guestValue =
      Number(guests.value);

    if (
      !Number.isInteger(
        guestValue
      ) ||
      guestValue < 1 ||
      guestValue > 20
    ) {

      showAlert(
        '인원수는 1명 이상 20명 이하로 입력해 주세요.'
      );

      return false;
    }

    return true;
  }


  function renderPreview(
    accommodations
  ) {

    previewList.textContent = '';

    accommodations.forEach(
      function (accommodation) {

        var card =
          document.createElement(
            'article'
          );

        card.className =
          'home-accommodation-card';


        /*
         * 숙소 대표 이미지
         */
        var imageBox =
          document.createElement(
            'div'
          );

        imageBox.className =
          'home-accommodation-image';


        if (
          accommodation.mainImage
        ) {

          var image =
            document.createElement(
              'img'
            );

          image.src =
            accommodation.mainImage;

          image.alt =
            accommodation.name +
            ' 숙소 사진';

          imageBox.append(image);

        } else {

          var placeholder =
            document.createElement(
              'div'
            );

          placeholder.className =
            'image-placeholder';

          placeholder.textContent =
            '숙소 이미지 준비 중';

          imageBox.append(
            placeholder
          );
        }


        var name =
          document.createElement('h3');

        name.textContent =
          accommodation.name;


        var address =
          document.createElement('p');

        address.className =
          'home-accommodation-address';

        address.textContent =
          accommodation.address;


        var description =
          document.createElement('p');

        description.className =
          'home-accommodation-description';

        description.textContent =
          accommodation.description;


        var time =
          document.createElement('p');

        time.className =
          'home-accommodation-time';

        time.textContent =
          '체크인 ' +
          accommodation.checkInTime +
          ' · 체크아웃 ' +
          accommodation.checkOutTime;


        var link =
          document.createElement('a');

        link.className =
          'btn-small';

        link.textContent =
          '상세 보기';

        link.href =
          '/accommodation/detail.html?id=' +
          encodeURIComponent(
            accommodation
              .accommodationId
          );


        card.append(
          imageBox,
          name,
          address,
          description,
          time,
          link
        );

        previewList.append(
          card
        );
      }
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
      ).padStart(2, '0');

    var dd =
      String(
        today.getDate()
      ).padStart(2, '0');

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
    dateString
  ) {

    var date =
      new Date(
        dateString +
        'T00:00:00'
      );

    date.setDate(
      date.getDate() + 1
    );

    var yyyy =
      date.getFullYear();

    var mm =
      String(
        date.getMonth() + 1
      ).padStart(2, '0');

    var dd =
      String(
        date.getDate()
      ).padStart(2, '0');

    return (
      yyyy +
      '-' +
      mm +
      '-' +
      dd
    );
  }


  function showAlert(message) {

    alertBox.textContent =
      message;

    alertBox.hidden = false;
  }


  function hideAlert() {

    alertBox.textContent = '';

    alertBox.hidden = true;
  }

})();