package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MapController {

    private final RestaurantService restaurantService;
    private final RestaurantBookmarkService bookmarkService;

    @Value("${kakao.maps.appkey}")
    private String kakaoAppKey;

    public MapController(RestaurantService restaurantService, RestaurantBookmarkService bookmarkService) {
        this.restaurantService = restaurantService;
        this.bookmarkService = bookmarkService;
    }

    @GetMapping("/map")
    public String mapPage(@RequestParam(required = false) String category,
                          @RequestParam(required = false) String keyword,
                          HttpSession session,
                          Model model) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }

        List<Restaurant> restaurants = restaurantService.search(keyword, category);

        model.addAttribute("kakaoAppKey", kakaoAppKey);
        model.addAttribute("restaurants", restaurants);
        model.addAttribute("currentCategory", (category == null || category.isBlank()) ? "전체" : category);
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("loginMember", loginMember);

        return "map/index";
    }
}
