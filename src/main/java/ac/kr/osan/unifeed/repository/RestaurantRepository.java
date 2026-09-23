package ac.kr.osan.unifeed.repository;

import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 맛집은 전부 소속 대학교(university) 기준으로 나뉜다(멀티캠퍼스) — 학교가 없는(null) 목록은
// 서비스 계층에서 빈 리스트로 처리하므로, 여기 쿼리들은 university가 있는 경우만 다룬다.
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // 학교별 전체 목록 (평점 높은 순)
    List<Restaurant> findByUniversityOrderByRatingDesc(University university);

    // 학교별 + 카테고리별 목록
    List<Restaurant> findByUniversityAndCategoryOrderByRatingDesc(University university, String category);

    // 학교별 검색어 (가게명 또는 주소/설명 포함)
    @Query("select r from Restaurant r where r.university = :university and (lower(r.name) like lower(concat('%', :kw, '%')) or lower(r.address) like lower(concat('%', :kw, '%')) or lower(r.description) like lower(concat('%', :kw, '%'))) order by r.rating desc")
    List<Restaurant> searchByKeyword(@Param("university") University university, @Param("kw") String keyword);

    // 학교별 카테고리 + 검색어
    @Query("select r from Restaurant r where r.university = :university and r.category = :cat and (lower(r.name) like lower(concat('%', :kw, '%')) or lower(r.address) like lower(concat('%', :kw, '%'))) order by r.rating desc")
    List<Restaurant> searchByCategoryAndKeyword(@Param("university") University university, @Param("cat") String category, @Param("kw") String keyword);

    // 식당 이름 및 주소로 단건 조회 (동기화용) — 학교가 다르면 같은 상호여도 다른 맛집으로 취급한다.
    java.util.Optional<Restaurant> findByUniversityAndNameAndAddress(University university, String name, String address);

    java.util.Optional<Restaurant> findByUniversityAndName(University university, String name);
}
