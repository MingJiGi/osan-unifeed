package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Comment;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// ############################################################################
// MemberWithdrawalService — 회원 탈퇴를 "흔적 정리까지" 책임지고 처리한다.
// ----------------------------------------------------------------------------
// [왜 필요한가]
// 회원 행을 그냥 지우면 FK(ON DELETE CASCADE)로 그 사람의 리뷰·댓글·좋아요·찜이 DB에서 함께
// 사라진다. 그런데 맛집의 평점/리뷰수, 게시글의 댓글수/좋아요수는 따로 저장해둔 "집계 값"이라
// 자동으로 줄지 않는다. 그래서 탈퇴 후에도
//   - 맛집은 탈퇴자가 준 별점이 평점에 영원히 반영된 채로 남고(리뷰는 0개인데 "리뷰 3개"로 표시)
//   - 게시글은 사라진 댓글/좋아요가 숫자로만 남는
// 문제가 있었다(실제로 재현 확인함). 신뢰도 높은 맛집 데이터가 이 서비스의 핵심인데 평점이
// 조용히 틀어지는 건 그냥 두기 어렵다.
//
// [방법]
// 지우기 전에 그 회원의 리뷰/댓글/좋아요/찜을 각 서비스의 정상 삭제 경로로 먼저 없앤다.
// 그 경로들은 이미 집계 값을 함께 맞추도록 되어 있으므로, 결과적으로 숫자가 정확히 유지된다.
// (MemberService 에 이걸 넣으면 PostService -> MemberService 와 순환 의존이 생겨서 별도 서비스로 뺐다)
// ############################################################################
@Service
public class MemberWithdrawalService {

    private final MemberService memberService;
    private final RestaurantReviewService reviewService;
    private final CommentService commentService;
    private final PostService postService;
    private final RestaurantBookmarkService bookmarkService;

    public MemberWithdrawalService(MemberService memberService,
                                   RestaurantReviewService reviewService,
                                   CommentService commentService,
                                   PostService postService,
                                   RestaurantBookmarkService bookmarkService) {
        this.memberService = memberService;
        this.reviewService = reviewService;
        this.commentService = commentService;
        this.postService = postService;
        this.bookmarkService = bookmarkService;
    }

    @Transactional
    public void withdraw(Long memberId, String rawPassword) {
        Member member = memberService.findById(memberId);

        // 비밀번호부터 확인한다 — 틀린 비밀번호로도 아래 정리가 먼저 돌면 남의 글/리뷰가 날아간다.
        if (!memberService.verifyPassword(memberId, rawPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 1) 리뷰 — 맛집 평점/리뷰수가 다시 계산된다.
        for (RestaurantReview review : reviewService.findByAuthor(member)) {
            reviewService.deleteReview(review.getId(), member);
        }

        // 2) 찜 — 맛집 찜 수가 줄어든다(토글이라 한 번 부르면 해제).
        for (Restaurant restaurant : bookmarkService.findBookmarkedRestaurants(member)) {
            bookmarkService.toggleBookmark(restaurant.getId(), member);
        }

        // 3) 좋아요 — 게시글 좋아요 수가 줄어든다.
        for (Post post : postService.findLikedPosts(member)) {
            postService.toggleLike(post.getId(), member);
        }

        // 4) 댓글 — 게시글 댓글 수가 줄어든다. 부모 댓글을 지우면 거기 달린 답글도 함께 사라지는데,
        //    그 답글이 이 목록에 같이 들어있을 수 있어서 "이미 없으면 넘어가는" 삭제를 쓴다.
        for (Comment comment : commentService.findByAuthor(member)) {
            commentService.deleteIfPresent(comment.getId(), member);
        }

        // 5) 내가 쓴 글 — 글에 달린 (남이 쓴) 댓글/좋아요도 글과 함께 사라지므로 따로 셀 필요가 없다.
        //    [중요] 이걸 회원 삭제 "전에" 해야 한다. 위 과정에서 내 글 엔티티가 영속성 컨텍스트에
        //    올라와 있는데, 그 상태로 회원을 지우면 "삭제된 회원을 참조하는 글이 남아있다"며
        //    Hibernate 가 flush 에 실패한다(실제로 500 에러로 재현됨).
        for (Post post : postService.findByAuthor(member)) {
            postService.delete(post.getId());
        }

        // 6) 마지막으로 회원 삭제 — 남은 시간표/알림 등은 FK CASCADE 로 함께 정리된다.
        memberService.withdraw(memberId, rawPassword);
    }
}
