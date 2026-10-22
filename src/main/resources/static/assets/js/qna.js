/* 고객센터 FAQ와 실제 문의 목록 */
(function () {
  'use strict';

  var labels = { booking: '예약', payment: '결제', refund: '취소·환불', account: '회원', etc: '기타' };
  var alertBox = document.getElementById('qna-alert');

  document.querySelectorAll('.faq-tab').forEach(function (tab) {
    tab.addEventListener('click', function () {
      document.querySelectorAll('.faq-tab').forEach(function (button) {
        button.setAttribute('aria-pressed', String(button === tab));
      });
      document.querySelectorAll('#faq-list details').forEach(function (item) {
        item.hidden = tab.dataset.category !== 'all' && item.dataset.category !== tab.dataset.category;
      });
    });
  });

  function renderList(list, empty, inquiries, limit) {
    list.textContent = '';
    inquiries.slice(0, limit || inquiries.length).forEach(function (q) {
      var item = document.createElement('li');
      var info = document.createElement('div');
      var title = document.createElement('a');
      title.className = 'inquiry-title';
      title.href = '/qna/detail.html?id=' + encodeURIComponent(q.qnaId);
      title.textContent = q.title;
      if (q.secret) {
        var lock = document.createElement('span');
        lock.className = 'inquiry-lock';
        lock.textContent = '비밀글';
        title.append(' ', lock);
      }
      var meta = document.createElement('p');
      meta.className = 'inquiry-meta';
      meta.textContent = (q.createdAt || '').slice(0, 10) + ' · ' + (labels[q.category] || '기타');
      var badge = document.createElement('span');
      badge.className = 'inquiry-badge ' + (q.answered ? 'done' : 'wait');
      badge.textContent = q.answered ? '답변 완료' : '답변 대기';
      info.append(title, meta);
      item.append(info, badge);
      list.append(item);
    });
    empty.hidden = inquiries.length > 0;
  }

  async function load() {
    Ui.renderHeader();
    var notice = new URLSearchParams(window.location.search).get('inquiry');
    var messages = {
      done: '문의가 등록되었습니다.',
      updated: '문의가 수정되었습니다.',
      deleted: '문의가 삭제되었습니다.'
    };
    if (messages[notice]) {
      Ui.showAlert(alertBox, messages[notice], 'success');
      window.history.replaceState(null, '', window.location.pathname);
    }

    var publicList = document.getElementById('public-qna-list');
    var publicEmpty = document.getElementById('public-qna-empty');
    try {
      renderList(publicList, publicEmpty, await BookingApi.getPublicQna());
    } catch (e) {
      publicEmpty.textContent = '공개 문의를 불러오지 못했습니다.';
      publicEmpty.hidden = false;
      Ui.showAlert(alertBox, e.message, 'error');
    }

    var recentList = document.getElementById('inquiry-recent');
    var recentEmpty = document.getElementById('inquiry-empty');
    var showAll = document.getElementById('inquiry-show-all');
    try {
      var mine = await BookingApi.getMyQna();
      renderList(recentList, recentEmpty, mine, 3);
      showAll.hidden = mine.length <= 3;
      showAll.addEventListener('click', function () {
        renderList(recentList, recentEmpty, mine);
        showAll.hidden = true;
      });
    } catch (e) {
      recentEmpty.textContent = e.status === 401
        ? '로그인하면 내 문의를 확인할 수 있습니다.' : '내 문의를 불러오지 못했습니다.';
      recentEmpty.hidden = false;
      if (e.status !== 401) Ui.showAlert(alertBox, e.message, 'error');
    }
  }

  load();
})();
