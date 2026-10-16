package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.service.UniversityRequestService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    private final UniversityRequestService universityRequestService;

    // 승인 화면에서 "주소로 좌표 찾기"에 쓰는 카카오 지도 JS 키(지도 화면과 같은 키).
    @org.springframework.beans.factory.annotation.Value("${kakao.maps.appkey}")
    private String kakaoAppKey;

    public AdminController(ReportService reportService, MemberService memberService, PostService postService,
                           UniversityService universityService, UniversityRequestService universityRequestService) {
        this.reportService = reportService;
        this.memberService = memberService;
        this.postService = postService;
        this.universityService = universityService;
        this.universityRequestService = universityRequestService;
    }

    private Member currentAdmin(HttpSession session) {
        return (Member) session.getAttribute("loginMember");
    }

    // ------------------------------------------------------------------------
    // 학교 등록 신청 — 목록에 없는 학교 학생이 가입 화면에서 남긴 요청을 처리한다.
    // ------------------------------------------------------------------------
    @GetMapping("/university-requests")
    public String universityRequests(Model model) {
        model.addAttribute("pendingRequests", universityRequestService.findPending());
        model.addAttribute("processedRequests", universityRequestService.findProcessed());
        model.addAttribute("kakaoAppKey", kakaoAppKey);
        return "admin/university-requests";
    }

    // 승인 — 신청 내용을 그대로 쓰지 않고, 관리자가 화면에서 확인·수정한 값으로 학교를 만든다.
    // 좌표는 비워도 되며(모르면 넣지 않는다), 비어 있으면 그 학교 지도는 기본 좌표로 열린다.
    @PostMapping("/university-requests/{id}/approve")
    public String approveUniversityRequest(@PathVariable Long id,
                                           @RequestParam String name,
                                           @RequestParam(required = false) String emailDomain,
                                           @RequestParam(required = false) String regionName,
                                           @RequestParam(required = false) Double latitude,
                                           @RequestParam(required = false) Double longitude,
                                           RedirectAttributes redirectAttributes) {
        try {
            universityRequestService.approve(id, name, emailDomain, regionName, latitude, longitude);
            redirectAttributes.addFlashAttribute("adminSuccess", name + " 을(를) 등록했습니다. 신청자에게 안내 메일을 보냈습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("adminError", e.getMessage());
        }
        return "redirect:/admin/university-requests";
    }

    @PostMapping("/university-requests/{id}/reject")
    public String rejectUniversityRequest(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            universityRequestService.reject(id);
            redirectAttributes.addFlashAttribute("adminSuccess", "신청을 반려했습니다.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("adminError", e.getMessage());
        }
        return "redirect:/admin/university-requests";
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
    //
    // [가입 상태 필터] 이메일 인증을 마쳐야 로그인이 되므로, 미인증 행은 아직 회원이 아니라
    // "가입하다 만 흔적"이다. 그래서 기본 목록에서는 빼고(status=verified) 따로 골라서 본다.
    // 오타 메일로 가입을 시도해 영영 인증 못 하는 행 같은 걸 정리하려면 볼 수는 있어야 한다.
    @GetMapping("/members")
    public String members(@RequestParam(required = false) String q,
                          @RequestParam(required = false) Long universityId,
                          @RequestParam(required = false, defaultValue = "verified") String status,
                          Model model) {
        Boolean verified = switch (status) {
            case "unverified" -> Boolean.FALSE;
            case "all" -> null;
            default -> Boolean.TRUE;      // 기본 = 인증 완료된 실제 회원만
        };
        model.addAttribute("members", memberService.search(q, universityId, verified));
        model.addAttribute("universities", universityService.search(null));
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("universityId", universityId);
        model.addAttribute("status", status);
        model.addAttribute("unverifiedCount", memberService.countUnverified());
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
