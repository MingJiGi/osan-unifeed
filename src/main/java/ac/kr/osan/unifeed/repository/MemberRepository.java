package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// ############################################################################
// MemberRepository — 회원 DB 접근
// ----------------------------------------------------------------------------
// 인터페이스만 선언하면 Spring Data JPA 가 구현체를 자동 생성한다.
// (원리는 spring-data-jpas 예제 참고)
// ############################################################################
public interface MemberRepository extends JpaRepository<Member, Long> {

	// 가입 최신순 목록. (메서드 이름만으로 쿼리 생성 — 쿼리 메서드)
	List<Member> findAllByOrderByCreatedAtDesc();

	// 관리자 회원 검색: 이름/이메일 부분일치(대소문자 무시) AND 대학 필터. q는 ""이면 무시, universityId는 null이면 무시.
	// left join fetch 라서 대학 미지정 회원도 나오고 대학 조회 쿼리가 추가로 나가지 않는다.
	// verified: true=인증 완료만, false=미인증만, null=전부.
	// 이메일 인증을 마쳐야 로그인이 되므로, 미인증 행은 아직 "가입한 회원"이 아니다.
	// 기본 화면에서는 인증 완료만 보여주고, 미인증은 따로 골라서 확인·정리한다.
	@Query("select m from Member m left join fetch m.university u where "
			+ "(:q = '' or lower(m.name) like lower(concat('%', :q, '%')) or lower(m.email) like lower(concat('%', :q, '%'))) "
			+ "and (:universityId is null or u.id = :universityId) "
			+ "and (:verified is null or m.emailVerified = :verified) order by m.createdAt desc")
	List<Member> search(@Param("q") String q, @Param("universityId") Long universityId,
						@Param("verified") Boolean verified);

	long countByEmailVerifiedFalse();

	// 시드 데이터 중복 방지에 쓴다(이미 있으면 안 만들기).
	boolean existsByEmail(String email);

    // 이메일 찾기
    Optional<Member> findByEmail(String email);

    // 이메일 인증 토큰으로 찾기
    Optional<Member> findByVerificationToken(String verificationToken);

    // 비밀번호 재설정 토큰으로 찾기
    Optional<Member> findByResetToken(String resetToken);
}
