package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.RestaurantReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantReviewRepository extends JpaRepository<RestaurantReview, Long> {

    // 특정 맛집의 리뷰 목록 (작성자 정보 페치 조인, 최신순)
    @Query("select r from RestaurantReview r join fetch r.author where r.restaurant = :restaurant order by r.createdAt desc")
    List<RestaurantReview> findByRestaurantWithAuthor(@Param("restaurant") Restaurant restaurant);

    // 특정 회원이 작성한 맛집 리뷰 목록 (맛집 정보 페치 조인, 최신순) -> 마이페이지 [내 리뷰] 연동
    @Query("select r from RestaurantReview r join fetch r.restaurant where r.author = :author order by r.createdAt desc")
    List<RestaurantReview> findByAuthorWithRestaurant(@Param("author") Member author);

    // 맛집 평균 평점 계산
    @Query("select avg(r.rating) from RestaurantReview r where r.restaurant = :restaurant")
    Double calculateAverageRating(@Param("restaurant") Restaurant restaurant);
}
