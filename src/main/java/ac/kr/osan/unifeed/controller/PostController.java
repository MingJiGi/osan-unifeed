package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.entity.Post;

import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.dto.PostForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// ############################################################################
// PostController — 게시판 화면 (목록/상세/작성/수정/삭제)
// ----------------------------------------------------------------------------
// [핵심] @Controller 는 메서드가 반환한 문자열 이름의 템플릿을 렌더링한다.
//        "redirect:/..." 를 반환하면 그 URL 로 리다이렉트한다.
// Model = 템플릿에 넘길 데이터 보따리.  model.addAttribute("posts", ...) → 템플릿에서 ${posts}.
// ############################################################################
@Controller
@RequestMapping("/posts")
public class PostController {

	private final PostService postService;
	private final MemberService memberService; // 작성자 드롭다운 목록용

	public PostController(PostService postService, MemberService memberService) {
		this.postService = postService;
		this.memberService = memberService;
	}

	// ── 목록 ──────────────────────────────────────────────────────────────────
	@GetMapping
	public String list(Model model) {
		model.addAttribute("posts", postService.findAll());
		return "posts/list";
	}

	// ── 상세 ──────────────────────────────────────────────────────────────────
	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("post", postService.findById(id));
		return "posts/detail";
	}

	// ── 새 글 폼 ──────────────────────────────────────────────────────────────
	// 빈 PostForm + 작성자 선택용 회원 목록을 모델에 담아 폼을 그린다.
	@GetMapping("/new")
	public String createForm(Model model) {
		model.addAttribute("postForm", new PostForm());
		model.addAttribute("members", memberService.findAll());
		return "posts/form";
	}

	// ── 새 글 저장 ────────────────────────────────────────────────────────────
	// @Valid 바로 뒤에 BindingResult 를 두어 검증 결과를 받는다(순서 중요).
	@PostMapping
	public String create(@Valid @ModelAttribute PostForm postForm,
	                     BindingResult bindingResult,
	                     Model model) {
		if (bindingResult.hasErrors()) {
			// 폼을 다시 그릴 때도 드롭다운용 회원 목록이 필요하다.
			model.addAttribute("members", memberService.findAll());
			return "posts/form";
		}
		Long newId = postService.create(postForm);
		// PRG 패턴: 저장 후 상세로 redirect. 새로고침해도 중복 저장이 안 된다.
		return "redirect:/posts/" + newId;
	}

	// ── 수정 폼 ──────────────────────────────────────────────────────────────
	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		Post post = postService.findById(id);
		// 기존 값을 폼 DTO 로 옮겨 담는다(작성자는 수정 대상이 아니라 authorId 로만 유지).
		model.addAttribute("postForm",
				new PostForm(post.getAuthor().getId(), post.getTitle(), post.getContent()));
		model.addAttribute("members", memberService.findAll());
		model.addAttribute("postId", id); // 폼 action 이 어느 글을 수정하는지.
		return "posts/form";
	}

	// ── 수정 저장 ────────────────────────────────────────────────────────────
	@PostMapping("/{id}")
	public String update(@PathVariable Long id,
	                     @Valid @ModelAttribute PostForm postForm,
	                     BindingResult bindingResult,
	                     Model model) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("members", memberService.findAll());
			model.addAttribute("postId", id);
			return "posts/form";
		}
		postService.update(id, postForm);
		return "redirect:/posts/" + id;
	}

	// ── 삭제 ──────────────────────────────────────────────────────────────────
	// GET 이 아니라 POST 로 삭제한다(링크 프리페치로 실수 삭제되는 것 방지).
	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id) {
		postService.delete(id);
		return "redirect:/posts";
	}
}
