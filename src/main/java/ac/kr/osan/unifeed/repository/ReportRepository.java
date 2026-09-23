package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Report;
import ac.kr.osan.unifeed.entity.ReportStatus;
import ac.kr.osan.unifeed.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();

    // 신고 누적 자동 숨김 판단용 — 같은 대상에 대한 처리 대기(PENDING) 신고가 몇 건인지.
    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);
}
