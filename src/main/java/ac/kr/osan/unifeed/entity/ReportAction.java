package ac.kr.osan.unifeed.entity;

// 관리자가 신고를 처리하며 실제로 취한 조치. 감사 로그(누가/언제/무엇)의 "무엇" 부분.
public enum ReportAction {
    NONE,
    CONTENT_DELETED,
    AUTHOR_SUSPENDED,
    AUTHOR_UNSUSPENDED
}
