/*
 * Q&A 관리 (ADMIN_006)
 *   GET   /api/v1/admin/qna/search?userId=&answered=&deleted=&q=&page=&size=
 *   PUT   /api/v1/admin/qna/{qnaId}/answer   { content }   (등록·수정 겸용, 2000자 이하)
 *   PATCH /api/v1/admin/qna/{qnaId}/hide     { reason }    (soft delete, 255자 이하)
 * 숨김 처리된 글에는 답변할 수 없다 (서버 규칙).
 * 제목·내용·숨김 사유는 사용자가 입력한 값이므로 textContent 로만 넣는다.
 */
(function () {
  'use strict';

  var API = '/api/v1/admin/qna';
  var CATEGORY = { booking: '예약', payment: '결제', refund: '취소·환불', account: '회원', etc: '기타' };

  var alertBox = document.getElementById('page-alert');
  var tbody = document.getElementById('list-body');
  var pager = document.getElementById('pagination');
  var detail = document.getElementById('qna-detail');
  var answerForm = document.getElementById('answer-form');
  var answerInput = document.getElementById('answer-content');
  var answerCount = document.getElementById('answer-count');
  var answerSubmit = document.getElementById('answer-submit');
  var hideButton = document.getElementById('qna-hide');

  var filters = null;
  var current = null; // 상세에 열려 있는 문의

  AdminUi.init({ page: 'qna', roles: ['ROLE_ADMIN'] }).then(function (me) {
    if (!me) return;
    filters = AdminUi.bindFilters(document.getElementById('filter-form'),
      { q: '', answered: '', deleted: 'false', userId: '', page: 0 }, load);
  });

  async function load(query) {
    AdminUi.hideMessage(alertBox);
    if (query.userId && !AdminUi.isPositiveId(query.userId)) {
      AdminUi.showMessage(alertBox, '작성자 ID는 1 이상의 숫자로 입력해 주세요.');
      return;
    }
    try {
      var page = await AdminUi.api('GET', API + '/search' + AdminUi.buildQuery({
        userId: query.userId, answered: query.answered, deleted: query.deleted, q: query.q,
        page: query.page, size: AdminUi.DEFAULT_PAGE_SIZE
      }));
      render(page);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }

  function badges(q) {
    var nodes = [AdminUi.statusBadge(q.answered ? 'Y' : 'N',
      { Y: '답변 완료', N: '답변 대기' }, { Y: 'ok', N: 'warn' })];
    if (q.secret) nodes.push(AdminUi.statusBadge('S', { S: '비밀글' }, { S: 'muted' }));
    if (q.deleted) nodes.push(AdminUi.statusBadge('D', { D: '숨김' }, { D: 'error' }));
    return AdminUi.group(nodes, 'admin-badges');
  }

  function render(page) {
    AdminUi.renderTable(tbody, page.content || [], [
      { label: '문의 ID', render: function (q) { return q.qnaId; } },
      { label: '유형', render: function (q) { return CATEGORY[q.category] || '기타'; } },
      { label: '제목', className: 'admin-wrap', render: function (q) { return q.title; } },
      { label: '작성자 ID', render: function (q) { return q.userId; } },
      { label: '작성일', render: function (q) { return AdminUi.formatDateTime(q.createdAt); } },
      { label: '상태', render: badges },
      { label: '관리', render: function (q) {
        return AdminUi.actionButton('보기', function () { openDetail(q); });
      } }
    ], '조건에 맞는 문의가 없습니다.');
    AdminUi.renderPagination(pager, page, filters.go);

    // 목록을 다시 불러온 뒤에도 열려 있던 문의는 최신 내용으로 다시 보여 준다
    if (current) {
      var fresh = (page.content || []).filter(function (q) { return q.qnaId === current.qnaId; })[0];
      if (fresh) openDetail(fresh, true);
    }
  }

  function openDetail(q, keepScroll) {
    current = q;
    document.getElementById('qna-detail-title').textContent = q.title;
    document.getElementById('qna-detail-meta').textContent = '문의 #' + q.qnaId + ' · '
      + (CATEGORY[q.category] || '기타') + ' · 작성자 ID ' + q.userId
      + (q.bookingId ? ' · 예약 #' + q.bookingId : '') + ' · ' + AdminUi.formatDateTime(q.createdAt);

    var badgeBox = document.getElementById('qna-detail-badges');
    badgeBox.textContent = '';
    badgeBox.append(badges(q));

    document.getElementById('qna-detail-content').textContent = q.content;

    var hiddenNote = document.getElementById('qna-detail-hidden');
    hiddenNote.hidden = !q.deleted;
    hiddenNote.textContent = q.deleted ? '숨김 사유: ' + (q.deletedReason || '-') : '';

    answerInput.value = q.answer || '';
    answerCount.textContent = answerInput.value.length + ' / 2000';
    answerSubmit.textContent = q.answered ? '답변 수정' : '답변 등록';
    // 숨김 처리된 글에는 답변·재숨김을 할 수 없다
    answerInput.disabled = q.deleted;
    answerSubmit.disabled = q.deleted;
    hideButton.hidden = q.deleted;

    detail.hidden = false;
    if (!keepScroll) detail.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  document.getElementById('qna-detail-close').addEventListener('click', function () {
    detail.hidden = true;
    current = null;
  });

  answerInput.addEventListener('input', function () {
    answerCount.textContent = answerInput.value.length + ' / 2000';
  });

  answerForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    if (!current) return;
    var content = answerInput.value.trim();
    if (!content) {
      AdminUi.showMessage(alertBox, '답변 내용을 입력해 주세요.');
      answerInput.focus();
      return;
    }
    answerSubmit.disabled = true;
    try {
      var updated = await AdminUi.api('PUT', API + '/' + encodeURIComponent(current.qnaId) + '/answer',
        { content: content });
      current = updated;
      await filters.reload();
      openDetail(updated, true);
      AdminUi.showMessage(alertBox, '답변을 저장했습니다.', 'success');
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    } finally {
      answerSubmit.disabled = Boolean(current && current.deleted);
    }
  });

  hideButton.addEventListener('click', async function () {
    if (!current) return;
    var answer = await AdminUi.confirmAction({
      title: '문의 숨기기',
      message: '문의 #' + current.qnaId + '을(를) 숨깁니다. 작성자와 다른 이용자에게 더 이상 보이지 않습니다. (원본은 보관됩니다)',
      reasonLabel: '숨김 사유',
      reasonRequired: true,
      confirmLabel: '숨기기',
      danger: true
    });
    if (!answer.confirmed) return;
    hideButton.disabled = true;
    try {
      var updated = await AdminUi.api('PATCH', API + '/' + encodeURIComponent(current.qnaId) + '/hide',
        { reason: answer.reason });
      current = updated;
      await filters.reload();
      openDetail(updated, true);
      AdminUi.showMessage(alertBox, '문의를 숨겼습니다.', 'success');
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    } finally {
      hideButton.disabled = false;
    }
  });
})();
