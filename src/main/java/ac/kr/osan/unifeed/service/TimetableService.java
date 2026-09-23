package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.TimetableCourse;
import ac.kr.osan.unifeed.repository.TimetableCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class TimetableService {

    private final TimetableCourseRepository courseRepository;

    public TimetableService(TimetableCourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    // 회원의 전체 수업 목록
    public List<TimetableCourse> findByMember(Member member) {
        return courseRepository.findByMemberOrderByDayColAscStartHourAscStartMinAsc(member);
    }

    // 수업 추가
    @Transactional
    public TimetableCourse addCourse(Member member, String semester, String name, String info, Integer dayCol,
                                      String color, Integer startHour, Integer startMin, Integer endHour, Integer endMin) {
        validateCourseInput(name, dayCol, startHour, startMin, endHour, endMin);

        TimetableCourse course = new TimetableCourse(
                member,
                normalizeSemester(semester),
                name.trim(),
                info == null ? null : info.trim(),
                dayCol, color, startHour, startMin, endHour, endMin
        );
        return courseRepository.save(course);
    }

    // 수업 수정 (본인 수업만) — 시간표 카드 클릭해서 여는 수정 모달용
    @Transactional
    public TimetableCourse updateCourse(Long courseId, Member member, String semester, String name, String info,
                                         Integer dayCol, String color, Integer startHour, Integer startMin,
                                         Integer endHour, Integer endMin) {
        validateCourseInput(name, dayCol, startHour, startMin, endHour, endMin);

        TimetableCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("수업을 찾을 수 없습니다. id=" + courseId));
        if (!course.getMember().getId().equals(member.getId())) {
            throw new IllegalStateException("본인의 시간표만 수정할 수 있습니다.");
        }

        course.update(normalizeSemester(semester), name.trim(), info == null ? null : info.trim(),
                dayCol, color, startHour, startMin, endHour, endMin);
        return course;
    }

    // 시간표 화면(timetable/index.html)이 09:00~22:00 그리드로만 그려지므로 서버도 같은 범위를 강제한다 —
    // 프론트 드롭다운만 믿으면 API를 직접 호출해서 이 범위 밖 시간을 그대로 저장할 수 있었다.
    private static final int START_HOUR = 9;
    private static final int END_HOUR = 22;

    private void validateCourseInput(String name, Integer dayCol, Integer startHour, Integer startMin,
                                      Integer endHour, Integer endMin) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("과목명을 입력해주세요.");
        }
        if (dayCol == null || dayCol < 2 || dayCol > 6) {
            throw new IllegalArgumentException("요일이 올바르지 않습니다.");
        }
        if (startHour == null || startMin == null || endHour == null || endMin == null) {
            throw new IllegalArgumentException("수업 시간을 입력해주세요.");
        }
        int startTotal = startHour * 60 + startMin;
        int endTotal = endHour * 60 + endMin;
        if (endTotal <= startTotal) {
            throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야 합니다.");
        }
        if (startTotal < START_HOUR * 60 || endTotal > END_HOUR * 60) {
            throw new IllegalArgumentException("수업 시간은 09:00~22:00 사이여야 합니다.");
        }
    }

    private String normalizeSemester(String semester) {
        return (semester == null || semester.isBlank()) ? "미지정 학기" : semester.trim();
    }

    // 수업 삭제 (본인 수업만)
    @Transactional
    public void deleteCourse(Long courseId, Member member) {
        TimetableCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NoSuchElementException("수업을 찾을 수 없습니다. id=" + courseId));
        if (!course.getMember().getId().equals(member.getId())) {
            throw new IllegalStateException("본인의 시간표만 수정할 수 있습니다.");
        }
        courseRepository.delete(course);
    }

    // 시간표 전체 비우기
    @Transactional
    public void clearAll(Member member) {
        courseRepository.deleteByMember(member);
    }

    // 지금 이 순간이 "수업과 수업 사이"의 진짜 공강인지 계산한다.
    // [주의] 오늘 수업이 아예 없거나, 첫 수업 전이거나, 마지막 수업 이후면 "공강"으로 안 친다 —
    //        그건 수업 사이의 빈 시간이 아니라 그냥 시간표가 비어있는 것뿐이라 추천을 안 보여준다.
    public GapInfo getCurrentGap(Member member) {
        LocalDateTime now = LocalDateTime.now();
        // 이 앱의 요일 코드: 2=월 ~ 6=금 (timetable JS 와 동일). 주말(요일코드 7,8)은 그대로 두면
        // 어차피 수업이 하나도 안 걸려서 자연스럽게 "공강 없음"으로 처리된다.
        int dayCol = now.getDayOfWeek().getValue() + 1;
        int nowMin = now.getHour() * 60 + now.getMinute();

        List<TimetableCourse> todayCourses = courseRepository.findByMemberOrderByDayColAscStartHourAscStartMinAsc(member)
                .stream()
                .filter(c -> c.getDayCol() == dayCol)
                .sorted(Comparator.comparingInt(c -> c.getStartHour() * 60 + c.getStartMin()))
                .toList();

        // 1. 지금 수업 중인가?
        for (int i = 0; i < todayCourses.size(); i++) {
            TimetableCourse c = todayCourses.get(i);
            int startMin = c.getStartHour() * 60 + c.getStartMin();
            int endMin = c.getEndHour() * 60 + c.getEndMin();
            if (nowMin >= startMin && nowMin < endMin) {
                // 이 수업 바로 다음 수업이 있어야만 "수업 사이" 미리보기를 보여준다(없으면 오늘 마지막 수업).
                TimetableCourse next = (i + 1 < todayCourses.size()) ? todayCourses.get(i + 1) : null;
                if (next == null) {
                    return GapInfo.inClass(c.getName(), formatTime(c.getEndHour(), c.getEndMin()));
                }
                int nextStartMin = next.getStartHour() * 60 + next.getStartMin();
                int upcomingGapMinutes = nextStartMin - endMin;
                if (upcomingGapMinutes < 1) {
                    // 바로 다음 수업이 연강으로 붙어있음 -> 보여줄 공강이 없다.
                    return GapInfo.inClass(c.getName(), formatTime(c.getEndHour(), c.getEndMin()));
                }
                return GapInfo.inClassWithPreview(
                        c.getName(), formatTime(c.getEndHour(), c.getEndMin()),
                        upcomingGapMinutes, next.getName(), formatTime(next.getStartHour(), next.getStartMin())
                );
            }
        }

        // 2. 수업 중이 아니면, "이미 끝난 수업"과 "곧 시작할 수업" 사이에 지금이 껴있는지 확인한다.
        //    둘 다 있어야 진짜 "수업과 수업 사이"의 공강이다 — 하나라도 없으면(첫 수업 전/마지막 수업 후/수업 없음) 표시 안 함.
        TimetableCourse prev = todayCourses.stream()
                .filter(c -> (c.getEndHour() * 60 + c.getEndMin()) <= nowMin)
                .max(Comparator.comparingInt(c -> c.getEndHour() * 60 + c.getEndMin()))
                .orElse(null);
        TimetableCourse next = todayCourses.stream()
                .filter(c -> (c.getStartHour() * 60 + c.getStartMin()) > nowMin)
                .min(Comparator.comparingInt(c -> c.getStartHour() * 60 + c.getStartMin()))
                .orElse(null);

        if (prev == null || next == null) {
            return GapInfo.none();
        }

        int gapMinutes = (next.getStartHour() * 60 + next.getStartMin()) - nowMin;
        return GapInfo.gap(gapMinutes, next.getName(), formatTime(next.getStartHour(), next.getStartMin()));
    }

    private String formatTime(int hour, int min) {
        return String.format("%02d:%02d", hour, min);
    }

    public record GapInfo(
            String status, // "inClass" | "gap" | "none"
            String currentClassName,
            String currentClassEndTime,
            Integer gapMinutes,
            String nextClassName,
            String nextClassTime,
            // 수업 중일 때, 그 수업이 끝난 뒤 다음 수업 전까지 생길 공강 미리보기(맛집 추천 미리보기용). 다음 수업이 없으면 null.
            Integer upcomingGapMinutes,
            String upcomingGapNextClassName,
            String upcomingGapNextClassTime
    ) {
        // 수업 중, 다음 수업까지의 공강 미리보기 없음(연강이거나 오늘 마지막 수업)
        public static GapInfo inClass(String className, String endTime) {
            return new GapInfo("inClass", className, endTime, null, null, null, null, null, null);
        }

        // 수업 중, 이 수업 끝나면 생길 공강 미리보기 포함
        public static GapInfo inClassWithPreview(String className, String endTime,
                                                  Integer upcomingGapMinutes, String upcomingGapNextClassName, String upcomingGapNextClassTime) {
            return new GapInfo("inClass", className, endTime, null, null, null,
                    upcomingGapMinutes, upcomingGapNextClassName, upcomingGapNextClassTime);
        }

        // 지금 실제로 수업과 수업 사이 공강
        public static GapInfo gap(int gapMinutes, String nextClassName, String nextClassTime) {
            return new GapInfo("gap", null, null, gapMinutes, nextClassName, nextClassTime, null, null, null);
        }

        // 보여줄 공강 없음(오늘 수업 없음 / 첫 수업 전 / 마지막 수업 후 등)
        public static GapInfo none() {
            return new GapInfo("none", null, null, null, null, null, null, null, null);
        }
    }
}
