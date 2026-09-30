package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Report;
import ac.kr.osan.unifeed.entity.ReportStatus;
import ac.kr.osan.unifeed.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findAllByOrderByCreatedAtDesc();

    // 신고 누적 자동 숨김 판단용 — 같은 대상에 대한 처리 대기(PENDING) 신고가 몇 건인지.
    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);

    // 관리자 화면 "처리 대기 N건" 배지용 — 전체를 로드하지 않고 DB에서 바로 COUNT.
    long countByStatus(ReportStatus status);

    // 중복 신고 방지 — 같은 신고자가 같은 대상에 이미 처리 대기 신고를 넣었는지.
    boolean existsByReporterAndTargetTypeAndTargetIdAndStatus(Member reporter, ReportTargetType targetType, Long targetId, ReportStatus status);
}
