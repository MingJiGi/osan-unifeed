package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.service.UniversityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

// ############################################################################
// UniversityApiController — 회원가입 화면의 학교 검색/자동추천 전용.
// ----------------------------------------------------------------------------
// 가입 전(비로그인) 상태에서 호출되므로 로그인 확인을 하지 않는다(공개 API).
// ############################################################################
@RestController
@RequestMapping("/api/universities")
public class UniversityApiController {

    private final UniversityService universityService;

    public UniversityApiController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping
    public List<UniversityDto> search(@RequestParam(required = false) String keyword) {
        return universityService.search(keyword).stream().map(UniversityDto::from).toList();
    }

    // 이메일 입력 직후 도메인으로 자동 추천 — 못 찾으면 빈 리스트(직접 검색해서 고르면 됨).
    @GetMapping("/suggest")
    public List<UniversityDto> suggest(@RequestParam String email) {
        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : "";
        Optional<University> match = universityService.findByEmailDomainSuffix(domain);
        return match.map(u -> List.of(UniversityDto.from(u))).orElse(List.of());
    }

    public record UniversityDto(Long id, String name, String regionName) {
        static UniversityDto from(University u) {
            return new UniversityDto(u.getId(), u.getName(), u.getRegionName());
        }
    }
}
