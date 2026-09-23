package ac.kr.osan.unifeed.controller;


import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantReviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class MypageController {

    private final PostService postService;
    private final RestaurantReviewService restaurantReviewService;
    private final RestaurantBookmarkService restaurantBookmarkService;
    private final MemberService memberService;

    public MypageController(PostService postService,
                            RestaurantReviewService restaurantReviewService,
                            RestaurantBookmarkService restaurantBookmarkService,
                            MemberService memberService) {
        this.postService = postService;
        this.restaurantReviewService = restaurantReviewService;
        this.restaurantBookmarkService = restaurantBookmarkService;
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

        // 1. 내가 쓴 커뮤니티 게시글 목록
        List<Post> myPosts = postService.findByAuthor(loginMember);
        // 2. 지도 맛집에 내가 남긴 리뷰 목록 (식당 정보 포함)
        List<RestaurantReview> myReviews = restaurantReviewService.findByAuthor(loginMember);
        // 3. 지도에서 내가 저장(찜)한 맛집 목록
        List<Restaurant> savedRestaurants = restaurantBookmarkService.findBookmarkedRestaurants(loginMember);

        model.addAttribute("loginMember", loginMember);
        model.addAttribute("myPosts", myPosts);
        model.addAttribute("myReviews", myReviews);
        model.addAttribute("savedRestaurants", savedRestaurants);

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

    // 비밀번호 변경 — 현재 비밀번호 재확인 후 교체
    @PostMapping("/mypage/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String newPasswordConfirm,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }

        if (!newPassword.equals(newPasswordConfirm)) {
            redirectAttributes.addFlashAttribute("passwordError", "새 비밀번호가 일치하지 않습니다.");
            return "redirect:/mypage";
        }
        if (newPassword.length() < 8) {
            redirectAttributes.addFlashAttribute("passwordError", "비밀번호는 8자 이상이어야 합니다.");
            return "redirect:/mypage";
        }

        try {
            memberService.changePassword(loginMember.getId(), currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("passwordError", e.getMessage());
            return "redirect:/mypage";
        }

        redirectAttributes.addFlashAttribute("passwordSuccess", "비밀번호가 변경되었습니다.");
        return "redirect:/mypage";
    }

    // 회원 탈퇴 처리 — 비밀번호 재확인 후 삭제 (게시글/댓글/리뷰/북마크/시간표는 DB에서 함께 삭제됨)
    @PostMapping("/mypage/withdraw")
    public String withdraw(@RequestParam String password, HttpSession session, RedirectAttributes redirectAttributes) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }

        try {
            memberService.withdraw(loginMember.getId(), password);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("withdrawError", e.getMessage());
            return "redirect:/mypage";
        }

        session.invalidate();
        return "redirect:/login";
    }
}
