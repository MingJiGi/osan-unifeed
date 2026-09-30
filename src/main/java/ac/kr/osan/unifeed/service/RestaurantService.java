package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.repository.PostRepository;
import ac.kr.osan.unifeed.repository.RestaurantBookmarkRepository;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import ac.kr.osan.unifeed.repository.RestaurantReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantReviewRepository reviewRepository;
    private final RestaurantBookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             RestaurantReviewRepository reviewRepository,
                             RestaurantBookmarkRepository bookmarkRepository,
                             PostRepository postRepository) {
        this.restaurantRepository = restaurantRepository;
        this.reviewRepository = reviewRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.postRepository = postRepository;
    }

    /**
     * 관리자 맛집 삭제 — 리뷰/찜 삭제, 게시글의 맛집 태그 해제(글은 유지), 맛집 삭제를 한 트랜잭션으로.
     * DB FK(CASCADE/SET NULL)에만 기대지 않고 명시적으로 지운다. 대표사진 파일(uploads/)은 건드리지 않는다.
     */
    @Transactional
    public void deleteRestaurant(Long id) {
        Restaurant restaurant = findById(id);
        reviewRepository.deleteByRestaurant(restaurant);
        bookmarkRepository.deleteByRestaurant(restaurant);
        postRepository.clearRestaurant(restaurant);
        restaurantRepository.delete(restaurant);
    }

    // 아직 소속 대학교를 안 정한 회원(university == null)은 빈 목록 — 데이터가 없을 때 그럴듯한
    // 추측으로 채우지 않고 "없으면 표시 안 함"을 기본으로 하는 이 프로젝트의 기존 원칙과 동일.
    public List<Restaurant> findAll(University university) {
        if (university == null) return List.of();
        return restaurantRepository.findByUniversityOrderByRatingDesc(university);
    }

    public List<Restaurant> findByCategory(University university, String category) {
        if (university == null) return List.of();
        if (category == null || category.isBlank() || "전체".equals(category)) {
            return findAll(university);
        }
        return restaurantRepository.findByUniversityAndCategoryOrderByRatingDesc(university, category);
    }

    public List<Restaurant> search(University university, String keyword, String category) {
        if (university == null) return List.of();
        boolean hasCategory = category != null && !category.isBlank() && !"전체".equals(category);
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasCategory && hasKeyword) {
            return restaurantRepository.searchByCategoryAndKeyword(university, category, keyword.trim());
        } else if (hasCategory) {
            return restaurantRepository.findByUniversityAndCategoryOrderByRatingDesc(university, category);
        } else if (hasKeyword) {
            return restaurantRepository.searchByKeyword(university, keyword.trim());
        } else {
            return findAll(university);
        }
    }

    public Restaurant findById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("맛집을 찾을 수 없습니다. id=" + id));
    }

    @Transactional
    public Restaurant save(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }

    public long count() {
        return restaurantRepository.count();
    }

    /**
     * 사용자가 직접 또는 검색을 통해 신규 맛집 등록
     */
    @Transactional
    public Restaurant createRestaurant(University university, String name, String category, String address, String phone,
                                       Double latitude, Double longitude, String description, String imgUrl,
                                       String tags) {
        if (address != null && !address.isBlank()) {
            var found = restaurantRepository.findByUniversityAndNameAndAddress(university, name.trim(), address.trim());
            if (found.isPresent()) return found.get();
        }

        String cat = (category != null && !category.isBlank()) ? category.trim() : "한식";
        String img = (imgUrl != null && !imgUrl.isBlank()) ? imgUrl.trim() : null;
        String desc = (description != null && !description.isBlank()) ? description.trim() : "";
        String tel = (phone != null && !phone.isBlank()) ? phone.trim() : "";
        String tagStr = (tags != null && !tags.isBlank()) ? tags.trim() : null;

        Restaurant restaurant = new Restaurant(name.trim(), cat, address.trim(), tel, latitude, longitude, desc, img, tagStr);
        restaurant.setUniversity(university);
        return restaurantRepository.save(restaurant);
    }

    /**
     * 기존 DB에 자동 생성되었던 불필요한 기본 소개 문구("식당명 - 오산대 캠퍼스 맛집...") 일괄 정리
     */
    @jakarta.annotation.PostConstruct
    @Transactional
    public void cleanUpAutoDescriptions() {
        try {
            List<Restaurant> all = restaurantRepository.findAll();
            for (Restaurant r : all) {
                if (r.getDescription() != null && (
                        r.getDescription().contains("오산대 캠퍼스 맛집") ||
                        r.getDescription().contains("오산대 주변 맛집") ||
                        r.getDescription().contains("오산대 캠퍼스 추천 맛집")
                )) {
                    r.setDescription("");
                    restaurantRepository.save(r);
                }
            }
        } catch (Exception e) {
            // 초기화 도중 예외 발생 시 서비스 기동에 영향 없도록 로깅
            System.err.println("기존 맛집 소개글 정리 중 예외 발생: " + e.getMessage());
        }
    }
}
