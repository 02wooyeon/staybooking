/*
 * 숙소 목록 / 검색 화면
 *
 * 요구사항
 * ACCOM_001 : 전체 숙소 목록 조회
 * ACCOM_002 : 숙소 검색 및 필터링
 *
 * API
 * GET /api/v1/accommodations
 * GET /api/v1/accommodations/search
 */

(function () {
  'use strict';

  var form = document.getElementById('accommodation-search-form');

  /*
   * detail.html에서도 accommodation.js를 사용할 예정이므로
   * list.html이 아닌 경우 아래 목록 관련 코드는 실행하지 않는다.
   */
  if (!form) {
    return;
  }

  var keyword = document.getElementById('keyword');
  var checkIn = document.getElementById('check-in');
  var checkOut = document.getElementById('check-out');
  var guests = document.getElementById('guests');

  var list = document.getElementById('accommodation-list');
  var alertBox = document.getElementById('accommodation-alert');
  var loadingMessage = document.getElementById('loading-message');
  var emptyMessage = document.getElementById('empty-message');
  var resultCount = document.getElementById('result-count');
  var searchButton = document.getElementById('search-button');

  /*
   * 현재 백엔드의 숙소 Controller가 아직 완성되지 않았을 수 있으므로
   * API 실패 시 화면 개발을 계속 할 수 있도록 임시 데이터 사용.
   *
   * 백엔드 숙소 API 구현이 완료되면 false로 변경한다.
   */
  var USE_MOCK_DATA = true;

  var MOCK_ACCOMMODATIONS = [
  {
    accommodationId: 1,
    name: '스테이 강남',
    address: '서울특별시 강남구',
    description: '강남 중심에 위치한 편안한 숙소입니다.',
    checkInTime: '15:00',
    checkOutTime: '11:00',
    status: 'ACTIVE',

    // 나중에 서버 이미지 URL로 교체
    mainImage: null
  },
  {
    accommodationId: 2,
    name: '서울 시티 호텔',
    address: '서울특별시 중구',
    description: '서울 도심 여행에 적합한 숙소입니다.',
    checkInTime: '15:00',
    checkOutTime: '11:00',
    status: 'ACTIVE',

    mainImage: null
  },
  {
    accommodationId: 3,
    name: '한강 스테이',
    address: '서울특별시 마포구',
    description: '한강과 가까운 숙소입니다.',
    checkInTime: '16:00',
    checkOutTime: '11:00',
    status: 'ACTIVE',

    mainImage: null
  }
];

  /*
   * 기존 팀원 코드와 동일하게 로그인 상태를 확인해
   * 헤더를 렌더링한다.
   */
  Ui.renderHeader();

  /*
   * 최초 페이지 접근 시 전체 숙소 조회
   * ACCOM_001
   */
  initializeFromQuery();

  /*
 * 메인 화면에서 전달받은 검색조건 처리
 *
 * 예:
 * /accommodation/list.html
 * ?keyword=서울
 * &checkIn=2026-10-20
 * &checkOut=2026-10-22
 * &guests=2
 */
function initializeFromQuery() {

  var params =
    new URLSearchParams(
      window.location.search
    );

  var hasSearchCondition =
    params.has('keyword') ||
    params.has('checkIn') ||
    params.has('checkOut') ||
    params.has('guests');

  if (!hasSearchCondition) {

    loadAccommodations();

    return;
  }

  if (params.get('keyword')) {

    keyword.value =
      params.get('keyword');
  }

  if (params.get('checkIn')) {

    checkIn.value =
      params.get('checkIn');
  }

  if (params.get('checkOut')) {

    checkOut.value =
      params.get('checkOut');
  }

  if (params.get('guests')) {

    guests.value =
      params.get('guests');
  }

  /*
   * URL 값도 그대로 신뢰하지 않고
   * 프론트에서 한 번 검증한다.
   *
   * 실제 보안 검증은 서버에서 다시 수행해야 한다.
   */
  if (!validateSearch()) {

    loadAccommodations();

    return;
  }

  searchAccommodations();
}

  /*
   * 검색 버튼
   * ACCOM_002
   */
  form.addEventListener('submit', function (event) {
    event.preventDefault();

    Ui.hideAlert(alertBox);

    if (!validateSearch()) {
      return;
    }

    searchAccommodations();
  });

  /*
   * 검색 입력값 검증
   *
   * API 명세의 보안 요구사항:
   * - checkIn/checkOut 날짜 형식 검증
   * - guests 양의 정수 검증
   *
   * 단, 프론트 검증만 신뢰하면 안 되고
   * 백엔드에서도 반드시 다시 검증해야 한다.
   */
  function validateSearch() {

    if (checkIn.value && checkOut.value) {

      var start = new Date(checkIn.value);
      var end = new Date(checkOut.value);

      if (start >= end) {
        Ui.showAlert(
          alertBox,
          '체크아웃 날짜는 체크인 날짜보다 이후여야 합니다.',
          'error'
        );

        return false;
      }
    }

    var guestValue = Number(guests.value);

    if (
      !Number.isInteger(guestValue) ||
      guestValue < 1
    ) {
      Ui.showAlert(
        alertBox,
        '인원수는 1명 이상의 정수로 입력해 주세요.',
        'error'
      );

      return false;
    }

    return true;
  }

  /*
   * 전체 숙소 조회
   *
   * GET /api/v1/accommodations
   */
  async function loadAccommodations() {

    setLoading(true);

    try {

      var data;

      if (USE_MOCK_DATA) {
        data = MOCK_ACCOMMODATIONS;
      } else {

        var response = await fetch(
          '/api/v1/accommodations?page=0&size=20',
          {
            method: 'GET',
            headers: {
              Accept: 'application/json'
            },
            credentials: 'same-origin',
            cache: 'no-store'
          }
        );

        if (!response.ok) {
          throw new Error('숙소 정보를 불러오지 못했습니다.');
        }

        data = await response.json();
      }

      renderAccommodations(
        normalizeAccommodationList(data)
      );

    } catch (error) {

      console.error(error);

      Ui.showAlert(
        alertBox,
        '숙소 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
        'error'
      );

    } finally {
      setLoading(false);
    }
  }

  /*
   * 숙소 검색
   *
   * GET /api/v1/accommodations/search
   *
   * 값을 문자열로 직접 붙이지 않고
   * URLSearchParams를 사용해 URL Encoding 처리한다.
   */
  async function searchAccommodations() {

    Ui.setLoading(
      searchButton,
      true,
      '검색 중...'
    );

    setLoading(true);

    try {

      var params = new URLSearchParams();

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

      if (guests.value) {
        params.set(
          'guests',
          guests.value
        );
      }

      params.set('page', '0');
      params.set('size', '20');

      var data;

      if (USE_MOCK_DATA) {

        data = filterMockData(
          keyword.value.trim()
        );

      } else {

        var response = await fetch(
          '/api/v1/accommodations/search?' +
          params.toString(),
          {
            method: 'GET',
            headers: {
              Accept: 'application/json'
            },
            credentials: 'same-origin',
            cache: 'no-store'
          }
        );

        if (!response.ok) {
          throw new Error('숙소 검색에 실패했습니다.');
        }

        data = await response.json();
      }

      renderAccommodations(
        normalizeAccommodationList(data)
      );

    } catch (error) {

      console.error(error);

      Ui.showAlert(
        alertBox,
        '숙소 검색 중 오류가 발생했습니다.',
        'error'
      );

    } finally {

      setLoading(false);

      Ui.setLoading(
        searchButton,
        false
      );
    }
  }

  /*
   * 서버 응답 형태가 아직 확정되지 않았기 때문에
   * 흔히 사용되는 여러 응답 형태를 처리한다.
   *
   * [
   *   {...}
   * ]
   *
   * 또는
   *
   * {
   *   "content": [...]
   * }
   *
   * 또는
   *
   * {
   *   "data": [...]
   * }
   */
  function normalizeAccommodationList(data) {

    if (Array.isArray(data)) {
      return data;
    }

    if (
      data &&
      Array.isArray(data.content)
    ) {
      return data.content;
    }

    if (
      data &&
      Array.isArray(data.data)
    ) {
      return data.data;
    }

    if (
      data &&
      data.data &&
      Array.isArray(data.data.content)
    ) {
      return data.data.content;
    }

    return [];
  }

  /*
   * 숙소 목록 화면 출력
   *
   * innerHTML 대신 DOM API와 textContent를 사용한다.
   * 서버에서 악성 문자열이 내려와도 HTML로 실행되지 않게 한다.
   */
  function renderAccommodations(accommodations) {

    list.textContent = '';

    resultCount.textContent =
      accommodations.length + '개';

    if (accommodations.length === 0) {

      emptyMessage.hidden = false;

      return;
    }

    emptyMessage.hidden = true;

    accommodations.forEach(function (accommodation) {

      var card = document.createElement('article');

      card.className = 'accommodation-card';

      var imageBox = document.createElement('div');

imageBox.className =
  'accommodation-card-image';


if (accommodation.mainImage) {

  var image =
    document.createElement('img');

  image.src =
    accommodation.mainImage;

  image.alt =
    accommodation.name +
    ' 숙소 사진';

  image.loading =
    'lazy';

  imageBox.append(image);

} else {

  /*
   * 아직 실제 이미지가 없을 때
   * 깨진 이미지 대신 placeholder 표시
   */
  var placeholder =
    document.createElement('div');

  placeholder.className =
    'image-placeholder';

  placeholder.textContent =
    '숙소 이미지 준비 중';

  imageBox.append(
    placeholder
  );
}

      var content = document.createElement('div');

      content.className =
        'accommodation-card-content';

      var name = document.createElement('h3');

      name.textContent =
        accommodation.name ||
        '숙소 이름 없음';

      var address = document.createElement('p');

      address.className =
        'accommodation-address';

      address.textContent =
        accommodation.address ||
        '주소 정보 없음';

      var description =
        document.createElement('p');

      description.className =
        'accommodation-description';

      description.textContent =
        accommodation.description ||
        '숙소 설명이 없습니다.';

      var time =
        document.createElement('p');

      time.className =
        'accommodation-time';

      time.textContent =
        '체크인 ' +
        formatTime(
          accommodation.checkInTime
        ) +
        ' · 체크아웃 ' +
        formatTime(
          accommodation.checkOutTime
        );

      var link =
        document.createElement('a');

      link.className =
        'btn-small accommodation-detail-button';

      link.textContent =
        '상세 보기';

      /*
       * accommodationId를 상세 페이지 URL에 전달
       *
       * 예:
       * /accommodation/detail.html?id=3
       */
      link.href =
        '/accommodation/detail.html?id=' +
        encodeURIComponent(
          accommodation.accommodationId
        );

      content.append(
        name,
        address,
        description,
        time,
        link
      );

    card.append(
  imageBox,
  content
);

      list.append(card);
    });
  }

  function formatTime(value) {

    if (!value) {
      return '-';
    }

    /*
     * 서버가 15:00:00 형태로 반환해도
     * 화면에는 15:00만 표시
     */
    return String(value).slice(0, 5);
  }

  /*
   * 백엔드 연결 전 임시 검색
   */
  function filterMockData(searchKeyword) {

    if (!searchKeyword) {
      return MOCK_ACCOMMODATIONS;
    }

    var value =
      searchKeyword.toLowerCase();

    return MOCK_ACCOMMODATIONS.filter(
      function (item) {

        return (
          item.name
            .toLowerCase()
            .indexOf(value) !== -1 ||

          item.address
            .toLowerCase()
            .indexOf(value) !== -1
        );
      }
    );
  }

  function setLoading(loading) {

    loadingMessage.hidden =
      !loading;

    if (loading) {
      emptyMessage.hidden = true;
    }
  }

})();