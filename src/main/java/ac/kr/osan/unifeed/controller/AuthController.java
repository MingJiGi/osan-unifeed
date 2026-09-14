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

@Controller
public class AuthController {

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 로그인 폼 화면
    @GetMapping("/login")
    public String loginForm(Model model){
        model.addAttribute("loginForm", new LoginForm());

        return "auth/login";
    }

    // 로그인 처리
    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginForm loginForm,
                        BindingResult bindingResult, HttpSession session, Model model){
        if (bindingResult.hasErrors()){
            return "auth/login";
        }

        // 이메일로 회원 찾기
        Member member = memberService.findByEmail(loginForm.getEmail());

        //회원이 없거나 비밀번호 불일치
        if (member == null || !member.getPassword().equals(loginForm.getPassword())){
            model.addAttribute("loginError", "이메일 또는 비밀번호가 일치하지 않습니다.");

            return "auth/login";
        }

        // 세선에 로그인한 회원 저장
        session.setAttribute("loginMember", member);
        return "redirect:/posts";
    }

    // 로그아웃
    @PostMapping("/logout")
    public String logout(HttpSession session){
        session.invalidate(); // 세션 전체 삭제
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
        // 가입 성공
        return "redirect:/login";
    }





































}
