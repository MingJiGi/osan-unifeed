package ac.kr.osan.unifeed.controller.api;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantReviewService;
import ac.kr.osan.unifeed.service.RestaurantService;
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

    public RestaurantApiController(RestaurantService restaurantService,
                                   RestaurantReviewService reviewService,
                                   RestaurantBookmarkService bookmarkService) {
        this.restaurantService = restaurantService;
        this.reviewService = reviewService;
        this.bookmarkService = bookmarkService;
    }

    // 맛집 목록 조회 (카테고리/검색 필터)
    @GetMapping
    public ResponseEntity<?> getRestaurants(@RequestParam(required = false) String category,
                                            @RequestParam(required = false) String keyword,
                                            HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }
        List<Restaurant> list = restaurantService.search(keyword, category);

        List<RestaurantDto> dtos = list.stream().map(r -> {
            boolean bookmarked = bookmarkService.isBookmarked(r.getId(), loginMember);
            return RestaurantDto.from(r, bookmarked);
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    // 신규 맛집 등록
    @PostMapping
    public ResponseEntity<?> registerRestaurant(@RequestBody RestaurantCreateRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
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

        Restaurant restaurant = restaurantService.createRestaurant(
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
        boolean bookmarked = bookmarkService.isBookmarked(id, loginMember);

        List<RestaurantReview> reviews = reviewService.findByRestaurant(restaurant);
        List<ReviewDto> reviewDtos = reviews.stream().map(ReviewDto::from).toList();

        return ResponseEntity.ok(Map.of(
                "restaurant", RestaurantDto.from(restaurant, bookmarked),
                "reviews", reviewDtos
        ));
    }

    // 리뷰 작성
    @PostMapping("/{id}/reviews")
    public ResponseEntity<?> createReview(@PathVariable Long id,
                                          @RequestBody ReviewCreateRequest request,
                                          HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "리뷰 내용을 입력해주세요."));
        }

        RestaurantReview review = reviewService.createReview(id, loginMember, request.rating(), request.content(), request.imgUrl(),
                Boolean.TRUE.equals(request.useAsCover()));
        Restaurant restaurant = restaurantService.findById(id);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "review", ReviewDto.from(review),
                "restaurant", RestaurantDto.from(restaurant, bookmarkService.isBookmarked(id, loginMember))
        ));
    }

    // 리뷰 수정 (본인 리뷰만)
    @PutMapping("/reviews/{reviewId}")
    public ResponseEntity<?> updateReview(@PathVariable Long reviewId,
                                          @RequestBody ReviewCreateRequest request,
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
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "review", ReviewDto.from(review, loginMember),
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

    // 찜 토글
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<?> toggleBookmark(@PathVariable Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        boolean bookmarked = bookmarkService.toggleBookmark(id, loginMember);
        Restaurant restaurant = restaurantService.findById(id);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "bookmarked", bookmarked,
                "bookmarkCount", restaurant.getBookmarkCount()
        ));
    }

    // 카카오 장소 검색 등 외부 식당 DB 동기화 및 상세 정보/리뷰 조회
    @PostMapping("/sync")
    public ResponseEntity<?> syncRestaurant(@RequestBody RestaurantSyncRequest request, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요한 기능입니다."));
        }

        Restaurant restaurant = restaurantService.findOrCreate(
                request.name(),
                request.category(),
                request.address(),
                request.phone(),
                request.latitude(),
                request.longitude(),
                request.description(),
                request.imgUrl()
        );

        boolean bookmarked = (loginMember != null) && bookmarkService.isBookmarked(restaurant.getId(), loginMember);
        List<RestaurantReview> reviews = reviewService.findByRestaurant(restaurant);
        List<ReviewDto> reviewDtos = reviews.stream().map(ReviewDto::from).toList();

        return ResponseEntity.ok(Map.of(
                "restaurant", RestaurantDto.from(restaurant, bookmarked),
                "reviews", reviewDtos
        ));
    }

    // DTO records
    public record RestaurantCreateRequest(
            String name,
            String category,
            String address,
            String phone,
            Double latitude,
            Double longitude,
            String description,
            String imgUrl,
            String tags
    ) {}

    public record RestaurantSyncRequest(
            String name,
            String category,
            String address,
            String phone,
            Double latitude,
            Double longitude,
            String description,
            String imgUrl
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

    public record ReviewCreateRequest(Integer rating, String content, String imgUrl, Boolean useAsCover) {}

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

        public static ReviewDto from(RestaurantReview r) {
            return from(r, r.getAuthor());
        }

        // author를 밖에서 넘겨받는 버전 — updateReview처럼 review.getAuthor()가
        // 지연로딩 프록시라 트랜잭션 밖에서 못 건드릴 때, 이미 알고 있는(로그인한 본인) Member로 대신 채운다.
        public static ReviewDto from(RestaurantReview r, Member author) {
            return new ReviewDto(
                    r.getId(),
                    author.getId(),
                    author.getName(),
                    author.getProfileImage(),
                    r.getRating(),
                    r.getContent(),
                    r.getImgUrl(),
                    r.getCreatedAt().format(FORMATTER)
            );
        }
    }
}
