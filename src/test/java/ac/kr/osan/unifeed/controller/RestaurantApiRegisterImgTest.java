package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.controller.api.RestaurantApiController;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantReviewService;
import ac.kr.osan.unifeed.service.RestaurantService;
import ac.kr.osan.unifeed.service.UniversityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 맛집 등록 시 대표 사진(imgUrl)은 /uploads/<파일명> 만 허용
@WebMvcTest(RestaurantApiController.class)
class RestaurantApiRegisterImgTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RestaurantService restaurantService;
    @MockitoBean RestaurantReviewService reviewService;
    @MockitoBean RestaurantBookmarkService bookmarkService;
    @MockitoBean UniversityService universityService;
    @MockitoBean ac.kr.osan.unifeed.service.ReportService reportService;

    private String body(String img) {
        return "{\"name\":\"ZZ-x\",\"address\":\"a\",\"latitude\":37.1,\"longitude\":127.0,\"imgUrl\":" + img + "}";
    }

    private org.springframework.test.web.servlet.ResultActions register(String img) throws Exception {
        return mockMvc.perform(post("/api/restaurants").contentType(MediaType.APPLICATION_JSON)
                .content(body(img)).sessionAttr("loginMember", new Member("학생", "s@osan.ac.kr")));
    }

    @Test
    void 외부_URL과_경로조작은_400() throws Exception {
        register("\"https://evil.example/x.png\"").andExpect(status().isBadRequest());
        register("\"/uploads/../etc/passwd\"").andExpect(status().isBadRequest());
        verifyNoInteractions(restaurantService);
    }

    @Test
    void 업로드경로_또는_없음은_통과() throws Exception {
        when(restaurantService.createRestaurant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ac.kr.osan.unifeed.entity.Restaurant("ZZ-x", "한식", "a", "", 37.1, 127.0, "", null));
        when(restaurantService.createRestaurant(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ac.kr.osan.unifeed.entity.Restaurant("ZZ-x", "한식", "a", "", 37.1, 127.0, "", null));
        register("\"/uploads/abc-123.png\"").andExpect(status().isOk());
        register("null").andExpect(status().isOk());
    }
}
