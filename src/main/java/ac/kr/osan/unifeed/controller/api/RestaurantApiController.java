package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantReviewService;
import ac.kr.osan.unifeed.service.RestaurantService;
import ac.kr.osan.unifeed.service.UniversityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantApiController {

    private final RestaurantService restaurantService;
    private final RestaurantReviewService reviewService;
    private final RestaurantBookmarkService bookmarkService;
    private final UniversityService universityService; // 관리자의 학교 전환(뷰) 지원용

    private final ac.kr.osan.unifeed.config.RateLimiter rateLimiter; // 도배 방지

    public RestaurantApiController(RestaurantService restaurantService,
                                   RestaurantReviewService reviewService,
                                   RestaurantBookmarkService bookmarkService,
                                   UniversityService universityService,
                                   ac.kr.osan.unifeed.config.RateLimiter rateLimiter) {
        this.restaurantService = restaurantService;
        this.reviewService = reviewService;
        this.bookmarkService = bookmarkService;
        this.universityService = universityService;
        this.rateLimiter = rateLimiter;
    }

    // 쓰기 요청 도배 방지용 공통 응답 — 넘으면 429.
    private ResponseEntity<?> tooManyRequests() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("message", "요청이 너무 잦습니다. 잠시 후 다시 시도해주세요."));
    }

    // /map?viewUniversity=ID 로 관리자가 전환한 학교 컨텍스트를, 사이드바 목록을 채우는 이 API 호출까지
    // 그대로 이어받는다 — 안 그러면 지도 페이지 자체는 전환됐는데 맛집 목록만 관리자 본인 학교로 남는다.
    private University resolveViewUniversity(Member loginMember, Long viewUniversity) {
        if (viewUniversity == null || !loginMember.isAdmin()) {
            return loginMember.getUniversity();
        }
        University chosen = universityService.findById(viewUniversity);
        return chosen != null ? chosen : loginMember.getUniversity();
    }

    // 목록(search())은 학교별로 걸러서 보여주지만, ID로 직접 접근하는 상세/리뷰작성은 그것만으로
    // 막히지 않는다 — 다른 학교 맛집 ID를 알면 그대로 열람·리뷰 작성이 가능했던 문제를 여기서 막는다.
    private boolean isSameUniversity(Restaurant restaurant, Member member) {
        return restaurant.getUniversity() != null && member.getUniversity() != null
                && restaurant.getUniversity().getId().equals(member.getUniversity().getId());
    }

    // 방금 만들거나 수정한 리뷰 하나의 익명 번호 — 그 맛집의 전체 리뷰 목록 기준으로 계산한다
    // (프론트는 사실 이 값을 안 쓰고 전체 목록을 다시 불러오지만, 응답에 실명이 남지 않게 방어적으로 계산).
    private String anonLabelFor(Restaurant restaurant, Long reviewId) {
        List<RestaurantReview> reviews = reviewService.findByRestaurant(restaurant);
        return ReviewDto.anonymizeList(reviews).stream()
                .filter(dto -> dto.id().equals(reviewId))
                .findFirst()
                .map(ReviewDto::authorName)
                .orElse("익명");
    }

    // 맛집 목록 조회 (카테고리/검색 필터)
    @GetMapping
    public ResponseEntity<?> getRestaurants(@RequestParam(required = false) String category,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long viewUniversity,
                                            HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        List<Restaurant> list = restaurantService.search(resolveViewUniversity(loginMember, viewUniversity), keyword, category);

        var bookmarkedIds = bookmarkService.bookmarkedIds(loginMember);
        List<RestaurantDto> dtos = list.stream()
                .map(r -> RestaurantDto.from(r, bookmarkedIds.contains(r.getId())))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    // 신규 맛집 등록
    @PostMapping
    public ResponseEntity<?> registerRestaurant(@jakarta.validation.Valid @RequestBody RestaurantCreateRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        // 도배 방지 — 한 계정이 10분에 10곳까지 등록 가능.
        if (!rateLimiter.allow("restaurant:" + loginMember.getId(), 10, 600)) {
            return tooManyRequests();
        }

        if (request.name() == null || request.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "식당 이름을 입력해주세요."));
        }
        if (request.address() == null || request.address().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "식당 주소를 입력해주세요."));
        }
        if (request.latitude() == null || request.longitude() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "식당 위치(좌표)를 지정해주세요."));
        }

        // 대표 사진은 우리 업로드 API가 돌려준 /uploads/<파일명> 값만 허용 (임의 외부 URL 저장 금지)
        if (request.imgUrl() != null && !request.imgUrl().isBlank()
                && !request.imgUrl().trim().matches("^/uploads/[A-Za-z0-9._-]+$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "대표 사진은 사진 업로드로만 등록할 수 있습니다."));
        }

        Restaurant restaurant = restaurantService.createRestaurant(
                loginMember.getUniversity(),
                request.name(),
                request.category(),
                request.address(),
                request.phone(),
                request.latitude(),
                request.longitude(),
                request.description(),
                request.imgUrl(),
                request.tags()
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "새로운 맛집이 등록되었습니다!",
                "restaurant", RestaurantDto.from(restaurant, false)
        ));
    }

    // 맛집 단건 상세 + 리뷰 목록 + 찜 여부
    @GetMapping("/{id}")
    public ResponseEntity<?> getRestaurantDetail(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        Restaurant restaurant = restaurantService.findById(id);
        // 관리자는 신고 처리 등 운영 목적으로 다른 학교 맛집도 열람만은 할 수 있어야 한다
        // (리뷰작성/찜 같은 직접 참여는 여전히 본인 학교로 제한).
        if (!isSameUniversity(restaurant, loginMember) && !loginMember.isAdmin()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "존재하지 않는 맛집입니다."));
        }
        boolean bookmarked = bookmarkService.isBookmarked(id, loginMember);

        List<RestaurantReview> reviews = reviewService.findByRestaurant(restaurant);
        List<ReviewDto> reviewDtos = ReviewDto.anonymizeList(reviews);

        return ResponseEntity.ok(Map.of(
                "restaurant", RestaurantDto.from(restaurant, bookmarked),
                "reviews", reviewDtos
        ));
    }

    // 리뷰 작성
    @PostMapping("/{id}/reviews")
    public ResponseEntity<?> createReview(@PathVariable Long id,
                                          @jakarta.validation.Valid @RequestBody ReviewCreateRequest request,
                                          HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        // 도배 방지 — 한 계정이 10분에 리뷰 10개까지.
        if (!rateLimiter.allow("review:" + loginMember.getId(), 10, 600)) {
            return tooManyRequests();
        }

        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "리뷰 내용을 입력해주세요."));
        }
        try {
            if (!isSameUniversity(restaurantService.findById(id), loginMember)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "존재하지 않는 맛집입니다."));
            }

            RestaurantReview review = reviewService.createReview(id, loginMember, request.rating(), request.content(), request.imgUrl(),
                    Boolean.TRUE.equals(request.useAsCover()));
            Restaurant restaurant = restaurantService.findById(id);
            String anonLabel = anonLabelFor(restaurant, review.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "review", ReviewDto.from(review, loginMember, anonLabel),
                    "restaurant", RestaurantDto.from(restaurant, bookmarkService.isBookmarked(id, loginMember))
            ));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "존재하지 않는 맛집입니다."));
        }
    }

    // 리뷰 수정 (본인 리뷰만)
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<?> updateReview(@PathVariable Long reviewId,
                                          @jakarta.validation.Valid @RequestBody ReviewCreateRequest request,
                                          HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        try {
            RestaurantReview review = reviewService.updateReview(reviewId, loginMember, request.rating(), request.content(), request.imgUrl(),
                    Boolean.TRUE.equals(request.useAsCover()));
            // review.getRestaurant()는 지연로딩 프록시라 트랜잭션 밖에서 안전하게 쓰려고 id로 다시 조회한다(createReview와 동일 패턴).
            Restaurant restaurant = restaurantService.findById(review.getRestaurant().getId());
            String anonLabel = anonLabelFor(restaurant, review.getId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "review", ReviewDto.from(review, loginMember, anonLabel),
                    "restaurant", RestaurantDto.from(restaurant, bookmarkService.isBookmarked(restaurant.getId(), loginMember))
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    // 리뷰 삭제 (본인 리뷰만)
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<?> deleteReview(@PathVariable Long reviewId, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        try {
            reviewService.deleteReview(reviewId, loginMember);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    // 맛집 삭제 (관리자 전용 — 다른 학교 맛집도 가능)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRestaurant(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        if (!loginMember.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "관리자만 맛집을 삭제할 수 있습니다."));
        }
        try {
            restaurantService.deleteRestaurant(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "맛집이 삭제되었습니다."));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "존재하지 않는 맛집입니다."));
        }
    }

    // 찜 토글
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<?> toggleBookmark(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        try {
            Restaurant restaurant = restaurantService.findById(id);
            if (!isSameUniversity(restaurant, loginMember)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "존재하지 않는 맛집입니다."));
            }

            boolean bookmarked = bookmarkService.toggleBookmark(id, loginMember);

            // 찜 토글은 자기 트랜잭션 안에서 찜 수를 바꾸는데, 위에서 읽어둔 restaurant 인스턴스는
            // 그 전 트랜잭션에서 온 detached 객체라 변경이 반영돼 있지 않다(= 옛날 숫자).
            // 그대로 내려주면 화면의 "저장됨 (N)" 숫자가 안 올라간다 — 다시 읽어서 최신 값을 준다.
            int bookmarkCount = restaurantService.findById(id).getBookmarkCount();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "bookmarked", bookmarked,
                    "bookmarkCount", bookmarkCount
            ));
        } catch (java.util.NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "존재하지 않는 맛집입니다."));
        }
    }

    // DTO records
    // @Size 값은 전부 엔티티(=DB 컬럼) 길이와 맞춘 것이다. 검증이 없으면 긴 값이 그대로 DB까지 가서
    // "Data too long" 오류로 터지고 사용자에겐 500이 보인다(실제로 재현됨).
    public record RestaurantCreateRequest(
            @jakarta.validation.constraints.Size(max = 100, message = "식당 이름은 100자 이내여야 합니다.") String name,
            @jakarta.validation.constraints.Size(max = 50, message = "카테고리는 50자 이내여야 합니다.") String category,
            @jakarta.validation.constraints.Size(max = 200, message = "주소는 200자 이내여야 합니다.") String address,
            @jakarta.validation.constraints.Size(max = 50, message = "전화번호는 50자 이내여야 합니다.") String phone,
            Double latitude,
            Double longitude,
            @jakarta.validation.constraints.Size(max = 500, message = "소개는 500자 이내여야 합니다.") String description,
            @jakarta.validation.constraints.Size(max = 500, message = "이미지 주소가 너무 깁니다.") String imgUrl,
            @jakarta.validation.constraints.Size(max = 300, message = "태그는 300자 이내여야 합니다.") String tags
    ) {}

    public record RestaurantDto(
            Long id,
            String name,
            String category,
            String address,
            String phone,
            Double latitude,
            Double longitude,
            String description,
            String imgUrl,
            String tags,
            Double rating,
            Integer reviewCount,
            Integer bookmarkCount,
            boolean isBookmarked
    ) {
        public static RestaurantDto from(Restaurant r, boolean bookmarked) {
            return new RestaurantDto(
                    r.getId(),
                    r.getName(),
                    r.getCategory(),
                    r.getAddress(),
                    r.getPhone(),
                    r.getLatitude(),
                    r.getLongitude(),
                    r.getDescription(),
                    r.getImgUrl(),
                    r.getTags(),
                    r.getRating(),
                    r.getReviewCount(),
                    r.getBookmarkCount(),
                    bookmarked
            );
        }
    }

    public record ReviewCreateRequest(
            Integer rating,
            @jakarta.validation.constraints.Size(max = 500, message = "리뷰는 500자 이내여야 합니다.") String content,
            @jakarta.validation.constraints.Size(max = 500, message = "이미지 주소가 너무 깁니다.") String imgUrl,
            Boolean useAsCover) {}

    // 리뷰 작성자는 익명 처리(맛집별 리뷰 목록 안에서만 통하는 "익명1" 식 번호) — 실명/프로필사진은
    // 절대 내려주지 않는다. authorId는 "내 리뷰 수정/삭제 버튼 보여주기" 용도로만 프론트에서 쓰인다.
    public record ReviewDto(
            Long id,
            Long authorId,
            String authorName,
            String authorProfileImage,
            Integer rating,
            String content,
            String imgUrl,
            String createdAt
    ) {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

        public static ReviewDto from(RestaurantReview r, String anonLabel) {
            return from(r, r.getAuthor(), anonLabel);
        }

        // author를 밖에서 넘겨받는 버전 — updateReview처럼 review.getAuthor()가
        // 지연로딩 프록시라 트랜잭션 밖에서 못 건드릴 때, 이미 알고 있는(로그인한 본인) Member로 대신 채운다.
        public static ReviewDto from(RestaurantReview r, Member author, String anonLabel) {
            return new ReviewDto(
                    r.getId(),
                    author.getId(),
                    anonLabel,
                    null,
                    r.getRating(),
                    r.getContent(),
                    r.getImgUrl(),
                    r.getCreatedAt().format(FORMATTER)
            );
        }

        // 리뷰 목록 하나를 그 목록 안에서만 통하는 익명 번호로 한 번에 변환한다.
        // 화면에는 최신순으로 보여주지만(reviews 원래 순서 그대로 유지), 번호를 매기는 기준은
        // 반드시 "작성 시각이 오래된 순"이어야 한다 — 최신순 그대로 번호를 매기면 새 리뷰가 달릴
        // 때마다 기존 사람들 번호가 통째로 밀려서(방금 쓴 사람이 항상 "익명1") 같은 사람이
        // 볼 때마다 다른 번호로 보이는 버그가 생긴다(라이브 테스트로 실제 재현하고 잡음).
        public static List<ReviewDto> anonymizeList(List<RestaurantReview> reviews) {
            List<Long> chronologicalAuthorIds = reviews.stream()
                    .sorted(java.util.Comparator.comparing(RestaurantReview::getCreatedAt))
                    .map(r -> r.getAuthor().getId())
                    .toList();
            Map<Long, String> labels = ac.kr.osan.unifeed.util.AnonymousNaming.assignLabels(chronologicalAuthorIds);
            return reviews.stream().map(r -> from(r, labels.get(r.getAuthor().getId()))).toList();
        }
    }
}
