package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantReviewRepository extends JpaRepository<RestaurantReview, Long> {

    // 특정 맛집의 리뷰 목록 (작성자 정보 페치 조인, 최신순) — hidden(신고 누적 자동 숨김)은 제외.
    @Query("select r from RestaurantReview r join fetch r.author where r.restaurant = :restaurant and r.hidden = false order by r.createdAt desc")
    List<RestaurantReview> findByRestaurantWithAuthor(@Param("restaurant") Restaurant restaurant);

    // 특정 회원이 작성한 맛집 리뷰 목록 (맛집 정보 페치 조인, 최신순) -> 마이페이지 [내 리뷰] 연동
    // 본인이 자기 리뷰를 보는 화면이라 hidden이어도 그대로 보여준다(숨김 필터 없음, 의도적).
    @Query("select r from RestaurantReview r join fetch r.restaurant where r.author = :author order by r.createdAt desc")
    List<RestaurantReview> findByAuthorWithRestaurant(@Param("author") Member author);

    // 맛집 삭제 시 그 맛집의 리뷰 전부 삭제(hidden 포함)
    @Modifying
    @Query("delete from RestaurantReview r where r.restaurant = :restaurant")
    void deleteByRestaurant(@Param("restaurant") Restaurant restaurant);

    // 맛집 평균 평점 계산 — hidden 리뷰는 평점에도 반영 안 됨.
    @Query("select avg(r.rating) from RestaurantReview r where r.restaurant = :restaurant and r.hidden = false")
    Double calculateAverageRating(@Param("restaurant") Restaurant restaurant);
}
