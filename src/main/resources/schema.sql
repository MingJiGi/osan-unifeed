-- ##########################################################################
-- 스키마 정의 (ddl-auto=none 이므로 Hibernate 대신 이 스크립트로 테이블을 만든다)
-- --------------------------------------------------------------------------
-- 앱 기동 시 spring.sql.init 이 실행한다. IF NOT EXISTS 라 이미 있으면 넘어간다.
-- 공유 toydb DB 를 쓰므로 테이블에 osan_ 프리픽스를 붙여 다른 프로젝트와 분리한다.
-- 실무에서는 이 파일을 Flyway/Liquibase 로 버전 관리한다.
-- ##########################################################################

-- 회원
CREATE TABLE IF NOT EXISTS osan_member (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    profile_image VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_osan_member_email (email)
);

-- 게시글 (작성자 = osan_member 로의 N:1)
CREATE TABLE IF NOT EXISTS osan_post (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    author_id  BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    img_url VARCHAR(500) NULL,
    tags VARCHAR(300) NULL,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_osan_post_author (author_id),
    CONSTRAINT fk_osan_post_author FOREIGN KEY (author_id) REFERENCES osan_member (id)
);

ALTER TABLE osan_member
    ADD COLUMN profile_image VARCHAR(500) NULL;

ALTER TABLE osan_post
    ADD COLUMN img_url       VARCHAR(500) NULL,
    ADD COLUMN tags          VARCHAR(300) NULL,
    ADD COLUMN like_count    INT NOT NULL DEFAULT 0,
    ADD COLUMN comment_count INT NOT NULL DEFAULT 0;


ALTER TABLE osan_member ADD COLUMN password VARCHAR(100) NOT NULL DEFAULT '';

-- 좋아요 매핑 테이블 (회원 1명당 게시글 1개에 좋아요 1회만 허용)
CREATE TABLE IF NOT EXISTS osan_post_like (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    post_id    BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_osan_post_member (post_id, member_id),
    CONSTRAINT fk_osan_like_post FOREIGN KEY (post_id) REFERENCES osan_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_osan_like_member FOREIGN KEY (member_id) REFERENCES osan_member (id) ON DELETE CASCADE
);

-- 댓글 테이블
CREATE TABLE IF NOT EXISTS osan_comment (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    content    VARCHAR(500) NOT NULL,
    post_id    BIGINT NOT NULL,
    author_id  BIGINT NOT NULL,
    parent_id  BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_osan_comment_post (post_id),
    KEY idx_osan_comment_author (author_id),
    KEY idx_osan_comment_parent (parent_id),
    CONSTRAINT fk_osan_comment_post FOREIGN KEY (post_id) REFERENCES osan_post (id) ON DELETE CASCADE,
    CONSTRAINT fk_osan_comment_author FOREIGN KEY (author_id) REFERENCES osan_member (id) ON DELETE CASCADE,
    CONSTRAINT fk_osan_comment_parent FOREIGN KEY (parent_id) REFERENCES osan_comment (id) ON DELETE CASCADE
);

ALTER TABLE osan_comment ADD COLUMN parent_id BIGINT NULL;

-- 맛집 (음식점) 테이블
CREATE TABLE IF NOT EXISTS osan_restaurant (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    category       VARCHAR(50)  NOT NULL,
    address        VARCHAR(200) NOT NULL,
    phone          VARCHAR(50)  NULL,
    latitude       DOUBLE       NOT NULL,
    longitude      DOUBLE       NOT NULL,
    description    VARCHAR(500) NULL,
    img_url        VARCHAR(500) NULL,
    rating         DOUBLE       NOT NULL DEFAULT 0.0,
    review_count   INT          NOT NULL DEFAULT 0,
    bookmark_count INT          NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_osan_restaurant_category (category)
);

-- 맛집 리뷰 테이블
CREATE TABLE IF NOT EXISTS osan_restaurant_review (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    content       VARCHAR(500) NOT NULL,
    rating        INT          NOT NULL DEFAULT 5,
    restaurant_id BIGINT       NOT NULL,
    author_id     BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_osan_review_restaurant (restaurant_id),
    KEY idx_osan_review_author (author_id),
    CONSTRAINT fk_osan_review_restaurant FOREIGN KEY (restaurant_id) REFERENCES osan_restaurant (id) ON DELETE CASCADE,
    CONSTRAINT fk_osan_review_author FOREIGN KEY (author_id) REFERENCES osan_member (id) ON DELETE CASCADE
);

-- 맛집 저장 (북마크/찜) 테이블
CREATE TABLE IF NOT EXISTS osan_restaurant_bookmark (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    restaurant_id BIGINT      NOT NULL,
    member_id     BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_osan_rest_bookmark (restaurant_id, member_id),
    CONSTRAINT fk_osan_bookmark_restaurant FOREIGN KEY (restaurant_id) REFERENCES osan_restaurant (id) ON DELETE CASCADE,
    CONSTRAINT fk_osan_bookmark_member FOREIGN KEY (member_id) REFERENCES osan_member (id) ON DELETE CASCADE
);

-- 맛집 리뷰 사진 (선택)
ALTER TABLE osan_restaurant_review ADD COLUMN img_url VARCHAR(500) NULL;

-- 게시글 - 맛집 태그 연동 (게시글 상세에서 지도 맛집 카드 노출용)
ALTER TABLE osan_post ADD COLUMN restaurant_id BIGINT NULL;
ALTER TABLE osan_post ADD KEY idx_osan_post_restaurant (restaurant_id);
ALTER TABLE osan_post ADD CONSTRAINT fk_osan_post_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES osan_restaurant (id) ON DELETE SET NULL;

-- 시간표 수업 테이블 (회원별, 5분 단위 배치 그대로 저장 — 기존 localStorage 대체)
CREATE TABLE IF NOT EXISTS osan_timetable_course (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    member_id   BIGINT       NOT NULL,
    semester    VARCHAR(100) NOT NULL,
    name        VARCHAR(100) NOT NULL,
    info        VARCHAR(100) NULL,
    day_col     INT          NOT NULL,
    color       VARCHAR(30)  NOT NULL,
    start_hour  INT          NOT NULL,
    start_min   INT          NOT NULL,
    end_hour    INT          NOT NULL,
    end_min     INT          NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_osan_timetable_member (member_id),
    CONSTRAINT fk_osan_timetable_member FOREIGN KEY (member_id) REFERENCES osan_member (id) ON DELETE CASCADE
);

-- 맛집 등록자가 직접 입력한 실제 태그 (더미 카테고리 태그 대체용)
ALTER TABLE osan_restaurant ADD COLUMN tags VARCHAR(300) NULL;

-- 이메일(대학교 웹메일) 인증. 기존 가입자는 DEFAULT TRUE로 그랜드파더링(갑자기 로그인 막히지 않게),
-- 신규 가입자는 애플리케이션 코드에서 명시적으로 false로 저장한다.
ALTER TABLE osan_member ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE osan_member ADD COLUMN verification_token VARCHAR(100) NULL;
ALTER TABLE osan_member ADD COLUMN token_expiry DATETIME(6) NULL;

-- 회원 탈퇴 시 작성 게시글도 함께 삭제되도록 CASCADE로 변경.
-- (원래 fk_osan_post_author 에는 ON DELETE 옵션이 없어서, 게시글을 쓴 회원은 탈퇴 자체가 DB 제약으로 막혀 있었음)
ALTER TABLE osan_post DROP FOREIGN KEY fk_osan_post_author;
ALTER TABLE osan_post ADD CONSTRAINT fk_osan_post_author FOREIGN KEY (author_id) REFERENCES osan_member (id) ON DELETE CASCADE;