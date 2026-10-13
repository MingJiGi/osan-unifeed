package ac.kr.osan.unifeed.controller;

import ac.kr.osan.unifeed.service.PostService;
import ac.kr.osan.unifeed.controller.PostController;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// ############################################################################
// PostController 테스트 — 화면(View) 계층만 떼어 검증한다 (DB 없이)
// ----------------------------------------------------------------------------
// @WebMvcTest : 컨트롤러 + MVC 인프라만 로드한다. 서비스는 가짜(@MockitoBean)로 갈아끼운다.
//               "어떤 URL 이 어떤 뷰를 반환하고, 모델에 무엇을 담는가" 를 빠르게 확인한다.
// (Boot 4 에서는 @MockBean 대신 @MockitoBean 을 쓴다)
// ############################################################################
@WebMvcTest(PostController.class)
class PostControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PostService postService;

	@MockitoBean
	MemberService memberService;

	@MockitoBean
	ac.kr.osan.unifeed.service.CommentService commentService;

	@MockitoBean
	ac.kr.osan.unifeed.service.UniversityService universityService;

	// 컨트롤러가 직접 쓰진 않지만, 모든 화면에 전역으로 걸리는 CsrfModelAdvice(GNB 신고 배지)가
	// 의존하는 서비스라 슬라이스 컨텍스트에도 있어야 컨텍스트가 뜬다.
	@MockitoBean
	ac.kr.osan.unifeed.service.ReportService reportService;

    // 전역 CsrfModelAdvice(GNB "학교 신청" 배지)가 쓰는 서비스 — 슬라이스 컨텍스트에도 있어야 한다.
    @MockitoBean
    ac.kr.osan.unifeed.service.UniversityRequestService universityRequestService;
	// 컨트롤러가 도배 방지로 쓰는 RateLimiter — 슬라이스 컨텍스트에는 없어서 가짜로 넣는다.
	@MockitoBean ac.kr.osan.unifeed.config.RateLimiter rateLimiter;

	@org.junit.jupiter.api.BeforeEach
	void 기본은_제한에_안_걸리게() {
	    org.mockito.BDDMockito.given(rateLimiter.allow(org.mockito.ArgumentMatchers.anyString(),
	            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt())).willReturn(true);
	}

	@Test
	void 목록요청은_로그인시_list뷰와_posts모델을_반환한다() throws Exception {
		Member loginMember = new Member("김철수", "kim@test.com");
		// 목록이 무한스크롤로 바뀌면서 컨트롤러는 findAll(university)가 아니라
		// findAll(university, pageable) -> Slice 를 쓴다. 스텁도 그쪽을 맞춰야 한다.
		when(postService.findAll(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
				.thenReturn(new org.springframework.data.domain.SliceImpl<>(List.of()));

		mockMvc.perform(get("/posts").sessionAttr("loginMember", loginMember))
				.andExpect(status().isOk())
				.andExpect(view().name("posts/list"))
				.andExpect(model().attributeExists("posts"));
	}

	@Test
	void 미로그인시_목록요청은_로그인으로_리다이렉트한다() throws Exception {
		mockMvc.perform(get("/posts"))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
	}

	@Test
	void 미로그인시_상세요청은_로그인으로_리다이렉트한다() throws Exception {
		mockMvc.perform(get("/posts/1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
	}

	@Test
	void 새글폼은_로그인시_글작성모달로_리다이렉트한다() throws Exception {
		Member loginMember = new Member("김철수", "kim@test.com");

		mockMvc.perform(get("/posts/new").sessionAttr("loginMember", loginMember))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/posts?write=true"));
	}

	@Test
	void 미로그인_새글폼은_로그인으로_리다이렉트한다() throws Exception {
		mockMvc.perform(get("/posts/new"))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/login"));
	}

	@Test
	void 제목이_비면_저장이_안되고_폼을_다시_보여준다() throws Exception {
		Member loginMember = new Member("김철수", "kim@test.com");
		when(postService.findAll(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
		when(memberService.findAll()).thenReturn(List.of());

		mockMvc.perform(post("/posts")
						.sessionAttr("loginMember", loginMember)
						.sessionAttr("csrfToken", "test-token")
						.param("_csrf", "test-token")
						.param("title", "")
						.param("content", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("posts/list"))
				.andExpect(model().attributeHasFieldErrors("postForm", "title", "content"));
	}

	@Test
	void 정상저장은_목록으로_리다이렉트한다() throws Exception {
		Member loginMember = new Member("김철수", "kim@test.com");
		when(postService.create(org.mockito.ArgumentMatchers.any())).thenReturn(7L);

		mockMvc.perform(post("/posts")
						.sessionAttr("loginMember", loginMember)
						.sessionAttr("csrfToken", "test-token")
						.param("_csrf", "test-token")
						.param("title", "제목")
						.param("content", "내용"))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/posts"));
	}
}
