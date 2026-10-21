/* 고객센터 화면: 자주 묻는 질문 분류 필터, 최근 내 문의 */
(function () {
  'use strict';

  var USE_MOCK_DATA = true;

  /*
   * 백엔드 연동 전 mock 데이터
   *
   * 실제 API:
   * GET /api/v1/members/me/activities (내 Q&A)
   */
  var MOCK_INQUIRIES = [
  {
    qnaId: 12,
    category: 'booking',
    title: '체크인 시간 변경 가능한가요?',
    content: '체크인 시간을 오후 3시에서 오후 5시로 변경하고 싶습니다.',
    secret: true,
    answered: false,
    createdAt: '2026-10-18',
    owned: true
  },

  {
    qnaId: 9,
    category: 'refund',
    title: '환불 금액이 예상과 달라요',
    content: '예약 취소 후 안내받은 환불 금액과 실제 금액이 달라 문의드립니다.',
    secret: false,
    answered: true,
    createdAt: '2026-10-12',
    owned: true
  }
];

var MOCK_PUBLIC_QNA = [
  {
    qnaId: 201,
    category: 'booking',
    title: '체크인 전에 짐 보관 가능한가요?',
    content: '체크인 시간보다 일찍 도착할 예정인데 짐을 먼저 맡길 수 있는지 궁금합니다.',
    secret: false,
    answered: true,
    createdAt: '2026-10-20',
    owned: false,
    answer: '체크인 전 짐 보관은 가능합니다. 프런트에 예약자 성함을 말씀해 주세요.'
  },

  {
    qnaId: 202,
    category: 'payment',
    title: '카드 결제 관련 문의입니다.',
    content: '결제 과정에서 카드 승인 오류가 발생해 문의드립니다.',
    secret: true,
    answered: false,
    createdAt: '2026-10-19',
    owned: false
  },

  {
    qnaId: 203,
    category: 'refund',
    title: '취소 수수료 기준이 궁금합니다.',
    content: '체크인 3일 전에 취소하면 수수료가 어떻게 적용되는지 알고 싶습니다.',
    secret: false,
    answered: true,
    createdAt: '2026-10-17',
    owned: false,
    answer: '취소 수수료는 숙소별 환불 규정에 따라 다르며 예약 상세 화면에서 확인할 수 있습니다.'
  }
];

  var RECENT_LIMIT = 3;
  var CATEGORY_LABELS = { booking: '예약', payment: '결제', refund: '취소·환불', account: '회원', etc: '기타' };

  Ui.renderHeader();

  /* ---------- 자주 묻는 질문 ---------- */

  var tabs = document.querySelectorAll('.faq-tab');
  var items = document.querySelectorAll('#faq-list details');

  tabs.forEach(function (tab) {
    tab.addEventListener('click', function () {
      var category = tab.dataset.category;
      tabs.forEach(function (t) {
        t.setAttribute('aria-pressed', t === tab ? 'true' : 'false');
      });
      items.forEach(function (item) {
        item.hidden = category !== 'all' && item.dataset.category !== category;
      });
    });
  });

  /* ---------- 최근 내 문의 ---------- */

  var recentList = document.getElementById('inquiry-recent');
  var recentEmpty = document.getElementById('inquiry-empty');

  // 연동 전: 1:1 문의 작성 화면에서 등록한 글은 이 탭의 sessionStorage에 임시 보관된다
  function loadDrafts() {
    try {
      var stored = JSON.parse(sessionStorage.getItem('staybookMockInquiries'));
      return Array.isArray(stored) ? stored : [];
    } catch (e) {
      return [];
    }
  }

  function renderRecent(inquiries) {
    recentList.textContent = '';
    inquiries.slice(0, RECENT_LIMIT).forEach(function (q) {
      var li = document.createElement('li');

      var info = document.createElement('div');
      var title = document.createElement('a');

      title.className = 'inquiry-title';

      title.href ='/qna/detail.html?id=' + q.qnaId;

      title.textContent = q.title;

      title.addEventListener(
  'click',
  function () {

    sessionStorage.setItem(
      'staybookSelectedInquiry',
      JSON.stringify(q)
    );
  }
);


      if (q.secret) {
        var lock = document.createElement('span');
        lock.className = 'inquiry-lock';
        lock.textContent = '비밀글';
        title.append(' ', lock);
      }
      var meta = document.createElement('p');
      meta.className = 'inquiry-meta';
      meta.textContent = q.createdAt + ' · ' + (CATEGORY_LABELS[q.category] || '기타');
      info.append(title, meta);

      var badge = document.createElement('span');
      badge.className = 'inquiry-badge ' + (q.answered ? 'done' : 'wait');
      badge.textContent = q.answered ? '답변 완료' : '답변 대기';



li.append(
  info,
  badge,
);

recentList.append(li);
    });
    recentEmpty.hidden = inquiries.length > 0;
  }


  function renderPublicQna(inquiries) {

  var publicList =
    document.getElementById(
      'public-qna-list'
    );

  var publicEmpty =
    document.getElementById(
      'public-qna-empty'
    );


  publicList.textContent = '';


  inquiries.forEach(
    function (q) {

      var li =
        document.createElement('li');


      var info =
        document.createElement('div');


      var title =
        document.createElement('a');

      title.className =
        'inquiry-title';

      title.href =
        '/qna/detail.html?id='
        + q.qnaId;

      title.textContent =
        q.title;


      title.addEventListener(
        'click',
        function () {

          sessionStorage.setItem(
            'staybookSelectedInquiry',
            JSON.stringify(q)
          );
        }
      );


      if (q.secret) {

        var lock =
          document.createElement('span');

        lock.className =
          'inquiry-lock';

        lock.textContent =
          '비밀글';

        title.append(
          ' ',
          lock
        );
      }


      var meta =
        document.createElement('p');

      meta.className =
        'inquiry-meta';

      meta.textContent =
        q.createdAt
        + ' · '
        + (
          CATEGORY_LABELS[q.category]
          || '기타'
        );


      info.append(
        title,
        meta
      );


      var badge =
        document.createElement('span');

      badge.className =
        'inquiry-badge '
        + (
          q.answered
            ? 'done'
            : 'wait'
        );

      badge.textContent =
        q.answered
          ? '답변 완료'
          : '답변 대기';


      li.append(
        info,
        badge
      );


      publicList.append(
        li
      );
    }
  );


  publicEmpty.hidden =
    inquiries.length > 0;
}

  function applyEditedInquiry(inquiries) {

  var edited;

  try {

    edited =
      JSON.parse(
        sessionStorage.getItem(
          'staybookEditInquiry'
        )
      );

  } catch (e) {

    edited = null;
  }

  if (!edited) {
    return inquiries;
  }

  return inquiries.map(
    function (q) {

      if (
        String(q.qnaId) ===
        String(edited.qnaId)
      ) {

        return Object.assign(
          {},
          q,
          edited
        );
      }

      return q;
    }
  );
}

function removeDeletedInquiries(inquiries) {

  var deletedIds;

  try {

    deletedIds =
      JSON.parse(
        sessionStorage.getItem(
          'staybookDeletedInquiryIds'
        )
      );

  } catch (e) {

    deletedIds = [];
  }


  if (!Array.isArray(deletedIds)) {
    deletedIds = [];
  }


  return inquiries.filter(
    function (q) {

      return deletedIds.indexOf(
        String(q.qnaId)
      ) === -1;
    }
  );
}

 var inquiries =
  loadDrafts().concat(
    MOCK_INQUIRIES
  );

inquiries =
  applyEditedInquiry(
    inquiries
  );

inquiries =
  removeDeletedInquiries(
    inquiries
  );

renderRecent(
  USE_MOCK_DATA
    ? inquiries
    : []
);

/* 공개 Q&A 렌더링 */
renderPublicQna(
  MOCK_PUBLIC_QNA
);


  // 1:1 문의 등록 직후
  if (new URLSearchParams(window.location.search).get('inquiry') === 'done') {

    if (
  new URLSearchParams(
    window.location.search
  ).get('inquiry') === 'updated'
) {

  Ui.showAlert(
    document.getElementById(
      'qna-alert'
    ),
    '문의가 수정되었습니다.',
    'success'
  );

  window.history.replaceState(
    null,
    '',
    window.location.pathname
  );
}

    Ui.showAlert(document.getElementById('qna-alert'),
      '문의가 등록되었습니다. 답변이 등록되면 최근 내 문의에서 확인할 수 있습니다.', 'success');
    // 새로고침해도 알림이 다시 뜨지 않도록 주소에서 표시를 지운다
    window.history.replaceState(null, '', window.location.pathname);
  }

  if (
  new URLSearchParams(
    window.location.search
  ).get('inquiry') === 'deleted'
) {

  Ui.showAlert(
    document.getElementById(
      'qna-alert'
    ),
    '문의가 삭제되었습니다.',
    'success'
  );

  window.history.replaceState(
    null,
    '',
    window.location.pathname
  );
}

})();



