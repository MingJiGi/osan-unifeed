package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // 전체 목록 (평점 높은 순)
    List<Restaurant> findAllByOrderByRatingDesc();

    // 카테고리별 목록
    List<Restaurant> findByCategoryOrderByRatingDesc(String category);

    // 검색어 (가게명 또는 주소/설명 포함)
    @Query("select r from Restaurant r where lower(r.name) like lower(concat('%', :kw, '%')) or lower(r.address) like lower(concat('%', :kw, '%')) or lower(r.description) like lower(concat('%', :kw, '%')) order by r.rating desc")
    List<Restaurant> searchByKeyword(@Param("kw") String keyword);

    // 카테고리 + 검색어
    @Query("select r from Restaurant r where r.category = :cat and (lower(r.name) like lower(concat('%', :kw, '%')) or lower(r.address) like lower(concat('%', :kw, '%'))) order by r.rating desc")
    List<Restaurant> searchByCategoryAndKeyword(@Param("cat") String category, @Param("kw") String keyword);

    // 식당 이름 및 주소로 단건 조회 (동기화용)
    java.util.Optional<Restaurant> findByNameAndAddress(String name, String address);

    java.util.Optional<Restaurant> findByName(String name);
}
