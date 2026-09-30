package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.service.UniversityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// ############################################################################
// PostApiController — 게시판 목록 무한스크롤용 JSON API.
// 화면 렌더링은 PostController(posts/list)가 첫 페이지만 그리고, 그 다음부터는
// 이 API를 스크롤할 때마다 불러 .feed-grid 에 카드를 append 한다.
// ############################################################################
@RestController
@RequestMapping("/api/posts")
public class PostApiController {

    private final PostService postService;
    private final UniversityService universityService; // 관리자의 학교 전환(뷰) 지원용

    public PostApiController(PostService postService, UniversityService universityService) {
        this.postService = postService;
        this.universityService = universityService;
    }

    // PostController.resolveViewUniversity()와 동일한 패턴 — 관리자만 ?viewUniversity= 로 다른 학교를 볼 수 있다.
    private University resolveViewUniversity(Member loginMember, Long viewUniversity) {
        if (viewUniversity == null || !loginMember.isAdmin()) {
            return loginMember.getUniversity();
        }
        University chosen = universityService.findById(viewUniversity);
        return chosen != null ? chosen : loginMember.getUniversity();
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) Long viewUniversity,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        University targetUniversity = resolveViewUniversity(loginMember, viewUniversity);
        Slice<Post> slice = postService.findAll(targetUniversity, PageRequest.of(page, size));
        List<PostDto> items = slice.getContent().stream().map(PostDto::from).toList();

        return ResponseEntity.ok(Map.of(
                "items", items,
                "hasNext", slice.hasNext()
        ));
    }

    // 카드 렌더링에 필요한 필드만 내려준다 — 목록에서는 항상 "익명1" 고정 표시라 작성자 이름은 뺀다.
    public record PostDto(Long id, String title, String content, String tags, String imgUrl,
                          int likeCount, int commentCount, String createdAt) {
        // list.html 서버 렌더링 카드와 동일한 포맷(#temporals.format(post.createdAt, 'MM/dd HH:mm')).
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm");

        public static PostDto from(Post p) {
            return new PostDto(p.getId(), p.getTitle(), p.getContent(), p.getTags(), p.getImgUrl(),
                    p.getLikeCount(), p.getCommentCount(), p.getCreatedAt().format(FORMATTER));
        }
    }
}
