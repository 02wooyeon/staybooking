/* 관리자 대시보드: 관리자만 사용. 운영 통계(GET /api/v1/admin/statistics/*)는 다음 단계에서 붙인다 */
(function () {
  'use strict';

  AdminUi.init({ page: 'dashboard', roles: ['ROLE_ADMIN'] });
})();
