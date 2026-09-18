package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.TimetableCourse;
import ac.kr.osan.unifeed.service.TimetableService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetable/courses")
public class TimetableApiController {

    private final TimetableService timetableService;

    public TimetableApiController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    // 내 시간표 수업 목록 조회
    @GetMapping
    public ResponseEntity<?> getCourses(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        List<CourseDto> dtos = timetableService.findByMember(loginMember).stream()
                .map(CourseDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    // 수업 추가
    @PostMapping
    public ResponseEntity<?> addCourse(@RequestBody CourseCreateRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        try {
            TimetableCourse course = timetableService.addCourse(
                    loginMember, request.semester(), request.name(), request.info(), request.dayCol(),
                    request.color(), request.startHour(), request.startMin(), request.endHour(), request.endMin()
            );
            return ResponseEntity.ok(CourseDto.from(course));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // 수업 수정 (시간표 카드 클릭 -> 수정 모달 저장)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @RequestBody CourseCreateRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        try {
            TimetableCourse course = timetableService.updateCourse(
                    id, loginMember, request.semester(), request.name(), request.info(), request.dayCol(),
                    request.color(), request.startHour(), request.startMin(), request.endHour(), request.endMin()
            );
            return ResponseEntity.ok(CourseDto.from(course));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    // 수업 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        try {
            timetableService.deleteCourse(id, loginMember);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    // 시간표 전체 비우기
    @DeleteMapping
    public ResponseEntity<?> clearAll(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        timetableService.clearAll(loginMember);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // 지금 이 순간 공강 여부 조회 (지도 페이지의 "공강 맛집 추천"용)
    @GetMapping("/current-gap")
    public ResponseEntity<?> getCurrentGap(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        return ResponseEntity.ok(timetableService.getCurrentGap(loginMember));
    }

    public record CourseCreateRequest(
            String semester,
            String name,
            String info,
            Integer dayCol,
            String color,
            Integer startHour,
            Integer startMin,
            Integer endHour,
            Integer endMin
    ) {}

    public record CourseDto(
            Long id,
            String semester,
            String name,
            String info,
            Integer dayCol,
            String color,
            Integer startHour,
            Integer startMin,
            Integer endHour,
            Integer endMin
    ) {
        public static CourseDto from(TimetableCourse c) {
            return new CourseDto(
                    c.getId(), c.getSemester(), c.getName(), c.getInfo(), c.getDayCol(), c.getColor(),
                    c.getStartHour(), c.getStartMin(), c.getEndHour(), c.getEndMin()
            );
        }
    }
}
