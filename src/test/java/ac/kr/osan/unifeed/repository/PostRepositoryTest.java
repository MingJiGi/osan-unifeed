package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.repository.PostRepository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ############################################################################
// PostRepository 테스트 — H2 로 저장/조회를 검증한다 (도커 불필요)
// ----------------------------------------------------------------------------
// @DataJpaTest : JPA 관련 빈만 로드하고, DB 를 임베디드 H2 로 바꿔 끼운다.
//                각 테스트는 끝나면 자동 롤백되어 서로 영향을 주지 않는다.
// ############################################################################
@DataJpaTest
@ActiveProfiles("test")
class PostRepositoryTest {

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	PostRepository postRepository;

	@Test
	void 목록조회는_작성자까지_함께_읽어온다() {
		// given — 회원 1명 + 글 2개
		Member kim = memberRepository.save(new Member("김철수", "kim@test.com"));
		postRepository.save(new Post("첫 글", "내용1", kim));
		postRepository.save(new Post("둘째 글", "내용2", kim));

		// when — join fetch 목록 조회
		List<Post> posts = postRepository.findAllWithAuthor();

		// then — 2건, 작성자 이름까지 접근된다(LAZY 예외 없이)
		assertThat(posts).hasSize(2);
		assertThat(posts.get(0).getAuthor().getName()).isEqualTo("김철수");
	}

	@Test
	void 회원별_작성글을_최신순으로_찾는다() {
		Member lee = memberRepository.save(new Member("이영희", "lee@test.com"));
		postRepository.save(new Post("A", "a", lee));
		postRepository.save(new Post("B", "b", lee));

		List<Post> posts = postRepository.findByAuthorOrderByCreatedAtDesc(lee);

		assertThat(posts).hasSize(2);
		// 나중에 저장된 B 가 먼저(최신순)
		assertThat(posts.get(0).getTitle()).isEqualTo("B");
	}
}
