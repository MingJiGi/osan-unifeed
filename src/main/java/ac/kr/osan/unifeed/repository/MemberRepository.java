package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// ############################################################################
// MemberRepository — 회원 DB 접근
// ----------------------------------------------------------------------------
// 인터페이스만 선언하면 Spring Data JPA 가 구현체를 자동 생성한다.
// (원리는 spring-data-jpas 예제 참고)
// ############################################################################
public interface MemberRepository extends JpaRepository<Member, Long> {

	// 가입 최신순 목록. (메서드 이름만으로 쿼리 생성 — 쿼리 메서드)
	List<Member> findAllByOrderByCreatedAtDesc();

	// 시드 데이터 중복 방지에 쓴다(이미 있으면 안 만들기).
	boolean existsByEmail(String email);
}
