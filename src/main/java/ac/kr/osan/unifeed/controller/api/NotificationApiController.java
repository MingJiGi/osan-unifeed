package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Notification;
import ac.kr.osan.unifeed.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final NotificationService notificationService;

    public NotificationApiController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        List<NotificationDto> items = notificationService.findRecent(loginMember).stream()
                .map(NotificationDto::from).toList();
        return ResponseEntity.ok(Map.of(
                "items", items,
                "unreadCount", notificationService.countUnread(loginMember)
        ));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        notificationService.markRead(id, loginMember);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        notificationService.markAllRead(loginMember);
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record NotificationDto(Long id, String type, String message, String link, boolean read, String createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getType().name(), n.getMessage(), n.getLink(), n.isRead(),
                    n.getCreatedAt().format(FORMATTER));
        }
    }
}
