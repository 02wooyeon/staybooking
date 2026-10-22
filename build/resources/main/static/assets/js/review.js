/*
 * 숙소 리뷰 화면
 *
 * 현재는 백엔드 연동 전 mock 동작.
 *
 * 실제 API 연동 시:
 * - 리뷰 목록은 서버 응답으로 렌더링
 * - averageRating, reviewCount는 서버 응답값 사용
 * - 리뷰 등록은 실제 리뷰 등록 API 호출
 */


(function () {
  'use strict';

  var writeButton =
    document.getElementById('review-write-button');

  var form =
    document.getElementById('review-form');

  var cancelButton =
    document.getElementById('review-cancel-button');

  var rating =
    document.getElementById('review-rating');

  var content =
    document.getElementById('review-content');

var imageInput =
  document.getElementById('review-image');

var imagePreview =
  document.getElementById('review-image-preview');

var imagePreviewImg =
  document.getElementById('review-image-preview-img');

var imageRemoveButton =
  document.getElementById('review-image-remove');

var reviewImageUrl =
  null; 

  var list =
    document.getElementById('review-list');

var reviewCountText =
    document.querySelector('#review-summary p');


  if (!writeButton || !form) {
    return;
  }


  /* 리뷰 작성 버튼 */
  writeButton.addEventListener(
    'click',
    function () {

      form.hidden = false;

      writeButton.hidden = true;

      rating.focus();
    }
  );


  /* 작성 취소 */
  cancelButton.addEventListener(
    'click',
    function () {

      form.reset();

      form.hidden = true;

      writeButton.hidden = false;
    }
  );


  /* 리뷰 등록 - 현재는 mock */
  form.addEventListener(
    'submit',
    function (event) {

      event.preventDefault();


      if (!rating.value) {
        alert('별점을 선택해 주세요.');
        return;
      }


      if (!content.value.trim()) {
        alert('후기를 입력해 주세요.');
        return;
      }


      var article =
        document.createElement('article');

      article.className =
        'review-item';


      var header =
        document.createElement('div');

      header.className =
        'review-item-header';


      var name =
        document.createElement('strong');

      name.textContent =
        '나';


      var stars =
        document.createElement('span');

      stars.textContent =
        makeStars(
          Number(rating.value)
        );


      var text =
        document.createElement('p');

      text.textContent =
        content.value.trim();

      var reviewImage =
  null;

if (
  imageInput.files &&
  imageInput.files[0]
) {

  reviewImage =
    document.createElement('img');

  reviewImage.className =
    'review-item-image';

  reviewImage.src =
    URL.createObjectURL(
      imageInput.files[0]
    );

  reviewImage.alt =
    '리뷰 첨부 이미지';
}

      var date =
        document.createElement('small');

      date.textContent =
        new Date()
          .toISOString()
          .slice(0, 10);


      header.append(
        name,
        stars
      );


      article.append(
    header,
    text
);

if (reviewImage) {
  article.append(reviewImage);
}

article.append(date);

    article.dataset.owned = 'true';

var actions =
  document.createElement('div');

actions.className =
  'review-item-actions';


var editButton =
  document.createElement('button');

editButton.type = 'button';
editButton.className =
  'review-edit-button';
editButton.textContent =
  '수정';


var deleteButton =
  document.createElement('button');

deleteButton.type = 'button';
deleteButton.className =
  'review-delete-button';
deleteButton.textContent =
  '삭제';


actions.append(
  editButton,
  deleteButton
);

article.append(actions);

      list.prepend(article);

      var currentCount =
    list.querySelectorAll('.review-item').length;

    reviewCountText.textContent =
    '리뷰 ' + currentCount + '개';


      form.reset();

      imagePreview.hidden = true;

imagePreviewImg.removeAttribute(
  'src'
);

if (reviewImageUrl) {

  URL.revokeObjectURL(
    reviewImageUrl
  );

  reviewImageUrl = null;
}

      form.hidden = true;

      writeButton.hidden = false;


      alert(
        '리뷰 화면 테스트가 완료되었습니다.'
      );
    }
  );

  /* 내 리뷰 수정 / 삭제 */
list.addEventListener(
  'click',
  function (event) {

    var item =
      event.target.closest('.review-item');

    if (!item) {
      return;
    }


    /* 본인 리뷰가 아니면 수정/삭제 불가 */
    if (item.dataset.owned !== 'true') {
      return;
    }


    /* 수정 */
    if (
      event.target.classList.contains(
        'review-edit-button'
      )
    ) {

      var reviewText =
        item.querySelector('p');

      var currentText =
        reviewText.textContent.trim();

      var changedText =
        prompt(
          '리뷰 내용을 수정해 주세요.',
          currentText
        );

      if (changedText === null) {
        return;
      }

      changedText =
        changedText.trim();

      if (!changedText) {
        alert('리뷰 내용을 입력해 주세요.');
        return;
      }

      reviewText.textContent =
        changedText;

      alert('리뷰가 수정되었습니다.');

      return;
    }


    /* 삭제 */
    if (
      event.target.classList.contains(
        'review-delete-button'
      )
    ) {

      var confirmed =
        confirm(
          '이 리뷰를 삭제하시겠습니까?'
        );

      if (!confirmed) {
        return;
      }

      item.remove();

      alert('리뷰가 삭제되었습니다.');
    }
  }
);

function validateReviewImage(file) {

  var allowedTypes = [
    'image/jpeg',
    'image/png',
    'image/webp'
  ];

  var maxSize =
    5 * 1024 * 1024;

  if (
    allowedTypes.indexOf(file.type) === -1
  ) {

    alert(
      'jpg, png, webp 이미지만 첨부할 수 있습니다.'
    );

    return false;
  }


  if (file.size > maxSize) {

    alert(
      '이미지는 5MB 이하만 첨부할 수 있습니다.'
    );

    return false;
  }


  return true;
}

imageInput.addEventListener(
  'change',
  function () {

    var file =
      imageInput.files[0];

    if (!file) {
      return;
    }


    if (!validateReviewImage(file)) {

      imageInput.value = '';

      return;
    }


    if (reviewImageUrl) {
      URL.revokeObjectURL(
        reviewImageUrl
      );
    }


    reviewImageUrl =
      URL.createObjectURL(file);


    imagePreviewImg.src =
      reviewImageUrl;

    imagePreview.hidden =
      false;
  }
);


imageRemoveButton.addEventListener(
  'click',
  function () {

    imageInput.value = '';

    imagePreview.hidden =
      true;

    imagePreviewImg.removeAttribute(
      'src'
    );


    if (reviewImageUrl) {

      URL.revokeObjectURL(
        reviewImageUrl
      );

      reviewImageUrl = null;
    }
  }
);

  function makeStars(score) {

    return (
      '★'.repeat(score) +
      '☆'.repeat(5 - score)
    );
  }

})();