package ac.kr.osan.unifeed.config;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.repository.MemberRepository;
import ac.kr.osan.unifeed.entity.Post;
import ac.kr.osan.unifeed.repository.PostRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ############################################################################
// DataInitializer — 처음 실행할 때 예시 데이터를 넣어준다
// ----------------------------------------------------------------------------
// "db 조회해서 view 까지" 를 바로 눈으로 보려면 데이터가 있어야 한다.
// 앱이 뜰 때 회원/게시글이 비어 있으면 몇 건 만들어 넣는다(이미 있으면 안 한다).
//
// @Profile("!test") : 테스트(H2)에서는 이 시더가 돌지 않게 한다.
//   테스트는 각자 필요한 데이터를 직접 준비하는 게 깔끔하기 때문이다.
//
// [주의] toydb 는 공유 DB 다. 여러 명이 실습하면 이 데이터가 공유된다(학습/데모용).
// ############################################################################
@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

	private final MemberRepository memberRepository;
	private final PostRepository postRepository;

	public DataInitializer(MemberRepository memberRepository, PostRepository postRepository) {
		this.memberRepository = memberRepository;
		this.postRepository = postRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		// 이미 회원이 있으면(=다른 사람이 먼저 시딩했으면) 건너뛴다.
		if (memberRepository.count() > 0) {
			return;
		}

		Member kim = memberRepository.save(new Member("김철수", "chulsoo@example.com", "1234"));
		Member lee = memberRepository.save(new Member("이영희", "younghee@example.com", "12345"));
		Member park = memberRepository.save(new Member("박민수", "minsoo@example.com", "123456"));

		postRepository.save(new Post("첫 글입니다", "Thymeleaf + Spring Data JPA 게시판 예제입니다.", kim));
		postRepository.save(new Post("공지사항", "이 게시판은 학습용입니다. 자유롭게 글을 남겨보세요.", lee));
		postRepository.save(new Post("질문 있어요", "게시글 수정은 어떻게 하나요?", park));
		postRepository.save(new Post("두 번째 글", "작성자별로 회원 상세에서 모아볼 수 있습니다.", kim));
	}
}
