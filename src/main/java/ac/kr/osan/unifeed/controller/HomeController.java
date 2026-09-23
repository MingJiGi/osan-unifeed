package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// ############################################################################
// HomeController — 첫 페이지
// ----------------------------------------------------------------------------
// 루트("/")로 들어오면 로그인 여부를 확인하여 분기한다.
// ############################################################################
@Controller
public class HomeController {

	@GetMapping("/")
	public String home(HttpSession session) {
		Member loginMember = (Member) session.getAttribute("loginMember");
		if (loginMember == null) {
			return "redirect:/login";
		}
		return "redirect:/map";
	}

	// 약관/정책 문서 (로그인 없이도 열람 가능)
	@GetMapping("/terms")
	public String terms() {
		return "legal/terms";
	}

	@GetMapping("/privacy")
	public String privacy() {
		return "legal/privacy";
	}

	@GetMapping("/community-guidelines")
	public String communityGuidelines() {
		return "legal/community-guidelines";
	}
}
