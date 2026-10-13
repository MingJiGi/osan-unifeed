package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.TimetableCourse;
import ac.kr.osan.unifeed.service.TimetableOcrService;
import ac.kr.osan.unifeed.service.TimetableService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/timetable/courses")
public class TimetableApiController {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");
    private static final long MAX_IMAGE_SIZE = 8L * 1024 * 1024; // 시간표 캡처는 보통 1MB 미만

    private final TimetableService timetableService;
    private final TimetableOcrService timetableOcrService;
    private final ac.kr.osan.unifeed.config.RateLimiter rateLimiter;

    public TimetableApiController(TimetableService timetableService,
                                  TimetableOcrService timetableOcrService,
                                  ac.kr.osan.unifeed.config.RateLimiter rateLimiter) {
        this.timetableService = timetableService;
        this.timetableOcrService = timetableOcrService;
        this.rateLimiter = rateLimiter;
    }

    // 시간표 사진 인식 — 이미지를 읽어 수업 목록만 돌려준다(저장은 하지 않는다).
    // 화면이 "이렇게 읽었어요" 목록을 보여주고, 사용자가 확인하면 위의 수업 추가 API로 저장한다.
    // 잘못 읽은 수업이 묻지도 않고 시간표에 들어가는 걸 막기 위한 2단계 구조.
    @PostMapping("/from-image")
    public ResponseEntity<?> parseFromImage(@RequestParam("file") MultipartFile file, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        if (!timetableOcrService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "시간표 사진 인식 기능이 아직 설정되지 않았습니다."));
        }
        // 이미지 인식은 호출당 비용이 텍스트보다 크다 — 한 계정이 10분에 5번까지.
        if (!rateLimiter.allow("ocr:" + loginMember.getId(), 5, 600)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "사진 인식은 잠시 후 다시 시도해주세요."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "시간표 이미지를 선택해주세요."));
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미지는 8MB 이하만 올릴 수 있습니다."));
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "PNG, JPG, WEBP 이미지만 올릴 수 있습니다."));
        }

        try {
            TimetableOcrService.OcrResult result =
                    timetableOcrService.parse(file.getBytes(), contentType);
            if (result.courses().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "courses", List.of(),
                        "message", "이미지에서 수업을 찾지 못했습니다. 시간표가 잘 보이는 사진으로 다시 시도해주세요."));
            }
            return ResponseEntity.ok(Map.of(
                    "courses", result.courses(),
                    "droppedOutOfRange", result.droppedOutOfRange(),
                    "message", result.courses().size() + "개 수업을 읽었습니다."
                            + (result.droppedOutOfRange() > 0
                                ? " (주말이거나 09~22시 밖이라 제외된 수업 " + result.droppedOutOfRange() + "개)" : "")));
        } catch (java.io.IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미지를 읽지 못했습니다. 다시 시도해주세요."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
        }
    }

    // 내 시간표 수업 목록 조회 — semester를 넘기면 그 학기 것만, 안 넘기면(구버전 호환) 전체를 돌려준다.
    // 화면(timetable/index.html)은 항상 현재 선택된 학기를 실어 보낸다 — 안 그러면 다른 학기에
    // 입력한 수업이 지금 보고 있는 학기 그리드에 그대로 섞여 보이는 문제가 있었다.
    @GetMapping
    public ResponseEntity<?> getCourses(@RequestParam(required = false) String semester, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        List<CourseDto> dtos = timetableService.findByMemberAndSemester(loginMember, semester).stream()
                .map(CourseDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    // 내가 수업을 넣어둔 학기 목록 — 화면이 학기 드롭다운에 합쳐서 보여준다.
    // (직접 입력한 학기도 수업을 하나 추가하는 순간부터 목록에 뜨고, 기기를 바꿔도 유지된다)
    @GetMapping("/semesters")
    public ResponseEntity<?> getSemesters(HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        return ResponseEntity.ok(timetableService.findSemesters(loginMember));
    }

    // 수업 추가
    @PostMapping
    public ResponseEntity<?> addCourse(@jakarta.validation.Valid @RequestBody CourseCreateRequest request, HttpSession session) {
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
    public ResponseEntity<?> updateCourse(@PathVariable Long id, @jakarta.validation.Valid @RequestBody CourseCreateRequest request, HttpSession session) {
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

    // 시간표 비우기 — 지금 보고 있는 학기 것만 지운다(semester 미지정 시에만 전체).
    @DeleteMapping
    public ResponseEntity<?> clearAll(@RequestParam(required = false) String semester, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        timetableService.clearAll(loginMember, semester);
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

    // @Size 값은 엔티티(=DB 컬럼) 길이와 맞췄다 — 없으면 긴 값이 DB까지 가서 500으로 터진다.
    public record CourseCreateRequest(
            @jakarta.validation.constraints.Size(max = 100, message = "학기명은 100자 이내여야 합니다.") String semester,
            @jakarta.validation.constraints.Size(max = 100, message = "과목명은 100자 이내여야 합니다.") String name,
            @jakarta.validation.constraints.Size(max = 100, message = "강의실/교수명은 100자 이내여야 합니다.") String info,
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
