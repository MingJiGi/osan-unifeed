package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ############################################################################
// RestaurantEnrichService — 맛집 소개글/태그 비어 있는 곳을 AI로 채운다 (관리자 1회성 작업)
// ----------------------------------------------------------------------------
// [왜 필요했나]
// 카카오 검색으로 넣은 맛집 152곳 전부 소개글과 태그가 비어 있었다. 그래서
//   - 지도 카드가 "이름 + 카테고리 + 별점" 뿐이라 비어 보이고
//   - AI 맛집 추천("오늘 뭐 먹지?")이 후보를 고를 때 참고할 정보가 이름밖에 없었다
// 태그/소개가 채워지면 추천 품질이 바로 올라간다.
//
// [지어내지 않게 하는 장치 — 이게 이 클래스의 핵심]
// 모델은 이 가게를 실제로 모른다. 그래서 가격·영업시간·맛·유명도처럼 "가게에 대한 사실"을
// 쓰게 하면 그건 그냥 거짓 정보가 된다. 그래서:
//   1) 가게 이름이 이미 말해주는 것(= 무슨 음식을 파는 집인지)만 쓰게 한다.
//   2) 이름만 봐선 무슨 집인지 모르겠으면 null 을 돌려주게 하고, 그 칸은 빈 채로 둔다.
//      (schema.sql 에도 적혀 있는 이 프로젝트의 원칙 — "추측값을 절대 넣지 않는다")
//   3) 사람이 직접 적어둔 소개/태그는 절대 덮지 않는다(Restaurant.fillEmptyAiHints).
// ############################################################################
@Service
public class RestaurantEnrichService {

    // 한 번에 보내는 맛집 수. 25로 돌렸더니 배치 하나가 60초 타임아웃에 걸려 통째로 날아갔다.
    // 15면 한 배치가 15~25초로 안정적이고, 실패해도 손실이 작다.
    private static final int BATCH_SIZE = 15;

    private final RestaurantRepository restaurantRepository;
    private final GeminiClient gemini;

    public RestaurantEnrichService(RestaurantRepository restaurantRepository, GeminiClient gemini) {
        this.restaurantRepository = restaurantRepository;
        this.gemini = gemini;
    }

    public record EnrichResult(int targets, int descriptionFilled, int tagsFilled,
                               int leftBlank, List<String> failedBatches) {}

    private static final String SYSTEM_PROMPT = """
            너는 대학가 맛집 앱의 데이터 정리 도우미다. 가게 이름/카테고리/주소만 보고
            '검색과 추천에 쓸 짧은 설명과 키워드'를 만든다.

            [절대 규칙 — 모르는 건 null]
            - 너는 이 가게에 가본 적이 없다. 가격, 영업시간, 메뉴 구성, 맛, 분위기, 인기/유명도,
              좌석, 주차 같은 "가게에 대한 사실"은 절대 쓰지 않는다. 틀리면 사용자를 속이는 정보다.
            - 가게 이름만으로 무슨 음식을 파는 곳인지 알 수 없으면 description 과 tags 를 둘 다
              null 로 둔다. 억지로 채우지 말 것. (예: "미소", "그집" 처럼 이름에 음식 단서가 없는 경우)

            [description]
            - 이름이 이미 알려주는 '무슨 음식을 파는 곳인지'만 한 줄로 쓴다. 25자 이내.
            - 좋은 예: "뼈해장국과 선지국밥을 내는 해장국 전문점" / "직접 뽑은 면으로 끓이는 칼국수집"
            - 나쁜 예: "가성비 최고의 맛집" (평가) / "점심시간 줄 서는 곳" (사실 주장) / "저렴한 가격" (가격)
            - 홍보 문구처럼 쓰지 않는다. 담백하게 음식 종류만.

            [tags]
            - 쉼표로 구분한 2~5개 키워드. 음식 종류와 '어떤 상황에 떠올릴 음식인지'까지만.
            - 쓸 수 있는 예: 국물요리, 해장, 면요리, 고기, 튀김, 분식, 매운맛, 디저트, 커피,
              아침식사, 야식, 간단한끼, 든든한끼, 여럿이먹기
            - 쓰면 안 되는 예: 가성비, 저렴, 맛집, 인기, 깔끔, 친절, 넓은좌석, 주차가능 (확인 불가)
            """;

    /**
     * 소개글 또는 태그가 비어 있는 맛집을 모아 AI로 채운다. 이미 채워진 칸은 건드리지 않는다.
     */
    @Transactional
    public EnrichResult enrichEmpty() {
        if (!gemini.isEnabled()) {
            throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");
        }

        List<Restaurant> targets = restaurantRepository.findAll().stream()
                .filter(r -> isBlank(r.getDescription()) || isBlank(r.getTags()))
                .toList();

        int descFilled = 0, tagFilled = 0;
        List<String> failedBatches = new ArrayList<>();

        for (int from = 0; from < targets.size(); from += BATCH_SIZE) {
            List<Restaurant> batch = targets.subList(from, Math.min(from + BATCH_SIZE, targets.size()));
            Map<Long, Restaurant> byId = new HashMap<>();
            for (Restaurant r : batch) byId.put(r.getId(), r);

            JsonNode items;
            try {
                items = gemini.generateJson(SYSTEM_PROMPT, List.of(GeminiClient.textPart(buildPrompt(batch))),
                        schema(), Duration.ofSeconds(90)).path("items");
            } catch (RuntimeException e) {
                // 배치 하나가 실패해도 나머지는 계속 처리한다 — 152곳을 처음부터 다시 돌릴 이유가 없다.
                failedBatches.add("id " + batch.get(0).getId() + "~" + batch.get(batch.size() - 1).getId()
                        + ": " + e.getMessage());
                continue;
            }

            for (JsonNode item : items) {
                Restaurant r = byId.get(item.path("id").asLong(0));
                if (r == null) continue; // 후보에 없는 id 는 버린다(모델이 만들어낸 id).

                boolean descWasBlank = isBlank(r.getDescription());
                boolean tagWasBlank = isBlank(r.getTags());
                r.fillEmptyAiHints(nullIfBlank(item.path("description").asString(null)),
                        normalizeTags(item.path("tags").asString(null)));
                if (descWasBlank && !isBlank(r.getDescription())) descFilled++;
                if (tagWasBlank && !isBlank(r.getTags())) tagFilled++;
            }
        }

        int skipped = 0;
        for (Restaurant r : targets) {
            if (isBlank(r.getDescription()) && isBlank(r.getTags())) skipped++;
        }
        return new EnrichResult(targets.size(), descFilled, tagFilled, skipped, failedBatches);
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of("items", Map.of(
                        "type", "ARRAY",
                        "items", Map.of(
                                "type", "OBJECT",
                                "properties", Map.of(
                                        "id", Map.of("type", "INTEGER", "description", "입력으로 받은 가게 id"),
                                        "description", Map.of("type", "STRING", "nullable", true,
                                                "description", "무슨 음식을 파는 곳인지 25자 이내. 모르면 null"),
                                        "tags", Map.of("type", "STRING", "nullable", true,
                                                "description", "쉼표로 구분한 2~5개 키워드. 모르면 null")
                                ),
                                "required", List.of("id")))),
                "required", List.of("items")
        );
    }

    private String buildPrompt(List<Restaurant> batch) {
        StringBuilder sb = new StringBuilder("# 정리할 가게 (id|이름|카테고리|주소)\n");
        for (Restaurant r : batch) {
            sb.append(r.getId()).append('|')
              .append(clean(r.getName())).append('|')
              .append(clean(r.getCategory())).append('|')
              .append(clean(r.getAddress())).append('\n');
        }
        sb.append("\n입력으로 받은 id 전부에 대해 한 줄씩 결과를 돌려줘. 모르겠으면 null 로 두면 된다.\n");
        return sb.toString();
    }

    // "국물요리, 해장, 아침식사" -> "국물요리,해장,아침식사" (최대 5개)
    private static String normalizeTags(String raw) {
        if (isBlank(raw)) return null;
        List<String> parts = new ArrayList<>();
        for (String t : raw.split(",")) {
            String s = t.trim();
            if (!s.isEmpty() && !parts.contains(s) && parts.size() < 5) parts.add(s);
        }
        return parts.isEmpty() ? null : String.join(",", parts);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullIfBlank(String s) {
        return isBlank(s) ? null : s;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ').trim();
    }
}
