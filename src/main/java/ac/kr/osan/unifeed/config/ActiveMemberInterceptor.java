package ac.kr.osan.unifeed.config;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.NoSuchElementException;

// ############################################################################
// ActiveMemberInterceptor — 세션에 박혀있는 회원 정보를 DB 기준으로 다시 확인한다.
// ----------------------------------------------------------------------------
// 이 앱은 로그인 시점의 Member 객체를 HttpSession에 통째로 넣고 계속 재사용한다(앱 전체의 기존 패턴).
// 그래서 정지(suspended)/관리자 권한 같은 상태가 로그인 이후에 바뀌어도 그 세션에는 반영되지 않아,
//   - 관리자가 악성 사용자를 정지시켜도 그 사람 세션이 살아있는 동안은 계속 글/댓글을 쓸 수 있고
//   - 관리자 권한을 회수해도 세션의 낡은 admin=true 로 /admin/** 을 계속 통과하는
// 문제가 있었다. 여기서 DB를 다시 읽어 막고, 세션 복사본도 최신으로 갈아끼운다.
//
// [비용] DB가 원격이라 쿼리 1번이 네트워크 왕복 1번이다. 그래서 매 요청이 아니라
//        "상태를 바꾸는 요청(POST/PUT/DELETE)"과 "/admin/**" 에서만 검사한다.
//        단순 조회(GET)는 정지된 계정이 잠깐 더 볼 수 있을 뿐이라 피해가 없고, 어차피
//        다음 쓰기 시도나 재로그인에서 막힌다.
// ############################################################################
@Component
public class ActiveMemberInterceptor implements HandlerInterceptor {

    private final MemberService memberService;

    public ActiveMemberInterceptor(MemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        Member sessionMember = session != null ? (Member) session.getAttribute("loginMember") : null;
        if (sessionMember == null || !needsRecheck(request)) {
            return true;
        }
        // id가 없으면 DB에서 조회할 방법이 없다. 실제 로그인은 항상 저장된 회원을 세션에 넣으므로
        // 운영 중엔 생기지 않는 상태이고(슬라이스 테스트의 가짜 Member 등), 공격자가 만들 수 있는
        // 상황도 아니라서 그대로 통과시킨다 — 각 컨트롤러의 기존 권한 검사는 그대로 동작한다.
        if (sessionMember.getId() == null) {
            return true;
        }

        Member fresh;
        try {
            fresh = memberService.findById(sessionMember.getId());
        } catch (NoSuchElementException e) {
            // 탈퇴/삭제된 계정의 세션이 남아있는 경우
            return reject(request, response, session, "계정을 찾을 수 없습니다. 다시 로그인해주세요.");
        }
        // 운영에서는 findById가 없으면 예외를 던지므로 null이 올 수 없다. 조회가 불가능한 경우
        // (슬라이스 테스트의 가짜 서비스 등)까지 500으로 죽이지 않도록 그대로 통과시킨다.
        if (fresh == null) {
            return true;
        }

        if (fresh.isSuspended()) {
            return reject(request, response, session, "정지된 계정입니다. 문의가 필요하면 관리자에게 연락해주세요.");
        }

        // 권한/프로필 등 최신 상태로 세션 복사본 갱신 — 뒤따르는 AdminAccessInterceptor가 이 값을 본다.
        session.setAttribute("loginMember", fresh);
        return true;
    }

    private boolean needsRecheck(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/admin/")) {
            return true;
        }
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    // 세션을 끊고, API 요청이면 JSON 401을, 화면 요청이면 로그인 페이지로 보낸다.
    private boolean reject(HttpServletRequest request, HttpServletResponse response, HttpSession session, String message)
            throws Exception {
        session.invalidate();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"" + message + "\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/login");
        }
        return false;
    }
}
