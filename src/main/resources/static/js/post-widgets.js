// ############################################################################
// post-widgets.js — 게시글 작성/수정 폼 공용 위젯 (이미지 업로드, 맛집 태그 검색)
// posts/list.html(새 글 모달)과 posts/form.html(수정 페이지)이 같이 쓴다.
// ############################################################################

let postImageUploading = false;

function handlePostImageSelect(e) {
  const file = e.target.files[0];
  if (!file) return;

  const status = document.getElementById('imgUploadStatus');
  const preview = document.getElementById('imgPreview');
  const boxText = document.getElementById('imgUploadBoxText');
  const formData = new FormData();
  formData.append('file', file);

  postImageUploading = true;
  boxText.textContent = file.name;
  status.textContent = '업로드 중...';
  status.className = 'img-upload-status uploading';

  fetch('/api/uploads', { method: 'POST', body: formData })
    .then(res => res.json().then(data => ({ ok: res.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) {
        status.textContent = data.message || '업로드에 실패했습니다.';
        status.className = 'img-upload-status error';
        return;
      }
      document.getElementById('imgUrl').value = data.url;
      preview.src = data.url;
      preview.style.display = 'block';
      status.textContent = '업로드 완료';
      status.className = 'img-upload-status success';
    })
    .catch(() => {
      status.textContent = '네트워크 오류로 업로드하지 못했습니다.';
      status.className = 'img-upload-status error';
    })
    .finally(() => { postImageUploading = false; });
}

let restaurantSearchTimer = null;

function searchRestaurantsForTag(keyword) {
  clearTimeout(restaurantSearchTimer);
  const resultsEl = document.getElementById('restaurantSearchResults');
  const kw = keyword.trim();
  if (!kw) {
    resultsEl.innerHTML = '';
    return;
  }
  restaurantSearchTimer = setTimeout(() => {
    fetch('/api/restaurants?keyword=' + encodeURIComponent(kw))
      .then(res => res.json())
      .then(list => renderRestaurantTagResults(Array.isArray(list) ? list.slice(0, 5) : []))
      .catch(() => { resultsEl.innerHTML = ''; });
  }, 250);
}

function renderRestaurantTagResults(list) {
  const resultsEl = document.getElementById('restaurantSearchResults');
  resultsEl.innerHTML = '';
  if (list.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'restaurant-tag-empty';
    empty.textContent = '검색 결과가 없습니다';
    resultsEl.appendChild(empty);
    return;
  }
  list.forEach(r => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'restaurant-tag-item';

    const nameSpan = document.createElement('span');
    nameSpan.className = 'restaurant-tag-item-name';
    nameSpan.textContent = r.name;

    const catSpan = document.createElement('span');
    catSpan.className = 'restaurant-tag-item-cat';
    catSpan.textContent = r.category;

    btn.appendChild(nameSpan);
    btn.appendChild(catSpan);
    btn.addEventListener('click', () => selectRestaurantTag(r));
    resultsEl.appendChild(btn);
  });
}

function selectRestaurantTag(r) {
  document.getElementById('restaurantId').value = r.id;
  document.getElementById('restaurantSearchInput').value = '';
  document.getElementById('restaurantSearchResults').innerHTML = '';
  document.getElementById('restaurantTagChipText').textContent = r.name + ' · ' + r.category;
  document.getElementById('restaurantTagChip').style.display = 'inline-flex';
}

function clearRestaurantTag() {
  document.getElementById('restaurantId').value = '';
  document.getElementById('restaurantTagChip').style.display = 'none';
}
