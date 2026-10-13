package ac.kr.osan.unifeed.config;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.UUID;

// ############################################################################
// CsrfModelAdvice — 모든 화면(Model)에 CSRF 토큰 / GNB 공통 값을 자동으로 심어준다.
// ----------------------------------------------------------------------------
// 세션당 토큰 1개(없으면 새로 발급, 있으면 재사용) — 컨트롤러를 일일이 안 건드려도
// 모든 th:action POST 폼에서 ${csrfToken}을 그대로 쓸 수 있게 해준다.
// 실제 검증은 CsrfFilter가 담당.
//
// pendingReportCount: GNB 관리자 배지용. 관리자 세션이 아니면 항상 0(배지 숨김) —
// 컨트롤러마다 model.addAttribute 하지 않아도 되게 같은 패턴으로 얹는다.
// ############################################################################
@ControllerAdvice
public class CsrfModelAdvice {

    public static final String SESSION_KEY = "csrfToken";

    private final ReportService reportService;
    private final ac.kr.osan.unifeed.service.UniversityRequestService universityRequestService;

    public CsrfModelAdvice(ReportService reportService,
                           ac.kr.osan.unifeed.service.UniversityRequestService universityRequestService) {
        this.reportService = reportService;
        this.universityRequestService = universityRequestService;
    }

    @ModelAttribute("csrfToken")
    public String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute(SESSION_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(SESSION_KEY, token);
        }
        return token;
    }

    @ModelAttribute("pendingReportCount")
    public long pendingReportCount(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null || !loginMember.isAdmin()) {
            return 0;
        }
        return reportService.countPending();
    }

    // GNB "학교 등록 신청" 배지 — 관리자가 아니면 항상 0(배지 숨김).
    @ModelAttribute("pendingUniversityRequestCount")
    public long pendingUniversityRequestCount(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null || !loginMember.isAdmin()) {
            return 0;
        }
        return universityRequestService.pendingCount();
    }
}
