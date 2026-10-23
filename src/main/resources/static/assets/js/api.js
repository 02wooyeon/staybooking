/*
 * 인증 API 공통 모듈.
 * - Access Token: 탭별 sessionStorage에 보관하고 Authorization 헤더로 전송
 * - Refresh Token: 서버가 HttpOnly 쿠키(path=/api/v1/auth)로만 관리하므로 JS는 손대지 않음
 * - API는 Nginx가 같은 도메인의 /api/ 를 Tomcat으로 넘겨 주므로 CORS 없이 호출
 */
(function () {
  'use strict';

  var TOKEN_KEY = 'accessToken';
  var EXPIRES_KEY = 'accessTokenExpiresAt';
  var DEFAULT_ERROR = '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';

  function ApiError(status, code, message) {
    this.status = status;
    this.code = code;
    this.message = message || DEFAULT_ERROR;
  }

  function saveToken(body) {
    try {
      sessionStorage.setItem(TOKEN_KEY, body.accessToken);
      // 만료 30초 전부터는 만료된 것으로 보고 재발급
      sessionStorage.setItem(EXPIRES_KEY, String(Date.now() + (body.expiresIn - 30) * 1000));
    } catch (e) { /* 저장소를 못 쓰면 매 요청마다 재발급으로 동작 */ }
  }

  function clearToken() {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(EXPIRES_KEY);
    } catch (e) { /* 무시 */ }
  }

  function getToken() {
    try {
      var token = sessionStorage.getItem(TOKEN_KEY);
      var expiresAt = Number(sessionStorage.getItem(EXPIRES_KEY));
      return token && Date.now() < expiresAt ? token : null;
    } catch (e) {
      return null;
    }
  }

  async function request(method, url, options) {
    options = options || {};
    var headers = { Accept: 'application/json' };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (options.token) headers.Authorization = 'Bearer ' + options.token;

    var res;
    try {
      res = await fetch(url, {
        method: method,
        headers: headers,
        body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
        credentials: 'same-origin',
        cache: 'no-store'
      });
    } catch (e) {
      throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.');
    }

    if (res.status === 204) return null;
    var data = null;
    try { data = await res.json(); } catch (e) { /* 본문 없음 */ }

    if (!res.ok) {
      throw new ApiError(res.status, data && data.code, data && data.message);
    }
    return data;
  }

  /*
   * Refresh Token Rotation 때문에 같은 쿠키로 재발급을 두 번 보내면 서버가 탈취로 보고
   * 모든 토큰을 폐기한다. 그래서 한 페이지 안에서는 진행 중인 재발급 요청을 공유하고,
   * 여러 탭 사이에서는 Web Locks API(HTTPS에서만 동작)로 순서대로 실행한다.
   */
  var reissuing = null;
  function reissue() {
    if (!reissuing) {
      var run = function () {
        return request('POST', '/api/v1/auth/reissue').then(function (body) {
          saveToken(body);
          return body.accessToken;
        });
      };
      var task = navigator.locks ? navigator.locks.request('booking-reissue', run) : run();
      reissuing = task.finally(function () { reissuing = null; });
    }
    return reissuing;
  }

  async function ensureToken() {
    var token = getToken();
    if (token) return token;
    try {
      return await reissue();
    } catch (e) {
      clearToken();
      return null;
    }
  }

  /** 로그인이 필요한 API 호출. 401이면 한 번 재발급 후 재시도 */
  async function authed(method, url, body) {
    var token = await ensureToken();
    if (!token) throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요합니다.');
    try {
      return await request(method, url, { token: token, body: body });
    } catch (e) {
      if (e.status !== 401) throw e;
      clearToken();
      token = await ensureToken();
      if (!token) throw e;
      return request(method, url, { token: token, body: body });
    }
  }

  /**
   * 로그인 후 돌아갈 주소. 같은 사이트의 상대 경로만 허용해
   * ?next=https://evil.example 같은 오픈 리다이렉트를 막는다.
   */
  function safeNext(value) {
    if (!value || value.charAt(0) !== '/' || value.charAt(1) === '/' || value.charAt(1) === '\\') {
      return '/';
    }
    try {
      var url = new URL(value, window.location.origin);
      return url.origin === window.location.origin ? url.pathname + url.search + url.hash : '/';
    } catch (e) {
      return '/';
    }
  }

  window.BookingApi = {
    ApiError: ApiError,
    safeNext: safeNext,

    checkEmail: function (email) {
      return request('GET', '/api/v1/auth/check-email?email=' + encodeURIComponent(email));
    },

    signup: function (form) {
      return request('POST', '/api/v1/auth/signup', { body: form });
    },

    login: async function (email, password) {
      var body = await request('POST', '/api/v1/auth/login', { body: { email: email, password: password } });
      saveToken(body);
      return body;
    },

    logout: async function () {
      try {
        await request('POST', '/api/v1/auth/logout');
      } finally {
        clearToken();
      }
    },

    /** 로그인 상태면 내 정보, 아니면 null */
    me: async function () {
      try {
        return await authed('GET', '/api/v1/members/me');
      } catch (e) {
        if (e.status === 401) return null;
        throw e;
      }
    },

    /** 로그인한 사용자의 예약 목록 */
    getMyBookings: function () {
      return authed('GET', '/api/v1/bookings/me');
    },

    getMyBooking: function (bookingId) {
      return authed('GET', '/api/v1/bookings/' + encodeURIComponent(bookingId));
    },

    getAccommodation: function (accommodationId, search) {
      var query = search ? '?' + search.toString() : '';
      return request('GET', '/api/v1/accommodations/' + encodeURIComponent(accommodationId) + query);
    },

    createBooking: function (form) {
      return authed('POST', '/api/v1/bookings', form);
    },

    getPaymentCapabilities: function () {
      return authed('GET', '/api/v1/payments/capabilities');
    },

    getPaidPaymentsForBooking: function (bookingId) {
      return authed('GET', '/api/v1/payments/me/search?bookingId='
        + encodeURIComponent(bookingId) + '&status=PAID&size=2');
    },

    completeDemoPayment: function (bookingId) {
      return authed('POST', '/api/v1/payments/demo-complete', { bookingId: bookingId });
    },

    preparePayment: function (bookingId, paymentMethod) {
      return authed('POST', '/api/v1/payments/prepare', {
        bookingId: bookingId, paymentMethod: paymentMethod
      });
    },

    confirmTossPayment: function (orderId, paymentKey, amount) {
      return authed('POST', '/api/v1/payments/toss/confirm', {
        orderId: orderId, paymentKey: paymentKey, amount: amount
      });
    },

    recoverTossPayment: function (orderId) {
      return authed('POST', '/api/v1/payments/toss/recover', { orderId: orderId });
    },

    /** 사용자는 예약을 즉시 취소하지 않고 승인자에게 취소를 신청한다. */
    requestBookingCancellation: function (bookingId, form) {
      return authed('POST', '/api/v1/bookings/' + encodeURIComponent(bookingId)
        + '/cancellation-requests', form);
    },

    getPublicQna: function () {
      return request('GET', '/api/v1/qna/public');
    },

    getMyQna: function () {
      return authed('GET', '/api/v1/qna/mine');
    },

    getQna: async function (qnaId) {
      var token = await ensureToken();
      return request('GET', '/api/v1/qna/' + encodeURIComponent(qnaId), { token: token });
    },

    createQna: function (form) {
      return authed('POST', '/api/v1/qna', form);
    },

    updateQna: function (qnaId, form) {
      return authed('PUT', '/api/v1/qna/' + encodeURIComponent(qnaId), form);
    },

    deleteQna: function (qnaId) {
      return authed('DELETE', '/api/v1/qna/' + encodeURIComponent(qnaId));
    }
  };
})();
