package ac.kr.osan.unifeed.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ############################################################################
// TimetableOcrService — 시간표 사진을 읽어 수업 목록으로 바꾼다
// ----------------------------------------------------------------------------
// [왜 만들었나]
// 회원 19명 중 시간표를 넣은 사람이 0명이었다. 수업을 한 칸씩 손으로 넣어야 하니 아무도 안 쓴 것이고,
// 그래서 시간표에 의존하는 기능 두 개(공강 맛집 추천 배너, "오늘 뭐 먹지?"의 공강 반영)가
// 사실상 작동하지 않는 상태였다. 사진 한 장으로 시간표가 채워지면 그 두 기능이 같이 살아난다.
//
// [설계]
// - 모델에는 이미지와 "이 앱이 저장할 수 있는 범위"만 알려주고, 요일은 '월~금' 글자로 받는다
//   (2~6 같은 내부 코드로 변환하는 산수를 모델에 맡기면 틀릴 여지만 커진다).
// - 돌려받은 값은 전부 서버에서 다시 검사해서, 범위를 벗어난 줄은 저장 대상에서 조용히 뺀다.
//   (시간표 그리드가 09:00~22:00 월~금이라 그 밖은 애초에 그릴 수가 없다)
// - 읽은 결과를 바로 저장하지 않는다. 화면에 "이렇게 읽었어요" 목록을 먼저 보여주고
//   사용자가 확인을 눌렀을 때 기존 수업 추가 API로 저장한다 — 잘못 읽은 걸 그냥 집어넣지 않기 위해.
// ############################################################################
@Service
public class TimetableOcrService {

    // 시간표 화면 그리드와 같은 범위(TimetableService 와 동일) — 이 밖의 값은 저장이 안 되므로 미리 걸러낸다.
    private static final int GRID_START_HOUR = 9;
    private static final int GRID_END_HOUR = 22;
    private static final int MAX_COURSES = 40;

    private final GeminiClient gemini;

    public TimetableOcrService(GeminiClient gemini) {
        this.gemini = gemini;
    }

    public boolean isEnabled() {
        return gemini.isEnabled();
    }

    public record ParsedCourse(String name, String info, Integer dayCol,
                               Integer startHour, Integer startMin, Integer endHour, Integer endMin) {}

    /** 읽어낸 수업들 + 범위를 벗어나 제외된 개수(화면에 그대로 안내한다). */
    public record OcrResult(List<ParsedCourse> courses, int droppedOutOfRange) {}

    private static final String SYSTEM_PROMPT = """
            너는 대학교 시간표 이미지를 읽어 수업 목록으로 바꿔주는 도우미다.
            학교 포털 시간표, 시간표 앱 캡처, 손으로 쓴 시간표, 사진 촬영본 모두 들어올 수 있다.

            규칙:
            - 이미지에 실제로 보이는 수업만 적는다. 안 보이는 건 추측해서 만들지 않는다.
            - name 은 과목명. 교수명/강의실/학점은 name 에 넣지 말고 info 에 넣는다.
            - info 는 강의실이나 교수명처럼 과목명 아래 작게 적힌 보조 정보. 없으면 null.
            - day 는 반드시 "월" "화" "수" "목" "금" 중 하나. 토/일 수업은 이 앱이 저장할 수 없으니 제외한다.
            - 시간은 24시간제 숫자로. 9시 30분 시작이면 startHour=9, startMin=30.
            - 한 과목이 요일마다 따로 적혀 있으면(월/수 같은 경우) 각각 별도 항목으로 적는다.
            - 블록이 두 칸 이상에 걸쳐 있으면 블록 전체의 시작~끝 시각을 쓴다.
            - 수업이 하나도 안 보이면 courses 를 빈 배열로 돌려준다. 억지로 채우지 않는다.
            """;

    /**
     * @param imageBytes 업로드된 이미지 원본
     * @param mimeType   image/png, image/jpeg 등
     */
    public OcrResult parse(byte[] imageBytes, String mimeType) {
        if (!isEnabled()) {
            throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");
        }

        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("courses", Map.of(
                        "type", "ARRAY",
                        "items", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "name", Map.of("type", "STRING", "description", "과목명"),
                                        "info", Map.of("type", "STRING", "nullable", true,
                                                "description", "강의실/교수명 등. 없으면 null"),
                                        "day", Map.of("type", "STRING", "description", "월/화/수/목/금 중 하나"),
                                        "startHour", Map.of("type", "INTEGER"),
                                        "startMin", Map.of("type", "INTEGER"),
                                        "endHour", Map.of("type", "INTEGER"),
                                        "endMin", Map.of("type", "INTEGER")
                                ),
                                "required", List.of("name", "day", "startHour", "startMin", "endHour", "endMin")))),
                "required", List.of("courses")
        );

        // 이미지 인식은 텍스트보다 오래 걸린다 — 타임아웃을 넉넉히 준다.
        JsonNode result = gemini.generateJson(
                SYSTEM_PROMPT,
                List.of(GeminiClient.imagePart(mimeType, imageBytes),
                        GeminiClient.textPart("이 시간표 이미지에 있는 수업을 전부 읽어줘.")),
                schema,
                Duration.ofSeconds(90));

        return validate(result.path("courses"));
    }

    // 모델이 돌려준 값을 전부 다시 검사한다. 저장 못 하는 값이 화면까지 올라가면
    // 사용자가 "추가하기"를 눌렀을 때 그 줄만 조용히 실패하므로, 여기서 미리 빼고 개수를 알려준다.
    private OcrResult validate(JsonNode courses) {
        List<ParsedCourse> ok = new ArrayList<>();
        int dropped = 0;

        for (JsonNode c : courses) {
            if (ok.size() >= MAX_COURSES) break;

            String name = c.path("name").asString("").trim();
            Integer dayCol = toDayCol(c.path("day").asString("").trim());
            Integer sh = intOrNull(c, "startHour"), sm = intOrNull(c, "startMin");
            Integer eh = intOrNull(c, "endHour"), em = intOrNull(c, "endMin");

            if (name.isEmpty() || dayCol == null || sh == null || sm == null || eh == null || em == null) {
                dropped++;
                continue;
            }
            int start = sh * 60 + sm, end = eh * 60 + em;
            if (end <= start || start < GRID_START_HOUR * 60 || end > GRID_END_HOUR * 60) {
                dropped++;
                continue;
            }

            String info = c.path("info").asString(null);
            ok.add(new ParsedCourse(
                    GeminiClient.trim(name, 100),
                    (info == null || info.isBlank()) ? null : GeminiClient.trim(info.trim(), 100),
                    dayCol, sh, sm, eh, em));
        }
        return new OcrResult(ok, dropped);
    }

    // 이 앱의 요일 코드: 2=월 ~ 6=금 (timetable JS / TimetableService 와 동일)
    private static Integer toDayCol(String day) {
        return switch (day) {
            case "월" -> 2;
            case "화" -> 3;
            case "수" -> 4;
            case "목" -> 5;
            case "금" -> 6;
            default -> null;
        };
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }
}
