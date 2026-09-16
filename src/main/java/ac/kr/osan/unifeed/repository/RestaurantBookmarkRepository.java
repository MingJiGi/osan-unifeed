package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantBookmarkRepository extends JpaRepository<RestaurantBookmark, Long> {

    // 찜 여부 확인
    boolean existsByRestaurantAndMember(Restaurant restaurant, Member member);

    // 특정 회원의 특정 가게 찜 조회 (취소용)
    Optional<RestaurantBookmark> findByRestaurantAndMember(Restaurant restaurant, Member member);

    // 특정 회원이 저장(찜)한 맛집 목록 (최신 찜 순) -> 마이페이지 [저장한 맛집] 연동
    @Query("select b.restaurant from RestaurantBookmark b where b.member = :member order by b.createdAt desc")
    List<Restaurant> findBookmarkedRestaurantsByMember(@Param("member") Member member);
}
