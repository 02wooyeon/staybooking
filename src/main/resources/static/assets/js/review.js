/*
 * 숙소 후기 (REVIEW_001 ~ REVIEW_004)
 *
 *   숙소 상세  /accommodation/detail.html?id=      후기 목록·평균 별점, 내 후기 삭제
 *   후기 작성  /accommodation/review.html?id=      새 후기 작성
 *   후기 수정  /accommodation/review.html?id=&reviewId=
 *
 * 현재는 백엔드 연동 전 mock 동작이며, 작성·수정·삭제한 후기는 이 탭의 sessionStorage에 남긴다.
 *
 * 실제 API 연동 시:
 * - 목록     GET    /api/v1/accommodations/{accommodationId}/reviews  (averageRating, reviewCount는 서버 값)
 * - 작성     POST   /api/v1/reviews        이용 완료(COMPLETED) 예약 보유 여부는 서버가 검증
 * - 수정     PUT    /api/v1/reviews/{reviewId}   작성자 본인 여부는 서버가 검증
 * - 삭제     DELETE /api/v1/reviews/{reviewId}
 * - 사진     POST   /api/v1/uploads/Images 로 올린 뒤 받은 파일 ID를 후기에 연결
 */

(function () {
  'use strict';

  var USE_MOCK_DATA = true;

  var STORE_KEY = 'staybookMockReviews';
  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp'];
  var ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];

  // 숙소 상세 mock과 같은 숙소 (후기 작성 화면 제목용)
  var MOCK_ACCOMMODATION_NAMES = {
    1: '스테이 강남',
    2: '서울 시티 호텔',
    3: '한강 스테이'
  };

  // 숙소마다 처음 보여 줄 mock 후기 (owned: 로그인한 회원이 쓴 후기)
  var MOCK_REVIEWS = [
    {
      reviewId: 2,
      author: '홍길동',
      rating: 5,
      content: '객실이 깨끗하고 위치가 좋아서 편하게 이용했습니다.',
      image: null,
      createdAt: '2026-10-12',
      owned: true
    },
    {
      reviewId: 1,
      author: '김스테이',
      rating: 4,
      content: '주변 교통이 편리하고 직원 응대가 좋았습니다.',
      image: null,
      createdAt: '2026-10-03',
      owned: false
    }
  ];

  var params = new URLSearchParams(window.location.search);
  var accommodationId = params.get('id');

  if (!/^[1-9]\d{0,18}$/.test(accommodationId || '')) {
    // 후기 작성 페이지를 숙소 번호 없이 연 경우
    var orphanForm = document.getElementById('review-form');
    if (orphanForm) {
      Ui.renderHeader();
      Ui.showAlert(document.getElementById('review-alert'), '잘못된 접근입니다. 숙소 화면에서 다시 시도해 주세요.', 'error');
      Array.from(orphanForm.elements).forEach(function (el) { el.disabled = true; });
    }
    return;
  }

  /* ---------- mock 저장소 ---------- */

  function loadAll() {
    try {
      var stored = JSON.parse(sessionStorage.getItem(STORE_KEY));
      return stored && typeof stored === 'object' ? stored : {};
    } catch (e) {
      return {};
    }
  }

  function loadReviews() {
    var all = loadAll();
    if (Array.isArray(all[accommodationId])) return all[accommodationId];
    return USE_MOCK_DATA ? MOCK_REVIEWS.slice() : [];
  }

  // 저장 공간이 부족하면(사진이 큰 경우) false
  function saveReviews(reviews) {
    var all = loadAll();
    all[accommodationId] = reviews;
    try {
      sessionStorage.setItem(STORE_KEY, JSON.stringify(all));
      return true;
    } catch (e) {
      return false;
    }
  }

  function today() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
      + '-' + String(d.getDate()).padStart(2, '0');
  }

  function makeStars(score) {
    return '★'.repeat(score) + '☆'.repeat(5 - score);
  }

  function detailUrl() {
    return '/accommodation/detail.html?id=' + encodeURIComponent(accommodationId);
  }

  function reviewPageUrl(reviewId) {
    return '/accommodation/review.html?id=' + encodeURIComponent(accommodationId)
      + (reviewId ? '&reviewId=' + encodeURIComponent(reviewId) : '');
  }

  if (document.getElementById('review-list')) {
    initList();
  } else if (document.getElementById('review-form')) {
    initForm();
  }

  /* ---------- 숙소 상세: 후기 목록 ---------- */

  function initList() {
    var list = document.getElementById('review-list');
    var empty = document.getElementById('review-empty');
    var notice = document.getElementById('review-notice');

    document.getElementById('review-write-button').href = reviewPageUrl(null);

    function render() {
      var reviews = loadReviews();
      list.textContent = '';

      reviews.forEach(function (review) {
        var article = document.createElement('article');
        article.className = 'review-item';

        var header = document.createElement('div');
        header.className = 'review-item-header';
        var name = document.createElement('strong');
        name.textContent = review.author;
        var stars = document.createElement('span');
        stars.textContent = makeStars(review.rating);
        stars.setAttribute('aria-label', '별점 ' + review.rating + '점');
        header.append(name, stars);

        article.append(header);

        // 내용 없이 별점만 남긴 후기는 본문을 그리지 않는다
        if (review.content) {
          var text = document.createElement('p');
          text.textContent = review.content;
          article.append(text);
        }

        // 저장된 사진은 data:image/ 주소만 사용한다
        if (review.image && /^data:image\/(jpeg|png|webp);base64,/.test(review.image)) {
          var image = document.createElement('img');
          image.className = 'review-item-image';
          image.src = review.image;
          image.alt = review.author + '님의 후기 사진';
          article.append(image);
        }

        var date = document.createElement('small');
        date.textContent = review.createdAt + (review.updatedAt ? ' (수정됨)' : '');
        article.append(date);

        // 본인 후기만 수정·삭제 (실제 권한 확인은 서버가 한다)
        if (review.owned) {
          var actions = document.createElement('div');
          actions.className = 'review-item-actions';

          var edit = document.createElement('a');
          edit.className = 'review-edit-button';
          edit.href = reviewPageUrl(review.reviewId);
          edit.textContent = '수정';

          var remove = document.createElement('button');
          remove.type = 'button';
          remove.className = 'review-delete-button';
          remove.textContent = '삭제';
          remove.addEventListener('click', function () {
            if (!confirm('이 후기를 삭제하시겠습니까?')) return;
            saveReviews(loadReviews().filter(function (r) { return r.reviewId !== review.reviewId; }));
            render();
            showNotice('후기가 삭제되었습니다.');
          });

          actions.append(edit, remove);
          article.append(actions);
        }

        list.append(article);
      });

      var total = reviews.reduce(function (sum, r) { return sum + r.rating; }, 0);
      var average = reviews.length ? total / reviews.length : 0;
      document.getElementById('review-average').textContent = reviews.length ? average.toFixed(1) : '-';
      document.getElementById('review-average-stars').textContent =
        reviews.length ? makeStars(Math.round(average)) : '';
      document.getElementById('review-count').textContent = '리뷰 ' + reviews.length + '개';
      empty.hidden = reviews.length > 0;
    }

    function showNotice(message) {
      notice.textContent = message;
      notice.hidden = false;
    }

    render();

    // 후기 작성·수정 화면에서 돌아온 경우
    var result = params.get('review');
    if (result === 'created' || result === 'updated') {
      showNotice(result === 'created' ? '후기가 등록되었습니다.' : '후기가 수정되었습니다.');
      params.delete('review');
      window.history.replaceState(null, '', window.location.pathname + '?' + params.toString() + '#reviews');
    }
  }

  /* ---------- 후기 작성·수정 페이지 ---------- */

  function initForm() {
    Ui.renderHeader();

    var form = document.getElementById('review-form');
    var alertBox = document.getElementById('review-alert');
    var submit = document.getElementById('review-submit');
    var rating = document.getElementById('review-rating');
    var content = document.getElementById('review-content');
    var count = document.getElementById('review-count-text');
    var imageInput = document.getElementById('review-image');
    var imageText = document.getElementById('review-image-text');
    var preview = document.getElementById('review-image-preview');
    var previewImg = document.getElementById('review-image-preview-img');
    var imageRemove = document.getElementById('review-image-remove');
    var IMAGE_HINT = imageText.textContent;

    // 등록할 사진 (data URL). 수정 모드에서는 기존 사진으로 시작
    var imageData = null;

    var accommodationName = MOCK_ACCOMMODATION_NAMES[accommodationId] || '숙소';
    var backLink = document.getElementById('review-back-link');
    backLink.href = detailUrl() + '#reviews';
    backLink.textContent = accommodationName;
    document.getElementById('review-cancel-link').href = detailUrl() + '#reviews';

    /* 수정 모드 */
    var reviewId = params.get('reviewId');
    var editing = null;

    if (reviewId !== null) {
      editing = loadReviews().filter(function (r) {
        return String(r.reviewId) === reviewId && r.owned;
      })[0] || null;

      if (!editing) {
        Ui.showAlert(alertBox, '수정할 수 있는 후기를 찾을 수 없습니다.', 'error');
        Array.from(form.elements).forEach(function (el) { el.disabled = true; });
        return;
      }

      document.title = '후기 수정 | 스테이북';
      document.getElementById('review-page-title').textContent = '후기 수정';
      document.getElementById('review-breadcrumb-title').textContent = '후기 수정';
      document.getElementById('review-page-sub').textContent = accommodationName + '에 남긴 후기를 수정합니다.';
      submit.textContent = '수정 완료';

      rating.value = String(editing.rating);
      content.value = editing.content;
      if (editing.image) showPreview(editing.image, '등록된 사진');
    } else {
      document.getElementById('review-page-sub').textContent =
        accommodationName + '에서의 숙박은 어떠셨나요? 다른 이용자에게 도움이 되는 후기를 남겨 주세요.';
    }

    updateCount();

    function updateCount() {
      count.textContent = content.value.length + ' / 1000';
    }

    rating.addEventListener('change', function () { Ui.setFieldError(rating, ''); });
    content.addEventListener('input', function () {
      updateCount();
      Ui.setFieldError(content, '');
    });

    /* 사진 첨부 */

    function showPreview(src, label) {
      imageData = src;
      previewImg.src = src;
      imageText.textContent = label;
      preview.hidden = false;
    }

    function clearImage() {
      imageData = null;
      imageInput.value = '';
      imageText.textContent = IMAGE_HINT;
      preview.hidden = true;
      previewImg.removeAttribute('src');
    }

    // 파일 앞부분(매직 바이트)으로 실제 이미지인지 확인한다
    function isImageSignature(b) {
      var jpeg = b[0] === 0xFF && b[1] === 0xD8 && b[2] === 0xFF;
      var png = b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47;
      var webp = b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46
        && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50;
      return jpeg || png || webp;
    }

    function readAs(file, method) {
      return new Promise(function (resolve, reject) {
        var reader = new FileReader();
        reader.onload = function () { resolve(reader.result); };
        reader.onerror = reject;
        reader[method](file);
      });
    }

    imageInput.addEventListener('change', async function () {
      var file = imageInput.files[0];
      Ui.setFieldError(imageInput, '');
      if (!file) return;

      var name = file.name;
      var ext = name.lastIndexOf('.') > 0 ? name.slice(name.lastIndexOf('.') + 1).toLowerCase() : '';
      var error = '';
      if (ALLOWED_EXT.indexOf(ext) === -1 || ALLOWED_MIME.indexOf(file.type) === -1) {
        error = 'jpg, png, webp 사진만 첨부할 수 있습니다.';
      } else if (name.split('.').length > 2 || name.indexOf('\0') !== -1) {
        error = '파일 이름을 확인해 주세요.'; // shell.jsp.png 같은 이중 확장자 차단
      } else if (file.size > MAX_FILE_SIZE) {
        error = '5MB 이하의 사진만 첨부할 수 있습니다.';
      } else {
        try {
          var head = new Uint8Array(await readAs(file.slice(0, 12), 'readAsArrayBuffer'));
          if (!isImageSignature(head)) error = '올바른 사진 파일이 아닙니다.';
        } catch (e) {
          error = '사진을 읽을 수 없습니다.';
        }
      }

      if (error) {
        clearImage();
        Ui.setFieldError(imageInput, error);
        return;
      }

      try {
        showPreview(await readAs(file, 'readAsDataURL'), name);
      } catch (e) {
        clearImage();
        Ui.setFieldError(imageInput, '사진을 읽을 수 없습니다.');
      }
    });

    imageRemove.addEventListener('click', function () {
      clearImage();
      Ui.setFieldError(imageInput, '');
    });

    /* 등록·수정 */

    form.addEventListener('submit', function (event) {
      event.preventDefault();
      Ui.hideAlert(alertBox);

      // 별점만 필수, 후기 내용은 선택
      if (!rating.value) {
        Ui.setFieldError(rating, '별점을 선택해 주세요.');
        rating.focus();
        return;
      }
      var text = content.value.trim();

      Ui.setLoading(submit, true, editing ? '수정 중...' : '등록 중...');

      var reviews = loadReviews();
      if (editing) {
        reviews = reviews.map(function (r) {
          if (r.reviewId !== editing.reviewId) return r;
          return Object.assign({}, r, {
            rating: Number(rating.value),
            content: text,
            image: imageData,
            updatedAt: today()
          });
        });
      } else {
        reviews.unshift({
          reviewId: Date.now(),
          author: '나',
          rating: Number(rating.value),
          content: text,
          image: imageData,
          createdAt: today(),
          owned: true
        });
      }

      if (!saveReviews(reviews)) {
        // 큰 사진은 브라우저 저장 공간을 넘을 수 있다 (실제로는 서버에 업로드)
        Ui.setLoading(submit, false);
        Ui.showAlert(alertBox, '사진이 커서 임시 저장하지 못했습니다. 더 작은 사진을 선택해 주세요.', 'error');
        return;
      }

      window.location.replace(detailUrl() + '&review=' + (editing ? 'updated' : 'created') + '#reviews');
    });
  }
})();
