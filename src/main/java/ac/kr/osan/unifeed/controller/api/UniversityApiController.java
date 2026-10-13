package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.service.UniversityRequestService;
import ac.kr.osan.unifeed.service.UniversityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// ############################################################################
// UniversityApiController — 회원가입 화면의 학교 검색/자동추천 전용.
// ----------------------------------------------------------------------------
// 가입 전(비로그인) 상태에서 호출되므로 로그인 확인을 하지 않는다(공개 API).
// ############################################################################
@RestController
@RequestMapping("/api/universities")
public class UniversityApiController {

    private final UniversityService universityService;
    private final UniversityRequestService universityRequestService;
    private final ac.kr.osan.unifeed.config.RateLimiter rateLimiter;

    public UniversityApiController(UniversityService universityService,
                                   UniversityRequestService universityRequestService,
                                   ac.kr.osan.unifeed.config.RateLimiter rateLimiter) {
        this.universityService = universityService;
        this.universityRequestService = universityRequestService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public List<UniversityDto> search(@RequestParam(required = false) String keyword) {
        return universityService.search(keyword).stream().map(UniversityDto::from).toList();
    }

    // 이메일 입력 직후 도메인으로 자동 추천 — 못 찾으면 빈 리스트(직접 검색해서 고르면 됨).
    @GetMapping("/suggest")
    public List<UniversityDto> suggest(@RequestParam String email) {
        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
        Optional<University> match = universityService.findByEmailDomainSuffix(domain);
        return match.map(u -> List.of(UniversityDto.from(u))).orElse(List.of());
    }

    // "내 학교가 목록에 없어요" 등록 신청 — 가입 전(비로그인) 공개 엔드포인트.
    // 로그인 벽이 없으므로 도배 방지를 반드시 건다(같은 IP 1시간 5회).
    @PostMapping("/requests")
    public ResponseEntity<?> requestUniversity(@RequestBody UniversityRequestForm form,
                                               HttpServletRequest httpRequest) {
        if (!rateLimiter.allow("univreq:" + clientIp(httpRequest), 5, 3600)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "신청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
        }
        try {
            universityRequestService.create(form.universityName(), form.address(), form.email());
            return ResponseEntity.ok(Map.of(
                    "message", "학교 등록을 신청했습니다. 승인되면 입력하신 메일로 알려드릴게요."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 프록시(nginx) 뒤에서도 실제 신청자 IP 기준으로 제한되도록 X-Forwarded-For 를 먼저 본다.
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public record UniversityRequestForm(String universityName, String address, String email) {}

    public record UniversityDto(Long id, String name, String regionName) {
        static UniversityDto from(University u) {
            return new UniversityDto(u.getId(), u.getName(), u.getRegionName());
        }
    }
}
