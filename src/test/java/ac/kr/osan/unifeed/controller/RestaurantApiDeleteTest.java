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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RestaurantApiController.class)
class RestaurantApiDeleteTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RestaurantService restaurantService;
    @MockitoBean RestaurantReviewService reviewService;
    @MockitoBean RestaurantBookmarkService bookmarkService;
    @MockitoBean UniversityService universityService;
    @MockitoBean ac.kr.osan.unifeed.service.ReportService reportService;
    @MockitoBean ac.kr.osan.unifeed.service.MemberService memberService;
    // 전역 CsrfModelAdvice(GNB "학교 신청" 배지)가 쓰는 서비스.
    @MockitoBean ac.kr.osan.unifeed.service.UniversityRequestService universityRequestService;
    // 컨트롤러가 도배 방지로 쓰는 RateLimiter — 슬라이스 컨텍스트에는 없어서 가짜로 넣는다.
    @MockitoBean ac.kr.osan.unifeed.config.RateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void 기본은_제한에_안_걸리게() {
        org.mockito.BDDMockito.given(rateLimiter.allow(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).willReturn(true);
    }

    private Member admin() {
        Member m = mock(Member.class);
        when(m.isAdmin()).thenReturn(true);
        return m;
    }

    @Test
    void 비로그인은_401() throws Exception {
        mockMvc.perform(delete("/api/restaurants/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(restaurantService);
    }

    @Test
    void 비관리자는_403이고_삭제되지_않는다() throws Exception {
        Member user = new Member("학생", "s@osan.ac.kr");
        mockMvc.perform(delete("/api/restaurants/1").sessionAttr("loginMember", user))
                .andExpect(status().isForbidden());
        verifyNoInteractions(restaurantService);
    }

    @Test
    void 없는_id는_404() throws Exception {
        doThrow(new NoSuchElementException("x")).when(restaurantService).deleteRestaurant(9L);
        mockMvc.perform(delete("/api/restaurants/9").sessionAttr("loginMember", admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자는_삭제_성공() throws Exception {
        mockMvc.perform(delete("/api/restaurants/1").sessionAttr("loginMember", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(restaurantService).deleteRestaurant(1L);
    }
}
