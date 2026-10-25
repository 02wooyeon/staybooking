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
    // 첨부 사진이 있을 때만 보여 준다. 서버가 준 업로드 주소 형식일 때만 받는다.
    if (q.image && /^\/api\/v1\/uploads\/Images\/\d{1,19}$/.test(q.image)) {
      showImage(q.image);
    }
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

  // <img src> 로 바로 부르면 로그인 토큰이 안 붙어 비밀글 사진이 404 로 깨진다.
  // 토큰을 붙여 받아 온 뒤 보여 주고, 받지 못하면 사진 칸은 숨긴 채 글만 보여 준다.
  async function showImage(url) {
    var image = document.getElementById('detail-image');
    try {
      var blob = await BookingApi.getImage(url);
      image.src = URL.createObjectURL(blob);
      image.hidden = false;
    } catch (e) {
      image.hidden = true;
    }
  }

  load();
})();
