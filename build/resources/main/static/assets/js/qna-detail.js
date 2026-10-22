/*
 * Q&A 상세 화면
 * 현재는 sessionStorage의 mock 데이터를 사용한다.
 *
 * 실제 API 연동 시:
 * GET /api/v1/qna/{qnaId}
 * 수정/삭제 권한은 서버에서 다시 검증해야 한다.
 */

(function () {
  'use strict';


  Ui.renderHeader();


  var params =
    new URLSearchParams(
      window.location.search
    );

  var qnaId =
    params.get('id');


  var inquiry =
    null;


  try {

    inquiry =
      JSON.parse(
        sessionStorage.getItem(
          'staybookSelectedInquiry'
        )
      );

  } catch (e) {

    inquiry = null;
  }


  var CATEGORY_LABELS = {
    booking: '예약',
    payment: '결제',
    refund: '취소·환불',
    account: '회원',
    etc: '기타'
  };


  /* 잘못된 접근 */
  if (
    !inquiry ||
    String(inquiry.qnaId) !==
      String(qnaId)
  ) {

    document.getElementById(
      'detail-title'
    ).textContent =
      '문의 정보를 찾을 수 없습니다.';

    document.getElementById(
      'qna-edit-button'
    ).hidden = true;

    document.getElementById(
      'qna-delete-button'
    ).hidden = true;

    return;
  }


  /* 상세 데이터 표시 */

  document.getElementById(
    'detail-category'
  ).textContent =
    CATEGORY_LABELS[inquiry.category]
    || '기타';


  document.getElementById(
    'detail-status'
  ).textContent =
    inquiry.answered
      ? '답변 완료'
      : '답변 대기';


  document.getElementById(
    'detail-secret'
  ).textContent =
    inquiry.secret
      ? '비밀글'
      : '';


  document.getElementById(
    'detail-title'
  ).textContent =
    inquiry.title;


  document.getElementById(
    'detail-date'
  ).textContent =
    inquiry.createdAt;


  document.getElementById(
    'detail-content'
  ).textContent =
    inquiry.content || '내용이 없습니다.';


    var editButton =
  document.getElementById(
    'qna-edit-button'
  );

var deleteButton =
  document.getElementById(
    'qna-delete-button'
  );

var answerBox =
  document.getElementById(
    'qna-answer-box'
  );

var answerText =
  document.getElementById(
    'detail-answer'
  );


  /* ---------- 접근 권한 ---------- */

/* 남이 작성한 글이면 수정/삭제 숨김 */
if (inquiry.owned !== true) {
  editButton.hidden = true;
  deleteButton.hidden = true;
}


/* 남이 작성한 비밀글이면 내용 숨김 */
if (
  inquiry.secret === true &&
  inquiry.owned === false
) {

  document.getElementById(
    'detail-content'
  ).textContent =
    '비밀글입니다. 작성자와 관리자만 내용을 확인할 수 있습니다.';

}


/* 관리자 답변 표시 */
if (
  inquiry.answered === true &&
  inquiry.answer &&
  !(
    inquiry.secret === true &&
    inquiry.owned === false
  )
) {

  answerBox.hidden = false;

  answerText.textContent =
    inquiry.answer;
}

  /* ---------- 수정 ---------- */

  document.getElementById(
    'qna-edit-button'
  ).addEventListener(
    'click',
    function () {

      sessionStorage.setItem(
        'staybookEditInquiry',
        JSON.stringify(inquiry)
      );

      window.location.href =
        '/qna/write.html?edit='
        + inquiry.qnaId;
    }
  );


  /* ---------- 삭제 ---------- */

  document.getElementById(
    'qna-delete-button'
  ).addEventListener(
    'click',
    function () {

      var confirmed =
        confirm(
          '이 문의를 삭제하시겠습니까?'
        );

      if (!confirmed) {
        return;
      }


      /*
       * 실제 API 연동 시:
       * Q&A 삭제 API 호출
       */


 var deletedIds = [];

try {

  deletedIds =
    JSON.parse(
      sessionStorage.getItem(
        'staybookDeletedInquiryIds'
      )
    );

  if (!Array.isArray(deletedIds)) {
    deletedIds = [];
  }

} catch (e) {

  deletedIds = [];
}


if (
  deletedIds.indexOf(
    String(inquiry.qnaId)
  ) === -1
) {

  deletedIds.push(
    String(inquiry.qnaId)
  );
}


sessionStorage.setItem(
  'staybookDeletedInquiryIds',
  JSON.stringify(deletedIds)
);


sessionStorage.removeItem(
  'staybookSelectedInquiry'
);


window.location.href =
  '/qna.html?inquiry=deleted';
    }
  );

})();