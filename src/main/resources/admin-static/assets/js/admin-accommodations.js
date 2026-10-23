/*
 * 숙소·객실 관리 (ADMIN_001, ADMIN_002)
 *   GET   /api/v1/admin/accommodations/search?keyword=&page=&size=
 *   GET   /api/v1/admin/accommodations/{id}          숙소 + 객실 목록
 *   POST  /api/v1/admin/accommodations               { name, address, checkInTime, checkOutTime, description, status }
 *   PATCH /api/v1/admin/accommodations/{id}          (같은 형식)
 *   POST  /api/v1/admin/accommodations/{id}/rooms    { name, price, capacity, totalQuantity, description, status }
 *   PATCH /api/v1/admin/accommodations/{id}/rooms/{roomId}
 *
 *
 * 숙소 이미지 (아래 IMAGE_API 참고. 백엔드 API가 생기면 IMAGE_API.ready 를 true 로 바꾼다)
 *
 * 주소에 따라 화면이 바뀐다: 목록(기본) / ?mode=new 등록 / ?id={숙소 ID} 상세·수정
 * 숙소·객실 삭제 API는 아직 없다 (판매 상태를 "판매 중지"로 바꿔 운영한다).
 */
(function () {
  'use strict';

  var API = '/api/v1/admin/accommodations';
  var alertBox = document.getElementById('page-alert');
  var params = new URLSearchParams(window.location.search);
  var accommodationId = params.get('id');
  var isNew = params.get('mode') === 'new';

  var accForm = document.getElementById('acc-form');
  var roomForm = document.getElementById('room-form');
  var editingRoomId = null;

  AdminUi.init({ page: 'accommodations', roles: ['ROLE_ADMIN'] }).then(function (me) {
    if (!me) return;
    if (isNew) {
      showCreate();
    } else if (accommodationId !== null) {
      if (!AdminUi.isPositiveId(accommodationId)) {
        AdminUi.showMessage(alertBox, '잘못된 숙소 주소입니다.');
        return;
      }
      showDetail();
    } else {
      showList();
    }
  });

  /* ---------- 목록 ---------- */

  var filters = null;

  function showList() {
    document.getElementById('list-view').hidden = false;
    filters = AdminUi.bindFilters(document.getElementById('filter-form'),
      { keyword: '', page: 0 }, loadList);
  }

  async function loadList(query) {
    AdminUi.hideMessage(alertBox);
    try {
      var page = await AdminUi.api('GET', API + '/search' + AdminUi.buildQuery({
        keyword: query.keyword, page: query.page, size: AdminUi.DEFAULT_PAGE_SIZE
      }));
      AdminUi.renderTable(document.getElementById('list-body'), page.content || [], [
        { label: '숙소 ID', render: function (a) { return a.accommodationId; } },
        { label: '숙소명', render: function (a) { return a.name; } },
        { label: '주소', className: 'admin-wrap', render: function (a) { return a.address; } },
        { label: '상태', render: function (a) { return AdminUi.badge('openStatus', a.status); } },
        { label: '관리', render: function (a) {
          var link = document.createElement('a');
          link.className = 'admin-action';
          link.href = '/accommodations.html?id=' + encodeURIComponent(a.accommodationId);
          link.textContent = '상세·수정';
          return link;
        } }
      ], '조건에 맞는 숙소가 없습니다.');
      AdminUi.renderPagination(document.getElementById('pagination'), page, filters.go);
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }

  /* ---------- 등록 · 상세 ---------- */

  function showCreate() {
    document.getElementById('edit-view').hidden = false;
    document.getElementById('edit-crumb').textContent = '숙소 등록';
    document.getElementById('edit-title').textContent = '숙소 등록';
    document.getElementById('edit-sub').textContent = '숙소를 먼저 등록한 뒤 상세 화면에서 객실을 추가합니다.';
    accForm.elements.checkInTime.value = '15:00';
    accForm.elements.checkOutTime.value = '11:00';
    document.getElementById('acc-submit').textContent = '숙소 등록';
    document.getElementById('images-after-create').hidden = false;
  }

  async function showDetail() {
    document.getElementById('edit-view').hidden = false;
    document.getElementById('rooms-card').hidden = false;
    initImages();
    await loadDetail();
  }

  async function loadDetail() {
    try {
      var detail = await AdminUi.api('GET', API + '/' + encodeURIComponent(accommodationId));
      var a = detail.accommodation;
      document.getElementById('edit-crumb').textContent = a.name;
      document.getElementById('edit-title').textContent = a.name;
      document.getElementById('edit-sub').textContent = '숙소 ID ' + a.accommodationId + ' · ' + a.address;
      accForm.elements.name.value = a.name || '';
      accForm.elements.address.value = a.address || '';
      accForm.elements.checkInTime.value = (detail.checkInTime || '').slice(0, 5);
      accForm.elements.checkOutTime.value = (detail.checkOutTime || '').slice(0, 5);
      accForm.elements.description.value = detail.description || '';
      accForm.elements.status.value = a.status;
      renderRooms(detail.rooms || []);
      // 상세 응답에 images 가 오면 그대로 보여 준다 (API 연동 전에는 빈 목록)
      renderImages(detail.images || []);
      return true;
    } catch (e) {
      AdminUi.showMessage(alertBox, e.status === 404 ? '숙소를 찾을 수 없습니다.' : AdminUi.errorMessage(e));
      return false;
    }
  }

  function renderRooms(rooms) {
    AdminUi.renderTable(document.getElementById('room-body'), rooms, [
      { label: '객실 ID', render: function (r) { return r.roomId; } },
      { label: '객실명', render: function (r) { return r.name; } },
      { label: '1박 가격', render: function (r) { return AdminUi.formatPrice(r.price); } },
      { label: '정원', render: function (r) { return r.capacity + '명'; } },
      { label: '판매 수량', render: function (r) { return r.totalQuantity + '실'; } },
      { label: '상태', render: function (r) { return AdminUi.badge('openStatus', r.status); } },
      { label: '관리', render: function (r) {
        return AdminUi.actionButton('수정', function () { openRoomForm(r); });
      } }
    ], '등록된 객실이 없습니다. "객실 추가"로 등록해 주세요.');
  }

  function formError(id, message) {
    var el = document.getElementById(id);
    el.textContent = message || '';
    el.hidden = !message;
  }

  accForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    AdminUi.hideMessage(alertBox);
    var f = accForm.elements;
    var body = {
      name: f.name.value.trim(),
      address: f.address.value.trim(),
      checkInTime: f.checkInTime.value,
      checkOutTime: f.checkOutTime.value,
      description: f.description.value.trim() || null,
      status: f.status.value
    };
    if (!body.name || !body.address || !body.checkInTime || !body.checkOutTime) {
      formError('acc-form-error', '숙소명, 주소, 체크인·체크아웃 시각을 입력해 주세요.');
      return;
    }
    formError('acc-form-error', '');

    var submit = document.getElementById('acc-submit');
    submit.disabled = true;
    try {
      if (isNew) {
        var created = await AdminUi.api('POST', API, body);
        // 등록되면 상세 화면에서 객실을 추가한다
        window.location.href = created && created.accommodationId
          ? '/accommodations.html?id=' + encodeURIComponent(created.accommodationId) + '&created=1'
          : '/accommodations.html';
        return;
      }
      await AdminUi.api('PATCH', API + '/' + encodeURIComponent(accommodationId), body);
      await loadDetail();
      AdminUi.showMessage(alertBox, '숙소 정보를 저장했습니다.', 'success');
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    } finally {
      submit.disabled = false;
    }
  });

  if (params.get('created') === '1') {
    // 등록 직후 안내 (주소에서 표시를 지워 새로고침 때 다시 뜨지 않게)
    params.delete('created');
    window.history.replaceState(null, '', '/accommodations.html?' + params.toString());
    setTimeout(function () {
      AdminUi.showMessage(alertBox, '숙소를 등록했습니다. 이제 객실을 추가해 주세요.', 'success');
    });
  }

  /* ---------- 객실 추가 · 수정 ---------- */

  function openRoomForm(room) {
    editingRoomId = room ? room.roomId : null;
    var f = roomForm.elements;
    document.getElementById('room-form-title').textContent = room ? '객실 수정 (ID ' + room.roomId + ')' : '객실 추가';
    f.name.value = room ? room.name : '';
    f.price.value = room ? room.price : '';
    f.capacity.value = room ? room.capacity : '2';
    f.totalQuantity.value = room ? room.totalQuantity : '1';
    f.status.value = room ? room.status : 'ACTIVE';
    f.description.value = room ? (room.description || '') : '';
    formError('room-form-error', '');
    roomForm.hidden = false;
    f.name.focus();
  }

  function closeRoomForm() {
    roomForm.hidden = true;
    editingRoomId = null;
  }

  document.getElementById('room-add').addEventListener('click', function () { openRoomForm(null); });
  document.getElementById('room-cancel').addEventListener('click', closeRoomForm);

  function wholeNumber(value, min) {
    var n = Number(value);
    return value !== '' && Number.isInteger(n) && n >= min ? n : null;
  }

  roomForm.addEventListener('submit', async function (event) {
    event.preventDefault();
    AdminUi.hideMessage(alertBox);
    var f = roomForm.elements;
    var body = {
      name: f.name.value.trim(),
      price: wholeNumber(f.price.value, 0),
      capacity: wholeNumber(f.capacity.value, 1),
      totalQuantity: wholeNumber(f.totalQuantity.value, 1),
      description: f.description.value.trim() || null,
      status: f.status.value
    };
    if (!body.name || body.price === null || body.capacity === null || body.totalQuantity === null) {
      formError('room-form-error', '객실명을 입력하고, 가격은 0 이상, 정원·판매 수량은 1 이상의 정수로 입력해 주세요.');
      return;
    }
    formError('room-form-error', '');

    var submit = document.getElementById('room-submit');
    submit.disabled = true;
    var base = API + '/' + encodeURIComponent(accommodationId) + '/rooms';
    try {
      if (editingRoomId) {
        await AdminUi.api('PATCH', base + '/' + encodeURIComponent(editingRoomId), body);
      } else {
        await AdminUi.api('POST', base, body);
      }
      var message = editingRoomId ? '객실을 수정했습니다.' : '객실을 추가했습니다.';
      closeRoomForm();
      await loadDetail();
      AdminUi.showMessage(alertBox, message, 'success');
    } catch (e) {
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
      // 응답이 실패해도 저장됐을 수 있으므로 목록을 다시 확인한다
      loadDetail();
    } finally {
      submit.disabled = false;
    }
  });

  /* ---------- 숙소 이미지 ----------
   *
   * 백엔드 API 가 아직 없어 아래 형식을 기준으로 만들었다. (엑셀 UPLOADS-001, DATABASE.md accommodation_files)
   *   1) POST   /api/v1/uploads/Images                         multipart "file" 1개 → { fileId, url }
   *   2) POST   /api/v1/admin/accommodations/{id}/images       { fileId }  숙소에 연결 (순서대로, 첫 장이 대표)
   *   3) DELETE /api/v1/admin/accommodations/{id}/images/{fileId}
   *   상세 조회(GET /api/v1/admin/accommodations/{id}) 응답에 images: [{ fileId, url }] 를 넣는다.
   * 서버도 확장자·MIME·매직 바이트·용량·이중 확장자를 다시 검사하고, 파일명은 UUID 로 저장해야 한다.
   * 경로가 바뀌면 IMAGE_API 만 고친다.
   */
  var IMAGE_API = {
    ready: false, // 백엔드 API 구현 후 true
    upload: '/api/v1/uploads/Images',
    attach: function (id) { return API + '/' + encodeURIComponent(id) + '/images'; },
    detach: function (id, fileId) {
      return API + '/' + encodeURIComponent(id) + '/images/' + encodeURIComponent(fileId);
    }
  };
  var MAX_IMAGES = 10;
  var MAX_IMAGE_BYTES = 5 * 1024 * 1024;
  var IMAGE_EXT = ['jpg', 'jpeg', 'png', 'webp'];
  var IMAGE_MIME = ['image/jpeg', 'image/png', 'image/webp'];

  var imageList = document.getElementById('image-list');
  var imageEmpty = document.getElementById('image-empty');
  var imageInput = document.getElementById('image-input');
  var pendingList = document.getElementById('pending-list');
  var imageError = document.getElementById('image-error');
  var uploadButton = document.getElementById('image-upload');
  var clearButton = document.getElementById('image-clear');

  var savedImages = [];   // 서버에 저장된 이미지 [{ fileId, url }]
  var pending = [];       // 올릴 이미지 [{ file, previewUrl, status, message }]
  var uploading = false;

  function initImages() {
    document.getElementById('images-card').hidden = false;
    document.getElementById('image-max').textContent = MAX_IMAGES;
    document.getElementById('images-not-ready').hidden = IMAGE_API.ready;
    renderImages([]);
    renderPending();
  }

  // 서버가 준 주소는 같은 사이트 경로(/...) 또는 https 만 이미지로 쓴다
  function safeImageUrl(url) {
    var text = String(url || '');
    if (/^\/(?!\/)/.test(text)) return text;
    if (/^https:\/\//i.test(text)) return text;
    return null;
  }

  function renderImages(images) {
    savedImages = images;
    imageList.textContent = '';
    images.forEach(function (image, index) {
      var li = document.createElement('li');
      li.className = 'admin-image-item';
      var src = safeImageUrl(image.url);
      if (src) {
        var img = document.createElement('img');
        img.src = src;
        img.alt = '숙소 이미지 ' + (index + 1);
        img.loading = 'lazy';
        li.append(img);
      } else {
        var broken = document.createElement('span');
        broken.className = 'admin-image-broken';
        broken.textContent = '표시할 수 없는 이미지';
        li.append(broken);
      }
      var bar = document.createElement('div');
      bar.className = 'admin-image-bar';
      if (index === 0) {
        bar.append(AdminUi.statusBadge('MAIN', { MAIN: '대표' }, { MAIN: 'ok' }));
      }
      bar.append(AdminUi.actionButton('삭제', function (button) { removeImage(image, button); }, 'danger'));
      li.append(bar);
      imageList.append(li);
    });
    imageEmpty.hidden = images.length > 0;
  }

  function showImageError(message) {
    imageError.textContent = message || '';
    imageError.hidden = !message;
  }

  /* 선택한 파일 검사 (화면 검사는 편의용, 실제 검사는 서버가 다시 한다) */

  function readHead(file) {
    return new Promise(function (resolve) {
      var reader = new FileReader();
      reader.onload = function () { resolve(new Uint8Array(reader.result)); };
      reader.onerror = function () { resolve(new Uint8Array(0)); };
      reader.readAsArrayBuffer(file.slice(0, 12));
    });
  }

  // 파일 앞부분(매직 바이트)이 실제 jpg·png·webp 인지
  function isImageSignature(b) {
    var jpeg = b[0] === 0xFF && b[1] === 0xD8 && b[2] === 0xFF;
    var png = b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47;
    var webp = b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46
      && b[8] === 0x57 && b[9] === 0x45 && b[10] === 0x42 && b[11] === 0x50;
    return jpeg || png || webp;
  }

  async function checkImage(file) {
    var name = file.name;
    var dot = name.lastIndexOf('.');
    var ext = dot > 0 ? name.slice(dot + 1).toLowerCase() : '';
    if (IMAGE_EXT.indexOf(ext) === -1 || IMAGE_MIME.indexOf(file.type) === -1) {
      return 'jpg, png, webp 이미지만 올릴 수 있습니다.';
    }
    // shell.jsp.png 같은 이중 확장자, 널 바이트 차단
    if (name.split('.').length > 2 || name.indexOf('\0') !== -1) return '파일 이름에 점(.)이 여러 개 있으면 올릴 수 없습니다.';
    if (file.size > MAX_IMAGE_BYTES) return '5MB 이하의 이미지만 올릴 수 있습니다.';
    if (!isImageSignature(await readHead(file))) return '이미지 파일이 아니거나 손상된 파일입니다.';
    return '';
  }

  imageInput.addEventListener('change', async function () {
    showImageError('');
    var files = Array.prototype.slice.call(imageInput.files || []);
    imageInput.value = ''; // 같은 파일을 다시 고를 수 있게
    var room = MAX_IMAGES - savedImages.length - pending.length;
    var tooMany = files.length > room;
    if (tooMany) files = files.slice(0, Math.max(room, 0));
    var problems = [];
    if (tooMany) {
      problems.push('이미지는 최대 ' + MAX_IMAGES + '장까지 등록할 수 있습니다. (' + Math.max(room, 0) + '장 더 추가 가능)');
    }
    for (var i = 0; i < files.length; i++) {
      var problem = await checkImage(files[i]);
      if (problem) {
        problems.push(files[i].name + ': ' + problem);
        continue;
      }
      pending.push({ file: files[i], previewUrl: URL.createObjectURL(files[i]), status: 'ready', message: '' });
    }
    // 걸러진 파일이 여러 개면 모두 알려 준다 (줄마다 하나)
    showImageError(problems.join('\n'));
    renderPending();
  });

  function renderPending() {
    pendingList.textContent = '';
    pending.forEach(function (item, index) {
      var li = document.createElement('li');
      li.className = 'admin-image-item';
      var img = document.createElement('img');
      img.src = item.previewUrl; // 내 PC 파일의 임시 주소 (blob:)
      img.alt = item.file.name;
      var bar = document.createElement('div');
      bar.className = 'admin-image-bar';
      var name = document.createElement('small');
      name.className = 'admin-cell-sub admin-image-name';
      name.textContent = item.file.name;
      var state = {
        ready: ['대기', 'muted'], uploading: ['올리는 중', 'warn'], done: ['완료', 'ok'], failed: ['실패', 'error']
      }[item.status];
      bar.append(AdminUi.statusBadge(item.status, { [item.status]: state[0] }, { [item.status]: state[1] }), name);
      if (item.status !== 'uploading' && item.status !== 'done') {
        bar.append(AdminUi.actionButton('빼기', function () { dropPending(index); }));
      }
      li.append(img, bar);
      if (item.message) {
        var msg = document.createElement('small');
        msg.className = 'field-error admin-image-msg';
        msg.textContent = item.message;
        li.append(msg);
      }
      pendingList.append(li);
    });
    var waiting = pending.filter(function (p) { return p.status === 'ready' || p.status === 'failed'; }).length;
    uploadButton.disabled = uploading || waiting === 0;
    uploadButton.textContent = uploading ? '올리는 중...' : '이미지 업로드' + (waiting ? ' (' + waiting + '장)' : '');
    clearButton.hidden = pending.length === 0 || uploading;
  }

  function dropPending(index) {
    URL.revokeObjectURL(pending[index].previewUrl);
    pending.splice(index, 1);
    renderPending();
  }

  clearButton.addEventListener('click', function () {
    pending.forEach(function (item) { URL.revokeObjectURL(item.previewUrl); });
    pending = [];
    showImageError('');
    renderPending();
  });

  uploadButton.addEventListener('click', async function () {
    showImageError('');
    if (!IMAGE_API.ready) {
      showImageError('이미지 업로드 API가 아직 준비되지 않아 저장할 수 없습니다.');
      return;
    }
    uploading = true;
    renderPending();
    // 요청 한 번에 파일 한 개 (UPLOADS-001)
    for (var i = 0; i < pending.length; i++) {
      var item = pending[i];
      if (item.status !== 'ready' && item.status !== 'failed') continue;
      item.status = 'uploading';
      item.message = '';
      renderPending();
      try {
        var form = new FormData();
        form.append('file', item.file);
        var uploaded = await AdminUi.api('POST', IMAGE_API.upload, form);
        await AdminUi.api('POST', IMAGE_API.attach(accommodationId), { fileId: uploaded.fileId });
        item.status = 'done';
      } catch (e) {
        item.status = 'failed';
        item.message = AdminUi.errorMessage(e);
      }
      renderPending();
    }
    uploading = false;
    // 완료된 것은 목록에서 빼고, 저장된 이미지를 다시 불러온다
    pending = pending.filter(function (item) {
      if (item.status === 'done') URL.revokeObjectURL(item.previewUrl);
      return item.status !== 'done';
    });
    renderPending();
    await loadDetail();
    if (!pending.length) AdminUi.showMessage(alertBox, '이미지를 업로드했습니다.', 'success');
    else showImageError('일부 이미지를 올리지 못했습니다. 실패한 이미지는 다시 업로드할 수 있습니다.');
  });

  async function removeImage(image, button) {
    var answer = await AdminUi.confirmAction({
      title: '이미지 삭제',
      message: '이 이미지를 숙소에서 삭제합니다.',
      confirmLabel: '삭제',
      danger: true
    });
    if (!answer.confirmed) return;
    if (!IMAGE_API.ready) {
      AdminUi.showMessage(alertBox, '이미지 API가 아직 준비되지 않았습니다.');
      return;
    }
    button.disabled = true;
    try {
      await AdminUi.api('DELETE', IMAGE_API.detach(accommodationId, image.fileId));
      await loadDetail();
      AdminUi.showMessage(alertBox, '이미지를 삭제했습니다.', 'success');
    } catch (e) {
      button.disabled = false;
      AdminUi.showMessage(alertBox, AdminUi.errorMessage(e));
    }
  }
})();
