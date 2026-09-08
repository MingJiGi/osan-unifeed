package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * AuthController는 사용자의 인증(로그인, 회원가입 등)과 관련된 웹 페이지 요청을 처리합니다.
 * 초보자를 위한 설명:
 * - @Controller: 이 클래스가 사용자 화면(View)을 렌더링하기 위한 컨트롤러임을 스프링에 명시합니다.
 */
@Controller
public class AuthController {

    /**
     * 사용자가 "/auth/login" 주소로 접속(GET 방식)했을 때 실행되는 메서드입니다.
     * @return "auth/login": src/main/resources/templates/auth/login.html 템플릿 파일을 화면에 보여줍니다.
     */
    @GetMapping("/auth/login")
    public String loginPage() {
        return "auth/login";
    }

    /**
     * 사용자가 "/auth/signup" 주소로 접속(GET 방식)했을 때 실행되는 메서드입니다.
     * @return "auth/signup": src/main/resources/templates/auth/signup.html 템플릿 파일을 화면에 보여줍니다.
     */
    @GetMapping("/auth/signup")
    public String signupPage() {
        return "auth/signup";
    }
}
