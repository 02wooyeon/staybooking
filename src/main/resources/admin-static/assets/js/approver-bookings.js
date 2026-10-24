/*
 * 예약 승인 담당자 (APPROVER_001)
 *
 * GET
 *   /api/v1/approver/bookings?status=PENDING
 *
 * PATCH
 *   /api/v1/approver/bookings/{bookingId}
 *
 * 승인:
 *   { action: 'ACCEPT' }
 *
 * 취소:
 *   {
 *     action: 'CANCEL',
 *     reasonType: '...',
 *     reason: '...'
 *   }
 *
 * ROLE_APPROVER와 ROLE_ADMIN이 사용할 수 있다.
 */
(function () {
  'use strict';

  var API = '/api/v1/approver/bookings';

  var alertBox = document.getElementById('page-alert');
  var tbody = document.getElementById('list-body');
  var filterForm = document.getElementById('filter-form');

  /*
   * 예약 직접 취소 시 사용할 취소 사유 목록
   */
  var REASON_CHOICES =
    Object.keys(AdminUi.LABELS.cancelReason).map(function (value) {
      return {
        value: value,
        label: AdminUi.LABELS.cancelReason[value]
      };
    });


  /*
   * 승인자 또는 관리자인지 확인한다.
   */
  AdminUi.init({
    page: 'approver-bookings',
    roles: ['ROLE_APPROVER', 'ROLE_ADMIN']
  }).then(function (me) {
    if (!me) return;

    /*
     * 처음 들어오면 승인 대기(PENDING) 예약 조회
     */
    load('PENDING');
  });


  /*
   * 상태 검색
   */
  filterForm.addEventListener('submit', function (event) {
    event.preventDefault();

    var status =
      filterForm.elements.status.value || 'PENDING';

    load(status);
  });


  /*
   * 예약 목록 조회
   *
   * 승인자 API는 PageResponse가 아니라
   * List<ApproverBookingResponse>를 반환한다.
   */
  async function load(status) {
    AdminUi.hideMessage(alertBox);

    try {
      var list = await AdminUi.api(
        'GET',
        API + AdminUi.buildQuery({
          status: status
        })
      );

      render(list || []);

    } catch (e) {
      AdminUi.showMessage(
        alertBox,
        AdminUi.errorMessage(e)
      );
    }
  }


  /*
   * 예약 목록 출력
   */
  function render(list) {
    AdminUi.renderTable(
      tbody,
      list,
      [
        {
          label: '예약 ID',
          render: function (r) {
            return r.booking.bookingId;
          }
        },

        {
          label: '숙소 · 객실',
          className: 'admin-wrap',
          render: function (r) {
            return stack(
              r.booking.accommodationName,
              r.booking.roomName
            );
          }
        },

        {
          label: '일정',
          render: function (r) {
            return (
              r.booking.checkIn +
              ' ~ ' +
              r.booking.checkOut
            );
          }
        },

        {
          label: '인원',
          render: function (r) {
            return r.booking.guests + '명';
          }
        },

        {
          label: '금액',
          render: function (r) {
            return AdminUi.formatPrice(
              r.booking.totalAmount
            );
          }
        },

        {
          label: '상태',
          render: function (r) {
            var nodes = [];

            nodes.push(
              AdminUi.badge(
                'bookingStatus',
                r.booking.status
              )
            );

            /*
             * 사용자가 취소 신청한 예약이면
             * 상태 옆에 표시만 해 준다.
             */
            if (
              r.cancellationRequest ||
              r.booking.cancellationRequested
            ) {
              nodes.push(
                AdminUi.statusBadge(
                  'CANCEL_REQUEST',
                  {
                    CANCEL_REQUEST: '취소 신청'
                  },
                  {
                    CANCEL_REQUEST: 'warn'
                  }
                )
              );
            }

            /*
             * 결제 대기 상태인 경우
             * 결제 기한 표시
             */
            if (
              r.booking.status === 'PAYMENT_PENDING' &&
              r.booking.paymentExpiresAt
            ) {
              var due =
                document.createElement('small');

              due.className =
                'admin-cell-sub';

              due.textContent =
                '결제 기한 ' +
                AdminUi.formatDateTime(
                  r.booking.paymentExpiresAt
                );

              nodes.push(due);
            }

            return AdminUi.group(
              nodes,
              'admin-badges'
            );
          }
        },

        {
          label: '처리',
          render: actions
        }
      ],

      '해당 상태의 예약이 없습니다.'
    );
  }


  /*
   * 숙소명 / 객실명을 위아래로 표시
   */
  function stack(main, sub) {
    var wrap =
      document.createElement('span');

    wrap.className = 'admin-stack';


    var strong =
      document.createElement('strong');

    strong.textContent = main || '-';


    var small =
      document.createElement('small');

    small.className =
      'admin-cell-sub';

    small.textContent = sub || '';


    wrap.append(
      strong,
      small
    );

    return wrap;
  }


  /*
   * 예약 상태에 따라 처리 버튼 생성
   */
  function actions(r) {
    var booking = r.booking;

    var buttons = [];


    /*
     * PENDING 예약만 승인 가능
     */
    if (booking.status === 'PENDING') {
      buttons.push(
        AdminUi.actionButton(
          '승인',
          function (button) {
            accept(
              booking,
              button
            );
          },
          'primary'
        )
      );
    }


    /*
     * 이미 취소된 예약에는
     * 취소 버튼을 표시하지 않는다.
     */
    if (booking.status !== 'CANCELLED') {
      buttons.push(
        AdminUi.actionButton(
          '취소',
          function (button) {
            cancel(
              booking,
              button
            );
          },
          'danger'
        )
      );
    }


    if (buttons.length === 0) {
      return '-';
    }


    return AdminUi.group(buttons);
  }


  /*
   * 확인창에 표시할 예약 정보
   */
  function describe(booking) {
    return (
      '예약 #' +
      booking.bookingId +
      ' (' +
      (booking.accommodationName || '') +
      ' · ' +
      booking.checkIn +
      ' ~ ' +
      booking.checkOut +
      ')'
    );
  }


  /*
   * 예약 승인
   */
  async function accept(booking, button) {
    var answer =
      await AdminUi.confirmAction({
        title: '예약 승인',

        message:
          describe(booking) +
          '을(를) 승인합니다.',

        confirmLabel: '승인'
      });


    if (!answer.confirmed) {
      return;
    }


    send(
      booking.bookingId,
      {
        action: 'ACCEPT'
      },
      '예약을 승인했습니다.',
      button
    );
  }


  /*
   * 예약 직접 취소
   */
  async function cancel(booking, button) {
    var answer =
      await AdminUi.confirmAction({
        title: '예약 취소',

        message:
          describe(booking) +
          '을(를) 취소합니다.',

        choiceLabel:
          '취소 사유 유형',

        choices:
          REASON_CHOICES,

        reasonLabel:
          '취소 사유',

        reasonRequired:
          true,

        confirmLabel:
          '예약 취소',

        danger:
          true
      });


    if (!answer.confirmed) {
      return;
    }


    send(
      booking.bookingId,

      {
        action: 'CANCEL',
        reasonType: answer.choice,
        reason: answer.reason
      },

      '예약을 취소했습니다.',

      button
    );
  }


  /*
   * 승인/취소 API 호출
   */
  async function send(
    bookingId,
    body,
    successMessage,
    button
  ) {
    button.disabled = true;


    try {
      await AdminUi.api(
        'PATCH',

        API +
          '/' +
          encodeURIComponent(
            bookingId
          ),

        body
      );


      /*
       * 현재 선택된 상태를 다시 조회해서
       * 변경 결과 반영
       */
      var status =
        filterForm.elements.status.value ||
        'PENDING';

      await load(status);


      AdminUi.showMessage(
        alertBox,
        successMessage,
        'success'
      );


    } catch (e) {
      button.disabled = false;


      /*
       * 서버에서 처리 자체는 됐지만
       * 응답 생성 과정에서 문제가 생겼을 수도 있으므로
       * 목록을 다시 조회한다.
       */
      if (e.status >= 500) {

        var currentStatus =
          filterForm.elements.status.value ||
          'PENDING';

        await load(currentStatus);


        AdminUi.showMessage(
          alertBox,
          '처리 결과를 확인하지 못했습니다. 목록에서 예약 상태를 확인해 주세요.',
          'warning'
        );

        return;
      }


      AdminUi.showMessage(
        alertBox,
        AdminUi.errorMessage(e)
      );
    }
  }

})();