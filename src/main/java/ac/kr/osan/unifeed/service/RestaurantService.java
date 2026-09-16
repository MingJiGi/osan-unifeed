package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public List<Restaurant> findAll() {
        return restaurantRepository.findAllByOrderByRatingDesc();
    }

    public List<Restaurant> findByCategory(String category) {
        if (category == null || category.isBlank() || "전체".equals(category)) {
            return findAll();
        }
        return restaurantRepository.findByCategoryOrderByRatingDesc(category);
    }

    public List<Restaurant> search(String keyword, String category) {
        boolean hasCategory = category != null && !category.isBlank() && !"전체".equals(category);
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasCategory && hasKeyword) {
            return restaurantRepository.searchByCategoryAndKeyword(category, keyword.trim());
        } else if (hasCategory) {
            return restaurantRepository.findByCategoryOrderByRatingDesc(category);
        } else if (hasKeyword) {
            return restaurantRepository.searchByKeyword(keyword.trim());
        } else {
            return findAll();
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
     * 카카오 장소 검색 등으로 찾은 식당을 DB에서 조회하거나 없으면 신규 저장(동기화)
     */
    @Transactional
    public Restaurant findOrCreate(String name, String category, String address, String phone,
                                  Double latitude, Double longitude, String description, String imgUrl) {
        if (address != null && !address.isBlank()) {
            var found = restaurantRepository.findByNameAndAddress(name, address);
            if (found.isPresent()) return found.get();
        }

        var foundByName = restaurantRepository.findByName(name);
        if (foundByName.isPresent()) return foundByName.get();

        String cat = (category != null && !category.isBlank()) ? category : "한식";
        String img = (imgUrl != null && !imgUrl.isBlank()) ? imgUrl : "/images/food1.jpg";
        String desc = (description != null && !description.isBlank()) ? description : "오산대 주변 맛집입니다.";

        Restaurant newRestaurant = new Restaurant(name, cat, address, phone, latitude, longitude, desc, img);
        return restaurantRepository.save(newRestaurant);
    }

    /**
     * 사용자가 직접 또는 검색을 통해 신규 맛집 등록
     */
    @Transactional
    public Restaurant createRestaurant(String name, String category, String address, String phone,
                                       Double latitude, Double longitude, String description, String imgUrl) {
        if (address != null && !address.isBlank()) {
            var found = restaurantRepository.findByNameAndAddress(name.trim(), address.trim());
            if (found.isPresent()) return found.get();
        }

        String cat = (category != null && !category.isBlank()) ? category.trim() : "한식";
        String img = (imgUrl != null && !imgUrl.isBlank()) ? imgUrl.trim() : "/images/food1.jpg";
        String desc = (description != null && !description.isBlank()) ? description.trim() : (name.trim() + " - 오산대 캠퍼스 추천 맛집입니다.");
        String tel = (phone != null && !phone.isBlank()) ? phone.trim() : "";

        Restaurant restaurant = new Restaurant(name.trim(), cat, address.trim(), tel, latitude, longitude, desc, img);
        return restaurantRepository.save(restaurant);
    }
}
