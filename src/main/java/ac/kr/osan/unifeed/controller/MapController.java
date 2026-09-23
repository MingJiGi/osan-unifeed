package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantService;
import ac.kr.osan.unifeed.service.UniversityService;
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
    private final UniversityService universityService; // 관리자용 학교 전환 드롭다운

    @Value("${kakao.maps.appkey}")
    private String kakaoAppKey;

    public MapController(RestaurantService restaurantService, RestaurantBookmarkService bookmarkService,
                          UniversityService universityService) {
        this.restaurantService = restaurantService;
        this.bookmarkService = bookmarkService;
        this.universityService = universityService;
    }

    @GetMapping("/map")
    public String mapPage(@RequestParam(required = false) String category,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Long viewUniversity,
                          @RequestParam(required = false) Long openRestaurant,
                          HttpSession session,
                          Model model) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return "redirect:/login";
        }

        // 관리자는 ?viewUniversity=ID 로 다른 학교 지도를 둘러볼 수 있다(운영 목적 전체 열람).
        University university = loginMember.getUniversity();
        if (viewUniversity != null && loginMember.isAdmin()) {
            University chosen = universityService.findById(viewUniversity);
            if (chosen != null) university = chosen;
        }

        List<Restaurant> restaurants = restaurantService.search(university, keyword, category);

        model.addAttribute("kakaoAppKey", kakaoAppKey);
        model.addAttribute("restaurants", restaurants);
        model.addAttribute("currentCategory", (category == null || category.isBlank()) ? "전체" : category);
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("loginMember", loginMember);
        // 신고 관리 화면의 "원본 보기"(리뷰) 링크가 이 파라미터로 넘어온다 — map.js가 로드 직후
        // 이 맛집 상세를 자동으로 연다.
        model.addAttribute("openRestaurantId", openRestaurant);

        // 캠퍼스 중심좌표 — 위에서 정한 university(관리자가 다른 학교를 보는 중이면 그 학교) 기준.
        // 아직 학교를 안 정했으면 지도 JS가 기본값(오산대)으로 그레이스풀하게 대체한다.
        model.addAttribute("campusLat", university != null && university.hasCoordinates() ? university.getLatitude() : null);
        model.addAttribute("campusLng", university != null && university.hasCoordinates() ? university.getLongitude() : null);
        model.addAttribute("campusName", university != null ? university.getName() : null);
        model.addAttribute("campusRegion", university != null && university.getRegionName() != null ? university.getRegionName() : "");

        // viewingUniversity는 관리자가 아니어도 항상 채워둔다(null) — 템플릿의 인라인 JS
        // 표현식이 모델에 아예 없는 속성을 참조하지 않도록(항상 안전하게 평가되도록) 하기 위함.
        model.addAttribute("viewingUniversity", loginMember.isAdmin() ? university : null);
        if (loginMember.isAdmin()) {
            model.addAttribute("allUniversities", universityService.search(null));
        }

        return "map/index";
    }
}
