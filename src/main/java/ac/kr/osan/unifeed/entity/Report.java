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

// 게시글/댓글/맛집 리뷰 신고. targetType+targetId로 대상을 가리키는 방식(다형 참조)이라
// FK 컬럼을 3개 따로 두지 않는다 — 미리보기는 ReportService에서 조회 시점에 만든다.
@Entity
@Table(name = "osan_report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    // 감사 로그 — 누가(handledBy) 이 신고를 처리했는지, 실제로 어떤 조치(action)를 취했는지.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private Member handledBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportAction action = ReportAction.NONE;

    protected Report() {
    }

    public Report(Member reporter, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getReporter() { return reporter; }
    public ReportTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public ReportReason getReason() { return reason; }
    public String getDetail() { return detail; }
    public ReportStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public Member getHandledBy() { return handledBy; }
    public ReportAction getAction() { return action; }

    public void resolve(Member admin) {
        this.status = ReportStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = admin;
    }

    public void dismiss(Member admin) {
        this.status = ReportStatus.DISMISSED;
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = admin;
    }

    public void markContentDeleted(Member admin) {
        this.status = ReportStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = admin;
        this.action = ReportAction.CONTENT_DELETED;
    }

    public void markAuthorSuspended(Member admin) {
        this.status = ReportStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = admin;
        this.action = ReportAction.AUTHOR_SUSPENDED;
    }

    public void markAuthorUnsuspended(Member admin) {
        this.resolvedAt = LocalDateTime.now();
        this.handledBy = admin;
        this.action = ReportAction.AUTHOR_UNSUSPENDED;
    }
}
