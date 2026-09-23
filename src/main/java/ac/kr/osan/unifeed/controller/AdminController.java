package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// ############################################################################
// AdminController — 신고 관리 전용 화면 (관리자만 접근 가능)
// ----------------------------------------------------------------------------
// 별도 권한 체계(Spring Security 등) 없이 Member.isAdmin 플래그 하나로 최소한만 구현.
// 관리자 지정은 회원가입 흐름에 없고, DB에서 직접 is_admin=TRUE로 바꿔야 한다(의도된 설계).
// ############################################################################
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ReportService reportService;

    public AdminController(ReportService reportService) {
        this.reportService = reportService;
    }

    private boolean isNotAdmin(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        return loginMember == null || !loginMember.isAdmin();
    }

    private Member currentAdmin(HttpSession session) {
        return (Member) session.getAttribute("loginMember");
    }

    @GetMapping("/reports")
    public String reports(HttpSession session, Model model) {
        if (isNotAdmin(session)) {
            return "redirect:/login";
        }
        model.addAttribute("reports", reportService.findAllForAdmin());
        return "admin/reports";
    }

    @PostMapping("/reports/{id}/resolve")
    public String resolve(@PathVariable Long id, HttpSession session) {
        if (isNotAdmin(session)) {
            return "redirect:/login";
        }
        reportService.resolve(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    @PostMapping("/reports/{id}/dismiss")
    public String dismiss(@PathVariable Long id, HttpSession session) {
        if (isNotAdmin(session)) {
            return "redirect:/login";
        }
        reportService.dismiss(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 신고된 콘텐츠(게시글/댓글/리뷰) 자체를 삭제.
    @PostMapping("/reports/{id}/delete-content")
    public String deleteContent(@PathVariable Long id, HttpSession session) {
        if (isNotAdmin(session)) {
            return "redirect:/login";
        }
        reportService.deleteContent(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 신고된 콘텐츠 작성자 계정을 정지(로그인 차단).
    @PostMapping("/reports/{id}/suspend-author")
    public String suspendAuthor(@PathVariable Long id, HttpSession session) {
        if (isNotAdmin(session)) {
            return "redirect:/login";
        }
        reportService.suspendAuthor(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 정지 해제.
    @PostMapping("/reports/{id}/unsuspend-author")
    public String unsuspendAuthor(@PathVariable Long id, HttpSession session) {
        if (isNotAdmin(session)) {
            return "redirect:/login";
        }
        reportService.unsuspendAuthor(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }
}
