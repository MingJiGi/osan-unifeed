package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import ac.kr.osan.unifeed.repository.RestaurantReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class RestaurantReviewService {

    private final RestaurantReviewRepository reviewRepository;
    private final RestaurantRepository restaurantRepository;

    public RestaurantReviewService(RestaurantReviewRepository reviewRepository,
                                   RestaurantRepository restaurantRepository) {
        this.reviewRepository = reviewRepository;
        this.restaurantRepository = restaurantRepository;
    }

    // 맛집별 리뷰 목록 (작성자 포함)
    public List<RestaurantReview> findByRestaurant(Restaurant restaurant) {
        return reviewRepository.findByRestaurantWithAuthor(restaurant);
    }

    // 특정 회원이 작성한 리뷰 목록 (맛집 포함) -> 마이페이지 연동
    public List<RestaurantReview> findByAuthor(Member author) {
        if (author == null) return List.of();
        return reviewRepository.findByAuthorWithRestaurant(author);
    }

    // 리뷰 등록 및 평점 자동 갱신
    @Transactional
    public RestaurantReview createReview(Long restaurantId, Member author, Integer rating, String content, String imgUrl, boolean useAsCover) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("리뷰 내용을 입력해주세요.");
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NoSuchElementException("맛집을 찾을 수 없습니다. id=" + restaurantId));

        RestaurantReview review = new RestaurantReview(content.trim(), rating, restaurant, author);
        if (imgUrl != null && !imgUrl.isBlank()) {
            review.setImgUrl(imgUrl);
            // 작성자가 "대표사진으로 사용" 동의했고, 맛집에 아직 대표 사진이 없으면 이 리뷰 사진을 대표 사진으로 승격.
            // 동의 없이는 절대 승격하지 않는다 — 리뷰 사진은 무관한 사진/얼굴 사진일 수 있어 자동 노출은 위험함.
            if (useAsCover && (restaurant.getImgUrl() == null || restaurant.getImgUrl().isBlank())) {
                restaurant.setImgUrl(imgUrl);
            }
        }
        RestaurantReview saved = reviewRepository.save(review);

        // 평점 및 리뷰 수 갱신
        restaurant.increaseReviewCount();
        Double avgRating = reviewRepository.calculateAverageRating(restaurant);
        if (avgRating != null) {
            restaurant.setRating(avgRating);
        }

        return saved;
    }

    // 리뷰 수정 (본인 리뷰만) + 평점 재계산
    @Transactional
    public RestaurantReview updateReview(Long reviewId, Member author, Integer rating, String content, String imgUrl, boolean useAsCover) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("리뷰 내용을 입력해주세요.");
        }
        RestaurantReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException("리뷰를 찾을 수 없습니다. id=" + reviewId));

        if (!review.getAuthor().getId().equals(author.getId())) {
            throw new IllegalStateException("본인이 작성한 리뷰만 수정할 수 있습니다.");
        }

        review.update(content.trim(), rating, imgUrl);

        Restaurant restaurant = review.getRestaurant();
        if (useAsCover && imgUrl != null && !imgUrl.isBlank()
                && (restaurant.getImgUrl() == null || restaurant.getImgUrl().isBlank())) {
            restaurant.setImgUrl(imgUrl);
        }
        Double avgRating = reviewRepository.calculateAverageRating(restaurant);
        if (avgRating != null) {
            restaurant.setRating(avgRating);
        }

        return review;
    }

    // 리뷰 삭제 — 관리자는 신고 없이도 직접 삭제 가능
    @Transactional
    public void deleteReview(Long reviewId, Member author) {
        RestaurantReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NoSuchElementException("리뷰를 찾을 수 없습니다. id=" + reviewId));

        if (!review.getAuthor().getId().equals(author.getId()) && !author.isAdmin()) {
            throw new IllegalStateException("본인이 작성한 리뷰만 삭제할 수 있습니다.");
        }

        deleteReviewInternal(review);
    }

    // 관리자용 — 작성자 본인 확인 없이 강제 삭제(신고 처리).
    @Transactional
    public void adminDelete(Long reviewId) {
        reviewRepository.findById(reviewId).ifPresent(this::deleteReviewInternal);
    }

    // 신고 누적 자동 숨김/해제 — 삭제와 달리 리뷰 자체는 남지만 목록·평점 계산에서 빠진다(제외되니 재계산 필요).
    @Transactional
    public void hideReview(Long reviewId) {
        reviewRepository.findById(reviewId).ifPresent(review -> {
            if (review.isHidden()) return;
            review.hide();
            review.getRestaurant().decreaseReviewCount();
            recalculateRating(review.getRestaurant());
        });
    }

    @Transactional
    public void unhideReview(Long reviewId) {
        reviewRepository.findById(reviewId).ifPresent(review -> {
            if (!review.isHidden()) return;
            review.unhide();
            review.getRestaurant().increaseReviewCount();
            recalculateRating(review.getRestaurant());
        });
    }

    private void recalculateRating(Restaurant restaurant) {
        Double avgRating = reviewRepository.calculateAverageRating(restaurant);
        restaurant.setRating(avgRating != null ? avgRating : 0.0);
    }

    private void deleteReviewInternal(RestaurantReview review) {
        Restaurant restaurant = review.getRestaurant();
        // 이미 신고 누적으로 자동 숨김된 리뷰는 hideReview()에서 reviewCount를 이미 한 번 뺐으므로
        // 삭제할 때 또 빼면 이중 차감된다 — 숨김 상태가 아니었을 때만 뺀다.
        boolean wasHidden = review.isHidden();
        reviewRepository.delete(review);

        if (!wasHidden) {
            restaurant.decreaseReviewCount();
        }
        Double avgRating = reviewRepository.calculateAverageRating(restaurant);
        restaurant.setRating(avgRating != null ? avgRating : 0.0);
    }
}
