/*
 * 리뷰 관리 (ADMIN_006)
 *
 * GET    /api/v1/admin/reviews?page=&size=
 * DELETE /api/v1/admin/reviews/{reviewId}?reason=
 *
 * 관리자 화면에서는 전체 리뷰를 조회하고,
 * 부적절한 리뷰를 사유와 함께 숨김 처리한다.
 */
(function () {
  'use strict';

  var API = '/api/v1/admin/reviews';

  var alertBox = document.getElementById('page-alert');
  var tbody = document.getElementById('list-body');
  var pager = document.getElementById('pagination');

  var detail = document.getElementById('review-detail');
  var detailTitle = document.getElementById('review-detail-title');
  var detailMeta = document.getElementById('review-detail-meta');
  var detailBadges = document.getElementById('review-detail-badges');
  var detailContent = document.getElementById('review-detail-content');
  var detailHidden = document.getElementById('review-detail-hidden');

  var closeButton = document.getElementById('review-detail-close');
  var hideButton = document.getElementById('review-hide');

  var current = null;
  var currentPage = 0;


  /*
   * 관리자 권한 확인 후 첫 페이지 조회
   */
  AdminUi.init({
    page: 'reviews',
    roles: ['ROLE_ADMIN']
  }).then(function (me) {
    if (!me) return;

    load(0);
  });


  /*
   * 리뷰 목록 조회
   */
  async function load(pageNumber) {
    AdminUi.hideMessage(alertBox);

    try {
      currentPage = pageNumber;

      var page = await AdminUi.api(
        'GET',
        API + AdminUi.buildQuery({
          page: pageNumber,
          size: AdminUi.DEFAULT_PAGE_SIZE
        })
      );

      render(page);

    } catch (e) {
      AdminUi.showMessage(
        alertBox,
        AdminUi.errorMessage(e)
      );
    }
  }


  /*
   * 리뷰 상태 배지 생성
   */
  function badges(review) {
    var nodes = [];

    if (review.deleted) {
      nodes.push(
        AdminUi.statusBadge(
          'D',
          { D: '숨김' },
          { D: 'error' }
        )
      );
    } else {
      nodes.push(
        AdminUi.statusBadge(
          'N',
          { N: '공개' },
          { N: 'ok' }
        )
      );
    }

    return AdminUi.group(
      nodes,
      'admin-badges'
    );
  }


  /*
   * 리뷰 목록 출력
   */
  function render(page) {
    AdminUi.renderTable(
      tbody,
      page.content || [],
      [
        {
          label: '리뷰 ID',
          render: function (review) {
            return review.reviewId;
          }
        },

        {
          label: '숙소',
          className: 'admin-wrap',
          render: function (review) {
            return review.accommodationName || '-';
          }
        },

        {
          label: '작성자',
          render: function (review) {
            if (review.author) {
              return review.author;
            }

            if (review.userId) {
              return '회원 #' + review.userId;
            }

            return '-';
          }
        },

        {
          label: '평점',
          render: function (review) {
            return review.rating != null
              ? review.rating + '점'
              : '-';
          }
        },

        {
          label: '내용',
          className: 'admin-wrap',
          render: function (review) {
            return review.content || '-';
          }
        },

        {
          label: '작성일',
          render: function (review) {
            return review.createdAt
              ? AdminUi.formatDateTime(review.createdAt)
              : '-';
          }
        },

        {
          label: '상태',
          render: badges
        },

        {
          label: '관리',
          render: function (review) {
            return AdminUi.actionButton(
              '보기',
              function () {
                openDetail(review);
              }
            );
          }
        }
      ],
      '등록된 리뷰가 없습니다.'
    );


    /*
     * 페이지네이션
     */
    AdminUi.renderPagination(
      pager,
      page,
      function (pageNumber) {
        load(pageNumber);
      }
    );


    /*
     * 상세창이 열려 있는 상태에서
     * 목록이 갱신되면 최신 데이터로 다시 표시
     */
    if (current) {
      var fresh = (page.content || []).filter(
        function (review) {
          return review.reviewId === current.reviewId;
        }
      )[0];

      if (fresh) {
        openDetail(fresh, true);
      }
    }
  }


  /*
   * 리뷰 상세 열기
   */
  function openDetail(review, keepScroll) {
    current = review;

    detailTitle.textContent =
      '리뷰 #' + review.reviewId;


    /*
     * 상세 상단 정보
     */
    var meta = [];

    if (review.accommodationName) {
      meta.push(review.accommodationName);
    }

    if (review.accommodationId) {
      meta.push(
        '숙소 #' + review.accommodationId
      );
    }

    if (review.bookingId) {
      meta.push(
        '예약 #' + review.bookingId
      );
    }

    if (review.author) {
      meta.push(
        '작성자 ' + review.author
      );
    } else if (review.userId) {
      meta.push(
        '작성자 ID ' + review.userId
      );
    }

    if (review.rating != null) {
      meta.push(
        '평점 ' + review.rating + '점'
      );
    }

    if (review.createdAt) {
      meta.push(
        AdminUi.formatDateTime(
          review.createdAt
        )
      );
    }

    detailMeta.textContent =
      meta.join(' · ');


    /*
     * 상태 배지
     */
    detailBadges.textContent = '';
    detailBadges.append(
      badges(review)
    );


    /*
     * 리뷰 내용
     *
     * 사용자 입력값이므로 innerHTML을 사용하지 않고
     * textContent로 넣는다.
     */
    detailContent.textContent =
      review.content || '';


    /*
     * 이미 숨김 처리된 경우
     * 숨김 사유 표시
     */
    if (review.deleted) {
      detailHidden.hidden = false;

      detailHidden.textContent =
        '숨김 사유: ' +
        (review.deletedReason || '-');

      hideButton.hidden = true;

    } else {
      detailHidden.hidden = true;
      detailHidden.textContent = '';

      hideButton.hidden = false;
    }


    detail.hidden = false;


    /*
     * 사용자가 직접 상세를 열었을 때만
     * 해당 위치로 이동
     */
    if (!keepScroll) {
      detail.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
    }
  }


  /*
   * 상세 닫기
   */
  closeButton.addEventListener(
    'click',
    function () {
      detail.hidden = true;
      current = null;
    }
  );


  /*
   * 관리자 리뷰 숨김
   */
  hideButton.addEventListener(
    'click',
    async function () {
      if (!current) return;


      /*
       * 숨김 사유 입력 창
       */
      var answer =
        await AdminUi.confirmAction({
          title: '리뷰 숨기기',

          message:
            '리뷰 #' +
            current.reviewId +
            '을(를) 숨깁니다. ' +
            '원본 데이터는 삭제하지 않고 보관됩니다.',

          reasonLabel: '숨김 사유',

          reasonRequired: true,

          confirmLabel: '숨기기',

          danger: true
        });


      if (!answer.confirmed) {
        return;
      }


      /*
       * 백엔드에서 reason은 최대 255자
       */
      if (
        !answer.reason ||
        !answer.reason.trim()
      ) {
        AdminUi.showMessage(
          alertBox,
          '숨김 사유를 입력해 주세요.'
        );
        return;
      }


      if (
        answer.reason.trim().length > 255
      ) {
        AdminUi.showMessage(
          alertBox,
          '숨김 사유는 255자 이하로 입력해 주세요.'
        );
        return;
      }


      hideButton.disabled = true;


      try {
        /*
         * DELETE
         * /api/v1/admin/reviews/{reviewId}?reason=...
         */
        var updated =
          await AdminUi.api(
            'DELETE',

            API +
              '/' +
              encodeURIComponent(
                current.reviewId
              ) +
              AdminUi.buildQuery({
                reason:
                  answer.reason.trim()
              })
          );


        /*
         * 서버에서 반환한 최신 리뷰 상태 저장
         */
        current = updated;


        AdminUi.showMessage(
          alertBox,
          '리뷰를 숨겼습니다.',
          'success'
        );


        /*
         * 현재 페이지를 다시 읽어
         * 목록 상태도 최신화
         */
        await load(currentPage);


      } catch (e) {
        AdminUi.showMessage(
          alertBox,
          AdminUi.errorMessage(e)
        );

      } finally {
        hideButton.disabled = false;
      }
    }
  );

})();
