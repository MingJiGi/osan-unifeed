package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * HomeController는 웹 애플리케이션의 기본 진입점(루트 경로)을 처리하는 컨트롤러입니다.
 * 초보자를 위한 설명:
 * - @Controller: 이 클래스가 웹 요청을 처리하는 역할을 한다는 것을 스프링(Spring)에게 알려줍니다.
 */
@Controller
public class HomeController {

    /**
     * @GetMapping("/"): 사용자가 웹 브라우저에서 사이트 주소만 입력했을 때(예: http://localhost:8080/)
     * 이 메서드가 실행됩니다.
     * 
     * @return "redirect:/map": 뷰(화면)의 이름을 반환하는 대신, 
     * 클라이언트(브라우저)에게 "/map" 주소로 다시 접속하라고(리다이렉트) 명령합니다.
     * 따라서 사용자가 메인 화면에 접속하면 자동으로 지도 페이지로 이동하게 됩니다.
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/map";
    }
}
