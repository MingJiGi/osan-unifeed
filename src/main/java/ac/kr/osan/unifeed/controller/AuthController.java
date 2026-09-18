package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.dto.SignupForm;
import ac.kr.osan.unifeed.dto.LoginForm;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 로그인 폼 화면 (이미 로그인 상태면 메인인 지도로 이동)
    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String verifyPending,
                            @RequestParam(required = false) String verified,
                            @RequestParam(required = false) String verifyError,
                            HttpSession session, Model model){
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember != null) {
            return "redirect:/map";
        }
        model.addAttribute("loginForm", new LoginForm());

        if (verifyPending != null) {
            model.addAttribute("loginInfo", "인증 메일을 보냈습니다. 받은 메일함(스팸함도 확인)에서 링크를 눌러 인증을 완료해주세요.");
        } else if (verified != null) {
            model.addAttribute("loginInfo", "이메일 인증이 완료되었습니다! 로그인해주세요.");
        } else if (verifyError != null) {
            model.addAttribute("loginError", "인증 링크가 유효하지 않거나 만료되었습니다. 이메일과 비밀번호로 로그인을 시도하시면 인증 메일을 다시 받을 수 있는 버튼이 나타납니다.");
        }

        return "auth/login";
    }

    // 로그인 처리 (성공 시 메인 페이지인 지도로 이동)
    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm loginForm,
                        BindingResult bindingResult, HttpSession session, Model model){
        if (bindingResult.hasErrors()){
            return "auth/login";
        }

        // 이메일 + 비밀번호 인증 (BCrypt 해시 검증, 레거시 평문 계정은 성공 시 자동 마이그레이션,
        // 이메일 미인증 계정은 IllegalStateException)
        Member member;
        try {
            member = memberService.authenticate(loginForm.getEmail(), loginForm.getPassword());
        } catch (IllegalStateException e) {
            model.addAttribute("loginError", e.getMessage());
            model.addAttribute("emailNotVerified", loginForm.getEmail());
            return "auth/login";
        }

        if (member == null){
            model.addAttribute("loginError", "이메일 또는 비밀번호가 일치하지 않습니다.");

            return "auth/login";
        }

        // 세션에 로그인한 회원 저장 후 메인 지도 페이지로 리다이렉트
        session.setAttribute("loginMember", member);
        return "redirect:/map";
    }

    // 이메일 인증 링크 클릭 처리
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token){
        boolean ok = memberService.verifyEmailToken(token);
        return ok ? "redirect:/login?verified=true" : "redirect:/login?verifyError=true";
    }

    // 인증 메일 재전송 — 로그인 화면에서 "인증 메일을 못 받았어요" 클릭 시
    @PostMapping("/resend-verification")
    public String resendVerification(@RequestParam String email, Model model){
        boolean sent = memberService.resendVerification(email);
        if (!sent) {
            model.addAttribute("loginForm", new LoginForm());
            model.addAttribute("loginError", "인증 메일을 재전송할 수 없습니다. 이메일을 다시 확인해주세요.");
            return "auth/login";
        }
        return "redirect:/login?verifyPending=true";
    }

    // 로그아웃
    @PostMapping("/logout")
    public String logout(HttpSession session){
        session.invalidate(); // 세션 전체 삭제
        return "redirect:/login";
    }

    // 비밀번호 찾기 1단계 화면 (이메일 입력)
    @GetMapping("/find-password")
    public String findPasswordForm(){
        return "auth/find-password";
    }

    // 비밀번호 찾기 1단계 처리 — 이메일이 가입되어 있으면 2단계(새 비밀번호 설정) 화면을 보여준다.
    // [주의] 이 앱엔 메일 발송 기능이 없어서 이메일 소유 확인은 못 한다. 학습용 데모 수준의 간단한 흐름이다.
    @PostMapping("/find-password")
    public String verifyEmailForReset(@RequestParam String email, Model model){
        Member member = memberService.findByEmail(email);
        if (member == null){
            model.addAttribute("error", "가입되지 않은 이메일입니다.");
            return "auth/find-password";
        }
        model.addAttribute("verifiedEmail", email);
        return "auth/find-password";
    }

    // 비밀번호 찾기 2단계 처리 — 새 비밀번호 저장
    @PostMapping("/find-password/reset")
    public String resetPassword(@RequestParam String email,
                                @RequestParam String newPassword,
                                @RequestParam String newPasswordConfirm,
                                Model model){
        if (newPassword == null || newPassword.length() < 4){
            model.addAttribute("error", "비밀번호는 4자 이상이어야 합니다.");
            model.addAttribute("verifiedEmail", email);
            return "auth/find-password";
        }
        if (!newPassword.equals(newPasswordConfirm)){
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
            model.addAttribute("verifiedEmail", email);
            return "auth/find-password";
        }
        if (!memberService.resetPassword(email, newPassword)){
            return "redirect:/find-password";
        }
        return "redirect:/login";
    }

    // 회원가입 폼 화면
    @GetMapping("/signup")
    public String signupForm(Model model){
        model.addAttribute("signupForm", new SignupForm());
        return "auth/signup";
    }

    // 회원가입 처리
    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupForm signupForm,
                         BindingResult bindingResult, Model model){
        if (bindingResult.hasErrors()){
            return "auth/signup";
        }
        //비밀번호 확인 일치 검사
        if (!signupForm.getPassword().equals(signupForm.getPasswordConfirm())){
            model.addAttribute("signupError", "비밀번호가 일치하지 않습니다");

            return "auth/signup";
        }

        // 회원 저장 (증복이면 null 반환)
        Member member = memberService.signup(
                signupForm.getName(),
                signupForm.getEmail(),
                signupForm.getPassword()
        );

        if (member == null){
            model.addAttribute("signupError", "이미 사용 중인 이메일입니다.");
            return "auth/signup";
        }
        // 가입 성공 — 인증 메일 발송됨, 로그인 화면에서 안내
        return "redirect:/login?verifyPending=true";
    }





































}
