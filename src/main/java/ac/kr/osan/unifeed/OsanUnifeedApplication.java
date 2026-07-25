package ac.kr.osan.unifeed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// ############################################################################
// 애플리케이션 시작점
// ----------------------------------------------------------------------------
// main() 하나로 내장 톰캣이 뜨고, 우리가 만든 컨트롤러/템플릿/JPA 가 전부 연결된다.
// 브라우저로 http://localhost:8080/memos 를 열면 메모 목록 화면이 나온다.
// ############################################################################
@SpringBootApplication
public class OsanUnifeedApplication {

	public static void main(String[] args) {
		SpringApplication.run(OsanUnifeedApplication.class, args);
	}
}
