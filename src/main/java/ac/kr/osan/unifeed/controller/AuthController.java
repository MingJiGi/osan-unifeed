package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.config.RateLimiter;
import ac.kr.osan.unifeed.dto.SignupForm;
import ac.kr.osan.unifeed.dto.LoginForm;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final RateLimiter rateLimiter;

    public AuthController(MemberService memberService, RateLimiter rateLimiter) {
        this.memberService = memberService;
        this.rateLimiter = rateLimiter;
    }

    // 요청자 IP — 무차별 대입/스팸성 반복 요청을 IP 단위로 제한할 때 쓴다.
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // 로그인 폼 화면 (이미 로그인 상태면 메인인 지도로 이동)
    @GetMapping("/login")
    public String loginForm(@RequestParam(required = false) String verifyPending,
                            @RequestParam(required = false) String verified,
                            @RequestParam(required = false) String verifyError,
                            @RequestParam(required = false) String passwordReset,
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
        } else if (passwordReset != null) {
            model.addAttribute("loginInfo", "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.");
        }

        return "auth/login";
    }

    // 로그인 처리 (성공 시 메인 페이지인 지도로 이동)
    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm loginForm,
                        BindingResult bindingResult, HttpSession session, Model model,
                        HttpServletRequest request){
        if (bindingResult.hasErrors()){
            return "auth/login";
        }

        // 무차별 대입 방어 — 같은 IP에서 5분에 10회 넘게 시도하면 잠시 막는다.
        if (!rateLimiter.allow("login:" + clientIp(request), 10, 300)) {
            model.addAttribute("loginError", "로그인 시도가 너무 많습니다. 5분 후 다시 시도해주세요.");
            return "auth/login";
        }

        // 이메일 + 비밀번호 인증 (BCrypt 해시 검증, 레거시 평문 계정은 성공 시 자동 마이그레이션,
        // 이메일 미인증 계정은 IllegalStateException)
        Member member;
        try {
            member = memberService.authenticate(loginForm.getEmail(), loginForm.getPassword());
        } catch (ac.kr.osan.unifeed.exception.MemberSuspendedException e) {
            model.addAttribute("loginError", e.getMessage());
            return "auth/login";
        } catch (IllegalStateException e) {
            model.addAttribute("loginError", e.getMessage());
            model.addAttribute("emailNotVerified", loginForm.getEmail());
            return "auth/login";
        }

        if (member == null){
            model.addAttribute("loginError", "이메일 또는 비밀번호가 일치하지 않습니다.");

            return "auth/login";
        }

        // 2차 인증(이메일 OTP)을 켜둔 회원이면 바로 로그인시키지 않고 코드 입력 화면으로 보낸다.
        // "loginMember"가 아니라 "pending2faMemberId"에 걸어둔다 — 세션에 loginMember가 있으면
        // 로그인 상태로 취급하는 다른 모든 화면/인터셉터가 코드 입력 전에 통과돼버리기 때문.
        if (member.isTwoFactorEnabled()) {
            memberService.issueLoginOtp(member.getId());
            session.setAttribute("pending2faMemberId", member.getId());
            return "redirect:/login/2fa";
        }

        // 세션에 로그인한 회원 저장 후 메인 지도 페이지로 리다이렉트
        grantLogin(request, session, member);
        return "redirect:/map";
    }

    // 로그인 성공 시 세션 ID를 새로 발급한 뒤 회원을 심는다(세션 고정 공격 방어).
    // 공용 PC 등에서 공격자가 미리 심어둔 세션 ID를 피해자가 로그인한 채로 그대로 쓰면,
    // 공격자가 같은 ID로 피해자 세션에 올라탈 수 있다. ID만 바꾸고 기존 속성은 유지된다.
    private void grantLogin(HttpServletRequest request, HttpSession session, Member member) {
        request.changeSessionId();
        session.setAttribute("loginMember", member);
    }

    // 로그인 2차 인증 — 코드 입력 화면 (비밀번호까지는 맞았지만 아직 로그인 완료 전)
    @GetMapping("/login/2fa")
    public String twoFactorForm(HttpSession session, Model model) {
        Member pending = pendingTwoFactorMember(session);
        if (pending == null) {
            return "redirect:/login";
        }
        model.addAttribute("maskedEmail", maskEmail(pending.getEmail()));
        return "auth/login-2fa";
    }

    // 로그인 2차 인증 — 코드 검증
    @PostMapping("/login/2fa")
    public String verifyTwoFactor(@RequestParam String code, HttpSession session, Model model,
                                  HttpServletRequest request) {
        Member pending = pendingTwoFactorMember(session);
        if (pending == null) {
            return "redirect:/login";
        }
        model.addAttribute("maskedEmail", maskEmail(pending.getEmail()));

        // 코드 무차별 대입 방어 — 계정당 10분에 5회.
        if (!rateLimiter.allow("2fa:" + pending.getId(), 5, 600)) {
            model.addAttribute("twoFactorError", "시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
            return "auth/login-2fa";
        }

        Member member = memberService.verifyLoginOtp(pending.getId(), code);
        if (member == null) {
            model.addAttribute("twoFactorError", "인증 코드가 올바르지 않거나 만료되었습니다.");
            return "auth/login-2fa";
        }

        session.removeAttribute("pending2faMemberId");
        grantLogin(request, session, member);
        return "redirect:/map";
    }

    // 로그인 2차 인증 — 코드 재전송
    @PostMapping("/login/2fa/resend")
    public String resendTwoFactor(HttpSession session, Model model) {
        Member pending = pendingTwoFactorMember(session);
        if (pending == null) {
            return "redirect:/login";
        }
        model.addAttribute("maskedEmail", maskEmail(pending.getEmail()));

        // 이메일 폭탄 방지 — 계정당 10분에 3회 넘게 재전송 요청하면 막는다.
        if (!rateLimiter.allow("2fa-resend:" + pending.getId(), 3, 600)) {
            model.addAttribute("twoFactorError", "재전송 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return "auth/login-2fa";
        }
        memberService.issueLoginOtp(pending.getId());
        model.addAttribute("twoFactorInfo", "인증 코드를 다시 보냈습니다.");
        return "auth/login-2fa";
    }

    // 2차 인증 대기 중인 회원을 돌려준다. 대기 상태가 아니거나, 대기 중에 계정이 사라졌으면(탈퇴 등)
    // null — 그냥 findById를 부르면 NoSuchElementException이 그대로 올라가 500이 난다.
    private Member pendingTwoFactorMember(HttpSession session) {
        Long pendingId = (Long) session.getAttribute("pending2faMemberId");
        if (pendingId == null) {
            return null;
        }
        try {
            return memberService.findById(pendingId);
        } catch (java.util.NoSuchElementException e) {
            session.removeAttribute("pending2faMemberId");
            return null;
        }
    }

    // 인증 코드 화면에 "t***@osan.ac.kr"처럼 가려서 보여주기 위한 간단한 마스킹.
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
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
        // 이메일 폭탄 방지 — 같은 이메일로 10분에 3회 넘게 요청하면 막는다.
        if (!rateLimiter.allow("resend:" + email.toLowerCase(), 3, 600)) {
            model.addAttribute("loginForm", new LoginForm());
            model.addAttribute("loginError", "인증 메일 재전송 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return "auth/login";
        }
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

    // 비밀번호 찾기 1단계 처리 — 가입된 이메일이면 재설정 링크를 메일로 보낸다.
    // 가입 여부와 무관하게 항상 같은 안내 문구를 보여준다(이메일 존재 여부가 응답으로 드러나지 않게).
    @PostMapping("/find-password")
    public String requestPasswordReset(@RequestParam String email, Model model){
        // 이메일 폭탄 방지 — 같은 이메일로 10분에 3회 넘게 요청하면 막는다.
        // (안내 문구는 그대로 "메일함을 확인해주세요"로 통일해서 이메일 존재 여부가 드러나지 않게 한다)
        if (rateLimiter.allow("pwreset:" + email.toLowerCase(), 3, 600)) {
            memberService.requestPasswordReset(email);
        }
        model.addAttribute("requestSent", true);
        return "auth/find-password";
    }

    // 메일 속 재설정 링크 클릭 시 — 토큰이 유효하면 새 비밀번호 입력 폼을 보여준다.
    @GetMapping("/find-password/reset")
    public String resetPasswordForm(@RequestParam String token, Model model){
        if (!memberService.isResetTokenValid(token)){
            model.addAttribute("error", "재설정 링크가 유효하지 않거나 만료되었습니다. 비밀번호 찾기를 다시 요청해주세요.");
            return "auth/find-password";
        }
        model.addAttribute("resetToken", token);
        return "auth/find-password";
    }

    // 비밀번호 찾기 2단계 처리 — 토큰을 다시 검증한 뒤에만 새 비밀번호를 저장한다.
    @PostMapping("/find-password/reset")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String newPasswordConfirm,
                                Model model){
        if (newPassword == null || newPassword.length() < 8){
            model.addAttribute("error", "비밀번호는 8자 이상이어야 합니다.");
            model.addAttribute("resetToken", token);
            return "auth/find-password";
        }
        if (!newPassword.equals(newPasswordConfirm)){
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
            model.addAttribute("resetToken", token);
            return "auth/find-password";
        }
        if (!memberService.resetPasswordWithToken(token, newPassword)){
            model.addAttribute("error", "재설정 링크가 유효하지 않거나 만료되었습니다. 비밀번호 찾기를 다시 요청해주세요.");
            return "auth/find-password";
        }
        return "redirect:/login?passwordReset=true";
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
                         BindingResult bindingResult, Model model,
                         HttpServletRequest request){
        if (bindingResult.hasErrors()){
            return "auth/signup";
        }

        // 스팸성 대량 가입 방지 — 같은 IP에서 1시간에 5회 넘게 시도하면 막는다.
        if (!rateLimiter.allow("signup:" + clientIp(request), 5, 3600)) {
            model.addAttribute("signupError", "가입 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
            return "auth/signup";
        }
        //비밀번호 확인 일치 검사
        if (!signupForm.getPassword().equals(signupForm.getPasswordConfirm())){
            model.addAttribute("signupError", "비밀번호가 일치하지 않습니다");

            return "auth/signup";
        }

        // 회원 저장 (증복이면 null 반환, 선택한 학교의 공식 도메인과 이메일이 다르면 예외)
        Member member;
        try {
            member = memberService.signup(
                    signupForm.getName(),
                    signupForm.getEmail(),
                    signupForm.getPassword(),
                    signupForm.getUniversityId()
            );
        } catch (IllegalArgumentException e) {
            model.addAttribute("signupError", e.getMessage());
            return "auth/signup";
        }

        if (member == null){
            model.addAttribute("signupError", "이미 사용 중인 이메일입니다.");
            return "auth/signup";
        }
        // 가입 성공 — 인증 메일 발송됨, 로그인 화면에서 안내
        return "redirect:/login?verifyPending=true";
    }





































}
