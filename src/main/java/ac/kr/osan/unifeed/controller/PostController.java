package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.service.CommentService;
import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.util.AnonymousNaming;

import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.service.UniversityService;
import ac.kr.osan.unifeed.dto.PostForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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
    private final CommentService commentService;
    private final UniversityService universityService; // 관리자용 학교 전환 드롭다운

	public PostController(PostService postService, MemberService memberService, CommentService commentService,
	                       UniversityService universityService) {
		this.postService = postService;
		this.memberService = memberService;
        this.commentService = commentService;
        this.universityService = universityService;
	}

    // 관리자가 ?viewUniversity=ID 로 다른 학교 커뮤니티를 둘러볼 수 있게 한다(신고 대응이 아니라
    // 그냥 운영 목적 전체 열람). 관리자가 아니거나 파라미터가 없으면 본인 학교 그대로.
    private University resolveViewUniversity(Member loginMember, Long viewUniversity) {
        if (viewUniversity == null || !loginMember.isAdmin()) {
            return loginMember.getUniversity();
        }
        University chosen = universityService.findById(viewUniversity);
        return chosen != null ? chosen : loginMember.getUniversity();
    }

    // 목록(list())은 학교별로 걸러서 보여주지만, ID로 직접 접근하는 상세/댓글/좋아요는 그것만으로
    // 막히지 않는다 — 다른 학교 게시글 ID를 알면 그대로 열람·댓글 작성이 가능했던 문제를 여기서 막는다.
    // 둘 중 하나라도 학교 미지정이면(NULL) 무조건 다른 학교로 취급한다.
    private boolean isSameUniversity(Post post, Member member) {
        var postUniversity = post.getAuthor().getUniversity();
        var memberUniversity = member.getUniversity();
        return postUniversity != null && memberUniversity != null
                && postUniversity.getId().equals(memberUniversity.getId());
    }

	// ── 목록 ──────────────────────────────────────────────────────────────────
	@GetMapping
	public String list(@RequestParam(required = false) Long viewUniversity, HttpSession session, Model model) {
		Member loginMember = (Member) session.getAttribute("loginMember");
		if (loginMember == null) {
			return "redirect:/login";
		}
		University targetUniversity = resolveViewUniversity(loginMember, viewUniversity);
		model.addAttribute("loginMember", loginMember);
		model.addAttribute("posts", postService.findAll(targetUniversity));
		model.addAttribute("postForm", new PostForm());
		model.addAttribute("members", memberService.findAll());
		if (loginMember.isAdmin()) {
			model.addAttribute("allUniversities", universityService.search(null));
			model.addAttribute("viewingUniversity", targetUniversity);
		}
		return "posts/list";
	}

//	// ── 상세 ──────────────────────────────────────────────────────────────────
//	@GetMapping("/{id}")
//	public String detail(@PathVariable Long id, Model model) {
//		model.addAttribute("post", postService.findById(id));
//		return "posts/detail";
//	}

	// ── 새 글 폼 ──────────────────────────────────────────────────────────────
	// 빈 PostForm + 작성자 선택용 회원 목록을 모델에 담아 폼을 그린다.
	@GetMapping("/new")
	public String createForm(HttpSession session, Model model) {
        //로그인 여부 확인
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null){
            return "redirect:/login";
        }
		return "redirect:/posts?write=true";
	}

	// ── 새 글 저장 ────────────────────────────────────────────────────────────
	// @Valid 바로 뒤에 BindingResult 를 두어 검증 결과를 받는다(순서 중요).
	@PostMapping
	public String create(@Valid @ModelAttribute PostForm postForm,
	                     BindingResult bindingResult,
	                     Model model, HttpSession session) {
        //로그인 여부 확인
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null){
            return "redirect:/login";
        }

		if (bindingResult.hasErrors()) {
			model.addAttribute("members", memberService.findAll());
            model.addAttribute("hasError", true);
            model.addAttribute("posts", postService.findAll(loginMember.getUniversity()));
			return "posts/list";
		}
        postForm.setAuthorId(loginMember.getId());
        postService.create(postForm);

		return "redirect:/posts";
	}

	// ── 수정 폼 ──────────────────────────────────────────────────────────────
	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model, HttpSession session) {
		Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null){
            return "redirect:/login";
        }

        Post post = postService.findById(id);

        if (!post.getAuthor().getId().equals(loginMember.getId())) {
            return "redirect:/posts/" + id;
        }

		// 기존 값을 폼 DTO 로 옮겨 담는다(작성자는 수정 대상이 아니라 authorId 로만 유지).
		// tags/imgUrl/restaurantId 도 같이 옮겨야 한다 — 안 그러면 수정 저장할 때 기존 값이 날아간다.
		PostForm postForm = new PostForm(post.getAuthor().getId(), post.getTitle(), post.getContent());
		postForm.setTags(post.getTags());
		postForm.setImgUrl(post.getImgUrl());
		postForm.setRestaurantId(post.getRestaurant() != null ? post.getRestaurant().getId() : null);

		model.addAttribute("postForm", postForm);
		model.addAttribute("post", post); // 사진/맛집 태그 미리보기 렌더링용
		model.addAttribute("members", memberService.findAll());
		model.addAttribute("postId", id); // 폼 action 이 어느 글을 수정하는지.
		return "posts/form";
	}

	// ── 수정 저장 ────────────────────────────────────────────────────────────
	@PostMapping("/{id}")
	public String update(@PathVariable Long id,
	                     @Valid @ModelAttribute PostForm postForm,
	                     BindingResult bindingResult,
	                     Model model, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null){
            return "redirect:/login";
        }

        Post post = postService.findById(id);

        // 작성자 본인이 아니면 거부
        if (!post.getAuthor().getId().equals(loginMember.getId())){
            return "redirect:/posts/" + id;
        }


        if (bindingResult.hasErrors()) {
			model.addAttribute("members", memberService.findAll());
			model.addAttribute("post", post);
			model.addAttribute("postId", id);
			return "posts/form";
		}
		postService.update(id, postForm);
		return "redirect:/posts/" + id;
	}

	// ── 삭제 ──────────────────────────────────────────────────────────────────
	// GET 이 아니라 POST 로 삭제한다(링크 프리페치로 실수 삭제되는 것 방지).
	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null){
            return "redirect:/login";
        }

        Post post = postService.findById(id);

        // 작성자 본인이 아니면 삭제 거부 — 단, 관리자는 신고 없이도 직접 삭제할 수 있어야 한다
        // (학교 전환으로 둘러보다가 문제되는 글을 발견해도 신고부터 넣어야 하는 건 비합리적).
        if (!post.getAuthor().getId().equals(loginMember.getId()) && !loginMember.isAdmin()){
            return "redirect:/posts/" + id;
        }

        postService.delete(id);
		return "redirect:/posts";
	}
//
//    @PostMapping("/{id}/like")
//    public String like(@PathVariable Long id){
//        postService.like(id);
//        return "redirect:/posts/" + id;
//    }

    /*--------------------- posts 상세 조회 --------------------- */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        Post post = postService.findById(id);
        // 관리자는 신고 처리 등 운영 목적으로 다른 학교 게시글도 열람만은 할 수 있어야 한다
        // (댓글/좋아요 같은 직접 참여는 여전히 본인 학교로 제한 — 아래 like/createComment는 그대로 둠).
        if (!isSameUniversity(post, loginMember) && !loginMember.isAdmin()) {
            return "redirect:/posts";
        }
        // 신고 누적으로 자동 숨김된 글은 목록에서만 빠지고 직접 URL로는 그대로 열려있었다 —
        // 댓글(th:text로 본문 마스킹)과 다르게 게시글은 별도 마스킹이 없으니 관리자 확인 전까지 아예 막는다.
        if (post.isHidden() && !loginMember.isAdmin()) {
            return "redirect:/posts";
        }
        List<Comment> comments = commentService.findByPost(post);

        // 익명 처리 — 이 게시글(스레드) 안에서만 통하는 "익명1", "익명2" 번호를 매긴다.
        // 글쓴이 → 댓글 → 그 댓글의 답글들 순서로, 같은 회원이면 항상 같은 번호가 붙는다.
        List<Long> orderedAuthorIds = new java.util.ArrayList<>();
        orderedAuthorIds.add(post.getAuthor().getId());
        for (Comment c : comments) {
            orderedAuthorIds.add(c.getAuthor().getId());
            for (Comment r : c.getReplies()) {
                orderedAuthorIds.add(r.getAuthor().getId());
            }
        }

        model.addAttribute("post", post);
        model.addAttribute("isLiked", postService.isLiked(id, loginMember));
        model.addAttribute("comments", comments);
        model.addAttribute("anonLabels", AnonymousNaming.assignLabels(orderedAuthorIds));
        model.addAttribute("loginMember", loginMember);
        return "posts/detail";
    }

    @PostMapping("/{id}/like")
    public String like(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login"; // 로그인 안 했으면 로그인 창으로
        }
        if (!isSameUniversity(postService.findById(id), loginMember)) {
            return "redirect:/posts";
        }
        postService.toggleLike(id, loginMember);
        return "redirect:/posts/" + id;
    }

    /*--------------------- 댓글 및 대댓글 작성 --------------------- */
    @PostMapping("/{id}/comments")
    public String createComment(@PathVariable Long id,
                                @RequestParam String content,
                                @RequestParam(required = false) Long parentId,
                                HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        if (!isSameUniversity(postService.findById(id), loginMember)) {
            return "redirect:/posts";
        }
        commentService.create(id, loginMember, content, parentId);
        return "redirect:/posts/" + id;
    }

    /*--------------------- 댓글 삭제 --------------------- */
    @PostMapping("/{id}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long id,
                                @PathVariable Long commentId,
                                HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        commentService.delete(commentId, loginMember);
        return "redirect:/posts/" + id;
    }

    /*--------------------- 댓글 수정 --------------------- */
    @PostMapping("/{id}/comments/{commentId}/edit")
    public String updateComment(@PathVariable Long id,
                                @PathVariable Long commentId,
                                @RequestParam String content,
                                HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        commentService.update(commentId, loginMember, content);
        return "redirect:/posts/" + id;
    }

}
