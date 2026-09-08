package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SettingsController는 애플리케이션의 설정 화면(프로필 수정, 로그아웃 등)에 대한 요청을 처리합니다.
 * 초보자를 위한 설명:
 * - 컨트롤러(@Controller)는 사용자의 요청(예: 버튼 클릭으로 인한 URL 이동)을 받아 
 * 알맞은 화면(HTML)을 응답으로 돌려주는 길잡이 역할을 합니다.
 */
@Controller
public class SettingsController {

    /**
     * 사용자가 마이페이지에서 '프로필 편집' 버튼 등을 눌러 "/settings" 주소로 이동했을 때 실행됩니다.
     * 
     * @return "settings/index": 서버는 templates 폴더 아래의 settings/index.html 파일을 찾아 
     * 웹 브라우저에 전송합니다.
     */
    @GetMapping("/settings")
    public String settingsPage() {
        return "settings/index";
    }
}
