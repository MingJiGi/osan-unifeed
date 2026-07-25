package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// ############################################################################
// HomeController — 첫 페이지
// ----------------------------------------------------------------------------
// 루트("/")로 들어오면 게시판 목록으로 보낸다.
// ############################################################################
@Controller
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "redirect:/posts";
	}
}
