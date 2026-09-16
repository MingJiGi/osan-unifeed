package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Post;

import ac.kr.osan.unifeed.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// ############################################################################
// PostRepository — 게시글 DB 접근
// ----------------------------------------------------------------------------
// [이 예제에서 가장 중요한 부분] "join fetch"
//   Post.author 는 LAZY 다. 화면에서 작성자 이름(author.name)을 보여주려면,
//   조회할 때 author 를 함께 읽어와야 한다.
//   - 안 그러면: 목록 100건을 그릴 때 author 를 100번 더 조회한다(N+1 문제).
//   - 게다가 open-in-view=false 라 렌더링 시점엔 이미 트랜잭션이 끝나 있어
//     LazyInitializationException 이 난다.
//   → 그래서 "join fetch p.author" 로 글 + 작성자를 한 방(SQL 1번)에 읽어온다.
// (N+1 문제 자체는 spring-data-jpas 예제에서 자세히 다룬다)
// ############################################################################
public interface PostRepository extends JpaRepository<Post, Long> {

	// 목록 — 작성자까지 함께(join fetch), 최신순.
	@Query("select p from Post p join fetch p.author order by p.createdAt desc")
	List<Post> findAllWithAuthor();

	// 상세 — 특정 글 + 작성자.
	@Query("select p from Post p join fetch p.author where p.id = :id")
	Optional<Post> findWithAuthorById(Long id);

	// 특정 회원이 쓴 글 목록(회원 상세 화면용). 여기서는 작성자를 화면에 안 그리므로
	// join fetch 없이 쿼리 메서드로 간단히 한다.
	List<Post> findByAuthorOrderByCreatedAtDescIdDesc(Member author);
}
