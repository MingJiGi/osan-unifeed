package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TimetableController {

    @GetMapping("/timetable")
    public String timetable(HttpSession session, Model model) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }
        model.addAttribute("loginMember", loginMember);
        return "timetable/index";
    }
}