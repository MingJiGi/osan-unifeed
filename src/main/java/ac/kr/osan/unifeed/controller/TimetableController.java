package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.TimetableOcrService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TimetableController {

    private final TimetableOcrService timetableOcrService; // 사진 인식 버튼을 띄울지(API 키 설정 여부) 판단용

    public TimetableController(TimetableOcrService timetableOcrService) {
        this.timetableOcrService = timetableOcrService;
    }

    @GetMapping("/timetable")
    public String timetable(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginMember", loginMember);
        model.addAttribute("ocrEnabled", timetableOcrService.isEnabled());
        return "timetable/index";
    }
}