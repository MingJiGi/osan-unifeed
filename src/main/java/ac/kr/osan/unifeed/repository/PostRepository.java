package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Post;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.University;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	// 학교별 목록 — 커뮤니티는 같은 대학교 학생끼리만 보인다(멀티캠퍼스).
	// hidden(신고 누적 자동 숨김) 게시글은 목록에서 빠진다 — 완전 삭제는 아니라 관리자가 처리하면 다시 보임.
	@Query("select p from Post p join fetch p.author where p.author.university = :university and p.hidden = false order by p.createdAt desc")
	List<Post> findAllByAuthorUniversityWithAuthor(@Param("university") University university);

	// 무한스크롤용 — 페이지 단위 조회. 전체 개수 COUNT가 필요 없어 Page 대신 가벼운 Slice(hasNext만 판단)를 쓴다.
	@Query("select p from Post p join fetch p.author where p.author.university = :university and p.hidden = false order by p.createdAt desc")
	Slice<Post> findAllByAuthorUniversityWithAuthor(@Param("university") University university, Pageable pageable);

	// 상세 — 특정 글 + 작성자 + 태그된 맛집(있으면). restaurant 는 nullable 이라 left join.
	@Query("select p from Post p join fetch p.author left join fetch p.restaurant where p.id = :id")
	Optional<Post> findWithAuthorById(Long id);

	// 맛집 삭제 시 그 맛집을 태그한 글의 연결만 해제(글은 유지)
	@Modifying
	@Query("update Post p set p.restaurant = null where p.restaurant = :restaurant")
	void clearRestaurant(@Param("restaurant") ac.kr.osan.unifeed.entity.Restaurant restaurant);

	// 특정 회원이 쓴 글 목록(회원 상세 화면용). 여기서는 작성자를 화면에 안 그리므로
	// join fetch 없이 쿼리 메서드로 간단히 한다.
	List<Post> findByAuthorOrderByCreatedAtDescIdDesc(Member author);
}
