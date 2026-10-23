/*
 * 숙소 후기 (REVIEW_001 ~ REVIEW_004)
 *
 *   숙소 상세  /accommodation/detail.html?id=      후기 목록·평균 별점, 내 후기 삭제
 *   후기 작성  /accommodation/review.html?id=      새 후기 작성
 *   후기 수정  /accommodation/review.html?id=&reviewId=
 *
 * API
 * - 목록     GET    /api/v1/accommodations/{accommodationId}/reviews  (averageRating, reviewCount는 서버 값)
 * - 조회     GET    /api/v1/reviews/{reviewId}       (수정 화면)
 * - 작성     POST   /api/v1/reviews        이용 완료(COMPLETED) 예약 보유 여부는 서버가 검증
 * - 수정     PUT    /api/v1/reviews/{reviewId}   작성자 본인 여부는 서버가 검증
 * - 삭제     DELETE /api/v1/reviews/{reviewId}
 * - 사진     POST   /api/v1/uploads/Images 로 올린 뒤 받은 파일 ID를 후기에 연결
 */

(function () {
  'use strict';

  var MAX_FILE_SIZE = 5 * 1024 * 1024;
  var ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'webp'];
  var ALLOWED_MIME = ['image/jpeg', 'image/png', 'image/webp'];
  // 서버가 준 사진 주소만 그린다 (다른 사이트 주소·스크립트 주소 차단)
  var IMAGE_URL_RE = /^\/api\/v1\/uploads\/Images\/(\d{1,19})$/;

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

  function goLogin() {
    window.location.href = '/auth/login.html?next=' + encodeURIComponent(
      window.location.pathname + window.location.search);
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

    function renderReview(review) {
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

      if (review.image && IMAGE_URL_RE.test(review.image)) {
        var image = document.createElement('img');
        image.className = 'review-item-image';
        image.src = review.image;
        image.alt = review.author + '님의 후기 사진';
        image.loading = 'lazy';
        article.append(image);
      }

      var date = document.createElement('small');
      date.textContent = review.createdAt + (review.updatedAt ? ' (수정됨)' : '');
      article.append(date);

      // 본인 후기만 수정·삭제 버튼을 보여 준다 (실제 권한 확인은 서버가 한다)
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
        remove.addEventListener('click', async function () {
          if (!confirm('이 후기를 삭제하시겠습니까?')) return;
          remove.disabled = true;
          try {
            await BookingApi.deleteReview(review.reviewId);
            showNotice('후기가 삭제되었습니다.');
            render();
          } catch (e) {
            remove.disabled = false;
            showNotice(e.message);
          }
        });

        actions.append(edit, remove);
        article.append(actions);
      }
      return article;
    }

    async function render() {
      var data;
      try {
        data = await BookingApi.getReviews(accommodationId, 0);
      } catch (e) {
        list.textContent = '';
        empty.hidden = false;
        showNotice('후기를 불러오지 못했습니다.');
        return;
      }
      var reviews = Array.isArray(data.reviews) ? data.reviews : [];
      list.textContent = '';
      reviews.forEach(function (review) { list.append(renderReview(review)); });

      var count = Number(data.reviewCount || 0);
      var average = Number(data.averageRating || 0);
      document.getElementById('review-average').textContent = count ? average.toFixed(1) : '-';
      document.getElementById('review-average-stars').textContent = count ? makeStars(Math.round(average)) : '';
      document.getElementById('review-count').textContent = '리뷰 ' + count + '개';
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
    var headerReady = Ui.renderHeader();

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

    // 새로 고른 사진(File)과, 수정 모드에서 이미 서버에 있는 사진 번호
    var newImageFile = null;
    var existingImageId = null;
    var reviewId = params.get('reviewId');
    var accommodationName = '숙소';

    var backLink = document.getElementById('review-back-link');
    backLink.href = detailUrl() + '#reviews';
    document.getElementById('review-cancel-link').href = detailUrl() + '#reviews';

    function disableForm(message) {
      Ui.showAlert(alertBox, message, 'error');
      Array.from(form.elements).forEach(function (el) { el.disabled = true; });
    }

    async function load() {
      var me = await headerReady;
      if (!me) {
        goLogin();
        return;
      }
      try {
        var accommodation = await BookingApi.getAccommodation(accommodationId);
        accommodationName = accommodation.name;
      } catch (e) {
        disableForm('숙소 정보를 찾을 수 없습니다.');
        return;
      }
      backLink.textContent = accommodationName;

      if (reviewId === null) {
        document.getElementById('review-page-sub').textContent =
          accommodationName + '에서의 숙박은 어떠셨나요? 다른 이용자에게 도움이 되는 후기를 남겨 주세요.';
        updateCount();
        return;
      }

      var editing = null;
      try {
        editing = await BookingApi.getReview(reviewId);
      } catch (e) {
        editing = null;
      }
      if (!editing || !editing.owned || String(editing.accommodationId) !== accommodationId) {
        disableForm('수정할 수 있는 후기를 찾을 수 없습니다.');
        return;
      }

      document.title = '후기 수정 | 스테이북';
      document.getElementById('review-page-title').textContent = '후기 수정';
      document.getElementById('review-breadcrumb-title').textContent = '후기 수정';
      document.getElementById('review-page-sub').textContent = accommodationName + '에 남긴 후기를 수정합니다.';
      submit.textContent = '수정 완료';

      rating.value = String(editing.rating);
      content.value = editing.content || '';
      var match = editing.image && IMAGE_URL_RE.exec(editing.image);
      if (match) {
        existingImageId = Number(match[1]);
        showPreview(editing.image, '등록된 사진');
      }
      updateCount();
    }

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
      previewImg.src = src;
      imageText.textContent = label;
      preview.hidden = false;
    }

    function clearImage() {
      newImageFile = null;
      existingImageId = null;
      imageInput.value = '';
      imageText.textContent = IMAGE_HINT;
      preview.hidden = true;
      previewImg.removeAttribute('src');
    }

    // 파일 앞부분(매직 바이트)으로 실제 이미지인지 확인한다 (서버도 다시 검사한다)
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
        // 미리보기는 이 브라우저 안에서만 보여 주고, 실제 사진은 등록할 때 서버에 올린다
        showPreview(await readAs(file, 'readAsDataURL'), name);
        newImageFile = file;
        existingImageId = null;
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

    form.addEventListener('submit', async function (event) {
      event.preventDefault();
      Ui.hideAlert(alertBox);

      // 별점만 필수, 후기 내용은 선택
      if (!rating.value) {
        Ui.setFieldError(rating, '별점을 선택해 주세요.');
        rating.focus();
        return;
      }

      Ui.setLoading(submit, true, reviewId ? '수정 중...' : '등록 중...');
      try {
        var imageFileId = existingImageId;
        if (newImageFile) {
          var uploaded = await BookingApi.uploadImage(newImageFile);
          imageFileId = uploaded.fileId;
        }
        var body = {
          rating: Number(rating.value),
          content: content.value.trim(),
          imageFileId: imageFileId
        };
        if (reviewId) {
          await BookingApi.updateReview(reviewId, body);
        } else {
          body.accommodationId = Number(accommodationId);
          await BookingApi.createReview(body);
        }
        window.location.replace(detailUrl() + '&review=' + (reviewId ? 'updated' : 'created') + '#reviews');
      } catch (e) {
        if (e.status === 401) {
          goLogin();
          return;
        }
        Ui.showAlert(alertBox, e.message, 'error');
        Ui.setLoading(submit, false);
      }
    });

    load();
  }
})();
