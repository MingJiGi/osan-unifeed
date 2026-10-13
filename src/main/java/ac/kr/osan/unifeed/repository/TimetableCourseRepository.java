package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.TimetableCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimetableCourseRepository extends JpaRepository<TimetableCourse, Long> {

    // 회원의 전체 수업 목록 (요일 -> 시작시각 순) — 오늘 공강 계산처럼 학기 구분 없이 전부 봐야 할 때만 쓴다.
    List<TimetableCourse> findByMemberOrderByDayColAscStartHourAscStartMinAsc(Member member);

    // 회원의 "그 학기" 수업 목록 — 시간표 화면은 선택한 학기 것만 보여줘야 한다
    // (학기를 안 가리면 1학기에 입력한 수업이 2학기 화면에도 그대로 섞여 보이는 버그가 났었음).
    List<TimetableCourse> findByMemberAndSemesterOrderByDayColAscStartHourAscStartMinAsc(Member member, String semester);

    // 내가 수업을 넣어둔 학기 목록 — 화면의 학기 드롭다운에 합쳐서, 직접 입력한 학기도
    // 다음부터는 목록에서 바로 고를 수 있게 한다. (그 학기 수업을 다 지우면 목록에서도 사라진다)
    @org.springframework.data.jpa.repository.Query(
            "select distinct c.semester from TimetableCourse c where c.member = :member order by c.semester desc")
    List<String> findSemestersByMember(@org.springframework.data.repository.query.Param("member") Member member);

    // 회원의 전체 수업 일괄 삭제 (회원 탈퇴 등 학기 구분이 의미 없는 경우용)
    void deleteByMember(Member member);

    // 회원의 "그 학기" 수업만 일괄 삭제 — 화면은 한 학기만 보여주는데 비우기가 전 학기를 날리면
    // 사용자는 안 보이던 다른 학기 시간표까지 통째로 잃는다.
    void deleteByMemberAndSemester(Member member, String semester);
}
