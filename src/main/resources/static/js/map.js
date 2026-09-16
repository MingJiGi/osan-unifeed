/**
 * 유니피드 오산대학교 맛집 지도 스크립트 (map.js)
 * - 카카오 지도 JavaScript SDK 연동
 * - 오산대학교 중심 (37.15589, 127.06061)
 * - 카카오 장소 검색 API(kakao.maps.services.Places) 연동
 *   - '할매순대국' 등 키워드 검색 시 오산/캠퍼스 권역의 모든 매장을 검색하여 지도 위 마커로 한눈에 표출
 *   - map.setBounds(bounds)를 통해 검색된 모든 마커가 화면에 다 들어오도록 지도 시야 자동 조절
 * - 신규 검색 맛집의 DB 자동 동기화(/api/restaurants/sync) 및 리뷰/찜 연동
 */

const CAMPUS_LAT = 37.15589;
const CAMPUS_LNG = 127.06061;

let map = null;
let markers = [];
let placesService = null;
let currentRestaurantList = [];
let currentRestaurantId = null;
let currentRating = 5;
let currentCategory = '전체';

document.addEventListener('DOMContentLoaded', () => {
  initKakaoMap();
  fetchRestaurants();
});

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

// 기본 DB 맛집 목록 조회 및 마커 렌더링
function fetchRestaurants() {
  const keyword = document.getElementById('mapSearchInput') ? document.getElementById('mapSearchInput').value.trim() : '';
  const url = `/api/restaurants?category=${encodeURIComponent(currentCategory)}&keyword=${encodeURIComponent(keyword)}`;

  fetch(url)
    .then(res => res.json())
    .then(data => {
      currentRestaurantList = data || [];
      renderSidebarList(currentRestaurantList);
      renderMarkers(currentRestaurantList, false);
    })
    .catch(err => console.error('맛집 목록 로드 실패:', err));
}

// 사이드바 맛집 리스트 렌더링
function renderSidebarList(restaurants) {
  const listEl = document.getElementById('restaurantList');
  if (!listEl) return;

  const countEl = document.getElementById('restaurantCount');
  if (countEl) countEl.innerText = restaurants.length;

  if (restaurants.length === 0) {
    listEl.innerHTML = `
      <div class="restaurant-count-bar">
        <span class="count-text">검색결과 <span>0</span>곳</span>
        <button type="button" class="btn-open-register" onclick="openRegisterModal()">
          <span>➕</span> 맛집 등록
        </button>
      </div>
      <div class="restaurant-empty-state">
        <div class="empty-icon">🍽️</div>
        <p class="empty-title">등록된 맛집이 없습니다</p>
        <p class="empty-desc">오산대 주변의 숨은 맛집이나 자주 가는 식당을<br>직접 등록하고 학우들과 공유해보세요!</p>
        <button type="button" class="btn-empty-register" onclick="openRegisterModal()">➕ 첫 번째 맛집 직접 등록하기</button>
      </div>
    `;
    return;
  }

  let html = `
    <div class="restaurant-count-bar">
      <span class="count-text">검색결과 <span>${restaurants.length}</span>곳</span>
      <button type="button" class="btn-open-register" onclick="openRegisterModal()">
        <span>➕</span> 맛집 등록
      </button>
    </div>
  `;

  restaurants.forEach((r, idx) => {
    const thumb = (r.imgUrl && r.imgUrl.trim()) ? r.imgUrl : getCategoryDefaultImg(r.category);
    const isActive = (currentRestaurantId && currentRestaurantId === r.id) ? 'active' : '';
    const ratingDisplay = (r.rating && r.rating > 0) ? r.rating.toFixed(1) : 'NEW';
    const reviewsDisplay = (r.reviewCount && r.reviewCount > 0) ? `(리뷰 ${r.reviewCount})` : '(리뷰 0)';

    html += `
      <div class="place-card ${isActive}" id="place-card-${idx}" onclick="selectRestaurantByIndex(${idx})">
        <img src="${thumb}" alt="${escapeHtml(r.name)}" class="place-thumb" onerror="this.src='/images/food1.jpg'">
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
            </div>
            <p class="place-desc">${escapeHtml(r.description || '')}</p>
          </div>
          <div class="place-address">${escapeHtml(r.address || '')}</div>
        </div>
      </div>
    `;
  });

  listEl.innerHTML = html;
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
    const ratingHtml = (r.rating && r.rating > 0)
      ? `<span style="color:#FBBF24; margin-left:3px; font-weight:700;">★${r.rating.toFixed(1)}</span>`
      : '';
    content.innerHTML = `<span>🍽️</span> <span style="font-weight:600;">${escapeHtml(r.name)}</span> ${ratingHtml}`;
    content.onclick = () => selectRestaurantByIndex(idx);

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
          <span>➕</span> 맛집 등록
        </button>
      </div>
      <div style="padding: 40px 16px; text-align: center; color: #F97316; font-size: 13px;">
        🔍 '${escapeHtml(keyword)}' 맛집을 검색하고 있습니다...
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
        placesService.keywordSearch(`오산 ${keyword}`, (data2, status2) => {
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
          id: null, // 미동기화 상태 (클릭 시 자동 DB sync)
          kakaoId: p.id,
          name: p.place_name,
          category: cat,
          address: p.road_address_name || p.address_name,
          phone: p.phone || '',
          latitude: parseFloat(p.y),
          longitude: parseFloat(p.x),
          description: `${p.place_name} (${p.category_name || cat})`,
          imgUrl: getCategoryDefaultImg(cat),
          rating: 0.0,
          reviewCount: 0,
          bookmarkCount: 0,
          isBookmarked: false
        });
      });
    }

    currentRestaurantList = combined;
    renderSidebarList(combined);
    // 검색 시 모든 마커가 화면에 쭉 보이도록 adjustBounds=true 호출!
    renderMarkers(combined, true);
  });
}

// 맛집 인덱스로 선택 (상세 모달 오픈 & DB 동기화)
function selectRestaurantByIndex(idx) {
  const r = currentRestaurantList[idx];
  if (!r) return;

  // 사이드바 카드 활성화 효과
  document.querySelectorAll('.place-card').forEach(c => c.classList.remove('active'));
  const targetCard = document.getElementById(`place-card-${idx}`);
  if (targetCard) {
    targetCard.classList.add('active');
    targetCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  // 지도 부드러운 중심 이동
  if (map && r.latitude && r.longitude) {
    map.panTo(new kakao.maps.LatLng(r.latitude, r.longitude));
  }

  // 1) 이미 DB에 등록된 식당인 경우
  if (r.id) {
    currentRestaurantId = r.id;
    fetch(`/api/restaurants/${r.id}`)
      .then(res => res.json())
      .then(data => {
        populateAndOpenModal(data.restaurant, data.reviews);
      })
      .catch(err => console.error('맛집 상세 로드 실패:', err));
  } else {
    // 2) 카카오 검색으로 찾은 신규 식당 -> DB에 자동 동기화(/api/restaurants/sync)
    fetch('/api/restaurants/sync', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: r.name,
        category: r.category,
        address: r.address,
        phone: r.phone || '',
        latitude: r.latitude,
        longitude: r.longitude,
        description: r.description || `${r.name} (오산대 주변 맛집)`,
        imgUrl: r.imgUrl || getCategoryDefaultImg(r.category)
      })
    })
      .then(res => res.json())
      .then(data => {
        r.id = data.restaurant.id; // DB ID 매핑
        currentRestaurantId = data.restaurant.id;
        populateAndOpenModal(data.restaurant, data.reviews);
      })
      .catch(err => console.error('식당 동기화 실패:', err));
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
      .catch(err => console.error('맛집 상세 로드 실패:', err));
  }
}

// 모달 엘리먼트 데이터 채우기 및 열기
function populateAndOpenModal(r, reviews) {
  const modalImg = document.getElementById('modalImg');
  if (modalImg) modalImg.src = (r.imgUrl && r.imgUrl.trim()) ? r.imgUrl : '/images/food1.jpg';

  document.getElementById('modalName').innerText = r.name;
  document.getElementById('modalCategory').innerText = r.category;
  document.getElementById('modalRating').innerText = (r.rating || 0).toFixed(1);
  document.getElementById('modalReviewCount').innerText = `(리뷰 ${r.reviewCount || 0}개)`;
  const badgeEl = document.getElementById('modalReviewBadge');
  if (badgeEl) badgeEl.innerText = r.reviewCount || 0;

  document.getElementById('modalDesc').innerText = r.description || '캠퍼스 주변 인기 맛집입니다.';
  document.getElementById('modalAddress').innerText = r.address;
  document.getElementById('modalPhone').innerText = r.phone || '등록된 번호 없음';

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

// 찜 버튼 UI 갱신
function updateBookmarkBtnUI(isBookmarked, bookmarkCount) {
  const btn = document.getElementById('modalBookmarkBtn');
  const icon = document.getElementById('modalBookmarkIcon');
  const text = document.getElementById('modalBookmarkText');
  if (!btn || !icon || !text) return;

  if (isBookmarked) {
    btn.classList.add('active');
    icon.innerText = '❤️';
    text.innerText = `저장됨 (${bookmarkCount})`;
  } else {
    btn.classList.remove('active');
    icon.innerText = '🤍';
    text.innerText = `저장 (${bookmarkCount})`;
  }
}

// 찜 토글 실행
function toggleBookmarkCurrent() {
  if (!currentRestaurantId) return;

  fetch(`/api/restaurants/${currentRestaurantId}/bookmark`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' }
  })
    .then(res => {
      if (res.status === 401) {
        alert('로그인이 필요한 기능입니다.');
        location.href = '/login';
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
    .catch(err => console.error('찜 토글 실패:', err));
}

// 리뷰 목록 렌더링 (UNIFEED detail.html 댓글 스타일 적용)
function renderReviews(reviews) {
  const listEl = document.getElementById('modalReviewList');
  if (!listEl) return;

  if (!reviews || reviews.length === 0) {
    listEl.innerHTML = `
      <div class="review-empty">
        <span class="review-empty-icon">🍽️</span>
        <span class="review-empty-text">아직 작성된 방문자 리뷰가 없습니다</span>
        <p style="margin:0; font-size:12px;">이 맛집의 첫 번째 리뷰어가 되어보세요!</p>
      </div>
    `;
    return;
  }

  let html = '';
  reviews.forEach(rev => {
    const stars = '★'.repeat(rev.rating) + '☆'.repeat(5 - rev.rating);
    const hasAvatar = rev.authorProfileImage && rev.authorProfileImage.trim();

    html += `
      <div class="review-item">
        <div class="review-avatar">
          ${hasAvatar
            ? `<img src="${rev.authorProfileImage}" alt="프로필" class="review-avatar-img">`
            : `<div class="review-avatar-default">👤</div>`
          }
        </div>
        <div class="review-body">
          <div class="review-meta">
            <span class="review-author-name">${escapeHtml(rev.authorName)}</span>
            <span class="review-date">${rev.createdAt}</span>
          </div>
          <div class="review-stars">${stars}</div>
          <p class="review-text">${escapeHtml(rev.content)}</p>
        </div>
      </div>
    `;
  });

  listEl.innerHTML = html;
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
    alert('리뷰 내용을 입력해주세요.');
    if (input) input.focus();
    return;
  }

  fetch(`/api/restaurants/${currentRestaurantId}/reviews`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ rating: currentRating, content: content })
  })
    .then(res => {
      if (res.status === 401) {
        alert('로그인이 필요한 기능입니다.');
        location.href = '/login';
        return;
      }
      if (!res.ok) {
        return res.json().then(d => { throw new Error(d.message || '리뷰 등록 실패'); });
      }
      return res.json();
    })
    .then(data => {
      if (data && data.success) {
        alert('소중한 맛집 리뷰가 등록되었습니다!\n(마이페이지 [내 리뷰]에서도 확인하실 수 있습니다)');
        if (input) input.value = '';

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
    .catch(err => alert(err.message || '리뷰 등록 중 오류가 발생했습니다.'));
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
  if (map) {
    map.panTo(new kakao.maps.LatLng(CAMPUS_LAT, CAMPUS_LNG));
    map.setLevel(4);
  }
}
function zoomIn() {
  if (map) map.setLevel(map.getLevel() - 1);
}
function zoomOut() {
  if (map) map.setLevel(map.getLevel() + 1);
}

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

// 유틸리티: 카테고리별 기본 썸네일 이미지
function getCategoryDefaultImg(cat) {
  switch (cat) {
    case '분식':
      return 'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=500&auto=format&fit=crop&q=80';
    case '카페':
      return 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=500&auto=format&fit=crop&q=80';
    case '양식':
      return 'https://images.unsplash.com/photo-1621996346565-e3d5d6281292?w=500&auto=format&fit=crop&q=80';
    case '한식':
    default:
      return 'https://images.unsplash.com/photo-1547928576-a4a33237cbc3?w=500&auto=format&fit=crop&q=80';
  }
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
    alert('검색할 식당 또는 카페명을 입력해주세요.');
    return;
  }

  const resultsContainer = document.getElementById('regSearchResults');
  if (resultsContainer) {
    resultsContainer.innerHTML = `
      <div class="reg-loading-box">
        <span class="placeholder-emoji">🔍</span>
        <p>'${escapeHtml(query)}' 검색 중...</p>
      </div>
    `;
  }

  if (!placesService && window.kakao && kakao.maps && kakao.maps.services) {
    placesService = new kakao.maps.services.Places();
  }

  if (!placesService) {
    alert('카카오 장소 검색 서비스를 불러올 수 없습니다. 지도를 새로고침해주세요.');
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
      placesService.keywordSearch(`오산 ${query}`, (data2, status2) => {
        if (status2 === kakao.maps.services.Status.OK && data2 && data2.length > 0) {
          renderRegisterSearchResults(data2);
        } else {
          if (resultsContainer) {
            resultsContainer.innerHTML = `
              <div class="reg-no-result">
                <span class="placeholder-emoji">🍽️</span>
                <p>'${escapeHtml(query)}'에 대한 카카오맵 검색 결과가 없습니다.</p>
                <button type="button" class="btn-goto-direct" onclick="switchRegisterTab('direct'); fillDirectFormWithName('${escapeHtml(query)}');">
                  ✍️ 직접 정보 입력하여 등록하기
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

    const alreadyRegistered = currentRestaurantList.some(r =>
      r.name === p.place_name || (r.address && r.address === addr)
    );

    html += `
      <div class="reg-place-item">
        <div class="reg-place-info">
          <div class="reg-place-header">
            <span class="reg-place-category">${cat}</span>
            <strong class="reg-place-name">${escapeHtml(p.place_name)}</strong>
            ${alreadyRegistered ? '<span class="reg-already-badge">등록됨</span>' : ''}
          </div>
          <p class="reg-place-addr">📍 ${escapeHtml(addr)}</p>
          <p class="reg-place-phone">📞 ${escapeHtml(phone)}</p>
        </div>
        <button type="button" class="btn-reg-action" onclick="registerPlaceFromKakao(${idx})">
          ${alreadyRegistered ? '위치 보기' : '➕ 등록하기'}
        </button>
      </div>
    `;
  });

  container.innerHTML = html;
  window._lastRegisterSearchResults = places;
}

// 카카오 장소 검색 결과에서 클릭하여 원클릭 등록
function registerPlaceFromKakao(index) {
  const places = window._lastRegisterSearchResults;
  if (!places || !places[index]) return;

  const p = places[index];
  const cat = parseCategory(p.category_name);
  const addr = p.road_address_name || p.address_name;

  const reqData = {
    name: p.place_name,
    category: cat,
    address: addr,
    phone: p.phone || '',
    latitude: parseFloat(p.y),
    longitude: parseFloat(p.x),
    description: `${p.place_name} - 오산대 캠퍼스 맛집입니다.`,
    imgUrl: getCategoryDefaultImg(cat)
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
      closeRegisterModal();
      alert(`'${reqData.name}' 맛집이 지도에 성공적으로 등록되었습니다! 🎉`);
      fetchRestaurantsAndSelect(data.restaurant.id);
    })
    .catch(err => {
      console.error(err);
      alert('맛집 등록 중 오류가 발생했습니다. 다시 시도해주세요.');
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
  markerContent.innerHTML = `<span>📍 선택한 위치</span>`;

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
  const name = document.getElementById('directName').value.trim();
  const category = document.getElementById('directCategory').value;
  const address = document.getElementById('directAddress').value.trim();
  const phone = document.getElementById('directPhone').value.trim();
  let lat = parseFloat(document.getElementById('directLat').value);
  let lng = parseFloat(document.getElementById('directLng').value);
  const description = document.getElementById('directDescription').value.trim();
  let imgUrl = document.getElementById('directImgUrl').value.trim();

  if (!name) {
    alert('식당 이름을 입력해주세요.');
    return;
  }
  if (!address) {
    alert('식당 주소를 입력해주세요.');
    return;
  }

  if (!imgUrl) {
    imgUrl = getCategoryDefaultImg(category);
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
        sendDirectRegisterRequest(name, category, address, phone, lat, lng, description, imgUrl);
      });
      return;
    } else {
      lat = CAMPUS_LAT;
      lng = CAMPUS_LNG;
    }
  }

  sendDirectRegisterRequest(name, category, address, phone, lat, lng, description, imgUrl);
}

function sendDirectRegisterRequest(name, category, address, phone, lat, lng, description, imgUrl) {
  const submitBtn = document.getElementById('btnDirectSubmit');
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.innerText = '등록 처리 중...';
  }

  const reqData = {
    name: name,
    category: category,
    address: address,
    phone: phone,
    latitude: lat,
    longitude: lng,
    description: description || `${name} - 오산대 캠퍼스 맛집`,
    imgUrl: imgUrl
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
      const locStatus = document.getElementById('locStatusText');
      if (locStatus) {
        locStatus.innerText = '위치를 지정해주세요';
        locStatus.style.color = '#64748B';
      }

      closeRegisterModal();
      alert(`'${name}' 맛집이 성공적으로 등록되었습니다! 🎉`);
      fetchRestaurantsAndSelect(data.restaurant.id);
    })
    .catch(err => {
      console.error(err);
      alert('맛집 등록에 실패했습니다. 다시 시도해주세요.');
    })
    .finally(() => {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.innerText = '맛집 등록 완료';
      }
    });
}

// 등록 후 전체 목록 새로고침 및 신규 식당 자동 선택
function fetchRestaurantsAndSelect(targetId) {
  const keyword = document.getElementById('mapSearchInput') ? document.getElementById('mapSearchInput').value.trim() : '';
  const url = `/api/restaurants?category=${encodeURIComponent(currentCategory)}&keyword=${encodeURIComponent(keyword)}`;

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
    .catch(err => console.error('맛집 목록 로드 실패:', err));
}
