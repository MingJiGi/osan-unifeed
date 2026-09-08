/**
 * ============================================================================
 * UNIFEED (유니피드) 공통 자바스크립트
 * ============================================================================
 * 이 파일은 웹 애플리케이션 전체에서 공통으로 사용되는 JS 기능들을 담고 있습니다.
 * (모달 창 열기/닫기, 팝업 메시지(토스트), 데이터 통신 시 보안 토큰 설정 등)
 * 
 * 모든 페이지에서 <script src="/js/common.js"></script> 로 불러와 사용합니다.
 * ============================================================================
 */

// 1. DOMContentLoaded 이벤트
// HTML 문서가 모두 브라우저에 불려진(로딩된) 후에 실행되는 함수입니다.
// 이 안에서 요소에 이벤트를 달아주는 것이 안전합니다.
document.addEventListener('DOMContentLoaded', function() {
    console.log('UNIFEED Common JS Loaded.');
    
    // 모달(팝업) 바깥 어두운 배경 영역 클릭 시 닫히도록 설정
    const modals = document.querySelectorAll('.modal-overlay');
    modals.forEach(modal => {
        modal.addEventListener('click', function(e) {
            // 클릭한 대상(e.target)이 내부 콘텐츠(.modal-content)가 아니라 배경(overlay) 자체일 때만 닫음
            if(e.target === this) {
                closeModal(this.id);
            }
        });
    });
});

/**
 * 2. 모달 열기 함수 (openModal)
 * 화면 중앙에 팝업창을 띄웁니다.
 * 
 * @param {string} modalId - 띄울 모달의 ID 값 (예: 'loginModal')
 */
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if(modal) {
        // 'show' 클래스를 추가하면 CSS에 의해 부드럽게 나타납니다.
        modal.classList.add('show');
        // 모달이 켜졌을 때 뒷 배경이 스크롤되지 않도록 막습니다.
        document.body.style.overflow = 'hidden';
    } else {
        console.error('해당 ID의 모달을 찾을 수 없습니다:', modalId);
    }
}

/**
 * 2. 모달 닫기 함수 (closeModal)
 * 화면에 떠있는 팝업창을 닫습니다.
 * 
 * @param {string} modalId - 닫을 모달의 ID 값
 */
function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if(modal) {
        // 'show' 클래스를 제거하여 숨깁니다.
        modal.classList.remove('show');
        // 모달이 닫히면 다시 배경 스크롤이 가능하도록 복구합니다.
        document.body.style.overflow = '';
    }
}

/**
 * 4. CSRF 토큰 헬퍼 함수
 * Spring Security를 사용할 때, POST/PUT/DELETE 등의 요청을 보낼 때 
 * 해킹을 방지하기 위해 보내야 하는 보안 토큰(CSRF Token)을 가져옵니다.
 * 향후 fetch()나 AJAX로 서버에 데이터를 보낼 때 사용됩니다.
 * 
 * @returns {object} headers 객체 - fetch()의 headers에 넣을 수 있는 형태
 */
function getCsrfHeaders() {
    // HTML <head> 태그 안에 심어둔 meta 태그에서 토큰 값을 찾습니다.
    const tokenElement = document.querySelector('meta[name="_csrf"]');
    const headerElement = document.querySelector('meta[name="_csrf_header"]');
    
    // 토큰이 존재하면 헤더 객체를 만들어 반환합니다.
    if (tokenElement && headerElement) {
        const token = tokenElement.getAttribute('content');
        const header = headerElement.getAttribute('content');
        return {
            [header]: token,
            'Content-Type': 'application/json'
        };
    }
    
    // 보안 토큰이 없는 경우 기본적으로 JSON 타입만 설정해서 보냅니다.
    return {
        'Content-Type': 'application/json'
    };
}

/**
 * 5. Toast 알림(잠깐 떴다 사라지는 메시지) 띄우기
 * (향후 토스트 UI 요소를 HTML에 추가했을 때 사용할 수 있는 뼈대 코드입니다.)
 * 
 * @param {string} message - 화면에 보여줄 알림 내용
 * @param {string} type - 'success', 'error', 'info' (색상 구분을 위함)
 */
function showToast(message, type = 'info') {
    // 임시로 브라우저 기본 알림(alert)을 사용합니다.
    // 추후 디자인된 HTML 토스트 팝업이 구현되면 이곳을 수정하세요.
    alert(`[${type}] ${message}`);
}

/**
 * 6. 사용자에게 확인/취소 묻기 (Confirm Dialog 헬퍼)
 * 
 * @param {string} message - 사용자에게 물어볼 메시지 ("정말 삭제하시겠습니까?")
 * @param {function} onConfirm - 사용자가 '확인'을 눌렀을 때 실행할 함수
 */
function confirmAction(message, onConfirm) {
    // 브라우저 기본 확인창 사용
    if(confirm(message)) {
        // 확인을 눌렀다면 전달받은 함수(onConfirm)를 실행합니다.
        if(typeof onConfirm === 'function') {
            onConfirm();
        }
    }
}
