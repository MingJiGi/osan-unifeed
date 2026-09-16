package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.RestaurantBookmarkService;
import ac.kr.osan.unifeed.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MapController.class)
class MapControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RestaurantService restaurantService;

    @MockitoBean
    RestaurantBookmarkService bookmarkService;

    @Test
    void 미로그인시_지도접근은_로그인으로_리다이렉트한다() throws Exception {
        mockMvc.perform(get("/map"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 로그인시_지도접근은_map뷰를_반환한다() throws Exception {
        Member loginMember = new Member("오산대학생", "osan@osan.ac.kr");
        when(restaurantService.search(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/map").sessionAttr("loginMember", loginMember))
                .andExpect(status().isOk())
                .andExpect(view().name("map/index"))
                .andExpect(model().attributeExists("restaurants", "kakaoAppKey", "loginMember"));
    }
}
