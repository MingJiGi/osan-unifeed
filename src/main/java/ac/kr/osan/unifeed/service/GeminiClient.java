package ac.kr.osan.unifeed.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// ############################################################################
// GeminiClient — Google Gemini 호출을 한 곳에 모아둔 공용 클라이언트
// ----------------------------------------------------------------------------
// 이 앱에서 AI를 쓰는 곳이 세 군데(맛집 추천 / 맛집 소개·태그 생성 / 시간표 사진 인식)인데,
// 세 곳 모두 "JSON 스키마를 주고 그 모양의 JSON만 받는다"는 똑같은 패턴이라 여기로 합쳤다.
// 각 기능은 '시스템 프롬프트 + 보낼 내용(텍스트/이미지) + 받을 JSON 모양'만 정하면 된다.
//
// 공통으로 처리하는 것:
//   - API 키 유무(없으면 기능만 꺼짐), 타임아웃, 일시 오류(429/500/503) 재시도
//   - 응답 껍데기(candidates[0].content.parts[0].text) 벗기고 JSON 파싱까지
//   - 실패는 모두 IllegalStateException(사용자에게 보여줄 한국어 메시지)으로 통일
//
// ponytail: 별도 SDK 의존성 없이 표준 java.net.http.HttpClient + 이미 있는 Jackson 으로 끝낸다.
// ############################################################################
@Component
public class GeminiClient {

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public GeminiClient(ObjectMapper objectMapper,
                        @Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-3.1-flash-lite}") String model) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    // 키가 없으면 AI 기능을 아예 화면에 띄우지 않는다.
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    // 보낼 내용 만들기용 짧은 도움 메서드 — 호출하는 쪽이 Map 을 직접 조립하지 않게.
    public static Map<String, Object> textPart(String text) {
        return Map.of("text", text);
    }

    public static Map<String, Object> imagePart(String mimeType, byte[] bytes) {
        return Map.of("inlineData", Map.of(
                "mimeType", mimeType,
                "data", java.util.Base64.getEncoder().encodeToString(bytes)));
    }

    /**
     * 모델에 JSON 스키마를 지정해 호출하고, 응답 JSON(모델이 만든 본문)을 파싱해서 돌려준다.
     *
     * @param systemPrompt   역할/규칙
     * @param userParts      보낼 내용 (textPart/imagePart 로 만든 것들)
     * @param responseSchema 받고 싶은 JSON 모양 (Gemini responseSchema 형식)
     * @param timeout        응답 대기 한도
     */
    public JsonNode generateJson(String systemPrompt, List<Map<String, Object>> userParts,
                                 Map<String, Object> responseSchema, Duration timeout) {
        if (!isEnabled()) {
            throw new IllegalStateException("AI 기능이 설정되지 않았습니다.");
        }

        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", userParts)),
                "generationConfig", Map.of(
                        // 작업 난이도에 비해 모델이 너무 오래 "생각"해서 응답이 17초까지 걸렸다.
                        // LOW 로 내리면 2~4초로 줄고 결과 품질 차이는 체감되지 않았다(실측).
                        "thinkingConfig", Map.of("thinkingLevel", "LOW"),
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema
                )
        );

        String rawJson = send(body, timeout);
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception e) {
            System.err.println("Gemini 응답 JSON 파싱 실패: " + trim(rawJson, 300));
            throw new IllegalStateException("AI 응답을 이해하지 못했습니다. 잠시 후 다시 시도해주세요.", e);
        }
    }

    // 실제 HTTP 호출 + 응답 껍데기 벗기기. 돌려주는 건 "모델이 만든 JSON 문자열".
    private String send(Map<String, Object> body, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + model + ":generateContent"))
                    // 키는 URL 쿼리스트링이 아니라 헤더로 보낸다 — URL 은 중간 서버 액세스 로그에 그대로 남는다.
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            // 503(모델 과부하)/429(쿼터)는 실제로 종종 난다. 맛집 152곳 일괄 보강을 돌려보니
            // 1회 재시도로는 부족해서(연속 503으로 배치 하나가 통째로 날아갔다) 2회까지 늘리고,
            // 두 번째는 더 길게 기다린다.
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            for (int attempt = 1; attempt <= 2 && isRetryable(response.statusCode()); attempt++) {
                System.err.println("Gemini 일시 오류 status=" + response.statusCode() + " — 재시도 " + attempt);
                Thread.sleep(attempt * 1500L);
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() != 200) {
                // 외부 서비스 오류 원문(영어 + 내부 정보)은 사용자에게 보여주지 않고 서버 로그에만 남긴다.
                System.err.println("Gemini 응답 오류 status=" + response.statusCode()
                        + " body=" + trim(response.body(), 500));
                throw new IllegalStateException("AI 응답을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            }

            JsonNode text = objectMapper.readTree(response.body())
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isString()) {
                // 안전필터로 막힌 경우(finishReason=SAFETY) 등 — parts 에 text 가 아예 없다.
                System.err.println("Gemini 응답에 본문이 없음: " + trim(response.body(), 500));
                throw new IllegalStateException("AI가 응답을 만들지 못했습니다. 다르게 시도해주세요.");
            }
            return text.asString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 응답이 중단되었습니다. 다시 시도해주세요.", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // 네트워크 차단(방화벽), 타임아웃 모두 여기로 — 사용자에겐 같은 메시지.
            System.err.println("Gemini 호출 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            throw new IllegalStateException("AI 응답을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.", e);
        }
    }

    private static boolean isRetryable(int status) {
        return status == 429 || status == 500 || status == 503;
    }

    static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
