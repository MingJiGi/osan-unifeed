package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MyPageController는 사용자의 개인 정보와 통계를 볼 수 있는 마이페이지 요청을 처리합니다.
 * 초보자를 위한 설명:
 * - @Controller 어노테이션은 이 클래스가 웹 화면과 연결된 컨트롤러임을 나타냅니다.
 */
@Controller
public class MyPageController {

    /**
     * 사용자가 하단의 '마이' 탭을 클릭하여 "/mypage" 경로로 접근했을 때 호출됩니다.
     * 
     * @return "mypage/index": resources/templates/mypage/index.html 템플릿 파일을 화면에 보여주도록 
     * 타임리프(Thymeleaf) 엔진에 요청합니다.
     */
    @GetMapping("/mypage")
    public String myPage() {
        return "mypage/index";
    }
}
