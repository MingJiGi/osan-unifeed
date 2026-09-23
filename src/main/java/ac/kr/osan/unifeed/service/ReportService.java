package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.entity.Report;
import ac.kr.osan.unifeed.entity.ReportAction;
import ac.kr.osan.unifeed.entity.ReportReason;
import ac.kr.osan.unifeed.entity.ReportStatus;
import ac.kr.osan.unifeed.entity.ReportTargetType;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import ac.kr.osan.unifeed.repository.CommentRepository;
import ac.kr.osan.unifeed.repository.PostRepository;
import ac.kr.osan.unifeed.repository.ReportRepository;
import ac.kr.osan.unifeed.repository.RestaurantReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

// ############################################################################
// ReportService — 게시글/댓글/맛집 리뷰 신고 접수 및 관리자 처리
// ############################################################################
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final int PREVIEW_MAX_LENGTH = 60;

    // 같은 대상에 처리 대기(PENDING) 신고가 이 건수 이상 쌓이면 관리자가 확인하기 전까지 자동 숨김.
    private static final int AUTO_HIDE_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final RestaurantReviewRepository restaurantReviewRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final RestaurantReviewService restaurantReviewService;

    public ReportService(ReportRepository reportRepository, PostRepository postRepository,
                         CommentRepository commentRepository, RestaurantReviewRepository restaurantReviewRepository,
                         PostService postService, CommentService commentService,
                         RestaurantReviewService restaurantReviewService) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.restaurantReviewRepository = restaurantReviewRepository;
        this.postService = postService;
        this.commentService = commentService;
        this.restaurantReviewService = restaurantReviewService;
    }

    @Transactional
    public void create(Member reporter, ReportTargetType targetType, Long targetId, ReportReason reason, String detail) {
        String trimmedDetail = (detail != null && !detail.isBlank()) ? detail.trim() : null;
        reportRepository.save(new Report(reporter, targetType, targetId, reason, trimmedDetail));

        // 신고 누적 자동 숨김 — 이 대상에 대한 처리 대기 신고가 임계치를 넘으면 관리자 확인 전까지 숨긴다.
        long pendingCount = reportRepository.countByTargetTypeAndTargetIdAndStatus(targetType, targetId, ReportStatus.PENDING);
        if (pendingCount >= AUTO_HIDE_THRESHOLD) {
            hideTarget(targetType, targetId);
        }
    }

    // 관리자 목록 화면용 — 대상 콘텐츠 미리보기까지 트랜잭션 안에서 미리 다 채워서
    // 지연로딩 프록시가 뷰(컨트롤러 밖)로 안 넘어가게 한다(이 세션에서 반복된 패턴과 동일).
    @Transactional
    public List<ReportView> findAllForAdmin() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    private ReportView toView(Report report) {
        String preview = resolvePreview(report.getTargetType(), report.getTargetId());
        Member author = resolveAuthor(report.getTargetType(), report.getTargetId());
        return new ReportView(
                report.getId(),
                report.getReporter().getName(),
                report.getTargetType(),
                report.getTargetId(),
                preview,
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getHandledBy() != null ? report.getHandledBy().getName() : null,
                report.getResolvedAt(),
                report.getAction(),
                author != null ? author.isSuspended() : null,
                author != null ? author.getName() : null,
                author != null ? author.getEmail() : null,
                author != null && author.getUniversity() != null ? author.getUniversity().getName() : "미지정",
                isTargetHidden(report.getTargetType(), report.getTargetId()),
                resolveLink(report.getTargetType(), report.getTargetId())
        );
    }

    // 관리자가 미리보기 텍스트만 보지 않고 원본 화면으로 바로 이동할 수 있게 하는 링크 — 관리자는
    // 학교 무관하게 열람 가능해졌고(university-switcher) 신고 없이 직접 삭제도 가능해졌으니, 여기서
    // 실제 화면으로 넘어가야 그 기능들을 쓸 수 있다. 댓글은 자체 URL이 없어서 부모 게시글로 보낸다.
    // 리뷰는 지도 페이지에 ?openRestaurant=ID로 자동으로 그 맛집 상세를 열게 했다(map.js 참고).
    // 원본이 이미 삭제됐으면 null(링크 안 보여줌).
    private String resolveLink(ReportTargetType type, Long targetId) {
        return switch (type) {
            case POST -> postRepository.findById(targetId).map(p -> "/posts/" + p.getId()).orElse(null);
            case COMMENT -> commentRepository.findById(targetId)
                    .map(c -> "/posts/" + c.getPost().getId()).orElse(null);
            // 사이드바 목록이 학교별로 걸러지므로, 그 맛집이 속한 학교로도 같이 전환해야
            // (viewUniversity) 목록 안에서 실제로 찾아서 열 수 있다(map.js selectRestaurant 참고).
            case REVIEW -> restaurantReviewRepository.findById(targetId)
                    .map(r -> {
                        var restaurant = r.getRestaurant();
                        var restaurantUniversity = restaurant.getUniversity();
                        String universityParam = restaurantUniversity != null
                                ? "&viewUniversity=" + restaurantUniversity.getId() : "";
                        return "/map?openRestaurant=" + restaurant.getId() + universityParam;
                    }).orElse(null);
        };
    }

    private String resolvePreview(ReportTargetType type, Long targetId) {
        return switch (type) {
            case POST -> {
                Post post = postRepository.findById(targetId).orElse(null);
                yield post != null ? post.getTitle() : "(삭제된 게시글)";
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId).orElse(null);
                yield comment != null ? truncate(comment.getContent()) : "(삭제된 댓글)";
            }
            case REVIEW -> {
                RestaurantReview review = restaurantReviewRepository.findById(targetId).orElse(null);
                yield review != null ? truncate(review.getContent()) : "(삭제된 리뷰)";
            }
        };
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > PREVIEW_MAX_LENGTH ? text.substring(0, PREVIEW_MAX_LENGTH) + "..." : text;
    }

    // "처리 완료"/"기각"/"정지·정지해제" 등 관리자가 직접 확인해서 조치했다는 뜻이므로,
    // 자동 숨김이 걸려있었다면 다시 노출한다(콘텐츠 삭제는 콘텐츠 자체가 없어지니 예외).
    @Transactional
    public void resolve(Long reportId, Member admin) {
        Report report = findOrThrow(reportId);
        report.resolve(admin);
        unhideTarget(report.getTargetType(), report.getTargetId());
    }

    @Transactional
    public void dismiss(Long reportId, Member admin) {
        Report report = findOrThrow(reportId);
        report.dismiss(admin);
        unhideTarget(report.getTargetType(), report.getTargetId());
    }

    // 신고된 콘텐츠 자체를 삭제(게시글/댓글/리뷰 각자의 삭제 로직 + 부수효과 재사용) + 감사 로그 기록.
    @Transactional
    public void deleteContent(Long reportId, Member admin) {
        Report report = findOrThrow(reportId);
        switch (report.getTargetType()) {
            case POST -> postService.delete(report.getTargetId());
            case COMMENT -> commentService.adminDelete(report.getTargetId());
            case REVIEW -> restaurantReviewService.adminDelete(report.getTargetId());
        }
        report.markContentDeleted(admin);
    }

    // 신고된 콘텐츠의 작성자 계정을 정지 + 감사 로그 기록. 작성자를 못 찾으면(이미 삭제됨) 정지 없이 로그만 남긴다.
    @Transactional
    public void suspendAuthor(Long reportId, Member admin) {
        Report report = findOrThrow(reportId);
        Member author = resolveAuthor(report.getTargetType(), report.getTargetId());
        if (author != null) {
            author.suspend();
        }
        report.markAuthorSuspended(admin);
        unhideTarget(report.getTargetType(), report.getTargetId());
    }

    // 정지 해제 — 어느 신고 카드에서 눌러도 같은 작성자를 가리키면 동일하게 동작한다.
    @Transactional
    public void unsuspendAuthor(Long reportId, Member admin) {
        Report report = findOrThrow(reportId);
        Member author = resolveAuthor(report.getTargetType(), report.getTargetId());
        if (author != null) {
            author.unsuspend();
        }
        report.markAuthorUnsuspended(admin);
    }

    private void hideTarget(ReportTargetType type, Long targetId) {
        switch (type) {
            case POST -> postService.hide(targetId);
            case COMMENT -> commentService.hide(targetId);
            case REVIEW -> restaurantReviewService.hideReview(targetId);
        }
    }

    private void unhideTarget(ReportTargetType type, Long targetId) {
        switch (type) {
            case POST -> postService.unhide(targetId);
            case COMMENT -> commentService.unhide(targetId);
            case REVIEW -> restaurantReviewService.unhideReview(targetId);
        }
    }

    private boolean isTargetHidden(ReportTargetType type, Long targetId) {
        return switch (type) {
            case POST -> postRepository.findById(targetId).map(Post::isHidden).orElse(false);
            case COMMENT -> commentRepository.findById(targetId).map(Comment::isHidden).orElse(false);
            case REVIEW -> restaurantReviewRepository.findById(targetId).map(RestaurantReview::isHidden).orElse(false);
        };
    }

    private Report findOrThrow(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("신고를 찾을 수 없습니다. id=" + reportId));
    }

    private Member resolveAuthor(ReportTargetType type, Long targetId) {
        return switch (type) {
            case POST -> postRepository.findById(targetId).map(Post::getAuthor).orElse(null);
            case COMMENT -> commentRepository.findById(targetId).map(Comment::getAuthor).orElse(null);
            case REVIEW -> restaurantReviewRepository.findById(targetId).map(RestaurantReview::getAuthor).orElse(null);
        };
    }

    // 관리자 목록 화면 상단에 "처리 대기 N건" 배지를 보여주기 위한 카운트.
    public long countPending() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getStatus() == ReportStatus.PENDING)
                .count();
    }

    // 관리자 화면 전용 표시용 DTO — 지연로딩 엔티티를 그대로 밖으로 안 넘긴다.
    public record ReportView(
            Long id,
            String reporterName,
            ReportTargetType targetType,
            Long targetId,
            String preview,
            ReportReason reason,
            String detail,
            ReportStatus status,
            LocalDateTime createdAt,
            String handledByName,
            LocalDateTime handledAt,
            ReportAction action,
            Boolean authorSuspended,
            // 신고 대상 콘텐츠의 실제 작성자 — 익명 커뮤니티 정책상 일반 회원에게는 안 보이고
            // 관리자 신고 처리 화면(admin/reports.html)에서만 노출된다.
            String authorName,
            String authorEmail,
            // 신고 대상이 어느 학교 커뮤니티 글인지 — 관리자가 전체 학교를 한 화면에서 같이 보므로
            // 어느 학교에서 올라온 신고인지 바로 구분할 수 있어야 한다. 작성자가 학교를 선택 안 했으면 "미지정".
            String authorUniversityName,
            // 지금 이 순간 콘텐츠가 자동 숨김 상태인지(신고 누적 3건 이상) — 관리자 화면 배지용.
            boolean targetHidden,
            // 원본 화면으로 바로 이동하는 링크. 원본이 삭제됐으면 null(admin/reports.html에서 숨김).
            String targetLink
    ) {}
}
