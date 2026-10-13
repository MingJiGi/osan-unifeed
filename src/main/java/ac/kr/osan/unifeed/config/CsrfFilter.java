package ac.kr.osan.unifeed.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.io.IOException;

// ############################################################################
// CsrfFilter — 전통적인 HTML 폼(POST) 제출에 대한 CSRF 방어(synchronizer token 패턴).
// ----------------------------------------------------------------------------
// 세션에 저장된 토큰과 폼이 같이 보낸 _csrf 파라미터가 일치해야 요청을 통과시킨다.
// /api/** 는 스코프에서 제외 — 전부 fetch()의 application/json body로만 호출되는
// REST 엔드포인트라서, 이 앱에 CORS 허용 설정이 전혀 없는 이상 다른 사이트에서
// 자격증명을 포함한 JSON 요청을 위조해서 보낼 수 없다(브라우저가 preflight로 차단).
// ############################################################################
@Component
public class CsrfFilter implements Filter {

    private static final String TOKEN_PARAM = "_csrf";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (needsCheck(request)) {
            HttpSession session = request.getSession(false);
            String sessionToken = session != null ? (String) session.getAttribute(CsrfModelAdvice.SESSION_KEY) : null;
            String requestToken = request.getParameter(TOKEN_PARAM);

            if (sessionToken == null || !sessionToken.equals(requestToken)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "잘못된 요청입니다. 페이지를 새로고침한 뒤 다시 시도해주세요.");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private boolean needsCheck(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) {
            return true; // 일반 HTML 폼은 항상 검사
        }

        // /api/** 중에서도 "JSON 본문을 받는" 요청만 면제다 — 그건 브라우저가 preflight(OPTIONS)로
        // 막아주기 때문. 반면 본문이 없는 POST(찜 토글/알림 읽음)와 multipart(이미지 업로드)는
        // preflight 없이 교차 출처로 보낼 수 있는 "단순 요청"이라, 다른 사이트가 로그인된 피해자
        // 명의로 호출할 수 있다. 그래서 이 둘은 폼과 똑같이 _csrf 토큰을 요구한다.
        String contentType = request.getContentType();
        return contentType == null || !contentType.toLowerCase().startsWith("application/json");
    }
}
