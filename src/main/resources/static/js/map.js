/**
 * 유니피드 오산대학교 맛집 지도 스크립트 (map.js)
 * - 카카오 지도 JavaScript SDK 연동
 * - 오산대학교 중심 (37.15531, 127.06081)
 * - 카카오 장소 검색 API(kakao.maps.services.Places) 연동
 *   - '할매순대국' 등 키워드 검색 시 오산/캠퍼스 권역의 모든 매장을 검색하여 지도 위 마커로 한눈에 표출
 *   - map.setBounds(bounds)를 통해 검색된 모든 마커가 화면에 다 들어오도록 지도 시야 자동 조절
 * - 검색으로만 찾은 미등록 맛집은 자동 저장하지 않고 등록 확인 후에만 저장, 리뷰/찜 연동
 */

// 멀티캠퍼스 — 로그인한 회원의 소속 대학교 좌표(map/index.html에서 주입). 학교가 없거나
// 좌표를 아직 못 구한 학교면 오산대 좌표로 대체한다(서비스가 안 죽게).
const CAMPUS_LAT = window.CAMPUS_LAT || 37.155120;
const CAMPUS_LNG = window.CAMPUS_LNG || 127.060977;
const CAMPUS_REGION = window.CAMPUS_REGION || '오산';

let map = null;
let markers = [];
let placesService = null;
let currentRestaurantList = [];
let currentRestaurantId = null;
let currentRating = 5;
let currentReviewsList = [];
let editingReviewId = null; // null이면 새 리뷰 작성, 값 있으면 그 리뷰 수정 중
let currentRestaurantHasImg = false; // 현재 열린 맛집에 대표 사진이 이미 있는지 (있으면 대표사진 동의 체크박스 숨김)
let currentCategory = '전체';
let currentSortMode = 'recommend'; // 'recommend' | 'rating' | 'review' | 'bookmark'

// 실시간 인페이지 도보 길찾기 관련 상태 변수
let currentRoutePolyline = null;
let currentRoutePolylineOutline = null;
let currentStartMarker = null;
let currentEndMarker = null;
let activeRouteRestaurant = null;
let routeOriginMode = 'campus'; // 'campus' | 'myLocation'
let userCurrentLatLng = null;

document.addEventListener('DOMContentLoaded', () => {
  initKakaoMap();
  fetchRestaurants();
  loadGapInfo();

  // 신고 관리 화면 "원본 보기"(리뷰) 링크로 들어온 경우 그 맛집 상세를 자동으로 연다.
  if (window.OPEN_RESTAURANT_ID) {
    viewExistingRestaurant(window.OPEN_RESTAURANT_ID);
  }
});

// 두 위도/경도 좌표 간 직선 거리(m) 계산 (Haversine formula)
function calculateDistance(lat1, lon1, lat2, lon2) {
  if (!lat1 || !lon1 || !lat2 || !lon2) return null;
  const R = 6371e3; // meters
  const phi1 = lat1 * Math.PI / 180;
  const phi2 = lat2 * Math.PI / 180;
  const deltaPhi = (lat2 - lat1) * Math.PI / 180;
  const deltaLambda = (lon2 - lon1) * Math.PI / 180;

  const a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
            Math.cos(phi1) * Math.cos(phi2) *
            Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.round(R * c);
}

// 도보 분(minutes)을 '도보 약 N분' 또는 '도보 약 H시간 M분'으로 읽기 쉽게 포맷팅
function formatWalkDuration(minutes) {
  if (!minutes || minutes < 1) return '도보 약 1분';
  if (minutes < 60) {
    return `도보 약 ${minutes}분`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMin = minutes % 60;
  if (remainingMin === 0) {
    return `도보 약 ${hours}시간`;
  }
  return `도보 약 ${hours}시간 ${remainingMin}분`;
}

// 오산대 캠퍼스 정문 기준 도보 소요시간 포맷
function formatCampusDistance(distMeters) {
  if (distMeters === null || isNaN(distMeters)) return '학교 캠퍼스 인근';
  // 성인 보행 표준 속도: 시속 4.0km/h (분당 약 67m) 기준
  const walkMin = Math.max(1, Math.round(distMeters / 67));
  const walkTimeText = formatWalkDuration(walkMin);
  if (distMeters < 1000) {
    return `학교 정문에서 ${walkTimeText} (${distMeters}m)`;
  } else {
    const km = (distMeters / 1000).toFixed(1);
    return `학교 정문에서 약 ${km}km (${walkTimeText})`;
  }
}

/* ==========================================================================
   공강 시간 맞춤 맛집 추천 (내 시간표 기준)
   ========================================================================== */
let gapInfo = null;
let gapFilterActive = false;
const GAP_EAT_BUFFER_MIN = 15; // 왕복 시간 계산할 때 식사 시간으로 뺴두는 최소 버퍼

function loadGapInfo() {
  fetch('/api/timetable/courses/current-gap')
    .then(res => res.ok ? res.json() : null)
    .then(data => {
      gapInfo = data;
      renderGapBanner();
    })
    .catch(() => {});
}

// 지금 실제 공강이면 그 분(分), 수업 중이면 "그 수업 끝난 뒤" 생길 공강 분(미리보기) — 없으면 null
function getEffectiveGapMinutes() {
  if (!gapInfo) return null;
  if (gapInfo.status === 'gap') return gapInfo.gapMinutes;
  if (gapInfo.status === 'inClass' && gapInfo.upcomingGapMinutes >= 1) return gapInfo.upcomingGapMinutes;
  return null;
}

function renderGapBanner() {
  const banner = document.getElementById('gapRecommendBanner');
  const textEl = document.getElementById('gapRecommendText');
  const toggleBtn = document.getElementById('gapRecommendToggle');
  if (!banner || !textEl || !toggleBtn || !gapInfo) return;
  toggleBtn.classList.toggle('active', gapFilterActive);

  if (gapInfo.status === 'gap' && gapInfo.gapMinutes >= 1) {
    banner.style.display = 'flex';
    const nextText = gapInfo.nextClassName
      ? ` · 다음 수업 ${gapInfo.nextClassTime} ${gapInfo.nextClassName}`
      : ' · 오늘 남은 수업 없음';
    textEl.textContent = `지금 공강 ${gapInfo.gapMinutes}분${nextText}`;
    toggleBtn.style.display = '';
    toggleBtn.textContent = gapFilterActive ? '전체 다시 보기' : '왕복 가능 맛집만 보기';
  } else if (gapInfo.status === 'inClass') {
    banner.style.display = 'flex';
    const base = `지금 수업 중 (${gapInfo.currentClassName} · ${gapInfo.currentClassEndTime}까지)`;

    if (gapInfo.upcomingGapMinutes >= 1) {
      const nextText = gapInfo.upcomingGapNextClassName
        ? ` · 다음 수업 ${gapInfo.upcomingGapNextClassTime} ${gapInfo.upcomingGapNextClassName}`
        : ' · 오늘 남은 수업 없음';
      textEl.textContent = `${base} · 끝나면 ${gapInfo.upcomingGapMinutes}분 공강${nextText}`;
      toggleBtn.style.display = '';
      toggleBtn.textContent = gapFilterActive ? '전체 다시 보기' : '끝나고 갈 맛집 미리보기';
    } else {
      textEl.textContent = base;
      toggleBtn.style.display = 'none';
      if (gapFilterActive) {
        gapFilterActive = false;
        renderSidebarList(currentRestaurantList);
        renderMarkers(currentRestaurantList, false);
      }
    }
  } else {
    banner.style.display = 'none';
  }
}

function toggleGapFilter() {
  gapFilterActive = !gapFilterActive;
  if (gapFilterActive) {
    applyGapFilterAndRender();
  } else {
    renderSidebarList(currentRestaurantList);
    renderMarkers(currentRestaurantList, false);
  }
  renderGapBanner(); // 버튼 라벨("전체 다시 보기" <-> 추천 문구) 갱신
}

function applyGapFilterAndRender() {
  const effectiveGapMinutes = getEffectiveGapMinutes();
  if (effectiveGapMinutes === null) return;
  const maxOneWayMin = Math.floor((effectiveGapMinutes - GAP_EAT_BUFFER_MIN) / 2);

  if (maxOneWayMin < 1) {
    const listEl = document.getElementById('restaurantList');
    if (listEl) {
      listEl.innerHTML = `
        <div class="restaurant-empty-state">
          <p class="empty-title">공강 시간이 너무 짧아요</p>
          <p class="empty-desc">왕복 이동 + 식사 시간을 생각하면 지금은 다녀올 만한 곳이 마땅치 않아요.</p>
        </div>
      `;
    }
    renderMarkers([], false);
    return;
  }

  const filtered = currentRestaurantList.filter(r => {
    if (!r.latitude || !r.longitude) return false;
    const dist = calculateDistance(CAMPUS_LAT, CAMPUS_LNG, r.latitude, r.longitude);
    if (dist === null) return false;
    const walkMin = Math.max(1, Math.round(dist / 67));
    return walkMin <= maxOneWayMin;
  });

  renderSidebarList(filtered);
  renderMarkers(filtered, true);
}

// 식당 카테고리 및 정보에 따른 추천 편의 태그 생성
function generateFeatureTags(r) {
  const tags = [];
  const userTags = (r.tags && r.tags.trim()) ? r.tags.split(',').map(t => t.trim()).filter(Boolean) : [];

  // 등록자가 직접 입력한 태그만 표시한다. 카테고리 기반 추측 태그는 실제 가게 특성과
  // 무관할 수 있어(예: 샌드위치집에 "국밥맛집") 태그 미입력 시 아예 달지 않는다.
  tags.push(...userTags.map(t => '#' + t));

  if (r.rating && r.rating >= 4.5) {
    tags.unshift('⭐ 평점 4.5+ 검증 맛집');
  }
  if (r.reviewCount && r.reviewCount >= 3) {
    tags.push('💬 리뷰 활발');
  }

  return tags.map(tag => `<span class="feature-tag">${escapeHtml(tag)}</span>`).join('');
}

// 정렬 모드 변경
function changeSortMode(mode) {
  if (currentSortMode === mode) return;
  currentSortMode = mode;
  applySortAndRender();
}

// 현재 정렬 모드에 맞게 리스트 정렬 후 사이드바 및 마커 재렌더링
function applySortAndRender() {
  if (!currentRestaurantList || currentRestaurantList.length === 0) return;

  if (currentSortMode === 'rating') {
    currentRestaurantList.sort((a, b) => {
      const diff = (b.rating || 0) - (a.rating || 0);
      if (Math.abs(diff) > 0.01) return diff;
      return (b.reviewCount || 0) - (a.reviewCount || 0);
    });
  } else if (currentSortMode === 'review') {
    currentRestaurantList.sort((a, b) => {
      const diff = (b.reviewCount || 0) - (a.reviewCount || 0);
      if (diff !== 0) return diff;
      return (b.rating || 0) - (a.rating || 0);
    });
  } else if (currentSortMode === 'bookmark') {
    currentRestaurantList.sort((a, b) => {
      const diff = (b.bookmarkCount || 0) - (a.bookmarkCount || 0);
      if (diff !== 0) return diff;
      return (b.rating || 0) - (a.rating || 0);
    });
  } else {
    // recommend: DB id 순서 우선, 신규 카카오 검색은 뒤쪽 유지
    currentRestaurantList.sort((a, b) => {
      if (a.id && b.id) return a.id - b.id;
      if (a.id && !b.id) return -1;
      if (!a.id && b.id) return 1;
      return 0;
    });
  }

  renderSidebarList(currentRestaurantList);
  renderMarkers(currentRestaurantList, false);
}

// 카카오 지도 초기화
function initKakaoMap() {
  const container = document.getElementById('map');
  if (!container) return;

  const options = {
    center: new kakao.maps.LatLng(CAMPUS_LAT, CAMPUS_LNG),
    level: 4
  };

  map = new kakao.maps.Map(container, options);

  // 카카오 장소 검색 서비스 객체 초기화
  if (window.kakao && kakao.maps && kakao.maps.services) {
    placesService = new kakao.maps.services.Places();
  }
}

// 관리자가 ?viewUniversity=ID 로 다른 학교를 보는 중이면(window.VIEW_UNIVERSITY_ID, map/index.html에서 주입)
// 맛집 목록/검색 API 호출에도 그대로 실어 보낸다 — 안 그러면 지도 페이지는 전환됐는데 목록만 관리자 본인 학교로 남는다.
function viewUniversityQueryParam() {
  return window.VIEW_UNIVERSITY_ID ? `&viewUniversity=${window.VIEW_UNIVERSITY_ID}` : '';
}

// 기본 DB 맛집 목록 조회 및 마커 렌더링
function fetchRestaurants() {
  const keyword = document.getElementById('mapSearchInput') ? document.getElementById('mapSearchInput').value.trim() : '';
  const url = `/api/restaurants?category=${encodeURIComponent(currentCategory)}&keyword=${encodeURIComponent(keyword)}${viewUniversityQueryParam()}`;

  fetch(url)
    .then(res => {
      if (!res.ok) throw new Error('맛집 목록을 불러오지 못했습니다.');
      return res.json();
    })
    .then(data => {
      currentRestaurantList = data || [];
      if (gapFilterActive) {
        applyGapFilterAndRender();
      } else if (currentSortMode !== 'recommend') {
        applySortAndRender();
      } else {
        renderSidebarList(currentRestaurantList);
        renderMarkers(currentRestaurantList, false);
      }
    })
    .catch(err => {
      console.error('맛집 목록 로드 실패:', err);
      showToast('맛집 목록을 불러오지 못했습니다. 네트워크 상태를 확인해주세요.', 'error');
    });
}

// 사이드바 맛집 리스트 렌더링
function renderSidebarList(restaurants) {
  const listEl = document.getElementById('restaurantList');
  if (!listEl) return;

  const countEl = document.getElementById('restaurantCount');
  if (countEl) countEl.innerText = restaurants.length;

  const sortBarHtml = `
    <div class="restaurant-sort-bar" id="restaurantSortBar">
      <button type="button" class="sort-chip ${currentSortMode === 'recommend' ? 'active' : ''}" data-sort="recommend" onclick="changeSortMode('recommend')">추천순</button>
      <button type="button" class="sort-chip ${currentSortMode === 'rating' ? 'active' : ''}" data-sort="rating" onclick="changeSortMode('rating')"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg> 별점순</button>
      <button type="button" class="sort-chip ${currentSortMode === 'review' ? 'active' : ''}" data-sort="review" onclick="changeSortMode('review')"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg> 리뷰순</button>
      <button type="button" class="sort-chip ${currentSortMode === 'bookmark' ? 'active' : ''}" data-sort="bookmark" onclick="changeSortMode('bookmark')"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78z"/></svg> 찜순</button>
    </div>
  `;

  if (restaurants.length === 0) {
    listEl.innerHTML = `
      <div class="restaurant-count-bar">
        <span class="count-text">검색결과 <span>0</span>곳</span>
        <button type="button" class="btn-open-register" onclick="openRegisterModal()">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> 맛집 등록
        </button>
      </div>
      ${sortBarHtml}
      <div class="restaurant-empty-state">
        <div class="empty-icon"><svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8.2 6v4a1.4 1.4 0 0 0 2.8 0V6M9.6 6v12"/><path d="M15 6c-1.1 1.6-1.1 3.4 0 5v7"/></svg></div>
        <p class="empty-title">등록된 맛집이 없습니다</p>
        <p class="empty-desc">우리 학교 주변의 숨은 맛집이나 자주 가는 식당을<br>직접 등록하고 학우들과 공유해보세요!</p>
        <button type="button" class="btn-empty-register" onclick="openRegisterModal()"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> 첫 번째 맛집 직접 등록하기</button>
      </div>
    `;
    return;
  }

  let html = `
    <div class="restaurant-count-bar">
      <span class="count-text">검색결과 <span>${restaurants.length}</span>곳</span>
      <button type="button" class="btn-open-register" onclick="openRegisterModal()">
        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> 맛집 등록
      </button>
    </div>
    ${sortBarHtml}
  `;

  restaurants.forEach((r, idx) => {
    const hasThumb = r.imgUrl && r.imgUrl.trim();
    const thumbHtml = hasThumb
      ? `<img src="${r.imgUrl}" alt="${escapeHtml(r.name)}" class="place-thumb" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
         <div class="place-thumb place-thumb-empty" style="display:none;"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg></div>`
      : `<div class="place-thumb place-thumb-empty"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg></div>`;
    const isActive = (currentRestaurantId && currentRestaurantId === r.id) ? 'active' : '';
    const ratingDisplay = (r.rating && r.rating > 0) ? r.rating.toFixed(1) : 'NEW';
    const reviewsDisplay = (r.reviewCount && r.reviewCount > 0) ? `(리뷰 ${r.reviewCount})` : '(리뷰 0)';
    const animDelay = (idx * 0.03).toFixed(2);

    let distHtml = '';
    if (r.latitude && r.longitude) {
      const dist = calculateDistance(CAMPUS_LAT, CAMPUS_LNG, r.latitude, r.longitude);
      if (dist !== null) {
        const walkMin = Math.max(1, Math.round(dist / 67));
        const distStr = dist < 1000 ? `도보 ${walkMin}분` : `${(dist / 1000).toFixed(1)}km`;
        const walkTimeText = formatWalkDuration(walkMin);
        const badgeTitle = dist < 1000 ? `학교 캠퍼스로부터 ${walkTimeText}` : `학교 캠퍼스로부터 약 ${(dist / 1000).toFixed(1)}km (${walkTimeText})`;
        distHtml = `<span class="place-dist-badge" title="${escapeHtml(badgeTitle)}"><svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polygon points="3 11 22 2 13 21 11 13 3 11"/></svg> ${distStr}</span>`;
      }
    }

    html += `
      <div class="place-card ${isActive}" id="place-card-${idx}"
           style="animation-delay: ${animDelay}s;"
           onmouseenter="hoverMarker(${idx}, true)"
           onmouseleave="hoverMarker(${idx}, false)"
           onclick="selectRestaurantByIndex(${idx})">
        ${thumbHtml}
        <div class="place-info">
          <div>
            <div class="place-header">
              <h4 class="place-name">${escapeHtml(r.name)}</h4>
              <span class="place-category">${escapeHtml(r.category)}</span>
            </div>
            <div class="place-rating-row">
              <span class="place-star">★</span>
              <span class="place-score">${ratingDisplay}</span>
              <span class="place-reviews">${reviewsDisplay}</span>
              ${distHtml}
            </div>
            ${(r.description && r.description.trim()) ? `<p class="place-desc">${escapeHtml(r.description.trim())}</p>` : ''}
          </div>
          <div class="place-address">${escapeHtml(r.address || '')}</div>
        </div>
      </div>
    `;
  });

  listEl.innerHTML = html;
}

// 사이드바 카드 호버 시 지도 위 해당 마커 바운스/펄스 토글
function hoverMarker(idx, isHover) {
  const markerEl = document.getElementById('map-marker-' + idx);
  if (!markerEl) return;
  if (isHover) {
    markerEl.classList.add('bouncing');
  } else {
    markerEl.classList.remove('bouncing');
  }
}

// 지도 위 커스텀 오버레이 마커 렌더링
function renderMarkers(restaurants, adjustBounds = false) {
  // 기존 마커 모두 지도에서 제거
  markers.forEach(m => m.setMap(null));
  markers = [];

  if (!restaurants || restaurants.length === 0) return;

  const bounds = new kakao.maps.LatLngBounds();
  let validCount = 0;

  restaurants.forEach((r, idx) => {
    if (!r.latitude || !r.longitude) return;

    const position = new kakao.maps.LatLng(r.latitude, r.longitude);
    bounds.extend(position);
    validCount++;

    const content = document.createElement('div');
    content.className = 'kakao-custom-marker';
    content.id = 'map-marker-' + idx;
    const ratingHtml = (r.rating && r.rating > 0)
      ? `<span style="color:#FBBF24; margin-left:3px; font-weight:700;">★${r.rating.toFixed(1)}</span>`
      : '';
    content.innerHTML = `<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8.2 6v4a1.4 1.4 0 0 0 2.8 0V6M9.6 6v12"/><path d="M15 6c-1.1 1.6-1.1 3.4 0 5v7"/></svg> <span style="font-weight:600;">${escapeHtml(r.name)}</span> ${ratingHtml}`;
    content.onclick = () => selectRestaurantByIndex(idx);
    content.onmouseenter = () => {
      const card = document.getElementById('place-card-' + idx);
      if (card) card.classList.add('hover-highlight');
    };
    content.onmouseleave = () => {
      const card = document.getElementById('place-card-' + idx);
      if (card) card.classList.remove('hover-highlight');
    };

    const customOverlay = new kakao.maps.CustomOverlay({
      position: position,
      content: content,
      yAnchor: 1.4
    });

    customOverlay.setMap(map);
    markers.push(customOverlay);
  });

  // 검색 결과가 있고 adjustBounds가 true이면 모든 마커가 화면에 다 보이도록 지도 영역 자동 조절
  if (adjustBounds && validCount > 0 && map) {
    map.setBounds(bounds);
  }
}

// 카카오 키워드 장소 검색 수행 ('할매순대국' 등 검색 시)
function searchKeywordPlaces(keyword) {
  const listEl = document.getElementById('restaurantList');
  if (listEl) {
    listEl.innerHTML = `
      <div class="restaurant-count-bar">
        <span class="count-text">검색 중...</span>
        <button type="button" class="btn-open-register" onclick="openRegisterModal()">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> 맛집 등록
        </button>
      </div>
      <div style="padding: 40px 16px; text-align: center; color: #F97316; font-size: 13px;">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> '${escapeHtml(keyword)}' 맛집을 검색하고 있습니다...
      </div>
    `;
  }

  // 1. 기존 DB 검색
  const dbPromise = fetch(`/api/restaurants?category=${encodeURIComponent(currentCategory)}&keyword=${encodeURIComponent(keyword)}`)
    .then(res => res.json())
    .catch(() => []);

  // 2. 카카오 장소 검색 API 호출 (오산대 기준 반경 8km)
  if (!placesService && window.kakao && kakao.maps && kakao.maps.services) {
    placesService = new kakao.maps.services.Places();
  }

  const kakaoPromise = new Promise(resolve => {
    if (!placesService) {
      resolve([]);
      return;
    }

    const options = {
      location: new kakao.maps.LatLng(CAMPUS_LAT, CAMPUS_LNG),
      radius: 8000,
      size: 15
    };

    placesService.keywordSearch(keyword, (data, status) => {
      if (status === kakao.maps.services.Status.OK && data && data.length > 0) {
        resolve(data);
      } else {
        // 반경 내 검색 결과가 없으면 '오산 ' 접두어로 재검색
        placesService.keywordSearch(`${CAMPUS_REGION} ${keyword}`, (data2, status2) => {
          if (status2 === kakao.maps.services.Status.OK && data2 && data2.length > 0) {
            resolve(data2);
          } else {
            resolve([]);
          }
        });
      }
    }, options);
  });

  Promise.all([dbPromise, kakaoPromise]).then(([dbList, kakaoList]) => {
    const combined = [];
    const nameSet = new Set();

    // DB 등록된 맛집 먼저 추가
    if (Array.isArray(dbList)) {
      dbList.forEach(r => {
        combined.push(r);
        nameSet.add(r.name.replace(/\s+/g, ''));
      });
    }

    // 카카오 검색 결과 중복 제외 후 추가
    if (Array.isArray(kakaoList)) {
      kakaoList.forEach(p => {
        const cleanName = p.place_name.replace(/\s+/g, '');
        if (nameSet.has(cleanName)) return;
        nameSet.add(cleanName);

        const cat = parseCategory(p.category_name);
        if (currentCategory !== '전체' && cat !== currentCategory) {
          return;
        }

        combined.push({
          id: null, // 미등록 상태 (클릭 시 등록 확인 화면)
          kakaoId: p.id,
          name: p.place_name,
          category: cat,
          address: p.road_address_name || p.address_name,
          phone: p.phone || '',
          latitude: parseFloat(p.y),
          longitude: parseFloat(p.x),
          description: '',
          imgUrl: null,
          rating: 0.0,
          reviewCount: 0,
          bookmarkCount: 0,
          isBookmarked: false
        });
      });
    }

    currentRestaurantList = combined;
    if (currentSortMode !== 'recommend') {
      applySortAndRender();
    } else {
      renderSidebarList(combined);
      // 검색 시 모든 마커가 화면에 쭉 보이도록 adjustBounds=true 호출!
      renderMarkers(combined, true);
    }
  });
}

// 맛집 인덱스로 선택 (상세 모달 오픈 & DB 동기화)
function selectRestaurantByIndex(idx) {
  const r = currentRestaurantList[idx];
  if (!r) return;

  // 사이드바 카드 활성화 효과 & 펄스 하이라이트
  document.querySelectorAll('.place-card').forEach(c => c.classList.remove('active'));
  const targetCard = document.getElementById(`place-card-${idx}`);
  if (targetCard) {
    targetCard.classList.add('active');
    targetCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    targetCard.classList.remove('pulse-highlight');
    void targetCard.offsetWidth; // trigger reflow
    targetCard.classList.add('pulse-highlight');
    setTimeout(() => {
      if (targetCard) targetCard.classList.remove('pulse-highlight');
    }, 600);
  }

  // 지도 부드러운 중심 이동
  if (map && r.latitude && r.longitude) {
    map.panTo(new kakao.maps.LatLng(r.latitude, r.longitude));
  }

  // 마커 펄스 발광 효과
  const markerEl = document.getElementById('map-marker-' + idx);
  if (markerEl) {
    markerEl.classList.add('highlighted');
    setTimeout(() => {
      if (markerEl) markerEl.classList.remove('highlighted');
    }, 1200);
  }

  // 1) 이미 DB에 등록된 식당인 경우
  if (r.id) {
    currentRestaurantId = r.id;
    fetch(`/api/restaurants/${r.id}`)
      .then(res => res.json())
      .then(data => {
        if (r.kakaoId && !data.restaurant.kakaoId) {
          data.restaurant.kakaoId = r.kakaoId;
        }
        populateAndOpenModal(data.restaurant, data.reviews);
      })
      .catch(err => {
        console.error('맛집 상세 로드 실패:', err);
        showToast('맛집 정보를 불러오지 못했습니다. 다시 시도해주세요.', 'error');
      });
  } else {
    // 2) 카카오 검색으로만 찾은 미등록 식당 -> 자동 저장하지 않고, 등록 확인 화면에서 '등록하기'를 눌러야 저장
    openRegisterModal(); // 내부에서 quickPlace를 초기화하므로 그 뒤에 채운다
    quickPlace = {
      name: r.name,
      category: r.category,
      address: r.address,
      phone: r.phone || '',
      latitude: r.latitude,
      longitude: r.longitude
    };
    document.getElementById('quickPlaceName').textContent = quickPlace.name;
    document.getElementById('quickPlaceAddr').textContent = quickPlace.address;
    resetRegImage('quick');
    updateQuickSubmitLabel();
    showQuickConfirm(true);
  }
}

// 하위 호환용 ID 기반 선택
function selectRestaurant(id) {
  const idx = currentRestaurantList.findIndex(x => x.id === id);
  if (idx !== -1) {
    selectRestaurantByIndex(idx);
  } else {
    currentRestaurantId = id;
    fetch(`/api/restaurants/${id}`)
      .then(res => res.json())
      .then(data => {
        if (map && data.restaurant) {
          map.panTo(new kakao.maps.LatLng(data.restaurant.latitude, data.restaurant.longitude));
        }
        populateAndOpenModal(data.restaurant, data.reviews);
      })
      .catch(err => {
        console.error('맛집 상세 로드 실패:', err);
        showToast('맛집 정보를 불러오지 못했습니다. 다시 시도해주세요.', 'error');
      });
  }
}

// 모달 엘리먼트 데이터 채우기 및 열기
function populateAndOpenModal(r, reviews) {
  const adminDelBtn = document.getElementById('modalAdminDeleteBtn');
  if (adminDelBtn) adminDelBtn.style.display = window.CURRENT_MEMBER_IS_ADMIN ? '' : 'none';
  const modalImg = document.getElementById('modalImg');
  const modalImgEmpty = document.getElementById('modalImgEmpty');
  const hasHeroImg = r.imgUrl && r.imgUrl.trim();
  if (modalImg) {
    modalImg.style.display = hasHeroImg ? '' : 'none';
    modalImg.src = hasHeroImg ? r.imgUrl : '';
  }
  if (modalImgEmpty) modalImgEmpty.style.display = hasHeroImg ? 'none' : 'flex';
  currentRestaurantHasImg = !!hasHeroImg;
  updateReviewCoverConsentVisibility();

  document.getElementById('modalName').innerText = r.name;
  document.getElementById('modalCategory').innerText = r.category;
  document.getElementById('modalRating').innerText = (r.rating || 0).toFixed(1);
  document.getElementById('modalReviewCount').innerText = `(리뷰 ${r.reviewCount || 0}개)`;
  const badgeEl = document.getElementById('modalReviewBadge');
  if (badgeEl) badgeEl.innerText = r.reviewCount || 0;

  const descEl = document.getElementById('modalDesc');
  if (descEl) {
    const descVal = (r.description && r.description.trim()) ? r.description.trim() : '';
    if (descVal) {
      descEl.innerText = descVal;
      descEl.style.display = 'block';
    } else {
      descEl.innerText = '';
      descEl.style.display = 'none';
    }
  }
  document.getElementById('modalAddress').innerText = r.address;

  // 오산대학교 정문 기준 도보 거리 계산 및 포맷
  const distanceEl = document.getElementById('modalDistance');
  if (distanceEl) {
    if (r.latitude && r.longitude) {
      const dist = calculateDistance(CAMPUS_LAT, CAMPUS_LNG, r.latitude, r.longitude);
      distanceEl.innerText = formatCampusDistance(dist);
    } else {
      distanceEl.innerText = '학교 캠퍼스 인근';
    }
  }

  // 추천 편의 태그 뱃지 생성 (달 태그가 없으면 섹션 자체를 숨김)
  const tagsEl = document.getElementById('modalFeatureTags');
  if (tagsEl) {
    const tagsHtml = generateFeatureTags(r);
    tagsEl.innerHTML = tagsHtml;
    tagsEl.style.display = tagsHtml ? '' : 'none';
  }

  // 전화번호 행 표시/숨김
  const phoneVal = r.phone && r.phone.trim() ? r.phone.trim() : '';
  const phoneRow = document.getElementById('modalPhoneRow');
  const phoneEl = document.getElementById('modalPhone');

  if (phoneEl) phoneEl.innerText = phoneVal || '등록된 번호 없음';
  if (phoneRow) phoneRow.style.display = phoneVal ? 'flex' : 'none';

  // 실시간 도보 길찾기 대상 식당 저장 및 버튼 활성화
  activeRouteRestaurant = r;
  const routeBtn = document.getElementById('modalRouteBtn');
  if (routeBtn) {
    if (r.latitude && r.longitude) {
      routeBtn.style.display = 'inline-flex';
    } else {
      routeBtn.style.display = 'none';
    }
  }

  // 카카오맵 장소 상세 링크 바인딩
  const kakaoPlaceBtn = document.getElementById('modalKakaoPlaceBtn');
  if (kakaoPlaceBtn) {
    if (r.kakaoId) {
      kakaoPlaceBtn.href = `https://place.map.kakao.com/${r.kakaoId}`;
      kakaoPlaceBtn.style.display = 'inline-flex';
    } else if (r.latitude && r.longitude) {
      kakaoPlaceBtn.href = `https://map.kakao.com/link/map/${encodeURIComponent(r.name)},${r.latitude},${r.longitude}`;
      kakaoPlaceBtn.style.display = 'inline-flex';
    } else {
      kakaoPlaceBtn.style.display = 'none';
    }
  }

  // 찜 버튼 상태
  updateBookmarkBtnUI(r.isBookmarked, r.bookmarkCount || 0);

  // 리뷰 목록 렌더링
  renderReviews(reviews || []);

  // 모달 열기
  openRestaurantModal();
}

// 모달 열기 / 닫기
function openRestaurantModal() {
  const modal = document.getElementById('restaurantModal');
  if (modal) {
    modal.style.display = 'flex';
  }
}

function closeRestaurantModal() {
  const modal = document.getElementById('restaurantModal');
  if (modal) {
    modal.style.display = 'none';
  }
  document.querySelectorAll('.place-card').forEach(c => c.classList.remove('active'));
}

// ==========================================================================
// 사이트 내 실시간 도보 길찾기 (In-Page Walking Route)
// ==========================================================================

// 모달에서 '도보 길찾기' 버튼 클릭 시
function startInPageRoutingCurrent() {
  let target = activeRouteRestaurant;
  if (!target && currentRestaurantId) {
    target = currentRestaurantList.find(x => x.id === currentRestaurantId);
  }
  if (!target) return;

  // 모달 닫기
  closeRestaurantModal();

  // 학교 정문 출발 기본값으로 길찾기 시작
  routeOriginMode = 'campus';
  findRouteToRestaurant(target, routeOriginMode);
}

// 길찾기 실행
function findRouteToRestaurant(r, originMode) {
  if (!r || !r.latitude || !r.longitude) {
    showToast('식당의 위치 좌표 정보가 없습니다.', 'error');
    return;
  }

  activeRouteRestaurant = r;
  const banner = document.getElementById('mapRouteBanner');
  if (banner) banner.style.display = 'block';

  const durationEl = document.getElementById('routeDurationText');
  const distanceEl = document.getElementById('routeDistanceText');
  const originBadgeEl = document.getElementById('routeOriginBadge');
  const destTextEl = document.getElementById('routeDestinationText');
  const kakaoAppBtn = document.getElementById('bannerKakaoAppBtn');
  const toggleBtn = document.getElementById('btnToggleOrigin');

  if (kakaoAppBtn) {
    kakaoAppBtn.href = `https://map.kakao.com/link/to/${encodeURIComponent(r.name)},${r.latitude},${r.longitude}`;
  }

  if (durationEl) durationEl.innerText = '경로 탐색 중...';
  if (distanceEl) distanceEl.innerText = '';
  if (destTextEl) destTextEl.innerHTML = `도착: <strong>${escapeHtml(r.name)}</strong>`;

  if (originMode === 'myLocation') {
    if (originBadgeEl) originBadgeEl.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg> 내 현재 위치 출발';
    if (toggleBtn) toggleBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 22V8l8-5 8 5v14"/><path d="M9 22v-6h6v6"/><line x1="4" y1="12" x2="20" y2="12"/></svg> 학교 정문으로 변경';

    if (userCurrentLatLng) {
      requestAndDrawRoute(userCurrentLatLng.lat, userCurrentLatLng.lng, r, '내 현재 위치');
    } else if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        pos => {
          userCurrentLatLng = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          requestAndDrawRoute(userCurrentLatLng.lat, userCurrentLatLng.lng, r, '내 현재 위치');
        },
        err => {
          console.warn('GPS 위치 권한 획득 실패, 학교 정문으로 대체:', err);
          showToast('현재 위치 정보를 가져올 수 없어 학교 정문 기준으로 안내합니다.', 'info');
          routeOriginMode = 'campus';
          if (originBadgeEl) originBadgeEl.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 22V8l8-5 8 5v14"/><path d="M9 22v-6h6v6"/><line x1="4" y1="12" x2="20" y2="12"/></svg> 학교 정문 출발';
          if (toggleBtn) toggleBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg> 내 위치로 변경';
          requestAndDrawRoute(CAMPUS_LAT, CAMPUS_LNG, r, '학교 정문');
        },
        { timeout: 8000 }
      );
    } else {
      showToast('브라우저가 위치 정보를 지원하지 않아 학교 정문 기준으로 안내합니다.', 'info');
      routeOriginMode = 'campus';
      requestAndDrawRoute(CAMPUS_LAT, CAMPUS_LNG, r, '학교 정문');
    }
  } else {
    if (originBadgeEl) originBadgeEl.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M4 22V8l8-5 8 5v14"/><path d="M9 22v-6h6v6"/><line x1="4" y1="12" x2="20" y2="12"/></svg> 학교 정문 출발';
    if (toggleBtn) toggleBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg> 내 위치로 변경';
    requestAndDrawRoute(CAMPUS_LAT, CAMPUS_LNG, r, '학교 정문');
  }
}

// 실제 보행자 도로망 API(OSRM) 호출 및 지도 위 렌더링
function requestAndDrawRoute(startLat, startLng, destRestaurant, startName) {
  const destLat = destRestaurant.latitude;
  const destLng = destRestaurant.longitude;

  const url = `https://router.project-osrm.org/route/v1/walking/${startLng},${startLat};${destLng},${destLat}?overview=full&geometries=geojson`;

  fetch(url)
    .then(res => res.json())
    .then(data => {
      if (data && data.code === 'Ok' && data.routes && data.routes.length > 0) {
        const route = data.routes[0];
        const coords = route.geometry.coordinates; // [[lng, lat], ...]
        const path = coords.map(c => new kakao.maps.LatLng(c[1], c[0]));
        const distMeters = Math.round(route.distance);
        
        // ※ 주의: OSRM 공개 데모 서버는 URL 경로에 /walking/이 지정되어도 내부적으로 차량(자동차) 주행 속도(약 37~40km/h) 프로필로 duration을 반환합니다.
        // 이로 인해 4.1km 도로 거리에 대해 약 6.5분(394초)의 duration이 반환되어 '도보 약 7분'으로 왜곡되는 현상이 발생했습니다.
        // 따라서 도로망 실거리(distMeters)를 바탕으로 성인 표준 보행 속도(시속 4.0km/h = 분당 약 67m)를 적용해 실제 도보 소요 시간을 정확히 산출합니다.
        const walkMin = Math.max(1, Math.round(distMeters / 67));

        drawRouteOnMap(path, startLat, startLng, destLat, destLng, startName, destRestaurant.name);
        updateRouteBanner(walkMin, distMeters, startName, destRestaurant.name);
      } else {
        // Fallback: direct line if OSRM gives no route
        const directDist = calculateDistance(startLat, startLng, destLat, destLng) || 300;
        // 도로망 굴곡률(약 1.25배) 감안한 실제 보행 거리 추산
        const estWalkingDist = Math.round(directDist * 1.25);
        const walkMin = Math.max(1, Math.round(estWalkingDist / 67));
        const path = [new kakao.maps.LatLng(startLat, startLng), new kakao.maps.LatLng(destLat, destLng)];
        drawRouteOnMap(path, startLat, startLng, destLat, destLng, startName, destRestaurant.name);
        updateRouteBanner(walkMin, estWalkingDist, startName, destRestaurant.name);
      }
    })
    .catch(err => {
      console.error('보행자 경로 탐색 실패, 직선 보간으로 대체:', err);
      const directDist = calculateDistance(startLat, startLng, destLat, destLng) || 300;
      const estWalkingDist = Math.round(directDist * 1.25);
      const walkMin = Math.max(1, Math.round(estWalkingDist / 67));
      const path = [new kakao.maps.LatLng(startLat, startLng), new kakao.maps.LatLng(destLat, destLng)];
      drawRouteOnMap(path, startLat, startLng, destLat, destLng, startName, destRestaurant.name);
      updateRouteBanner(walkMin, estWalkingDist, startName, destRestaurant.name);
    });
}

// 지도 위 경로선(Polyline) 및 출발/도착 플래그 오버레이 그리기
function drawRouteOnMap(path, startLat, startLng, destLat, destLng, startName, destName) {
  clearRouteLayers();

  // 외곽선 (글로우 / 그림자 효과)
  currentRoutePolylineOutline = new kakao.maps.Polyline({
    map: map,
    path: path,
    strokeWeight: 9,
    strokeColor: '#C2410C',
    strokeOpacity: 0.35,
    strokeStyle: 'solid'
  });

  // 메인 경로선 (유니피드 오렌지 테마)
  currentRoutePolyline = new kakao.maps.Polyline({
    map: map,
    path: path,
    strokeWeight: 5,
    strokeColor: '#F97316',
    strokeOpacity: 0.95,
    strokeStyle: 'solid'
  });

  // 🚩 출발 마커 오버레이
  const startContent = document.createElement('div');
  startContent.className = 'route-flag-overlay start-flag';
  startContent.innerHTML = `<span class="flag-icon">🚩</span> <span class="flag-label">${escapeHtml(startName)}</span>`;
  currentStartMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(startLat, startLng),
    content: startContent,
    yAnchor: 1.3
  });
  currentStartMarker.setMap(map);

  // 🎯 도착 마커 오버레이
  const endContent = document.createElement('div');
  endContent.className = 'route-flag-overlay end-flag';
  endContent.innerHTML = `<span class="flag-icon">🎯</span> <span class="flag-label">${escapeHtml(destName)}</span>`;
  currentEndMarker = new kakao.maps.CustomOverlay({
    position: new kakao.maps.LatLng(destLat, destLng),
    content: endContent,
    yAnchor: 1.3
  });
  currentEndMarker.setMap(map);

  // 경로 전체가 화면에 꽉 차도록 지도 줌/시야 자동 조절
  if (map && path.length > 0) {
    const bounds = new kakao.maps.LatLngBounds();
    path.forEach(p => bounds.extend(p));
    map.setBounds(bounds);
  }
}

// 배너 텍스트 갱신
function updateRouteBanner(minutes, meters, startName, destName) {
  const durationEl = document.getElementById('routeDurationText');
  const distanceEl = document.getElementById('routeDistanceText');
  const destTextEl = document.getElementById('routeDestinationText');

  if (durationEl) durationEl.innerText = formatWalkDuration(minutes);
  if (distanceEl) {
    const distStr = meters < 1000 ? `${meters}m` : `${(meters / 1000).toFixed(1)}km`;
    distanceEl.innerText = `(${distStr})`;
  }
  if (destTextEl) {
    destTextEl.innerHTML = `${escapeHtml(startName)} ➔ <strong>${escapeHtml(destName)}</strong>`;
  }
}

// 출발지 토글 (학교 정문 <-> 내 위치)
function toggleRouteOrigin() {
  if (!activeRouteRestaurant) return;
  routeOriginMode = (routeOriginMode === 'campus') ? 'myLocation' : 'campus';
  findRouteToRestaurant(activeRouteRestaurant, routeOriginMode);
}

// 지도 위 경로선 및 마커 레이어만 제거
function clearRouteLayers() {
  if (currentRoutePolyline) {
    currentRoutePolyline.setMap(null);
    currentRoutePolyline = null;
  }
  if (currentRoutePolylineOutline) {
    currentRoutePolylineOutline.setMap(null);
    currentRoutePolylineOutline = null;
  }
  if (currentStartMarker) {
    currentStartMarker.setMap(null);
    currentStartMarker = null;
  }
  if (currentEndMarker) {
    currentEndMarker.setMap(null);
    currentEndMarker = null;
  }
}

// 길찾기 완전 종료
function clearCurrentRoute() {
  clearRouteLayers();
  activeRouteRestaurant = null;
  const banner = document.getElementById('mapRouteBanner');
  if (banner) banner.style.display = 'none';

  // 원래 학교 캠퍼스 및 마커 중심으로 복귀
  if (map) {
    map.panTo(new kakao.maps.LatLng(CAMPUS_LAT, CAMPUS_LNG));
    map.setLevel(4);
  }
}

// 찜 버튼 UI 갱신
function updateBookmarkBtnUI(isBookmarked, bookmarkCount) {
  const btn = document.getElementById('modalBookmarkBtn');
  const icon = document.getElementById('modalBookmarkIcon');
  const text = document.getElementById('modalBookmarkText');
  if (!btn || !icon || !text) return;

  const heartSvg = fill => `<svg width="14" height="14" viewBox="0 0 24 24" fill="${fill}" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78z"/></svg>`;
  if (isBookmarked) {
    btn.classList.add('active');
    icon.innerHTML = heartSvg('currentColor');
    text.innerText = `저장됨 (${bookmarkCount})`;
  } else {
    btn.classList.remove('active');
    icon.innerHTML = heartSvg('none');
    text.innerText = `저장 (${bookmarkCount})`;
  }
}

// 찜 토글 실행
function toggleBookmarkCurrent() {
  if (!currentRestaurantId) return;

  const btn = document.getElementById('modalBookmarkBtn');
  if (btn) {
    btn.classList.remove('pop');
    void btn.offsetWidth;
    btn.classList.add('pop');
    setTimeout(() => {
      if (btn) btn.classList.remove('pop');
    }, 400);
  }

  fetch(`/api/restaurants/${currentRestaurantId}/bookmark`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  })
    .then(res => {
      if (res.status === 401) {
        showToast('로그인이 필요한 기능입니다.', 'info');
        setTimeout(() => { location.href = '/login'; }, 900);
        return;
      }
      return res.json();
    })
    .then(data => {
      if (data && data.success) {
        updateBookmarkBtnUI(data.bookmarked, data.bookmarkCount);
        const target = currentRestaurantList.find(x => x.id === currentRestaurantId);
        if (target) {
          target.isBookmarked = data.bookmarked;
          target.bookmarkCount = data.bookmarkCount;
        }
      }
    })
    .catch(err => {
      console.error('찜 토글 실패:', err);
      showToast('찜하기 처리에 실패했습니다. 다시 시도해주세요.', 'error');
    });
}

// 리뷰 목록 렌더링 (UNIFEED detail.html 댓글 스타일 적용)
function renderReviews(reviews) {
  const listEl = document.getElementById('modalReviewList');
  if (!listEl) return;

  if (!reviews || reviews.length === 0) {
    listEl.innerHTML = `
      <div class="review-empty">
        <span class="review-empty-icon"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg></span>
        <span class="review-empty-text">아직 작성된 방문자 리뷰가 없습니다</span>
        <p style="margin:0; font-size:12px;">이 맛집의 첫 번째 리뷰어가 되어보세요!</p>
      </div>
    `;
    return;
  }

  currentReviewsList = reviews; // 수정 버튼 눌렀을 때 원본 데이터 찾으려고 캐시

  let html = '';
  reviews.forEach(rev => {
    const stars = '★'.repeat(rev.rating) + '☆'.repeat(5 - rev.rating);
    const hasAvatar = rev.authorProfileImage && rev.authorProfileImage.trim();
    const isOwn = window.CURRENT_MEMBER_ID && rev.authorId === window.CURRENT_MEMBER_ID;

    html += `
      <div class="review-item">
        <div class="review-avatar">
          ${hasAvatar
            ? `<img src="${rev.authorProfileImage}" alt="프로필" class="review-avatar-img" onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
               <div class="review-avatar-default" style="display:none;"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></div>`
            : `<div class="review-avatar-default"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg></div>`
          }
        </div>
        <div class="review-body">
          <div class="review-meta">
            <span class="review-author-name">${escapeHtml(rev.authorName)}</span>
            <div class="review-meta-right">
              <span class="review-date">${rev.createdAt}</span>
              ${isOwn ? `
                <div class="review-actions">
                  <button type="button" class="review-act-btn" onclick="editReview(${rev.id})">수정</button>
                  <span class="review-act-divider">·</span>
                  <button type="button" class="review-act-btn del" onclick="deleteReviewItem(${rev.id})">삭제</button>
                </div>
              ` : `
                <div class="review-actions">
                  <button type="button" class="review-act-btn" data-target-id="${rev.id}" data-target-label="${escapeHtml(rev.authorName)} 리뷰" onclick="openReportModal('REVIEW', this.dataset.targetId, this.dataset.targetLabel)">신고</button>
                  ${window.CURRENT_MEMBER_IS_ADMIN ? `
                    <span class="review-act-divider">·</span>
                    <button type="button" class="review-act-btn del" onclick="deleteReviewItem(${rev.id})">🛠️ 관리자 삭제</button>
                  ` : ''}
                </div>
              `}
            </div>
          </div>
          <div class="review-stars">${stars}</div>
          <p class="review-text">${escapeHtml(rev.content)}</p>
          ${rev.imgUrl ? `<img src="${rev.imgUrl}" alt="리뷰 사진" class="review-photo">` : ''}
        </div>
      </div>
    `;
  });

  listEl.innerHTML = html;
}

// 리뷰 사진 선택 -> 업로드 (게시글 작성과 같은 /api/uploads 엔드포인트 재사용)
let reviewImageUploading = false;

function handleReviewImageSelect(e) {
  const file = e.target.files[0];
  if (!file) return;

  const status = document.getElementById('reviewImgUploadStatus');
  const preview = document.getElementById('reviewImgPreview');
  const boxText = document.getElementById('reviewImgUploadBoxText');
  const formData = new FormData();
  formData.append('file', file);

  reviewImageUploading = true;
  boxText.textContent = file.name;
  status.textContent = '업로드 중...';
  status.className = 'img-upload-status uploading';

  fetch(window.csrfParam('/api/uploads'), { method: 'POST', body: formData })
    .then(res => res.json().then(data => ({ ok: res.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) {
        status.textContent = data.message || '업로드에 실패했습니다.';
        status.className = 'img-upload-status error';
        return;
      }
      document.getElementById('reviewImgUrl').value = data.url;
      preview.src = data.url;
      preview.style.display = 'block';
      status.textContent = '업로드 완료';
      status.className = 'img-upload-status success';
      updateReviewCoverConsentVisibility();
    })
    .catch(() => {
      status.textContent = '네트워크 오류로 업로드하지 못했습니다.';
      status.className = 'img-upload-status error';
    })
    .finally(() => { reviewImageUploading = false; });
}

function resetReviewImageState() {
  document.getElementById('reviewImgUrl').value = '';
  document.getElementById('reviewImgFile').value = '';
  document.getElementById('reviewImgUploadBoxText').textContent = '사진 추가 (선택, jpg·png·gif·webp)';
  document.getElementById('reviewImgUploadStatus').textContent = '';
  document.getElementById('reviewImgUploadStatus').className = 'img-upload-status';
  const preview = document.getElementById('reviewImgPreview');
  preview.style.display = 'none';
  preview.src = '';
  updateReviewCoverConsentVisibility();
}

// 사진이 첨부돼 있고 & 현재 맛집에 아직 대표 사진이 없을 때만 "대표사진으로 사용" 동의 체크박스를 보여준다.
function updateReviewCoverConsentVisibility() {
  const row = document.getElementById('reviewCoverConsentRow');
  const consentBox = document.getElementById('reviewCoverConsent');
  if (!row || !consentBox) return;
  const hasDraftImg = document.getElementById('reviewImgUrl').value.trim() !== '';
  const shouldShow = hasDraftImg && !currentRestaurantHasImg;
  row.style.display = shouldShow ? 'flex' : 'none';
  if (!shouldShow) consentBox.checked = false;
}

// 별점 선택 인터랙션
function setRating(val) {
  currentRating = val;
  const stars = document.querySelectorAll('#starPicker .star-pick');
  stars.forEach((s, idx) => {
    if (idx < val) {
      s.classList.add('active');
    } else {
      s.classList.remove('active');
    }
    if (idx === val - 1) {
      s.classList.remove('star-bump');
      void s.offsetWidth;
      s.classList.add('star-bump');
      setTimeout(() => s.classList.remove('star-bump'), 300);
    }
  });
  const label = document.getElementById('starScoreLabel');
  if (label) label.innerText = `${val}.0점`;
}

// 리뷰 등록
function submitReview() {
  if (!currentRestaurantId) return;
  const input = document.getElementById('reviewContentInput');
  const content = input ? input.value.trim() : '';

  if (!content) {
    showToast('리뷰 내용을 입력해주세요.', 'error');
    if (input) input.focus();
    return;
  }
  if (reviewImageUploading) {
    showToast('이미지 업로드가 끝난 뒤 등록해주세요.', 'info');
    return;
  }

  const imgUrl = document.getElementById('reviewImgUrl').value;
  const consentBox = document.getElementById('reviewCoverConsent');
  const useAsCover = !!(consentBox && consentBox.checked);
  const isEdit = editingReviewId !== null;
  const url = isEdit ? `/api/restaurants/reviews/${editingReviewId}` : `/api/restaurants/${currentRestaurantId}/reviews`;

  const submitBtn = document.getElementById('reviewSubmitBtn');
  setButtonLoading(submitBtn, true, isEdit ? '수정 중...' : '등록 중...');

  fetch(url, {
    method: isEdit ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rating: currentRating, content: content, imgUrl: imgUrl, useAsCover: useAsCover })
  })
    .then(res => {
      if (res.status === 401) {
        showToast('로그인이 필요한 기능입니다.', 'info');
        setTimeout(() => { location.href = '/login'; }, 900);
        return;
      }
      if (!res.ok) {
        return res.json().then(d => { throw new Error(d.message || (isEdit ? '리뷰 수정 실패' : '리뷰 등록 실패')); });
      }
      return res.json();
    })
    .then(data => {
      setButtonLoading(submitBtn, false);
      if (data && data.success) {
        showToast(isEdit ? '리뷰가 수정되었습니다.' : '소중한 맛집 리뷰가 등록되었습니다! 마이페이지 [내 리뷰]에서도 확인하실 수 있습니다.', 'success');
        if (input) input.value = '';
        resetReviewImageState();
        cancelReviewEdit();

        // 리뷰 목록 갱신
        selectRestaurant(currentRestaurantId);

        // 사이드바 목록 및 마커 평점 갱신
        const target = currentRestaurantList.find(x => x.id === currentRestaurantId);
        if (target && data.restaurant) {
          target.rating = data.restaurant.rating;
          target.reviewCount = data.restaurant.reviewCount;
          renderSidebarList(currentRestaurantList);
          renderMarkers(currentRestaurantList, false);
        }
      }
    })
    .catch(err => {
      setButtonLoading(submitBtn, false);
      showToast(err.message || (isEdit ? '리뷰 수정 중 오류가 발생했습니다.' : '리뷰 등록 중 오류가 발생했습니다.'), 'error');
    });
}

// 리뷰 "수정" 클릭 -> 작성 카드에 기존 값 채우고 수정 모드로 전환
function editReview(reviewId) {
  const rev = currentReviewsList.find(r => r.id === reviewId);
  if (!rev) return;

  editingReviewId = reviewId;

  const input = document.getElementById('reviewContentInput');
  if (input) input.value = rev.content;
  setRating(rev.rating);

  if (rev.imgUrl) {
    document.getElementById('reviewImgUrl').value = rev.imgUrl;
    document.getElementById('reviewImgUploadBoxText').textContent = '기존 사진이 등록되어 있습니다 · 클릭해서 변경';
    const preview = document.getElementById('reviewImgPreview');
    preview.src = rev.imgUrl;
    preview.style.display = 'block';
  } else {
    resetReviewImageState();
  }
  updateReviewCoverConsentVisibility();

  const submitBtn = document.getElementById('reviewSubmitBtn');
  if (submitBtn) submitBtn.textContent = '리뷰 수정 저장';
  const cancelBtn = document.getElementById('reviewCancelEditBtn');
  if (cancelBtn) cancelBtn.style.display = '';

  const card = document.querySelector('.review-write-card');
  if (card) card.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// 리뷰 수정 취소 -> 작성 카드를 새 리뷰 작성 상태로 되돌림
function cancelReviewEdit() {
  editingReviewId = null;
  const submitBtn = document.getElementById('reviewSubmitBtn');
  if (submitBtn) submitBtn.textContent = '리뷰 등록';
  const cancelBtn = document.getElementById('reviewCancelEditBtn');
  if (cancelBtn) cancelBtn.style.display = 'none';
}

// 리뷰 삭제
function deleteReviewItem(reviewId) {
  if (!confirm('이 리뷰를 삭제할까요?')) return;

  fetch(`/api/restaurants/reviews/${reviewId}`, { method: 'DELETE' })
    .then(res => res.json().then(data => ({ ok: res.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) {
        showToast(data.message || '리뷰를 삭제하지 못했습니다.', 'error');
        return;
      }
      showToast('리뷰가 삭제되었습니다.', 'success');
      if (editingReviewId === reviewId) cancelReviewEdit();
      selectRestaurant(currentRestaurantId);
    })
    .catch(() => showToast('네트워크 오류로 리뷰를 삭제하지 못했습니다.', 'error'));
}

// 맛집 삭제 (관리자 전용 — 서버에서도 관리자 검사)
function deleteRestaurantCurrent() {
  if (!currentRestaurantId) return;
  if (!confirm('이 맛집을 삭제할까요? 리뷰와 저장 기록도 함께 삭제되며 되돌릴 수 없습니다.')) return;

  fetch(`/api/restaurants/${currentRestaurantId}`, { method: 'DELETE' })
    .then(res => res.json().then(data => ({ ok: res.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) {
        showToast(data.message || '맛집을 삭제하지 못했습니다.', 'error');
        return;
      }
      showToast('맛집이 삭제되었습니다.', 'success');
      currentRestaurantId = null;
      closeRestaurantModal();
      fetchRestaurants();
    })
    .catch(() => showToast('네트워크 오류로 맛집을 삭제하지 못했습니다.', 'error'));
}

// 바깥 클릭 및 ESC 키 닫기 이벤트
window.addEventListener('click', function(e) {
  const modal = document.getElementById('restaurantModal');
  if (e.target === modal) {
    closeRestaurantModal();
  }
});

window.addEventListener('keydown', function(e) {
  if (e.key === 'Escape') {
    closeRestaurantModal();
  }
});

// 카테고리 선택 필터
function selectCategory(cat) {
  currentCategory = cat;
  document.querySelectorAll('.category-chip').forEach(btn => {
    if (btn.innerText.includes(cat) || (cat === '전체' && btn.innerText === '전체')) {
      btn.classList.add('active');
    } else {
      btn.classList.remove('active');
    }
  });

  const keyword = document.getElementById('mapSearchInput') ? document.getElementById('mapSearchInput').value.trim() : '';
  if (keyword) {
    searchKeywordPlaces(keyword);
  } else {
    fetchRestaurants();
    panToCampus();
  }
}

// 검색어 검색 (Enter 또는 🔍 클릭)
function handleSearch() {
  const input = document.getElementById('mapSearchInput');
  const keyword = input ? input.value.trim() : '';

  if (!keyword) {
    fetchRestaurants();
    panToCampus();
    return;
  }

  searchKeywordPlaces(keyword);
}

// 지도 제어 함수
function panToCampus() {
  if (!map) return;
  const campusLatLng = new kakao.maps.LatLng(CAMPUS_LAT, CAMPUS_LNG);

  // 상단 캠퍼스 배지 반응 효과
  const badge = document.querySelector('.campus-badge');
  if (badge) {
    badge.style.transform = 'scale(1.05)';
    badge.style.boxShadow = '0 6px 20px rgba(249, 115, 22, 0.35)';
    setTimeout(() => {
      badge.style.transform = '';
      badge.style.boxShadow = '';
    }, 600);
  }

  try {
    if (map.getLevel() !== 4) {
      map.setLevel(4);
    }
    const bounds = map.getBounds();
    if (bounds && bounds.contain(campusLatLng)) {
      map.panTo(campusLatLng);
    } else {
      map.setCenter(campusLatLng);
    }
  } catch (e) {
    map.setCenter(campusLatLng);
  }
}

function zoomIn() {
  if (map) map.setLevel(map.getLevel() - 1);
}

function zoomOut() {
  if (map) map.setLevel(map.getLevel() + 1);
}

// 전역 window 객체 바인딩 (인라인 onclick 보장)
window.panToCampus = panToCampus;
window.zoomIn = zoomIn;
window.zoomOut = zoomOut;

// 유틸리티: 카카오 카테고리 텍스트 파싱
function parseCategory(raw) {
  if (!raw) return '한식';
  if (raw.includes('카페') || raw.includes('디저트') || raw.includes('커피')) return '카페';
  if (raw.includes('분식') || raw.includes('떡볶이') || raw.includes('김밥')) return '분식';
  if (raw.includes('양식') || raw.includes('피자') || raw.includes('파스타') || raw.includes('스테이크')) return '양식';
  if (raw.includes('일식') || raw.includes('초밥') || raw.includes('돈까스') || raw.includes('우동') || raw.includes('라멘')) return '일식';
  if (raw.includes('중식') || raw.includes('중국집') || raw.includes('짜장')) return '중식';
  return '한식';
}


// XSS 방지 HTML escape
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// ============================================================================
// 신규 맛집 등록 기능 (카카오 장소 검색 & 직접 위치 선택 2가지 모드)
// ============================================================================

let isPickingLocation = false;
let pickMarker = null;
let geocoder = null;

// 신규 맛집 등록 모달 열기
function openRegisterModal() {
  const modal = document.getElementById('registerRestaurantModal');
  if (!modal) return;

  if (!geocoder && window.kakao && kakao.maps && kakao.maps.services) {
    geocoder = new kakao.maps.services.Geocoder();
  }

  modal.style.display = 'flex';
  cancelQuickConfirm();
  switchRegisterTab('search');

  setTimeout(() => {
    const input = document.getElementById('regKakaoSearchInput');
    if (input) {
      input.focus();
      input.select();
    }
  }, 100);
}

// 신규 맛집 등록 모달 닫기
function closeRegisterModal() {
  const modal = document.getElementById('registerRestaurantModal');
  if (modal) modal.style.display = 'none';

  if (isPickingLocation) {
    cancelPickLocationOnMap();
  }
}

// 등록 모달 내부 탭 전환 ('search' | 'direct')
function switchRegisterTab(tab) {
  const tabSearchBtn = document.getElementById('tabSearchBtn');
  const tabDirectBtn = document.getElementById('tabDirectBtn');
  const regTabSearch = document.getElementById('regTabSearch');
  const regTabDirect = document.getElementById('regTabDirect');

  if (tab === 'search') {
    if (tabSearchBtn) tabSearchBtn.classList.add('active');
    if (tabDirectBtn) tabDirectBtn.classList.remove('active');
    if (regTabSearch) regTabSearch.style.display = 'block';
    if (regTabDirect) regTabDirect.style.display = 'none';
  } else {
    if (tabDirectBtn) tabDirectBtn.classList.add('active');
    if (tabSearchBtn) tabSearchBtn.classList.remove('active');
    if (regTabDirect) regTabDirect.style.display = 'block';
    if (regTabSearch) regTabSearch.style.display = 'none';
  }
}

// 탭 1: 카카오 키워드 장소 검색 수행
function searchPlacesForRegister() {
  const input = document.getElementById('regKakaoSearchInput');
  const query = input ? input.value.trim() : '';
  if (!query) {
    showToast('검색할 식당 또는 카페명을 입력해주세요.', 'error');
    return;
  }

  const resultsContainer = document.getElementById('regSearchResults');
  if (resultsContainer) {
    resultsContainer.innerHTML = `
      <div class="reg-loading-box">
        <span class="placeholder-emoji"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg></span>
        <p>'${escapeHtml(query)}' 검색 중...</p>
      </div>
    `;
  }

  if (!placesService && window.kakao && kakao.maps && kakao.maps.services) {
    placesService = new kakao.maps.services.Places();
  }

  if (!placesService) {
    showToast('카카오 장소 검색 서비스를 불러올 수 없습니다. 지도를 새로고침해주세요.', 'error');
    return;
  }

  const options = {
    location: new kakao.maps.LatLng(CAMPUS_LAT, CAMPUS_LNG),
    radius: 10000,
    size: 15
  };

  placesService.keywordSearch(query, (data, status) => {
    if (status === kakao.maps.services.Status.OK && data && data.length > 0) {
      renderRegisterSearchResults(data);
    } else {
      // 반경 외일 경우 '오산 ' 접두사로 재검색
      placesService.keywordSearch(`${CAMPUS_REGION} ${query}`, (data2, status2) => {
        if (status2 === kakao.maps.services.Status.OK && data2 && data2.length > 0) {
          renderRegisterSearchResults(data2);
        } else {
          if (resultsContainer) {
            resultsContainer.innerHTML = `
              <div class="reg-no-result">
                <span class="placeholder-emoji"><svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8.2 6v4a1.4 1.4 0 0 0 2.8 0V6M9.6 6v12"/><path d="M15 6c-1.1 1.6-1.1 3.4 0 5v7"/></svg></span>
                <p>'${escapeHtml(query)}'에 대한 카카오맵 검색 결과가 없습니다.</p>
                <button type="button" class="btn-goto-direct" onclick="switchRegisterTab('direct'); fillDirectFormWithName('${escapeHtml(query)}');">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4Z"/></svg> 직접 정보 입력하여 등록하기
                </button>
              </div>
            `;
          }
        }
      });
    }
  }, options);
}

// 카카오 검색 결과 목록 렌더링
function renderRegisterSearchResults(places) {
  const container = document.getElementById('regSearchResults');
  if (!container) return;

  let html = '';
  places.forEach((p, idx) => {
    const cat = parseCategory(p.category_name);
    const addr = p.road_address_name || p.address_name || '주소 정보 없음';
    const phone = p.phone || '전화번호 미등록';

    const existingRestaurant = currentRestaurantList.find(r =>
      r.name === p.place_name || (r.address && r.address === addr)
    );
    const alreadyRegistered = !!existingRestaurant;

    html += `
      <div class="reg-place-item">
        <div class="reg-place-info">
          <div class="reg-place-header">
            <span class="reg-place-category">${cat}</span>
            <strong class="reg-place-name">${escapeHtml(p.place_name)}</strong>
            ${alreadyRegistered ? '<span class="reg-already-badge">등록됨</span>' : ''}
          </div>
          <p class="reg-place-addr"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg> ${escapeHtml(addr)}</p>
          <p class="reg-place-phone"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/></svg> ${escapeHtml(phone)}</p>
        </div>
        <button type="button" class="btn-reg-action" onclick="${alreadyRegistered ? `viewExistingRestaurant(${existingRestaurant.id})` : `registerPlaceFromKakao(${idx})`}">
          ${alreadyRegistered ? '위치 보기' : '<svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg> 등록하기'}
        </button>
      </div>
    `;
  });

  container.innerHTML = html;
  window._lastRegisterSearchResults = places;
}

// 이미 등록된 맛집이면 새로 등록하지 않고 바로 그 맛집으로 이동해서 보여준다
function viewExistingRestaurant(restaurantId) {
  closeRegisterModal();
  fetchRestaurantsAndSelect(restaurantId);
}

// 등록 모달 대표 사진 업로드 (pfx: 'quick' | 'direct'). 게시글/리뷰와 같은 /api/uploads 재사용
const regImageUploading = { quick: false, direct: false };

function handleRegImageSelect(e, pfx) {
  const file = e.target.files[0];
  if (!file) return;

  const el = id => document.getElementById(pfx + id);
  const formData = new FormData();
  formData.append('file', file);

  regImageUploading[pfx] = true;
  el('ImgUrl').value = '';
  el('ImgBoxText').textContent = file.name;
  el('ImgStatus').textContent = '업로드 중...';
  el('ImgStatus').className = 'img-upload-status uploading';
  updateQuickSubmitLabel();

  fetch(window.csrfParam('/api/uploads'), { method: 'POST', body: formData })
    .then(res => res.json().then(data => ({ ok: res.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) {
        el('ImgStatus').textContent = data.message || '업로드에 실패했습니다.';
        el('ImgStatus').className = 'img-upload-status error';
        el('ImgFile').value = '';
        showToast(data.message || '사진 업로드에 실패했습니다.', 'error');
        return;
      }
      el('ImgUrl').value = data.url;
      el('ImgPreview').src = data.url;
      el('ImgPreview').style.display = 'block';
      el('ImgStatus').textContent = '업로드 완료';
      el('ImgStatus').className = 'img-upload-status success';
    })
    .catch(() => {
      el('ImgStatus').textContent = '네트워크 오류로 업로드하지 못했습니다.';
      el('ImgStatus').className = 'img-upload-status error';
      showToast('사진 업로드 중 네트워크 오류가 발생했습니다.', 'error');
    })
    .finally(() => { regImageUploading[pfx] = false; updateQuickSubmitLabel(); });
}

function resetRegImage(pfx) {
  const el = id => document.getElementById(pfx + id);
  el('ImgUrl').value = '';
  el('ImgFile').value = '';
  el('ImgBoxText').textContent = '대표 사진 추가 (선택, jpg·png·gif·webp, 5MB 이하)';
  el('ImgStatus').textContent = '';
  el('ImgStatus').className = 'img-upload-status';
  el('ImgPreview').style.display = 'none';
  el('ImgPreview').src = '';
}

function updateQuickSubmitLabel() {
  const btn = document.getElementById('btnQuickSubmit');
  if (!btn || btn.dataset.loading) return;
  const uploading = regImageUploading.quick;
  btn.disabled = uploading;
  btn.textContent = uploading ? '사진 업로드 중...'
    : (document.getElementById('quickImgUrl').value ? '등록하기' : '사진 없이 등록');
}

// 빠른 등록 확인 단계 표시/숨김 (검색창+결과 <-> 사진 추가 화면)
function showQuickConfirm(show) {
  const pane = document.getElementById('regTabSearch');
  ['.reg-search-box', '.reg-help-text', '#regSearchResults'].forEach(sel => {
    const n = pane.querySelector(sel);
    if (n) n.style.display = show ? 'none' : '';
  });
  document.getElementById('regQuickConfirm').style.display = show ? 'block' : 'none';
}

let quickPlace = null;

// 카카오 장소 검색 결과에서 선택 -> 사진(선택) 확인 단계
function registerPlaceFromKakao(index) {
  const places = window._lastRegisterSearchResults;
  if (!places || !places[index]) return;

  const p = places[index];
  quickPlace = {
    name: p.place_name,
    category: parseCategory(p.category_name),
    address: p.road_address_name || p.address_name,
    phone: p.phone || '',
    latitude: parseFloat(p.y),
    longitude: parseFloat(p.x)
  };
  document.getElementById('quickPlaceName').textContent = quickPlace.name;
  document.getElementById('quickPlaceAddr').textContent = quickPlace.address;
  resetRegImage('quick');
  updateQuickSubmitLabel();
  showQuickConfirm(true);
}

function cancelQuickConfirm() {
  quickPlace = null;
  resetRegImage('quick');
  showQuickConfirm(false);
}

function submitQuickRegister() {
  if (!quickPlace) return;
  if (regImageUploading.quick) {
    showToast('사진 업로드가 끝난 뒤 등록해주세요.', 'info');
    return;
  }
  const reqData = {
    ...quickPlace,
    description: '',
    imgUrl: document.getElementById('quickImgUrl').value || null
  };

  const btnEl = document.getElementById('btnQuickSubmit');
  setButtonLoading(btnEl, true, '등록 중...');
  btnEl.dataset.loading = '1';

  fetch('/api/restaurants', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(reqData)
  })
    .then(res => {
      if (!res.ok) throw new Error('맛집 등록 실패');
      return res.json();
    })
    .then(data => {
      cancelQuickConfirm();
      closeRegisterModal();
      showToast(`'${reqData.name}' 맛집이 지도에 성공적으로 등록되었습니다!`, 'success');
      fetchRestaurantsAndSelect(data.restaurant.id);
    })
    .catch(err => {
      console.error(err);
      showToast('맛집 등록 중 오류가 발생했습니다. 다시 시도해주세요.', 'error');
    })
    .finally(() => {
      delete btnEl.dataset.loading;
      setButtonLoading(btnEl, false);
      updateQuickSubmitLabel();
    });
}

// 직접 입력 탭 전환 시 상호명 자동 주입
function fillDirectFormWithName(name) {
  const nameInput = document.getElementById('directName');
  if (nameInput) nameInput.value = name;
}

// 탭 2: 지도에서 클릭하여 위치 지정 모드 활성화
function startPickLocationOnMap() {
  const modal = document.getElementById('registerRestaurantModal');
  if (modal) modal.style.display = 'none';

  const notice = document.getElementById('mapPickNotice');
  if (notice) notice.style.display = 'flex';

  isPickingLocation = true;

  if (!geocoder && window.kakao && kakao.maps && kakao.maps.services) {
    geocoder = new kakao.maps.services.Geocoder();
  }

  kakao.maps.event.addListener(map, 'click', handleMapClickLocation);
}

// 지도 클릭 시 좌표 추출 및 임시 핀 배치
function handleMapClickLocation(mouseEvent) {
  if (!isPickingLocation) return;

  const latLng = mouseEvent.latLng;
  const lat = latLng.getLat();
  const lng = latLng.getLng();

  if (pickMarker) {
    pickMarker.setMap(null);
  }

  const markerContent = document.createElement('div');
  markerContent.className = 'kakao-pick-marker';
  markerContent.innerHTML = `<span><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg> 선택한 위치</span>`;

  pickMarker = new kakao.maps.CustomOverlay({
    position: latLng,
    content: markerContent,
    yAnchor: 1.2
  });
  pickMarker.setMap(map);

  if (geocoder) {
    geocoder.coord2Address(lng, lat, (result, status) => {
      let addr = '';
      if (status === kakao.maps.services.Status.OK && result && result.length > 0) {
        if (result[0].road_address) {
          addr = result[0].road_address.address_name;
        } else if (result[0].address) {
          addr = result[0].address.address_name;
        }
      }
      applyPickedLocation(lat, lng, addr);
    });
  } else {
    applyPickedLocation(lat, lng, '');
  }
}

// 선택된 좌표와 주소를 직접 입력 폼에 주입하고 모달 복구
function applyPickedLocation(lat, lng, addr) {
  isPickingLocation = false;
  kakao.maps.event.removeListener(map, 'click', handleMapClickLocation);

  const notice = document.getElementById('mapPickNotice');
  if (notice) notice.style.display = 'none';

  const latInput = document.getElementById('directLat');
  const lngInput = document.getElementById('directLng');
  const addrInput = document.getElementById('directAddress');
  const locStatus = document.getElementById('locStatusText');

  if (latInput) latInput.value = lat;
  if (lngInput) lngInput.value = lng;
  if (addrInput && addr) addrInput.value = addr;
  if (locStatus) {
    locStatus.innerText = '✅ 위치 지정 완료 (' + lat.toFixed(4) + ', ' + lng.toFixed(4) + ')';
    locStatus.style.color = '#16A34A';
  }

  const modal = document.getElementById('registerRestaurantModal');
  if (modal) modal.style.display = 'flex';
  switchRegisterTab('direct');
}

// 지도 위치 지정 취소
function cancelPickLocationOnMap() {
  isPickingLocation = false;
  kakao.maps.event.removeListener(map, 'click', handleMapClickLocation);

  if (pickMarker) {
    pickMarker.setMap(null);
    pickMarker = null;
  }

  const notice = document.getElementById('mapPickNotice');
  if (notice) notice.style.display = 'none';

  const modal = document.getElementById('registerRestaurantModal');
  if (modal) modal.style.display = 'flex';
}

// 직접 정보 입력 폼 제출
function submitDirectRegister() {
  if (regImageUploading.direct) {
    showToast('사진 업로드가 끝난 뒤 등록해주세요.', 'info');
    return;
  }
  const name = document.getElementById('directName').value.trim();
  const category = document.getElementById('directCategory').value;
  const address = document.getElementById('directAddress').value.trim();
  const phone = document.getElementById('directPhone').value.trim();
  let lat = parseFloat(document.getElementById('directLat').value);
  let lng = parseFloat(document.getElementById('directLng').value);
  const description = document.getElementById('directDescription').value.trim();
  const imgUrl = document.getElementById('directImgUrl').value.trim();
  const tags = document.getElementById('directTags').value.trim();

  if (!name) {
    showToast('식당 이름을 입력해주세요.', 'error');
    return;
  }
  if (!address) {
    showToast('식당 주소를 입력해주세요.', 'error');
    return;
  }

  // 좌표가 미지정된 경우 주소 검색으로 좌표 자동 변환 시도
  if (isNaN(lat) || isNaN(lng) || !lat || !lng) {
    if (!geocoder && window.kakao && kakao.maps && kakao.maps.services) {
      geocoder = new kakao.maps.services.Geocoder();
    }
    if (geocoder) {
      geocoder.addressSearch(address, (result, status) => {
        if (status === kakao.maps.services.Status.OK && result && result.length > 0) {
          lat = parseFloat(result[0].y);
          lng = parseFloat(result[0].x);
        } else {
          lat = CAMPUS_LAT;
          lng = CAMPUS_LNG;
        }
        sendDirectRegisterRequest(name, category, address, phone, lat, lng, description, imgUrl, tags);
      });
      return;
    } else {
      lat = CAMPUS_LAT;
      lng = CAMPUS_LNG;
    }
  }

  sendDirectRegisterRequest(name, category, address, phone, lat, lng, description, imgUrl, tags);
}

function sendDirectRegisterRequest(name, category, address, phone, lat, lng, description, imgUrl, tags) {
  const submitBtn = document.getElementById('btnDirectSubmit');
  setButtonLoading(submitBtn, true, '등록 처리 중...');

  const reqData = {
    name: name,
    category: category,
    address: address,
    phone: phone,
    latitude: lat,
    longitude: lng,
    description: (description && description.trim()) ? description.trim() : '',
    imgUrl: imgUrl,
    tags: (tags && tags.trim()) ? tags.trim() : null
  };

  fetch('/api/restaurants', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(reqData)
  })
    .then(res => {
      if (!res.ok) throw new Error('맛집 등록 실패');
      return res.json();
    })
    .then(data => {
      if (pickMarker) {
        pickMarker.setMap(null);
        pickMarker = null;
      }
      document.getElementById('directRegisterForm').reset();
      resetRegImage('direct');
      const locStatus = document.getElementById('locStatusText');
      if (locStatus) {
        locStatus.innerText = '위치를 지정해주세요';
        locStatus.style.color = '#64748B';
      }

      closeRegisterModal();
      showToast(`'${name}' 맛집이 성공적으로 등록되었습니다!`, 'success');
      fetchRestaurantsAndSelect(data.restaurant.id);
    })
    .catch(err => {
      console.error(err);
      showToast('맛집 등록에 실패했습니다. 다시 시도해주세요.', 'error');
    })
    .finally(() => setButtonLoading(submitBtn, false));
}

// 등록 후 전체 목록 새로고침 및 신규 식당 자동 선택
function fetchRestaurantsAndSelect(targetId) {
  const keyword = document.getElementById('mapSearchInput') ? document.getElementById('mapSearchInput').value.trim() : '';
  const url = `/api/restaurants?category=${encodeURIComponent(currentCategory)}&keyword=${encodeURIComponent(keyword)}${viewUniversityQueryParam()}`;

  fetch(url)
    .then(res => res.json())
    .then(data => {
      currentRestaurantList = data || [];
      renderSidebarList(currentRestaurantList);
      renderMarkers(currentRestaurantList, false);

      if (targetId) {
        selectRestaurant(targetId);
      }
    })
    .catch(err => {
      console.error('맛집 목록 로드 실패:', err);
      showToast('맛집 목록을 새로고침하지 못했습니다. 페이지를 새로고침해주세요.', 'error');
    });
}

// ============================================================================
// AI 맛집 추천 ("오늘 뭐 먹지?")
// ----------------------------------------------------------------------------
// 자유 입력(상황)을 서버로 보내면, 서버가 등록된 맛집 중에서 2~3곳을 이유와 함께 돌려준다.
// 결과 카드를 누르면 기존 맛집 상세 모달(selectRestaurant)이 그대로 열린다.
// AI 호출은 몇 초 걸리므로 버튼을 잠그고 로딩 문구를 반드시 보여준다.
// ============================================================================
let aiPickLoading = false;

function askAiRecommend(presetSituation) {
  if (aiPickLoading) return;

  const input = document.getElementById('aiSituationInput');
  const resultEl = document.getElementById('aiPickResult');
  const btn = document.getElementById('aiPickBtn');
  if (!input || !resultEl || !btn) return;

  // 칩을 누른 경우 그 문구를 입력칸에도 채워준다(무엇으로 추천받았는지 보이게).
  if (presetSituation) input.value = presetSituation;

  const situation = input.value.trim();
  if (!situation) {
    showToast('어떤 걸 먹고 싶은지 적어주세요.', 'error');
    input.focus();
    return;
  }

  aiPickLoading = true;
  btn.disabled = true;
  btn.textContent = '추천 중...';
  resultEl.style.display = '';
  resultEl.innerHTML = '<div class="ai-pick-msg">AI가 우리 학교 맛집을 살펴보고 있어요...</div>';

  fetch('/api/ai/recommend', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ situation: situation })
  })
    .then(res => res.json().then(data => ({ ok: res.ok, data: data })))
    .then(({ ok, data }) => {
      if (!ok) {
        resultEl.innerHTML = `<div class="ai-pick-msg">${escapeHtml(data.message || 'AI 추천을 불러오지 못했습니다.')}</div>`;
        return;
      }
      renderAiPicks(data.picks || []);
    })
    .catch(err => {
      console.error('AI 추천 실패:', err);
      resultEl.innerHTML = '<div class="ai-pick-msg">AI 추천을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.</div>';
    })
    .finally(() => {
      aiPickLoading = false;
      btn.disabled = false;
      btn.textContent = '추천받기';
    });
}

function renderAiPicks(picks) {
  const resultEl = document.getElementById('aiPickResult');
  if (!resultEl) return;

  if (!picks.length) {
    resultEl.innerHTML = '<div class="ai-pick-msg">조건에 맞는 맛집을 찾지 못했어요. 다르게 적어보세요.</div>';
    return;
  }

  resultEl.innerHTML = picks.map((p, i) => `
    <button type="button" class="ai-pick-item" onclick="selectRestaurant(${p.restaurantId})">
      <span class="ai-pick-rank">${i + 1}</span>
      <span class="ai-pick-item-body">
        <span class="ai-pick-item-name">${escapeHtml(p.name)}</span>
        <span class="ai-pick-item-reason">${escapeHtml(p.reason)}</span>
      </span>
    </button>
  `).join('');
}
