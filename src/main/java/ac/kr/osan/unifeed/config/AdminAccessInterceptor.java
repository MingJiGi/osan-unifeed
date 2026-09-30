package ac.kr.osan.unifeed.config;

import ac.kr.osan.unifeed.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// ############################################################################
// AdminAccessInterceptor — /admin/** 전체에 공통으로 걸리는 관리자 권한 체크.
// ----------------------------------------------------------------------------
// AdminController의 메서드마다 isNotAdmin(session) 을 복붙하던 걸 여기 한 곳으로 모았다.
// 세션에 loginMember가 없거나 admin이 아니면 로그인 페이지로 보내고 컨트롤러 진입을 막는다.
// ############################################################################
@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Member loginMember = session != null ? (Member) session.getAttribute("loginMember") : null;
        if (loginMember == null || !loginMember.isAdmin()) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        return true;
    }
}
