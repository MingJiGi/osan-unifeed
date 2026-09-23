package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// 내 글에 댓글/답글이 달리거나 좋아요를 받았을 때의 알림 — 누가 그랬는지는 절대 안 담는다
// (익명 커뮤니티 정책상 message 자체에도 "누군가"로만 남기지 실명/익명번호를 안 적는다).
@Entity
@Table(name = "osan_notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String message;

    // 클릭 시 이동할 경로(예: /posts/42). 대상이 나중에 삭제되면 그냥 404로 자연스럽게 처리됨.
    @Column(length = 300)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Member recipient, NotificationType type, String message, String link) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
        this.link = link;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getRecipient() { return recipient; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void markRead() { this.read = true; }
}
