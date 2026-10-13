package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.NotificationType;
import ac.kr.osan.unifeed.entity.PostLike;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.repository.PostLikeRepository;
import ac.kr.osan.unifeed.repository.PostRepository;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import ac.kr.osan.unifeed.entity.Post;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.dto.PostForm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// ############################################################################
// PostService — 게시글 업무 로직
// ----------------------------------------------------------------------------
// 컨트롤러는 이 서비스만 부르고 DB(Repository)를 직접 만지지 않는다.
// ############################################################################
@Service
@Transactional(readOnly = true)
public class PostService {

	private final PostRepository postRepository;
	private final MemberService memberService; // 작성자(회원)를 찾을 때 재사용
    private final PostLikeRepository postLikeRepository;
    private final RestaurantRepository restaurantRepository; // 태그한 맛집을 찾을 때
    private final NotificationService notificationService;


    public PostService(PostRepository postRepository, MemberService memberService, PostLikeRepository postLikeRepository,
                        RestaurantRepository restaurantRepository, NotificationService notificationService) {
        this.postRepository = postRepository;
        this.memberService = memberService;
        this.postLikeRepository = postLikeRepository;
        this.restaurantRepository = restaurantRepository;
        this.notificationService = notificationService;
    }

    // 목록 — 작성자까지 함께 읽어온다(N+1/lazy 예외 방지). 커뮤니티는 같은 학교 학생끼리만 보인다.
	public List<Post> findAll(University university) {
		if (university == null) return List.of();
		return postRepository.findAllByAuthorUniversityWithAuthor(university);
	}

	// 목록 — 무한스크롤용 페이지 단위 조회(위 findAll과 동일 조건, 페이지만 나눔).
	public Slice<Post> findAll(University university, Pageable pageable) {
		if (university == null) return new SliceImpl<>(List.of(), pageable, false);
		return postRepository.findAllByAuthorUniversityWithAuthor(university, pageable);
	}

	// 상세 — 작성자 포함. 없으면 예외.
	public Post findById(Long id) {
		return postRepository.findWithAuthorById(id)
				.orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다. id=" + id));
	}

	// 특정 회원이 쓴 글(회원 상세 화면용).
	public List<Post> findByAuthor(Member author) {
		return postRepository.findByAuthorOrderByCreatedAtDescIdDesc(author);
	}

	// 새 글 저장. 폼의 authorId 로 회원을 찾아 연결한다.
	@Transactional
	public Long create(PostForm form) {
		Member author = memberService.findById(form.getAuthorId());
		Post post = new Post(form.getTitle(), form.getContent(), author);
        post.setTags(form.getTags());
        if (form.getImgUrl() != null && !form.getImgUrl().isBlank()) {
            post.setImgUrl(form.getImgUrl());
        }
        post.setRestaurant(resolveRestaurant(form.getRestaurantId()));
		return postRepository.save(post).getId();
	}

	// 수정. 영속 엔티티의 필드만 바꾸면 트랜잭션 종료 시 UPDATE 가 자동으로 나간다(변경 감지).
	@Transactional
	public void update(Long id, PostForm form) {
		Post post = findById(id);
		post.edit(form.getTitle(), form.getContent());
        post.setTags(form.getTags());
        if (form.getImgUrl() != null && !form.getImgUrl().isBlank()) {
            post.setImgUrl(form.getImgUrl());
        }
        post.setRestaurant(resolveRestaurant(form.getRestaurantId()));
	}

    // restaurantId가 없거나, 그 사이 삭제된 맛집이면 태그 없이(null) 진행한다.
    private Restaurant resolveRestaurant(Long restaurantId) {
        if (restaurantId == null) return null;
        return restaurantRepository.findById(restaurantId).orElse(null);
    }

	// 삭제.
	@Transactional
	public void delete(Long id) {
		postRepository.deleteById(id);
	}

	// 신고 누적 자동 숨김/해제 — 삭제와 달리 글 자체는 남지만 커뮤니티 목록에서만 빠진다.
	@Transactional
	public void hide(Long id) {
		findById(id).hide();
	}

	@Transactional
	public void unhide(Long id) {
		findById(id).unhide();
	}

    // [삭제됨] like(Long id) — PostLike 행을 안 보고 카운터만 올리던 옛 메서드.
    // 호출하던 컨트롤러가 주석 처리된 뒤로 쓰이지 않는 데드코드였고, 그대로 두면 나중에 실수로
    // 호출했을 때 같은 사람이 무한히 좋아요를 올릴 수 있어서 지웠다. 좋아요는 toggleLike만 쓴다.

    /* 좋아요 토글 */
    @Transactional
    public boolean toggleLike(Long postId, Member member){
        Post post = findById(postId);
        return postLikeRepository.findByPostAndMember(post, member)
                .map(postLike -> {
                    postLikeRepository.delete(postLike);
                    post.decreaseLikeCount();
                    return false; // 좋아요 취소
                })
                .orElseGet(() -> {
                    postLikeRepository.save(new PostLike(post, member));
                    post.increaseLikeCount();
                    notificationService.notify(post.getAuthor(), member, NotificationType.LIKE,
                            "내 게시글에 좋아요를 받았습니다.", "/posts/" + postId);
                    return true; // 좋아요 등록
                });
    }
    // 회원이 글에 좋아요를 눌렀는지 여부
    public boolean isLiked(Long postId, Member member){
        if (member == null) return false;
        Post post = findById(postId);
        return postLikeRepository.existsByPostAndMember(post,member);
    }

    // 회원이 좋아요 누른 게시글 목록 조회
    public List<Post> findLikedPosts(Member member) {
        if (member == null) return List.of();
        return postLikeRepository.findLikedPostsByMember(member);
    }
}
