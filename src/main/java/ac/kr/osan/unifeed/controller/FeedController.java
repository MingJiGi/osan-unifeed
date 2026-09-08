package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * FeedController는 커뮤니티 피드(게시판) 화면에 대한 요청을 처리합니다.
 * 초보자를 위한 설명:
 * - @Controller: 스프링 프레임워크에게 이 클래스가 웹 요청(HTTP Request)을 다루는 컴포넌트임을 알립니다.
 */
@Controller
public class FeedController {

    /**
     * 사용자가 커뮤니티 탭을 클릭하여 "/feed" 경로로 접근했을 때 호출됩니다.
     * 아직 데이터베이스 연동(DB 로직)은 없으며, 화면 틀(HTML)만 제공합니다.
     * 
     * @return "feed/index": templates 폴더 안의 feed 폴더 안에 있는 index.html 템플릿을 화면에 그립니다.
     */
    @GetMapping("/feed")
    public String feedPage() {
        return "feed/index";
    }
}
