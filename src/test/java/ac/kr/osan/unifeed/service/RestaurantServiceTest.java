package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Restaurant;
import ac.kr.osan.unifeed.repository.PostRepository;
import ac.kr.osan.unifeed.repository.RestaurantBookmarkRepository;
import ac.kr.osan.unifeed.repository.RestaurantRepository;
import ac.kr.osan.unifeed.repository.RestaurantReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock RestaurantReviewRepository reviewRepository;
    @Mock RestaurantBookmarkRepository bookmarkRepository;
    @Mock PostRepository postRepository;

    private RestaurantService newService() {
        return new RestaurantService(restaurantRepository, reviewRepository, bookmarkRepository, postRepository);
    }

    @Test
    void 맛집_삭제는_리뷰_찜_삭제_게시글_연결해제_맛집삭제_순서로_한다() {
        Restaurant r = new Restaurant("가게", "한식", "주소", "", 37.0, 127.0, "", null);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(r));

        newService().deleteRestaurant(1L);

        InOrder o = inOrder(reviewRepository, bookmarkRepository, postRepository, restaurantRepository);
        o.verify(reviewRepository).deleteByRestaurant(r);
        o.verify(bookmarkRepository).deleteByRestaurant(r);
        o.verify(postRepository).clearRestaurant(r);
        o.verify(restaurantRepository).delete(r);
        // 게시글 자체는 절대 지우지 않는다.
        verify(postRepository, never()).delete(any());
        verify(postRepository, never()).deleteAll();
    }

    @Test
    void 없는_맛집이면_아무것도_지우지_않고_예외() {
        when(restaurantRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().deleteRestaurant(9L)).isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(reviewRepository, bookmarkRepository, postRepository);
    }
}
