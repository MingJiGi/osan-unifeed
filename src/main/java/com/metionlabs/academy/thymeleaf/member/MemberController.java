package com.metionlabs.academy.thymeleaf.member;

import com.metionlabs.academy.thymeleaf.post.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

// ############################################################################
// MemberController — 회원 화면
// ----------------------------------------------------------------------------
// @Controller (not @RestController) : 메서드가 반환하는 문자열 = 렌더링할 템플릿 이름.
// ############################################################################
@Controller
@RequestMapping("/members")
public class MemberController {

	private final MemberService memberService;
	private final PostService postService;

	public MemberController(MemberService memberService, PostService postService) {
		this.memberService = memberService;
		this.postService = postService;
	}

	// 회원 목록.  GET /members → templates/members/list.html
	@GetMapping
	public String list(Model model) {
		model.addAttribute("members", memberService.findAll());
		return "members/list";
	}

	// 회원 상세 — 그 회원이 쓴 글 목록도 함께.  GET /members/{id}
	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {
		Member member = memberService.findById(id);
		model.addAttribute("member", member);
		model.addAttribute("posts", postService.findByAuthor(member));
		return "members/detail";
	}
}
