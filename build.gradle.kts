// ############################################################################
// Gradle 빌드 스크립트 (Kotlin DSL)
// ----------------------------------------------------------------------------
// 이 예제의 주제는 "Thymeleaf 서버 사이드 렌더링(SSR)" 이다.
// 요즘 흔한 React/Vue 처럼 브라우저가 JSON 을 받아 화면을 그리는 방식(CSR)이 아니라,
// 서버가 완성된 HTML 을 만들어서 통째로 내려주는 옛날식이자 여전히 강력한 방식이다.
//
// 그래서 REST(JSON) 스타터 대신 Thymeleaf 스타터를 쓰고, 컨트롤러는 JSON 이 아니라
// "어떤 HTML 템플릿을 렌더링할지" 를 반환한다.
//
// gradle 기본기는 앞 예제(spring-boot-dockers/build.gradle.kts)에 자세히 있다.
// ############################################################################

plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.metionlabs.academy"
version = "0.0.1-SNAPSHOT"
description = "osan-unifeed — Thymeleaf SSR + Spring Data JPA 게시판 (회원 1:N 게시글, osan_ 테이블 프리픽스)"

java {
	// 최신 LTS. 누가 어디서 빌드해도 같은 자바 버전이 쓰이도록 고정한다.
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// ------------------------------------------------------------------------
	// 이 예제의 주인공 — Thymeleaf
	// ------------------------------------------------------------------------
	// 서버에서 HTML 을 만들어 내려주는 템플릿 엔진.
	// 이 스타터를 넣으면 컨트롤러가 "memos/list" 같은 문자열을 반환할 때
	// src/main/resources/templates/memos/list.html 을 찾아 렌더링해준다.
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

	// 웹 MVC + 내장 톰캣.
	// [주의] Boot 3 까지의 이름은 spring-boot-starter-web 이었다. Boot 4 에서 webmvc 로 바뀌었다.
	// Thymeleaf 는 이 MVC 위에서 동작한다(컨트롤러 → View 이름 → 템플릿 렌더링).
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// 폼 입력값 검증(@NotBlank, @Size 등).
	// [주의] 예전에는 web 스타터에 포함돼 있었지만 지금은 분리되어 있다.
	// 이걸 빠뜨리면 @Valid 를 붙여도 검증이 조용히 무시된다(에러도 안 남).
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// DB 저장은 Spring Data JPA 로. (JPA 자체는 spring-data-jpas 예제에서 자세히 다뤘다)
	// 여기서는 "화면(Thymeleaf) ↔ DB(JPA)" 를 잇는 게 목적이라 저장 기술은 간단히 쓴다.
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")

	// 비밀번호 해싱(BCrypt) — spring-security-crypto 는 순수 유틸 모듈이라 이것만 추가해도
	// 시큐리티 필터체인/자동설정은 켜지지 않는다. 기존 세션 기반 인증 방식은 그대로 두고
	// 비밀번호 저장/검증만 BCrypt로 바꾸는 목적.
	implementation("org.springframework.security:spring-security-crypto")

	// 이메일 인증 — 가입 시 대학교 웹메일(@*.ac.kr)로 인증 링크 발송용 (JavaMailSender)
	implementation("org.springframework.boot:spring-boot-starter-mail")

	// ------------------------------------------------------------------------
	// MySQL 드라이버 — 실행 시점에만 필요
	// ------------------------------------------------------------------------
	// runtimeOnly = 컴파일엔 불필요, 실행할 때만 필요. (자세한 이유는 spring-data-jpas 참고)
	runtimeOnly("com.mysql:mysql-connector-j")

	// ------------------------------------------------------------------------
	// 테스트 — 도커 없이 H2 인메모리로 돌린다 (spring-data-jpas 와 같은 방식)
	// ------------------------------------------------------------------------
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	// JUnit 5 로 테스트를 실행한다. 없으면 테스트가 하나도 안 돌면서 조용히 성공한다.
	useJUnitPlatform()
}
