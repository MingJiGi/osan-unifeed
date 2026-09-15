package ac.kr.osan.unifeed.controller;


import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.service.CommentService;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.service.PostService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MypageController {

    private final PostService postService;
    private final CommentService commentService;
    private final MemberService memberService;

    public MypageController(PostService postService, CommentService commentService, MemberService memberService) {
        this.postService = postService;
        this.commentService = commentService;
        this.memberService = memberService;
    }

    @GetMapping("/mypage")
    public String mypage(HttpSession session, Model model){
        // 로그인 여부 확인
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null){
            return "redirect:/login";
        }

        // 최신 회원 정보 동기화 (혹시 다른 곳에서 변경되었을 경우 대비)
        loginMember = memberService.findById(loginMember.getId());
        session.setAttribute("loginMember", loginMember);

        // 1. 내가 쓴 글 목록
        List<Post> myPosts = postService.findByAuthor(loginMember);
        // 2. 내가 쓴 리뷰(댓글) 목록 (게시글 정보 함께 페치)
        List<Comment> myComments = commentService.findByAuthor(loginMember);
        // 3. 내가 저장한(좋아요 누른) 맛집 목록
        List<Post> likedPosts = postService.findLikedPosts(loginMember);

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("myPosts", myPosts);
        model.addAttribute("myComments", myComments);
        model.addAttribute("likedPosts", likedPosts);

        return "mypage/index";
    }

    // 프로필 편집 처리 (닉네임, 프로필 이미지 URL)
    @PostMapping("/mypage/profile")
    public String updateProfile(@RequestParam String name,
                                @RequestParam(required = false) String profileImage,
                                HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }

        Member updated = memberService.updateProfile(loginMember.getId(), name, profileImage);
        session.setAttribute("loginMember", updated);

        return "redirect:/mypage";
    }
}
