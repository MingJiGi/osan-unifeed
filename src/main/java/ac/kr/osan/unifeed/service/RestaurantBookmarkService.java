package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantBookmark;
import ac.kr.osan.unifeed.repository.RestaurantBookmarkRepository;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class RestaurantBookmarkService {

    private final RestaurantBookmarkRepository bookmarkRepository;
    private final RestaurantRepository restaurantRepository;

    public RestaurantBookmarkService(RestaurantBookmarkRepository bookmarkRepository,
                                     RestaurantRepository restaurantRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.restaurantRepository = restaurantRepository;
    }

    // 찜 토글 (찜 등록 / 취소)
    @Transactional
    public boolean toggleBookmark(Long restaurantId, Member member) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NoSuchElementException("맛집을 찾을 수 없습니다. id=" + restaurantId));

        return bookmarkRepository.findByRestaurantAndMember(restaurant, member)
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    restaurant.decreaseBookmarkCount();
                    return false; // 찜 취소
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(new RestaurantBookmark(restaurant, member));
                    restaurant.increaseBookmarkCount();
                    return true; // 찜 등록
                });
    }

    // 목록용: 회원이 찜한 맛집 id 집합(쿼리 1번)
    public java.util.Set<Long> bookmarkedIds(Member member) {
        return member == null ? java.util.Set.of() : bookmarkRepository.findBookmarkedRestaurantIds(member);
    }

    // 찜 여부 확인
    public boolean isBookmarked(Long restaurantId, Member member) {
        if (member == null) return false;
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NoSuchElementException("맛집을 찾을 수 없습니다. id=" + restaurantId));
        return bookmarkRepository.existsByRestaurantAndMember(restaurant, member);
    }

    // 회원이 찜(저장)한 맛집 목록 조회 -> 마이페이지 연동
    public List<Restaurant> findBookmarkedRestaurants(Member member) {
        if (member == null) return List.of();
        return bookmarkRepository.findBookmarkedRestaurantsByMember(member);
    }
}
