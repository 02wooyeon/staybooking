/* 메인 화면: 로그인 상태에 따라 헤더와 환영 문구를 바꾼다 */
(function () {
  'use strict';

  var greeting = document.getElementById('greeting');

  Ui.renderHeader().then(function (me) {
    if (me) greeting.textContent = me.name + '님, 어디로 떠나볼까요?';
  });
})();
