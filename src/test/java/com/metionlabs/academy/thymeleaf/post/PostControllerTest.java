package com.metionlabs.academy.thymeleaf.post;

import com.metionlabs.academy.thymeleaf.member.Member;
import com.metionlabs.academy.thymeleaf.member.MemberService;
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

	@Test
	void 목록요청은_list뷰와_posts모델을_반환한다() throws Exception {
		when(postService.findAll()).thenReturn(List.of());

		mockMvc.perform(get("/posts"))
				.andExpect(status().isOk())
				.andExpect(view().name("posts/list"))
				.andExpect(model().attributeExists("posts"));
	}

	@Test
	void 새글폼은_members드롭다운을_함께_준다() throws Exception {
		when(memberService.findAll()).thenReturn(List.of(new Member("김철수", "kim@test.com")));

		mockMvc.perform(get("/posts/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("posts/form"))
				.andExpect(model().attributeExists("postForm", "members"));
	}

	@Test
	void 제목이_비면_저장이_안되고_폼을_다시_보여준다() throws Exception {
		// title/content/authorId 없이 제출 → 검증 실패 → 폼으로 되돌아온다(redirect 아님)
		when(memberService.findAll()).thenReturn(List.of());

		mockMvc.perform(post("/posts")
						.param("title", "")
						.param("content", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("posts/form"))
				.andExpect(model().attributeHasFieldErrors("postForm", "title", "content", "authorId"));
	}

	@Test
	void 정상저장은_상세로_리다이렉트한다() throws Exception {
		when(postService.create(org.mockito.ArgumentMatchers.any())).thenReturn(7L);

		mockMvc.perform(post("/posts")
						.param("authorId", "1")
						.param("title", "제목")
						.param("content", "내용"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("/posts/*"));
	}
}
