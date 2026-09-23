/**
 * 유니피드 공통 UI 유틸리티 (ui-common.js)
 * - showToast(message, type): alert() 대체용 토스트 알림 ('success' | 'error' | 'info')
 * - setButtonLoading(btn, loading, loadingText): 비동기 제출 버튼 로딩 상태 표시
 * - openReportModal(targetType, targetId, targetLabel): 게시글/댓글/리뷰 공용 신고 모달
 * 모든 페이지에 fragments/layout.html :: head 로 전역 로드된다.
 */
(function () {
  let container = null;

  function ensureContainer() {
    if (container && document.body.contains(container)) return container;
    container = document.createElement('div');
    container.className = 'toast-container';
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);
    return container;
  }

  window.showToast = function (message, type) {
    if (!message) return;
    const c = ensureContainer();
    const el = document.createElement('div');
    el.className = 'toast toast-' + (type || 'info');
    el.textContent = message;
    c.appendChild(el);

    requestAnimationFrame(() => el.classList.add('toast-show'));

    setTimeout(() => {
      el.classList.remove('toast-show');
      el.classList.add('toast-hide');
      setTimeout(() => el.remove(), 250);
    }, 3200);
  };

  window.setButtonLoading = function (btn, loading, loadingText) {
    if (!btn) return;
    if (loading) {
      if (btn.dataset.originalHtml === undefined) {
        btn.dataset.originalHtml = btn.innerHTML;
      }
      btn.disabled = true;
      btn.classList.add('btn-loading');
      btn.innerHTML = '<span class="btn-spinner"></span>' + (loadingText || btn.dataset.originalHtml);
    } else {
      btn.disabled = false;
      btn.classList.remove('btn-loading');
      if (btn.dataset.originalHtml !== undefined) {
        btn.innerHTML = btn.dataset.originalHtml;
        delete btn.dataset.originalHtml;
      }
    }
  };

  // ── 신고 모달 (게시글/댓글/맛집 리뷰 공용) ──────────────────────────────
  function ensureReportModal() {
    if (document.getElementById('reportModalOverlay')) return;

    const overlay = document.createElement('div');
    overlay.id = 'reportModalOverlay';
    overlay.className = 'report-modal-overlay';
    overlay.innerHTML =
      '<div class="report-modal-card">' +
        '<div class="report-modal-header">' +
          '<h3 class="report-modal-title">신고하기</h3>' +
          '<button type="button" class="report-modal-close" onclick="closeReportModal()" aria-label="닫기">&times;</button>' +
        '</div>' +
        '<p class="report-modal-target" id="reportModalLabel"></p>' +
        '<div class="report-reason-list">' +
          '<label class="report-reason-item"><input type="radio" name="reportReason" value="SPAM" checked> 스팸/광고</label>' +
          '<label class="report-reason-item"><input type="radio" name="reportReason" value="ABUSE"> 욕설/비방</label>' +
          '<label class="report-reason-item"><input type="radio" name="reportReason" value="FALSE_INFO"> 허위 정보</label>' +
          '<label class="report-reason-item"><input type="radio" name="reportReason" value="ADULT"> 음란물/불법 콘텐츠</label>' +
          '<label class="report-reason-item"><input type="radio" name="reportReason" value="ETC"> 기타</label>' +
        '</div>' +
        '<textarea id="reportModalDetail" class="report-modal-textarea" placeholder="상세 내용을 입력해주세요 (선택)" maxlength="500"></textarea>' +
        '<div class="report-modal-btn-row">' +
          '<button type="button" class="report-modal-btn report-modal-btn-cancel" onclick="closeReportModal()">취소</button>' +
          '<button type="button" class="report-modal-btn report-modal-btn-submit" id="reportModalSubmitBtn">신고하기</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(overlay);

    overlay.addEventListener('click', function (e) {
      if (e.target === overlay) closeReportModal();
    });
    document.getElementById('reportModalSubmitBtn').addEventListener('click', submitReport);
  }

  let reportTargetType = null;
  let reportTargetId = null;

  window.openReportModal = function (targetType, targetId, targetLabel) {
    ensureReportModal();
    reportTargetType = targetType;
    reportTargetId = targetId;
    document.getElementById('reportModalLabel').textContent = targetLabel || '';
    const overlay = document.getElementById('reportModalOverlay');
    overlay.style.display = 'flex';
    document.body.style.overflow = 'hidden';
  };

  window.closeReportModal = function () {
    const overlay = document.getElementById('reportModalOverlay');
    if (overlay) overlay.style.display = 'none';
    document.body.style.overflow = '';
  };

  function submitReport() {
    if (!reportTargetType || !reportTargetId) return;
    const reasonEl = document.querySelector('input[name="reportReason"]:checked');
    const reason = reasonEl ? reasonEl.value : 'ETC';
    const detail = document.getElementById('reportModalDetail').value.trim();
    const btn = document.getElementById('reportModalSubmitBtn');

    setButtonLoading(btn, true, '신고 처리 중...');

    fetch('/api/reports', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ targetType: reportTargetType, targetId: reportTargetId, reason: reason, detail: detail })
    })
      .then(res => res.json().then(data => ({ ok: res.ok, data })))
      .then(({ ok, data }) => {
        setButtonLoading(btn, false);
        if (!ok) {
          showToast((data && data.message) || '신고 접수에 실패했습니다.', 'error');
          return;
        }
        showToast('신고가 접수되었습니다. 검토 후 조치하겠습니다.', 'success');
        document.getElementById('reportModalDetail').value = '';
        closeReportModal();
      })
      .catch(() => {
        setButtonLoading(btn, false);
        showToast('네트워크 오류로 신고를 접수하지 못했습니다.', 'error');
      });
  }

  // ── PWA 서비스워커 등록 (홈 화면 설치 지원) ──
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }

  // ── 알림 (GNB 벨 드롭다운) ──
  // 벨 아이콘 자체가 로그인했을 때만 렌더링되므로(fragments/layout.html th:if), 엘리먼트
  // 존재 여부로 "로그인 상태인지"를 판단한다 — 별도 플래그 없이 비로그인 페이지에서의
  // 불필요한 API 호출을 막는다.
  let notificationsCache = [];

  function loadNotifications() {
    if (!document.getElementById('gnbNotifyBadge')) return;
    fetch('/api/notifications')
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (!data) return;
        notificationsCache = data.items || [];
        renderNotifyBadge(data.unreadCount);
        renderNotifyList();
      })
      .catch(() => {});
  }

  function renderNotifyBadge(count) {
    const badge = document.getElementById('gnbNotifyBadge');
    if (!badge) return;
    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : String(count);
      badge.style.display = 'block';
    } else {
      badge.style.display = 'none';
    }
  }

  function renderNotifyList() {
    const listEl = document.getElementById('gnbNotifyList');
    if (!listEl) return;
    listEl.innerHTML = '';
    if (notificationsCache.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'gnb-notify-empty';
      empty.textContent = '알림이 없습니다.';
      listEl.appendChild(empty);
      return;
    }
    notificationsCache.forEach((n) => {
      const a = document.createElement('a');
      a.href = n.link || '#';
      a.className = 'gnb-notify-item' + (n.read ? '' : ' unread');
      a.addEventListener('click', () => markNotificationRead(n.id));

      const msg = document.createElement('div');
      msg.className = 'gnb-notify-item-message';
      msg.textContent = n.message;

      const date = document.createElement('span');
      date.className = 'gnb-notify-item-date';
      date.textContent = n.createdAt;

      a.appendChild(msg);
      a.appendChild(date);
      listEl.appendChild(a);
    });
  }

  window.toggleNotifyDropdown = function () {
    const dd = document.getElementById('gnbNotifyDropdown');
    if (!dd) return;
    const opening = dd.style.display === 'none';
    dd.style.display = opening ? 'flex' : 'none';
    if (opening) loadNotifications();
  };

  function markNotificationRead(id) {
    fetch('/api/notifications/' + id + '/read', { method: 'POST' }).catch(() => {});
  }

  window.markAllNotificationsRead = function () {
    fetch('/api/notifications/read-all', { method: 'POST' })
      .then(() => loadNotifications())
      .catch(() => {});
  };

  document.addEventListener('click', function (e) {
    const wrap = document.querySelector('.gnb-notify-wrap');
    const dd = document.getElementById('gnbNotifyDropdown');
    if (wrap && dd && dd.style.display !== 'none' && !wrap.contains(e.target)) {
      dd.style.display = 'none';
    }
  });

  loadNotifications();

  // ── 다크모드 토글 (초기 적용은 layout.html head의 인라인 스크립트가 FOUC 방지용으로 먼저 처리) ──
  window.toggleTheme = function () {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    if (isDark) {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', 'dark');
    }
    try {
      localStorage.setItem('unifeed-theme', isDark ? 'light' : 'dark');
    } catch (e) {}
  };
})();
