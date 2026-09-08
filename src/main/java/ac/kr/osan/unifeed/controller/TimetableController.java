package ac.kr.osan.unifeed.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TimetableController는 시간표 기능과 관련된 화면 요청을 처리합니다.
 * 초보자를 위한 설명:
 * - @Controller: 사용자가 브라우저에서 특정 URL을 요청했을 때, 어떤 화면을 보여줄지 결정하는 역할을 합니다.
 */
@Controller
public class TimetableController {

    /**
     * 사용자가 하단의 시간표 탭을 눌러 "/timetable" 경로로 접속했을 때 실행되는 메서드입니다.
     * 
     * @return "timetable/index": templates/timetable/index.html 파일을 읽어들여
     * 사용자에게 시간표 화면을 제공합니다.
     */
    @GetMapping("/timetable")
    public String timetablePage() {
        return "timetable/index";
    }
}
