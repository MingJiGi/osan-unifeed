package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.service.UniversityService;
import ac.kr.osan.unifeed.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// ############################################################################
// AdminController — 신고/회원 관리 전용 화면 (관리자만 접근 가능)
// ----------------------------------------------------------------------------
// 별도 권한 체계(Spring Security 등) 없이 Member.isAdmin 플래그 하나로 최소한만 구현.
// 관리자 지정은 회원가입 흐름에 없고, DB에서 직접 is_admin=TRUE로 바꿔야 한다(의도된 설계).
// 관리자 권한 체크는 AdminAccessInterceptor가 /admin/** 전체에 공통으로 걸어준다
// (config/WebConfig.java에 등록) — 메서드마다 따로 체크하지 않는다.
// ############################################################################
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ReportService reportService;
    private final MemberService memberService;
    private final PostService postService;
    private final UniversityService universityService;

    public AdminController(ReportService reportService, MemberService memberService, PostService postService, UniversityService universityService) {
        this.reportService = reportService;
        this.memberService = memberService;
        this.postService = postService;
        this.universityService = universityService;
    }

    private Member currentAdmin(HttpSession session) {
        return (Member) session.getAttribute("loginMember");
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("reports", reportService.findAllForAdmin());
        return "admin/reports";
    }

    @PostMapping("/reports/{id}/resolve")
    public String resolve(@PathVariable Long id, HttpSession session) {
        reportService.resolve(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    @PostMapping("/reports/{id}/dismiss")
    public String dismiss(@PathVariable Long id, HttpSession session) {
        reportService.dismiss(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 신고된 콘텐츠(게시글/댓글/리뷰) 자체를 삭제.
    @PostMapping("/reports/{id}/delete-content")
    public String deleteContent(@PathVariable Long id, HttpSession session) {
        reportService.deleteContent(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 신고된 콘텐츠 작성자 계정을 정지(로그인 차단).
    @PostMapping("/reports/{id}/suspend-author")
    public String suspendAuthor(@PathVariable Long id, HttpSession session) {
        reportService.suspendAuthor(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 정지 해제.
    @PostMapping("/reports/{id}/unsuspend-author")
    public String unsuspendAuthor(@PathVariable Long id, HttpSession session) {
        reportService.unsuspendAuthor(id, currentAdmin(session));
        return "redirect:/admin/reports";
    }

    // 전체 회원 목록 — 예전 MemberController#list는 관리자 체크가 없어서 아무나 남의 정보를
    // 볼 수 있는 프라이버시 문제가 있었다. 그 화면을 복원하지 않고 관리자 전용으로 새로 둔다.
    @GetMapping("/members")
    public String members(@RequestParam(required = false) String q,
                          @RequestParam(required = false) Long universityId, Model model) {
        model.addAttribute("members", memberService.search(q, universityId));
        model.addAttribute("universities", universityService.search(null));
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("universityId", universityId);
        return "admin/members-list";
    }

    // 회원 상세 + 그 회원이 쓴 글 목록.
    @GetMapping("/members/{id}")
    public String memberDetail(@PathVariable Long id, Model model) {
        Member member = memberService.findById(id);
        model.addAttribute("member", member);
        model.addAttribute("posts", postService.findByAuthor(member));
        return "admin/members-detail";
    }
}
