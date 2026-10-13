package ac.kr.osan.unifeed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

// ############################################################################
// WebConfig — 업로드된 이미지 파일을 정적 리소스로 서빙 + 인터셉터 등록
// ----------------------------------------------------------------------------
// classpath(static/) 밖에 있는 file.upload-dir 폴더를 /uploads/** 경로로 노출한다.
// 실행 중에 새로 생성되는 파일이라 클래스패스 안에 둘 수 없다.
// ############################################################################
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final AdminAccessInterceptor adminAccessInterceptor;
    private final ActiveMemberInterceptor activeMemberInterceptor;

    public WebConfig(AdminAccessInterceptor adminAccessInterceptor, ActiveMemberInterceptor activeMemberInterceptor) {
        this.adminAccessInterceptor = adminAccessInterceptor;
        this.activeMemberInterceptor = activeMemberInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    // 인터셉터는 등록 순서대로 실행된다 — ActiveMemberInterceptor가 세션의 회원 정보를 DB 기준으로
    // 먼저 갱신해야, 그 다음 AdminAccessInterceptor가 "지금" 관리자인지를 보고 판단할 수 있다.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activeMemberInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico");
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/admin/**");
    }
}
