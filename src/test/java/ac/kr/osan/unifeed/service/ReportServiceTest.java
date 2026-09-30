package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.entity.Report;
import ac.kr.osan.unifeed.entity.ReportReason;
import ac.kr.osan.unifeed.entity.ReportStatus;
import ac.kr.osan.unifeed.entity.ReportTargetType;
import ac.kr.osan.unifeed.repository.CommentRepository;
import ac.kr.osan.unifeed.repository.PostRepository;
import ac.kr.osan.unifeed.repository.ReportRepository;
import ac.kr.osan.unifeed.repository.RestaurantReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

// ############################################################################
// ReportService 테스트 — 코드리뷰로 발견된 3건(중복 신고 악용, 카운트 쿼리 비효율,
// 대상 엔티티 중복 조회)을 회귀 테스트로 고정한다. MemberServiceTest와 동일하게
// 순수 Mockito 단위 테스트로 실제 DB 없이 검증한다.
// ############################################################################
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock RestaurantReviewRepository restaurantReviewRepository;
    @Mock PostService postService;
    @Mock CommentService commentService;
    @Mock RestaurantReviewService restaurantReviewService;

    private ReportService newService() {
        return new ReportService(reportRepository, postRepository, commentRepository,
                restaurantReviewRepository, postService, commentService, restaurantReviewService);
    }

    // ── 문제 1: 중복 신고 악용 ──────────────────────────────────────────────
    @Test
    void 같은_사람이_같은_대상에_이미_처리대기_신고를_넣었으면_거부한다() {
        ReportService reportService = newService();
        Member reporter = new Member("김철수", "kim@test.ac.kr");
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, ReportTargetType.POST, 1L, ReportStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> reportService.create(reporter, ReportTargetType.POST, 1L, ReportReason.SPAM, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reportRepository, never()).save(any());
        // 거부된 중복 신고가 자동숨김 카운트에 더해지면 안 된다.
        verify(reportRepository, never()).countByTargetTypeAndTargetIdAndStatus(any(), anyLong(), any());
    }

    @Test
    void 처음_신고하는_경우엔_정상적으로_저장된다() {
        ReportService reportService = newService();
        Member reporter = new Member("김철수", "kim@test.ac.kr");
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, ReportTargetType.POST, 1L, ReportStatus.PENDING)).thenReturn(false);
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, 1L, ReportStatus.PENDING))
                .thenReturn(1L);

        reportService.create(reporter, ReportTargetType.POST, 1L, ReportReason.SPAM, "설명");

        verify(reportRepository).save(any(Report.class));
        verify(postService, never()).hide(1L); // 임계치(3) 미만이면 숨기지 않는다
    }

    @Test
    void 서로_다른_세_명이_신고하면_임계치를_넘겨_자동숨김된다() {
        ReportService reportService = newService();
        Member reporter = new Member("박민수", "park@test.ac.kr");
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                any(), eq(ReportTargetType.POST), eq(1L), eq(ReportStatus.PENDING))).thenReturn(false);
        when(reportRepository.countByTargetTypeAndTargetIdAndStatus(ReportTargetType.POST, 1L, ReportStatus.PENDING))
                .thenReturn(3L); // 이 신고까지 포함해 3건째

        reportService.create(reporter, ReportTargetType.POST, 1L, ReportReason.ABUSE, null);

        verify(postService).hide(1L);
    }

    // ── 문제 2: countPending()이 DB COUNT를 바로 쓰는지 ─────────────────────
    @Test
    void countPending은_전체_로드_대신_countByStatus를_쓴다() {
        ReportService reportService = newService();
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(7L);

        long result = reportService.countPending();

        assertThat(result).isEqualTo(7L);
        verify(reportRepository).countByStatus(ReportStatus.PENDING);
        verify(reportRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    // ── 문제 3: toView()가 대상 엔티티를 한 번만 조회하는지 ─────────────────
    @Test
    void findAllForAdmin은_신고당_대상_엔티티를_한_번만_조회한다() {
        ReportService reportService = newService();
        Member reporter = new Member("이영희", "lee@test.ac.kr");
        Member author = new Member("김철수", "kim@test.ac.kr");
        Report report = new Report(reporter, ReportTargetType.POST, 1L, ReportReason.SPAM, null);
        Post post = new Post("제목", "내용", author);

        when(reportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        List<ReportService.ReportView> views = reportService.findAllForAdmin();

        assertThat(views).hasSize(1);
        ReportService.ReportView view = views.get(0);
        assertThat(view.preview()).isEqualTo("제목");
        assertThat(view.authorName()).isEqualTo("김철수");
        assertThat(view.targetHidden()).isFalse();
        assertThat(view.targetLink()).isEqualTo("/posts/" + post.getId());

        // preview/author/hidden/link 네 곳에서 각자 재조회하던 것을 한 번으로 합쳤는지 검증.
        verify(postRepository, times(1)).findById(1L);
        verifyNoInteractions(commentRepository, restaurantReviewRepository);
    }

    @Test
    void 대상이_이미_삭제됐으면_기존과_동일하게_삭제된_콘텐츠로_표시된다() {
        ReportService reportService = newService();
        Member reporter = new Member("이영희", "lee@test.ac.kr");
        Report report = new Report(reporter, ReportTargetType.POST, 99L, ReportReason.SPAM, null);

        when(reportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        ReportService.ReportView view = reportService.findAllForAdmin().get(0);

        assertThat(view.preview()).isEqualTo("(삭제된 게시글)");
        assertThat(view.authorName()).isNull();
        assertThat(view.targetHidden()).isFalse();
        assertThat(view.targetLink()).isNull();
        verify(postRepository, times(1)).findById(99L);
    }
}
