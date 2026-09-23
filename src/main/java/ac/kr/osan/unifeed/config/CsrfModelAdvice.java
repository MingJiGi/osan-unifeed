package ac.kr.osan.unifeed.config;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.UUID;

// ############################################################################
// CsrfModelAdvice — 모든 화면(Model)에 CSRF 토큰을 자동으로 심어준다.
// ----------------------------------------------------------------------------
// 세션당 토큰 1개(없으면 새로 발급, 있으면 재사용) — 컨트롤러를 일일이 안 건드려도
// 모든 th:action POST 폼에서 ${csrfToken}을 그대로 쓸 수 있게 해준다.
// 실제 검증은 CsrfFilter가 담당.
// ############################################################################
@ControllerAdvice
public class CsrfModelAdvice {

    public static final String SESSION_KEY = "csrfToken";

    @ModelAttribute("csrfToken")
    public String csrfToken(HttpSession session) {
        String token = (String) session.getAttribute(SESSION_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(SESSION_KEY, token);
        }
        return token;
    }
}
