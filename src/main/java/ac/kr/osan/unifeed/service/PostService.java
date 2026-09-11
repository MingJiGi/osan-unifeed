package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.repository.PostRepository;
import ac.kr.osan.unifeed.entity.Post;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import ac.kr.osan.unifeed.dto.PostForm;
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

	public PostService(PostRepository postRepository, MemberService memberService) {
		this.postRepository = postRepository;
		this.memberService = memberService;
	}

	// 목록 — 작성자까지 함께 읽어온다(N+1/lazy 예외 방지).
	public List<Post> findAll() {
		return postRepository.findAllWithAuthor();
	}

	// 상세 — 작성자 포함. 없으면 예외.
	public Post findById(Long id) {
		return postRepository.findWithAuthorById(id)
				.orElseThrow(() -> new NoSuchElementException("게시글을 찾을 수 없습니다. id=" + id));
	}

	// 특정 회원이 쓴 글(회원 상세 화면용).
	public List<Post> findByAuthor(Member author) {
		return postRepository.findByAuthorOrderByCreatedAtDesc(author);
	}

	// 새 글 저장. 폼의 authorId 로 회원을 찾아 연결한다.
	@Transactional
	public Long create(PostForm form) {
		Member author = memberService.findById(form.getAuthorId());
		Post post = new Post(form.getTitle(), form.getContent(), author);
        post.setTags(form.getTags());
		return postRepository.save(post).getId();
	}

	// 수정. 영속 엔티티의 필드만 바꾸면 트랜잭션 종료 시 UPDATE 가 자동으로 나간다(변경 감지).
	@Transactional
	public void update(Long id, PostForm form) {
		Post post = findById(id);
		post.edit(form.getTitle(), form.getContent());
        post.setTags(form.getTags());
	}

	// 삭제.
	@Transactional
	public void delete(Long id) {
		postRepository.deleteById(id);
	}
}
