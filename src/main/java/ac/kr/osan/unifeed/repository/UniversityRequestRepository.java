package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.UniversityRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UniversityRequestRepository extends JpaRepository<UniversityRequest, Long> {

    // 관리자 화면 — 대기중인 것부터 보여주고, 그 아래 처리 완료된 이력을 둔다.
    List<UniversityRequest> findByStatusOrderByCreatedAtDesc(UniversityRequest.Status status);

    List<UniversityRequest> findByStatusNotOrderByProcessedAtDesc(UniversityRequest.Status status);

    long countByStatus(UniversityRequest.Status status);

    // 같은 사람이 같은 학교를 반복 신청하는 것만 막는다(다른 학교 신청은 허용).
    boolean existsByRequesterEmailAndUniversityNameAndStatus(
            String requesterEmail, String universityName, UniversityRequest.Status status);
}
