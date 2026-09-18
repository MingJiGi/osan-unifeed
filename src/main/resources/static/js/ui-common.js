/**
 * 유니피드 공통 UI 유틸리티 (ui-common.js)
 * - showToast(message, type): alert() 대체용 토스트 알림 ('success' | 'error' | 'info')
 * - setButtonLoading(btn, loading, loadingText): 비동기 제출 버튼 로딩 상태 표시
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
})();
