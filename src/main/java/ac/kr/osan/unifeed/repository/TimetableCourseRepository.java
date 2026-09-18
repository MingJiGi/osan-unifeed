package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.TimetableCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimetableCourseRepository extends JpaRepository<TimetableCourse, Long> {

    // 회원의 전체 수업 목록 (요일 -> 시작시각 순)
    List<TimetableCourse> findByMemberOrderByDayColAscStartHourAscStartMinAsc(Member member);

    // 회원의 전체 수업 일괄 삭제 (시간표 비우기)
    void deleteByMember(Member member);
}
