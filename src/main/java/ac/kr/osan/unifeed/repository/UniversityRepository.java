package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.University;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniversityRepository extends JpaRepository<University, Long> {

    // 학교 검색(가입 화면 자동완성용) — 이름 부분일치, 가나다순.
    List<University> findByNameContainingOrderByNameAsc(String keyword);

    // 가입 시 이메일 도메인으로 자동 추천할 때 쓴다.
    Optional<University> findByEmailDomain(String emailDomain);

    List<University> findAllByOrderByNameAsc();
}
