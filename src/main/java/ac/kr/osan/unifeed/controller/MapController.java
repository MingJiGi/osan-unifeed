package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MapController는 지도 탭에 해당하는 웹 페이지 요청을 처리합니다.
 * 초보자를 위한 설명:
 * - @Controller: 웹 브라우저의 요청을 받아 적절한 HTML 페이지를 반환하는 역할을 합니다.
 */
@Controller
public class MapController {

    /**
     * 사용자가 하단 탭이나 주소창을 통해 "/map"으로 접속했을 때 실행됩니다.
     * @return "map/index": src/main/resources/templates/map/index.html 파일을 찾아 사용자에게 보여줍니다.
     */
    @GetMapping("/map")
    public String mapPage() {
        return "map/index";
    }
}
