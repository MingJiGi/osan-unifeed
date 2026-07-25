# osan-unifeed

**Thymeleaf 서버 사이드 렌더링(SSR)** + **Spring Data JPA** 로 만든 회원·게시판 웹앱.
DB 조회 결과를 화면(View)까지 그려내는 기본 골격입니다. (오산 브릿지 멘토링 프로젝트 베이스)

- Java 25 (LTS) · Spring Boot 4.1.0 · Gradle 9.5.1 (Kotlin DSL)
- 실행 DB = 공유 MySQL(`localhost:3306`)의 **`toydb` DB**, 테이블은 **`osan_` 프리픽스**로 분리
- 테스트 DB = **H2 인메모리** (도커·네트워크 없이 즉시 실행)
- **Spring Security 안 씀** — 회원은 "작성자" 데이터일 뿐(로그인/인증 없음)
- **`ddl-auto=none`** — 스키마는 [`schema.sql`](src/main/resources/schema.sql) 로 관리(실무 방식)

## 도메인

**회원(Member) 1 : N 게시글(Post)** — 회원 한 명이 여러 글을 쓴다.

| 테이블 | 컬럼 |
|--------|------|
| `osan_member` | id, name, email(unique), created_at |
| `osan_post` | id, title, content, **author_id**(→osan_member), created_at |

## 화면

| URL | 화면 |
|-----|------|
| `/` → `/posts` | 홈 → 게시판 |
| `GET /posts` · `/posts/{id}` | 게시판 목록 · 글 상세 |
| `GET /posts/new` · `GET /posts/{id}/edit` | 새 글 · 수정 폼(작성자 드롭다운) |
| `POST /posts` · `/posts/{id}` · `/posts/{id}/delete` | 저장 · 수정 · 삭제 |
| `GET /members` · `/members/{id}` | 회원 목록 · 회원 상세(그 회원이 쓴 글) |

## 실행 방법

```bash
TOYDB_PASSWORD=<비번> ./gradlew bootRun
```

- **Java 25** (Gradle toolchain 이 맞춰줌), 도커 **불필요**
- **한국 IP** 필요(해외는 방화벽 timeout), 비번은 관리자에게 문의
- 기동 후 **http://localhost:8080/** → 게시판. 처음이면 시드 데이터(회원 3 + 글 4)가 자동으로 들어간다.
- 테이블은 `ddl-auto=none` 이라 [`schema.sql`](src/main/resources/schema.sql) 이 기동 시 만든다(`osan_member`, `osan_post`).

## 코드에서 볼 곳

- `repository/PostRepository.java` — `join fetch p.author` 로 목록에서 작성자를 함께 조회(N+1 / LazyInitializationException 회피)
- `controller/PostController.java` — `@Controller` 가 뷰 이름 반환, `@Valid`+`BindingResult`, `redirect:`(PRG)
- `templates/` — `th:each/text/if/object/field/errors` + `fragments/layout.html`(head·nav 재사용)
- `resources/schema.sql` — `ddl-auto=none` 스키마 관리(osan_ 프리픽스)

## 테스트

```bash
./gradlew test
```

H2 인메모리로 실행(도커 불필요). Repository join fetch 조회 + Controller 뷰/검증/redirect 를 검증한다.
