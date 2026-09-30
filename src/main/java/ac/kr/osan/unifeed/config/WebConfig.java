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

    public WebConfig(AdminAccessInterceptor adminAccessInterceptor) {
        this.adminAccessInterceptor = adminAccessInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    // /admin/** 전체에 관리자 권한 체크를 건다 — AdminController 각 메서드에 복붙하던 체크를 대체.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAccessInterceptor).addPathPatterns("/admin/**");
    }
}
