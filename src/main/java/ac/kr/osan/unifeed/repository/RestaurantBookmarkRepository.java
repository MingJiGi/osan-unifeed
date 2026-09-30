package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantBookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantBookmarkRepository extends JpaRepository<RestaurantBookmark, Long> {

    // 찜 여부 확인
    boolean existsByRestaurantAndMember(Restaurant restaurant, Member member);

    // 특정 회원의 특정 가게 찜 조회 (취소용)
    Optional<RestaurantBookmark> findByRestaurantAndMember(Restaurant restaurant, Member member);

    // 목록 화면용: 회원이 찜한 맛집 id 전부를 쿼리 1번으로 (맛집마다 찜 여부를 묻는 N+1 방지)
    @Query("select b.restaurant.id from RestaurantBookmark b where b.member = :member")
    java.util.Set<Long> findBookmarkedRestaurantIds(@Param("member") Member member);

    // 맛집 삭제 시 그 맛집의 찜 전부 삭제
    @Modifying
    @Query("delete from RestaurantBookmark b where b.restaurant = :restaurant")
    void deleteByRestaurant(@Param("restaurant") Restaurant restaurant);

    // 특정 회원이 저장(찜)한 맛집 목록 (최신 찜 순) -> 마이페이지 [저장한 맛집] 연동
    @Query("select b.restaurant from RestaurantBookmark b where b.member = :member order by b.createdAt desc")
    List<Restaurant> findBookmarkedRestaurantsByMember(@Param("member") Member member);
}
