/* 메인 화면: 로그인 상태에 따라 헤더와 환영 문구를 바꾼다 */
(function () {
  'use strict';

  var greeting = document.getElementById('greeting');

  Ui.renderHeader().then(function (me) {
    if (me) greeting.textContent = me.name + '님, 어디로 떠나볼까요?';
  });

  /* 검색창: 빈 칸은 주소에 넣지 않고, 날짜는 오늘 이후·체크아웃은 체크인 다음 날부터 고르게 한다 */
  var form = document.getElementById('home-search-form');
  var checkIn = document.getElementById('home-check-in');
  var checkOut = document.getElementById('home-check-out');

  function isoDate(date) {
    var local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
  }

  var today = new Date();
  checkIn.min = isoDate(today);
  checkOut.min = isoDate(new Date(today.getTime() + 86400000));

  checkIn.addEventListener('change', function () {
    if (!checkIn.value) return;
    var next = new Date(checkIn.value + 'T00:00:00');
    next.setDate(next.getDate() + 1);
    checkOut.min = isoDate(next);
    if (checkOut.value && checkOut.value <= checkIn.value) checkOut.value = isoDate(next);
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var params = new URLSearchParams();
    new FormData(form).forEach(function (value, key) {
      value = String(value).trim();
      if (value) params.set(key, value);
    });
    // 날짜는 둘 다 있을 때만 보낸다 (하나만 있으면 목록 화면이 잘못된 조건으로 본다)
    if (!params.get('checkIn') || !params.get('checkOut')) {
      params.delete('checkIn');
      params.delete('checkOut');
    }
    var query = params.toString();
    window.location.href = '/accommodation/list.html' + (query ? '?' + query : '');
  });
})();
