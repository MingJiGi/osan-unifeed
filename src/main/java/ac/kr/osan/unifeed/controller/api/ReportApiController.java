package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.config.RateLimiter;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.ReportReason;
import ac.kr.osan.unifeed.entity.ReportTargetType;
import ac.kr.osan.unifeed.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private final ReportService reportService;
    private final RateLimiter rateLimiter;

    public ReportApiController(ReportService reportService, RateLimiter rateLimiter) {
        this.reportService = reportService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<?> createReport(@RequestBody ReportCreateRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        // 신고 남발 방지 — 같은 사람이 10분에 10건 넘게 신고하면 잠시 막는다.
        if (!rateLimiter.allow("report:" + loginMember.getId(), 10, 600)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "신고 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."));
        }

        if (request.targetType() == null || request.targetId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "신고 대상 정보가 올바르지 않습니다."));
        }

        ReportTargetType targetType;
        ReportReason reason;
        try {
            targetType = ReportTargetType.valueOf(request.targetType());
            reason = ReportReason.valueOf(request.reason());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "신고 사유가 올바르지 않습니다."));
        }

        reportService.create(loginMember, targetType, request.targetId(), reason, request.detail());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "신고가 접수되었습니다. 검토 후 조치하겠습니다."
        ));
    }

    public record ReportCreateRequest(String targetType, Long targetId, String reason, String detail) {}
}
