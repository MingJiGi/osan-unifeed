package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.config.RateLimiter;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.AiRecommendService;
import ac.kr.osan.unifeed.service.RestaurantEnrichService;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.service.TimetableService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// "오늘 뭐 먹지?" AI 추천 API.
// 외부 유료 API를 호출하므로 로그인 필수 + 호출 횟수 제한을 꼭 둔다(한 계정이 돌려도 비용이 난다).
@RestController
@RequestMapping("/api/ai")
public class AiApiController {

    private final AiRecommendService aiRecommendService;
    private final RestaurantEnrichService restaurantEnrichService;
    private final TimetableService timetableService;
    private final MemberService memberService;
    private final RateLimiter rateLimiter;

    public AiApiController(AiRecommendService aiRecommendService,
                           RestaurantEnrichService restaurantEnrichService,
                           TimetableService timetableService,
                           MemberService memberService,
                           RateLimiter rateLimiter) {
        this.aiRecommendService = aiRecommendService;
        this.restaurantEnrichService = restaurantEnrichService;
        this.timetableService = timetableService;
        this.memberService = memberService;
        this.rateLimiter = rateLimiter;
    }

    public record RecommendRequest(String situation) {}

    @PostMapping("/recommend")
    public ResponseEntity<?> recommend(@RequestBody RecommendRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        if (!aiRecommendService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "AI 추천 기능이 아직 설정되지 않았습니다."));
        }
        // 외부 API 비용 방어 — 한 계정이 10분에 10번까지.
        if (!rateLimiter.allow("ai:" + loginMember.getId(), 10, 600)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "AI 추천은 잠시 후 다시 시도해주세요."));
        }

        try {
            // 세션의 Member 는 로그인 시점의 복사본(detached)이라 시간표 조회에 그대로 쓰면
            // 지연로딩/변경감지에서 문제가 생긴다. 다른 API와 같이 DB에서 다시 읽어 쓴다.
            Member member = memberService.findById(loginMember.getId());
            List<AiRecommendService.Pick> picks = aiRecommendService.recommend(
                    member.getUniversity(), request.situation(), gapContext(member));
            return ResponseEntity.ok(Map.of("picks", picks));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            // AI 쪽이 실패해도 서비스 장애는 아니다 — 화면은 "다시 시도" 안내만 띄운다.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // 맛집 소개글/태그 일괄 보강 — 관리자 1회성 데이터 작업.
    // /api/** 는 AdminAccessInterceptor(/admin/** 전용) 범위 밖이라 여기서 직접 관리자 확인을 한다.
    @PostMapping("/enrich-restaurants")
    public ResponseEntity<?> enrichRestaurants(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        // 세션 복사본이 아니라 DB의 현재 권한으로 판단한다(권한 회수 직후 호출 방지).
        if (!memberService.findById(loginMember.getId()).isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "관리자만 사용할 수 있습니다."));
        }
        // 맛집 전체를 순회하며 외부 API를 여러 번 부르는 무거운 작업 — 1시간에 3회로 묶는다.
        if (!rateLimiter.allow("enrich:" + loginMember.getId(), 3, 3600)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "일괄 보강은 1시간에 3번까지만 실행할 수 있습니다."));
        }

        try {
            RestaurantEnrichService.EnrichResult result = restaurantEnrichService.enrichEmpty();
            return ResponseEntity.ok(Map.of(
                    "message", "대상 " + result.targets() + "곳 중 소개 " + result.descriptionFilled()
                            + "곳, 태그 " + result.tagsFilled() + "곳을 채웠습니다."
                            + (result.leftBlank() > 0 ? " (이름만으론 알 수 없어 비워둔 곳 " + result.leftBlank() + ")" : ""),
                    "targets", result.targets(),
                    "descriptionFilled", result.descriptionFilled(),
                    "tagsFilled", result.tagsFilled(),
                    "leftBlank", result.leftBlank(),
                    "failedBatches", result.failedBatches()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        }
    }

    // 시간표에서 뽑은 "지금 공강 상황" 한 줄. 시간표를 안 넣은 회원은 null(그냥 안 알려줌).
    private String gapContext(Member member) {
        TimetableService.GapInfo gap = timetableService.getCurrentGap(member);
        if (gap == null) return null;
        if ("gap".equals(gap.status()) && gap.gapMinutes() != null) {
            return "지금 공강 " + gap.gapMinutes() + "분 남음";
        }
        if ("inClass".equals(gap.status()) && gap.upcomingGapMinutes() != null) {
            return "지금 수업 중이고, " + gap.currentClassEndTime() + "에 끝나면 "
                    + gap.upcomingGapMinutes() + "분 공강";
        }
        return null;
    }
}
