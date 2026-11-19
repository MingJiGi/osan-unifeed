# UniFeed (osan-unifeed)

**공강 시간에 실제로 다녀올 수 있는 캠퍼스 맛집만 골라주는 서비스.**
학교 웹메일(`@*.ac.kr`) 인증을 통과한 학생만 이용하는 맛집 지도 + 익명 커뮤니티입니다.

> 졸업작품 팀 프로젝트 (2026.07 ~ 2026.11)

---

## 왜 만들었나

포털 지도는 전국을 기준으로 하기 때문에 **"지금 내 공강 안에 다녀올 수 있는 곳"** 을 알려주지 못합니다.
수업이 12시에 끝나고 13시에 다음 수업이 있는 학생에게 도보 20분 거리의 맛집은 검색 결과에 뜰 이유가 없습니다.

UniFeed는 사용자의 시간표를 기준으로 **지금이 수업과 수업 사이의 공강인지, 몇 분 남았는지**를 계산하고,
거기서 식사 시간을 뺀 뒤 **왕복 이동이 가능한 맛집만** 걸러서 보여줍니다.

```
가용 편도 시간 = (공강 분 - 식사 버퍼 15분) ÷ 2
→ 캠퍼스 기준 도보 시간이 그 안에 들어오는 맛집만 지도·목록에 표시
```

수업 중이면 "이 수업 끝나고 갈 곳"을 미리 보여주고, 공강이 너무 짧으면 억지로 추천하지 않고 그렇다고 말합니다.
연강이거나 첫 수업 전·마지막 수업 후는 공강으로 치지 않습니다 — "수업과 수업 사이"만 진짜 공강으로 봅니다.

---

## 기능

| 영역 | 내용 |
|---|---|
| **회원** | 대학 웹메일 도메인 검증 가입, 이메일 인증 링크, 로그인, **이메일 OTP 2단계 인증**, 비밀번호 찾기, 회원 탈퇴 |
| **맛집 지도** | 카카오맵 연동, 카테고리·키워드 검색, 맛집 등록(장소 검색 / 지도 클릭), 실시간 도보 길찾기, 찜 |
| **공강 추천** | 시간표 기반 공강 판정 → 왕복 가능 맛집 필터링 |
| **리뷰** | 별점·사진 리뷰 CRUD, 맛집 평점/리뷰수 집계 |
| **커뮤니티** | 익명 게시글 CRUD, 계층형 댓글·대댓글, 1인 1좋아요, 맛집 태그 연동 |
| **시간표** | 5분 단위 수업 등록, 학기 관리, 학점 계산, **시간표 이미지 OCR 자동 입력** |
| **AI** | Gemini 기반 "오늘 뭐 먹지?" 추천(공강 상황 반영), 맛집 태그 생성, 시간표 OCR |
| **운영** | 신고 접수·처리(숨김/정지/해제), 알림, 관리자 회원 관리, 대학 등록 요청 승인 |
| **기타** | PWA(홈 화면 설치), 다크 모드, 반응형, 약관·개인정보·커뮤니티 가이드 |

맛집·게시글은 **소속 대학 단위로 분리**됩니다. 같은 상호라도 학교가 다르면 다른 맛집으로 취급합니다.

---

## 기술 스택

- **Java 25** · **Spring Boot 4.1** · Gradle (Kotlin DSL)
- **Thymeleaf** 서버 사이드 렌더링 + 부분 영역은 fetch 기반 JS
- **Spring Data JPA** / MySQL (운영) · H2 인메모리 (테스트)
- 스키마는 `ddl-auto=none` + [`schema.sql`](src/main/resources/schema.sql) 로 직접 관리
- Kakao Maps SDK, Google Gemini API, Gmail SMTP

---

## 설계에서 신경 쓴 것

### 1. N+1 제거

맛집·게시글 목록이 눈에 띄게 느려 SQL 로그를 켜고 추적한 결과, 행마다 연관 엔티티와 찜 여부를 다시 조회하는
N+1 문제였습니다. 세 가지로 나눠 해결했습니다.

- 작성자·맛집처럼 **항상 같이 쓰는 연관**은 `join fetch` 로 한 번에 ([`PostRepository`](src/main/java/ac/kr/osan/unifeed/repository/PostRepository.java))
- **찜 여부**는 맛집마다 묻지 않고 회원의 찜 id 전체를 한 번에 ([`RestaurantBookmarkRepository`](src/main/java/ac/kr/osan/unifeed/repository/RestaurantBookmarkRepository.java))
- 나머지 지연 로딩은 `default_batch_fetch_size=100` 으로 `IN (...)` 묶음 조회

DB가 원격이라 **쿼리 1번 = 네트워크 왕복 1번**이었던 점이 체감 차이를 키웠습니다.
`open-in-view=false` 로 두어 렌더링 전에 커넥션을 반납하고, 그 대신 조회 시점에 필요한 연관을 명시적으로 가져옵니다.

### 2. 세션 기반 인증을 직접 구현

Spring Security 없이 세션 인증을 직접 만들면서 각 방어가 무엇을 막는지 하나씩 적용했습니다.

- BCrypt 비밀번호 해싱 (`spring-security-crypto` 유틸만 사용)
- 로그인 성공 시 **세션 ID 재발급** — 세션 고정 공격 차단
- [`CsrfFilter`](src/main/java/ac/kr/osan/unifeed/config/CsrfFilter.java) — 토큰 검증, 면제 범위를 `application/json` 으로 한정
- [`SecurityHeadersFilter`](src/main/java/ac/kr/osan/unifeed/config/SecurityHeadersFilter.java) — X-Frame-Options, CSP, X-Content-Type-Options, Referrer-Policy
- [`RateLimiter`](src/main/java/ac/kr/osan/unifeed/config/RateLimiter.java) — IP·회원 단위 요청 제한을 로그인·가입·업로드·신고·AI 등 16개 엔드포인트에 적용
- **계정 단위 로그인 잠금** — 5회 실패 시 15분. IP 기반 제한과 별개라 **IP를 돌려써도 계정 자체가 잠깁니다** ([`MemberService`](src/main/java/ac/kr/osan/unifeed/service/MemberService.java))
- [`ActiveMemberInterceptor`](src/main/java/ac/kr/osan/unifeed/config/ActiveMemberInterceptor.java) — 정지·탈퇴 계정이 기존 세션으로 계속 활동하던 문제 차단
- 이메일 OTP 2단계 인증

> 실무라면 검증된 Spring Security를 쓰는 편이 맞다고 봅니다. 직접 구현한 것은 학습 목적이었고,
> 덕분에 세션 고정이나 CSRF가 **정확히 어떤 공격을 막는지** 알고 쓰게 됐습니다.

### 3. 탈퇴 시 집계 값 정합성

회원 행을 지우면 FK CASCADE로 리뷰·댓글·좋아요가 함께 사라지지만,
맛집의 **평점·리뷰수**와 게시글의 **댓글수·좋아요수**는 따로 저장한 집계 값이라 자동으로 줄지 않습니다.
탈퇴자가 준 별점이 맛집 평점에 영원히 남는 문제를 테스트 중 발견했습니다.

[`MemberWithdrawalService`](src/main/java/ac/kr/osan/unifeed/service/MemberWithdrawalService.java) 에서
삭제 전에 각 서비스의 **정상 삭제 경로**로 먼저 정리해, 집계 값이 함께 맞춰지도록 했습니다.
맛집 평점이 이 서비스의 핵심 데이터라 조용히 틀어지는 것을 두고 볼 수 없었습니다.

### 4. 외부 API 장애 대응

Gemini는 모델별로 과부하(503) 시점이 다릅니다. 실제로 한 모델이 연속 503을 내는 동안 다른 모델은 정상 응답했습니다.
재시도만으로는 모델 자체가 내려가면 뚫을 수 없어 **모델 폴백 체인**을 두었습니다.
API 키가 없으면 AI 기능만 조용히 꺼지고 나머지는 그대로 동작합니다.

---

## 실행

접속 정보는 소스에 두지 않고 환경변수로 받습니다.

```bash
export DB_URL="jdbc:mysql://localhost:3306/unifeed?useSSL=false&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
export DB_USERNAME=root
export DB_PASSWORD=...
export KAKAO_APPKEY=...          # 카카오 JavaScript 키 (없으면 지도 화면만 비활성)
export MAIL_USERNAME=...         # Gmail 주소
export MAIL_PASSWORD=...         # Gmail 앱 비밀번호 16자리
export GEMINI_API_KEY=...        # 없으면 AI 기능만 꺼짐

./gradlew bootRun
```

테이블은 `ddl-auto=none` 이라 기동 시 [`schema.sql`](src/main/resources/schema.sql) 이 생성합니다.

### 테스트

```bash
./gradlew test
```

H2 인메모리로 돌기 때문에 DB·네트워크 없이 실행됩니다. 9개 클래스 **45개 테스트**가 통과합니다.
(레포지토리 join fetch 조회, 컨트롤러 검증·리다이렉트, 신고 처리, 레이트리밋, 회원 가입·인증·잠금 등)

---

## 담당

팀 프로젝트이며, 저는 **Spring Boot 백엔드 설계·구현**을 맡았습니다.
회원 인증, 맛집·리뷰, 커뮤니티, 시간표 도메인과 공강 판정 API를 담당했습니다.

## 구조

```
src/main/java/ac/kr/osan/unifeed/
├── config/      보안 필터·인터셉터·레이트리밋·예외 처리
├── controller/  화면 컨트롤러 + api/ REST 엔드포인트
├── entity/      JPA 엔티티 (osan_ 프리픽스)
├── repository/  조회 쿼리 (join fetch 포함)
├── service/     도메인 로직 · AI · 메일
└── dto/ util/
```
