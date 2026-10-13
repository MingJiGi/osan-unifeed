package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.University;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// ############################################################################
// AiRecommendService — "오늘 뭐 먹지?" 자유 입력 맛집 추천
// ----------------------------------------------------------------------------
// [하는 일]
// "비 와서 뜨끈한 거", "4명 회식인데 가성비", "혼밥하기 편한 곳" 같은 자유로운 문장을 받아
// 우리 학교에 등록된 맛집 중에서 2~3곳을 이유와 함께 골라준다.
//
// [설계에서 중요한 두 가지]
// 1) 환각(없는 식당 추천) 방지 — 모델에 "후보 목록"을 주고 그 안의 id 만 고르게 한 뒤,
//    돌아온 id 를 서버에서 다시 후보 집합과 대조해 걸러낸다. 모델이 아무리 그럴듯하게
//    "○○식당"을 지어내도 DB에 없으면 응답에 포함되지 않는다.
// 2) 없어도 서비스가 돌아가게 — API 키가 없거나(배포 서버 환경변수 미설정), 외부 HTTPS 가
//    막혀 있거나, 모델이 느리거나 실패해도 지도/목록 같은 핵심 기능은 전혀 영향받지 않는다.
//
// 실제 호출(HTTP/재시도/스키마)은 GeminiClient 가 맡는다.
// ############################################################################
@Service
public class AiRecommendService {

    // 한 번에 모델에 넘기는 후보 맛집 수 상한. 현재 등록된 맛집이 150곳 정도라 전부 들어가지만,
    // 나중에 수천 곳이 되어도 요청이 무한정 커지지 않게 막아둔다(평점 높은 순으로 잘림).
    private static final int MAX_CANDIDATES = 200;
    private static final int MAX_PICKS = 3;
    private static final int SITUATION_MAX_LENGTH = 200;

    private final RestaurantService restaurantService;
    private final GeminiClient gemini;

    public AiRecommendService(RestaurantService restaurantService, GeminiClient gemini) {
        this.restaurantService = restaurantService;
        this.gemini = gemini;
    }

    // 키가 없으면 기능 자체를 숨긴다(버튼도 안 보여준다). 화면에서 쓰려고 public.
    public boolean isEnabled() {
        return gemini.isEnabled();
    }

    public record Pick(Long restaurantId, String name, String reason) {}

    /**
     * @param situation  사용자가 적은 상황(자유 입력). 예: "비 와서 뜨끈한 국물"
     * @param gapContext 시간표에서 뽑은 공강 정보 한 줄(없으면 null)
     */
    public List<Pick> recommend(University university, String situation, String gapContext) {
        if (!isEnabled()) {
            throw new IllegalStateException("AI 추천 기능이 설정되지 않았습니다.");
        }
        if (situation == null || situation.isBlank()) {
            throw new IllegalArgumentException("어떤 걸 먹고 싶은지 적어주세요.");
        }
        if (situation.length() > SITUATION_MAX_LENGTH) {
            throw new IllegalArgumentException("상황 설명은 " + SITUATION_MAX_LENGTH + "자 이내로 적어주세요.");
        }

        List<Restaurant> candidates = restaurantService.findAll(university);
        if (candidates.size() > MAX_CANDIDATES) {
            candidates = candidates.subList(0, MAX_CANDIDATES);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("추천할 맛집이 아직 등록되지 않았습니다.");
        }

        Map<String, Object> schema = Map.of(
                "type", "OBJECT",
                "properties", Map.of("picks", Map.of(
                        "type", "ARRAY",
                        "items", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "id", Map.of("type", "INTEGER", "description", "후보 목록의 id"),
                                        "reason", Map.of("type", "STRING", "description", "추천 이유 한 문장")
                                ),
                                "required", List.of("id", "reason")))),
                "required", List.of("picks")
        );

        JsonNode result = gemini.generateJson(
                SYSTEM_PROMPT,
                List.of(GeminiClient.textPart(buildPrompt(situation.trim(), gapContext, candidates))),
                schema,
                Duration.ofSeconds(25));

        return validate(result.path("picks"), candidates);
    }

    // 후보 목록은 "id|이름|카테고리|평점|리뷰수|태그|소개" 한 줄짜리로 압축해서 넘긴다.
    // JSON 으로 넘기면 같은 정보에 토큰이 3배쯤 들어간다(괄호/따옴표/키 이름).
    private String buildPrompt(String situation, String gapContext, List<Restaurant> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 상황\n").append(situation).append('\n');
        if (gapContext != null && !gapContext.isBlank()) {
            sb.append("\n# 이 학생의 지금 시간표 상황\n").append(gapContext).append('\n');
        }
        sb.append("\n# 후보 맛집 (id|이름|카테고리|평점|리뷰수|태그|소개)\n");
        for (Restaurant r : candidates) {
            sb.append(r.getId()).append('|')
              .append(clean(r.getName())).append('|')
              .append(clean(r.getCategory())).append('|')
              .append(r.getRating() == null ? 0.0 : r.getRating()).append('|')
              .append(r.getReviewCount() == null ? 0 : r.getReviewCount()).append('|')
              .append(clean(r.getTags())).append('|')
              .append(GeminiClient.trim(clean(r.getDescription()), 60))
              .append('\n');
        }
        return sb.toString();
    }

    private static final String SYSTEM_PROMPT = """
            너는 오산대학교 학생들이 쓰는 캠퍼스 맛집 앱 '유니피드'의 맛집 추천 도우미다.

            규칙:
            - 반드시 아래 '후보 맛집' 목록에 있는 곳만 고른다. 목록에 없는 식당은 절대 만들어내지 않는다.
            - 상황에 가장 잘 맞는 곳을 2곳 또는 3곳 고른다. 잘 맞는 곳이 1곳뿐이면 1곳만 골라도 된다.
            - reason 은 "왜 이 상황에 이 집인지"를 한 문장(50자 이내)으로, 학생에게 말하듯 친근한 한국어로 쓴다.
            - 평점과 리뷰수는 참고만 한다. 상황에 맞는 카테고리/태그가 평점보다 중요하다.
            - 공강 시간이 짧다고 알려주면 오래 걸리는 식사류보다 빨리 먹을 수 있는 쪽을 우선한다.
            - reason 은 후보 줄에 적힌 정보(이름/카테고리/태그/소개)만 근거로 쓴다. 거기 없는 것
              (가격, 영업시간, 좌석 형태, 분위기, 인기)은 추측해서 단정하지 않는다.
              나쁜 예: "바 테이블이 있어서 혼밥하기 좋아요"(좌석은 알 수 없는 정보)
            """;

    // 모델이 돌려준 id 를 후보 집합과 대조한다 — 여기가 환각 방어의 핵심.
    // 이름 같은 표시용 정보는 모델 응답이 아니라 DB의 후보 객체에서 가져온다.
    private List<Pick> validate(JsonNode picks, List<Restaurant> candidates) {
        Map<Long, Restaurant> byId = new HashMap<>();
        for (Restaurant r : candidates) {
            byId.put(r.getId(), r);
        }

        List<Pick> result = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (JsonNode pick : picks) {
            if (result.size() >= MAX_PICKS) break;
            Restaurant restaurant = byId.get(pick.path("id").asLong(0));
            if (restaurant == null || !seen.add(restaurant.getId())) {
                continue; // 후보에 없는 id(환각) 또는 같은 곳 중복 추천은 버린다.
            }
            result.add(new Pick(restaurant.getId(), restaurant.getName(),
                    GeminiClient.trim(pick.path("reason").asString("").trim(), 120)));
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("조건에 맞는 맛집을 찾지 못했습니다. 다르게 적어보세요.");
        }
        return result;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').trim();
    }
}
