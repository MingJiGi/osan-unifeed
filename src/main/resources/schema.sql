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
    PRIMARY KEY (id),
    KEY idx_osan_post_author (author_id),
    CONSTRAINT fk_osan_post_author FOREIGN KEY (author_id) REFERENCES osan_member (id)
);
