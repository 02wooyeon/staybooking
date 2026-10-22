/* 문의 상세는 URL의 ID로 서버에서 조회한다. 목록의 브라우저 저장값은 신뢰하지 않는다. */
(function () {
  'use strict';

  var labels = { booking: '예약', payment: '결제', refund: '취소·환불', account: '회원', etc: '기타' };
  var id = new URLSearchParams(window.location.search).get('id');
  var editButton = document.getElementById('qna-edit-button');
  var deleteButton = document.getElementById('qna-delete-button');
  editButton.hidden = true;
  deleteButton.hidden = true;

  function showError(message) {
    document.getElementById('detail-title').textContent = message;
    document.getElementById('detail-content').textContent = '';
  }

  async function load() {
    var me = await Ui.renderHeader();
    if (!id || !/^\d+$/.test(id)) {
      showError('문의 정보를 찾을 수 없습니다.');
      return;
    }
    var q;
    try {
      q = await BookingApi.getQna(id);
    } catch (e) {
      showError(e.status === 404 ? '문의 정보를 찾을 수 없습니다.' : e.message);
      return;
    }

    document.getElementById('detail-category').textContent = labels[q.category] || '기타';
    document.getElementById('detail-status').textContent = q.answered ? '답변 완료' : '답변 대기';
    document.getElementById('detail-secret').textContent = q.secret ? '비밀글' : '';
    document.getElementById('detail-title').textContent = q.title;
    document.getElementById('detail-date').textContent = (q.createdAt || '').slice(0, 10);
    document.getElementById('detail-content').textContent = q.content;
    if (q.answered && q.answer) {
      document.getElementById('qna-answer-box').hidden = false;
      document.getElementById('detail-answer').textContent = q.answer;
    }

    if (!me || me.userId !== q.userId) return;
    editButton.hidden = q.answered;
    deleteButton.hidden = false;
    editButton.addEventListener('click', function () {
      window.location.href = '/qna/write.html?edit=' + encodeURIComponent(id);
    });
    deleteButton.addEventListener('click', async function () {
      if (!window.confirm('이 문의를 삭제하시겠습니까?')) return;
      deleteButton.disabled = true;
      try {
        await BookingApi.deleteQna(id);
        window.location.replace('/qna.html?inquiry=deleted');
      } catch (e) {
        deleteButton.disabled = false;
        window.alert(e.message);
      }
    });
  }

  load();
})();
