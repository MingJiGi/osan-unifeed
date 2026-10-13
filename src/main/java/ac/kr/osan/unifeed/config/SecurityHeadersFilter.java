package ac.kr.osan.unifeed.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

// ############################################################################
// SecurityHeadersFilter — 모든 응답에 기본 보안 헤더를 붙인다.
// ----------------------------------------------------------------------------
// 이 앱은 Spring Security를 쓰지 않아서(세션 기반 자체 인증) 보안 헤더가 하나도 안 붙고 있었다.
// 특히 클릭재킹: 공격자가 자기 페이지에 우리 사이트를 iframe으로 띄워놓고 투명하게 덮어서
// 사용자가 "글 삭제", "회원 탈퇴" 같은 버튼을 모르고 누르게 만들 수 있다.
//
// [CSP를 최소로만 건 이유]
// 이 프로젝트는 화면 곳곳에서 인라인 <script>와 onclick 속성을 쓰고, 카카오 지도 SDK와
// Pretendard 폰트를 외부 CDN에서 불러온다. 그래서 script-src/style-src 같은 걸 조이면
// 화면이 통째로 깨진다. 대신 깨질 염려가 없으면서 효과가 확실한 세 가지만 건다.
//   - frame-ancestors 'none' : 클릭재킹 차단 (X-Frame-Options의 최신 대체재)
//   - object-src 'none'      : 플래시/플러그인 삽입 차단
//   - base-uri 'self'        : <base> 태그를 바꿔치기해 상대경로 리소스를 가로채는 공격 차단
// ############################################################################
@Component
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;

        // 클릭재킹 차단 — 구형 브라우저용(X-Frame-Options)과 최신(CSP frame-ancestors)을 함께 건다.
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'; object-src 'none'; base-uri 'self'");

        // 브라우저가 Content-Type을 멋대로 추측하지 않게 한다 —
        // 업로드된 이미지를 HTML로 해석해 스크립트를 실행하는 식의 우회를 막는다.
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 외부 사이트로 나갈 때 전체 URL(쿼리스트링에 토큰이 들어있을 수 있다)을 흘리지 않는다.
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        chain.doFilter(req, res);
    }
}
