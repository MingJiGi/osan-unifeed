package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.repository.UniversityRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UniversityService {

    private final UniversityRepository universityRepository;

    public UniversityService(UniversityRepository universityRepository) {
        this.universityRepository = universityRepository;
    }

    public List<University> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return universityRepository.findAllByOrderByNameAsc();
        }
        return universityRepository.findByNameContainingOrderByNameAsc(keyword.trim());
    }

    public University findById(Long id) {
        return universityRepository.findById(id).orElse(null);
    }

    // 가입 화면에서 이메일을 입력하는 순간 도메인으로 자동 추천할 때 쓴다.
    // 정확히 일치하는 도메인이 없으면 접미사(서브도메인) 매칭도 시도한다 —
    // 예: 학생 실제 메일이 portal.osan.ac.kr 이어도 osan.ac.kr 로 등록된 학교를 찾아준다.
    public Optional<University> findByEmailDomainSuffix(String emailDomain) {
        if (emailDomain == null || emailDomain.isBlank()) {
            return Optional.empty();
        }
        String domain = emailDomain.toLowerCase();
        return universityRepository.findAllByOrderByNameAsc().stream()
                .filter(u -> u.getEmailDomain() != null)
                // "." 경계 없이 endsWith만 하면 notosan.ac.kr 같은 메일이 osan.ac.kr로 오매칭될 수 있다
                // (MemberService.requireMatchingDomain과 동일한 경계 규칙으로 맞춘다).
                .filter(u -> {
                    String univDomain = u.getEmailDomain().toLowerCase();
                    return domain.equals(univDomain) || domain.endsWith("." + univDomain);
                })
                .findFirst();
    }
}
