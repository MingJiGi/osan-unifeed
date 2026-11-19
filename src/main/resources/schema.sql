-- ##########################################################################
-- 스키마 정의 (ddl-auto=none 이므로 Hibernate 대신 이 스크립트로 테이블을 만든다)
-- --------------------------------------------------------------------------
-- 앱 기동 시 spring.sql.init 이 실행한다. IF NOT EXISTS 라 이미 있으면 넘어간다.
-- 공유 DB 를 쓰므로 테이블에 osan_ 프리픽스를 붙여 다른 프로젝트와 분리한다.
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

-- [주의] 이 ALTER 는 컬럼만 추가하고, 위 CREATE TABLE 안에 있는
--   CONSTRAINT fk_osan_comment_parent ... ON DELETE CASCADE
-- 는 붙이지 않는다(CREATE TABLE IF NOT EXISTS 는 이미 있는 테이블에 다시 적용되지 않으므로).
-- 그래서 "새로 만든 DB"에는 이 FK 가 있고 "예전부터 쓰던 DB"에는 없다 = 부모 댓글 삭제 시
-- 답글이 연쇄 삭제되는지 여부가 환경마다 달라진다. 지금은 CommentService.deleteWithReplies 가
-- 답글을 직접 지우고 카운트를 맞춰서 어느 쪽이든 동일하게 동작하도록 처리하고 있다.
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

-- 비밀번호 재설정 — 이메일 소유 확인 없이 아무나 재설정 가능하던 취약점 수정용 토큰
ALTER TABLE osan_member ADD COLUMN reset_token VARCHAR(100) NULL;
ALTER TABLE osan_member ADD COLUMN reset_token_expiry DATETIME(6) NULL;

-- 신고 기능 + 관리자 권한
-- target_type/target_id로 게시글·댓글·맛집리뷰를 공통으로 가리킨다(다형 참조, FK 3개 안 둠).
CREATE TABLE IF NOT EXISTS osan_report (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id   BIGINT       NOT NULL,
    target_type   VARCHAR(20)  NOT NULL,
    target_id     BIGINT       NOT NULL,
    reason        VARCHAR(20)  NOT NULL,
    detail        VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME(6)  NOT NULL,
    resolved_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_osan_report_status (status),
    CONSTRAINT fk_osan_report_reporter FOREIGN KEY (reporter_id) REFERENCES osan_member (id) ON DELETE CASCADE
);

-- 멀티캠퍼스 — 소속 대학교. 경기도 소재 대학 위주로 시드(전국 확장은 나중에 필요할 때 추가).
-- emailDomain은 가입 화면 "자동 추천"에만 쓰는 힌트라서 틀려도 가입이 막히지 않는다.
-- latitude/longitude는 실제 카카오 API로 조회해서 채운 값만 넣는다 — 모르는 학교는 NULL로 비워두고,
-- 나중에 정확한 좌표를 확인한 뒤 UPDATE로 채워 넣는다(추측값을 절대 넣지 않는다).
CREATE TABLE IF NOT EXISTS osan_university (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    email_domain VARCHAR(100) NULL,
    region_name  VARCHAR(30)  NULL,
    latitude     DOUBLE       NULL,
    longitude    DOUBLE       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_osan_university_name (name)
);

-- 2026-10-13: 경기도 대학 좌표를 학교별 캠퍼스 공식 좌표표(엑셀)로 일괄 보정했다.
-- 그 표에서 4곳(예원예술대/한신대/한경국립대/가천대)은 다른 캠퍼스 좌표가 적혀 있어서
-- 카카오 로컬 API로 교차확인한 뒤 기존 값을 그대로 뒀다(최대 245km 차이).
-- map.js 의 CAMPUS_LAT/CAMPUS_LNG 기본값도 오산대학교 좌표와 같이 맞춰뒀다.
INSERT IGNORE INTO osan_university (name, email_domain, region_name, latitude, longitude) VALUES
    ('오산대학교', 'osan.ac.kr', '오산', 37.155120, 127.060977),
    ('가천대학교', 'gachon.ac.kr', '성남', 37.4503, 127.1298),
    ('강남대학교', 'kangnam.ac.kr', '용인', 37.275735, 127.132500),
    ('경기대학교', 'kyonggi.ac.kr', '수원', 37.301391, 127.036561),
    ('경인교육대학교', 'ginue.ac.kr', '안양', 37.434211, 126.917753),
    ('단국대학교', 'dankook.ac.kr', '용인', 37.319288, 127.128967),
    ('대진대학교', 'daejin.ac.kr', '포천', 37.871758, 127.156421),
    ('명지대학교', 'mju.ac.kr', '용인', 37.221054, 127.185604),
    ('성결대학교', 'sungkyul.ac.kr', '안양', 37.379866, 126.928517),
    ('수원대학교', 'suwon.ac.kr', '화성', 37.210036, 126.975775),
    ('신한대학교', 'shinhan.ac.kr', '의정부', 37.709040, 127.044521),
    ('아주대학교', 'ajou.ac.kr', '수원', 37.283002, 127.045378),
    ('안양대학교', 'anyang.ac.kr', '안양', 37.391381, 126.919808),
    ('용인대학교', 'yongin.ac.kr', '용인', 37.226394, 127.167746),
    ('을지대학교', 'eulji.ac.kr', '성남', 37.460909, 127.166008),
    ('차의과학대학교', 'cha.ac.kr', '포천', 37.856329, 127.137606),
    ('평택대학교', 'ptu.ac.kr', '평택', 36.994966, 127.132694),
    ('한경국립대학교', 'hknu.ac.kr', '안성', 37.0094, 127.2638),
    ('한국공학대학교', 'tukorea.ac.kr', '시흥', 37.340240, 126.733352),
    ('한국항공대학교', 'kau.ac.kr', '고양', 37.600307, 126.865393),
    ('한세대학교', 'hansei.ac.kr', '군포', 37.344095, 126.953735),
    ('한신대학교', 'hs.ac.kr', '오산', 37.1932, 127.0229),
    ('협성대학교', 'uhs.ac.kr', '화성', 37.212834, 126.952867),
    ('한국교통대학교', 'ut.ac.kr', '의왕', 37.314319, 126.954736);

-- 학교 등록 신청 — 가입 화면에서 "내 학교가 목록에 없어요"로 들어온 요청.
-- 신청자는 비로그인 상태라 회원과 연결하지 않고 이메일만 남긴다(승인 안내 메일 발송용).
CREATE TABLE IF NOT EXISTS osan_university_request (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    university_name VARCHAR(100) NOT NULL,
    address         VARCHAR(200) NOT NULL,
    requester_email VARCHAR(100) NOT NULL,
    email_domain    VARCHAR(100) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME(6)  NOT NULL,
    processed_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    KEY idx_osan_univ_request_status (status)
);

-- 경기도 추가 대학 55곳(4년제 19 + 전문대 36) — 실제 공식 도메인 확인됨.
-- 같은 도메인을 캠퍼스 여럿이 공유하는 학교(경복대/두원공과대/한국폴리텍대)는 이름 뒤에 캠퍼스명을 붙여 구분.
INSERT IGNORE INTO osan_university (name, email_domain, region_name, latitude, longitude) VALUES
    ('가톨릭대학교', 'catholic.ac.kr', '부천', 37.486878, 126.800981),
    ('경동대학교', 'kduniv.ac.kr', '양주', 37.810486, 127.071212),
    ('경희대학교', 'khu.ac.kr', '용인', 37.242400, 127.081334),
    ('동국대학교', 'dongguk.edu', '고양', 37.678656, 126.806246),
    ('동양대학교', 'dyu.ac.kr', '동두천', 37.934491, 127.057376),
    ('루터대학교', 'ltu.ac.kr', '용인', 37.265033, 127.112575),
    ('서울신학대학교', 'stu.ac.kr', '부천', 37.479748, 126.789993),
    ('서울장신대학교', 'sjs.ac.kr', '광주', 37.409462, 127.245976),
    ('성균관대학교', 'skku.edu', '수원', 37.294367, 126.974638),
    ('아신대학교', 'acts.ac.kr', '양평', 37.511774, 127.428340),
    ('예원예술대학교', 'yewon.ac.kr', '양주', 37.8464, 127.0369),
    ('중부대학교', 'joongbu.ac.kr', '고양', 37.713124, 126.890175),
    ('중앙대학교', 'cau.ac.kr', '안성', 37.004382, 127.227631),
    ('중앙승가대학교', 'sangha.ac.kr', '김포', 37.605408, 126.706683),
    ('총신대학교', 'chongshin.ac.kr', '용인', 37.2478, 127.2994),
    ('칼빈대학교', 'calvin.ac.kr', '용인', 37.305161, 127.128183),
    ('한국외국어대학교', 'hufs.ac.kr', '용인', 37.337423, 127.266687),
    ('한양대학교', 'hanyang.ac.kr', '안산', 37.296668, 126.835007),
    ('화성의과학대학교', 'hsmu.ac.kr', '화성', 37.202687, 126.839647),
    ('경기과학기술대학교', 'gtec.ac.kr', '시흥', 37.337182, 126.738335),
    ('경민대학교', 'kyungmin.ac.kr', '의정부', 37.745480, 127.023849),
    ('경복대학교(남양주캠퍼스)', 'kbu.ac.kr', '남양주', 37.735362, 127.210565),
    ('경복대학교(포천캠퍼스)', 'kbu.ac.kr', '포천', 37.9424, 127.2210),
    ('계원예술대학교', 'kaywon.ac.kr', '의왕', 37.379051, 126.980623),
    ('국제대학교', 'kookje.ac.kr', '평택', 37.064052, 127.081405),
    ('김포대학교', 'kimpo.ac.kr', '김포', 37.729653, 126.546987),
    ('농협대학교', 'nonghyup.ac.kr', '고양', 37.659538, 126.872700),
    ('대경대학교', 'tk.ac.kr', '남양주', 37.747986, 127.237385),
    ('대림대학교', 'daelim.ac.kr', '안양', 37.403524, 126.930499),
    ('동남보건대학교', 'dongnam.ac.kr', '수원', 37.298006, 126.987000),
    ('동서울대학교', 'duseoul.ac.kr', '성남', 37.459152, 127.128356),
    ('동아방송예술대학교', 'dima.ac.kr', '안성', 37.059131, 127.357835),
    ('동원대학교', 'tw.ac.kr', '광주', 37.328779, 127.403064),
    ('두원공과대학교(안성캠퍼스)', 'doowon.ac.kr', '안성', 37.066207, 127.422645),
    ('두원공과대학교(파주캠퍼스)', 'doowon.ac.kr', '파주', 37.8182, 126.8041),
    ('부천대학교', 'bc.ac.kr', '부천', 37.489425, 126.778718),
    ('서영대학교', 'seoyeong.ac.kr', '파주', 37.825203, 126.772693),
    ('서울예술대학교', 'seoularts.ac.kr', '안산', 37.333225, 126.837141),
    ('서정대학교', 'seojeong.ac.kr', '양주', 37.857113, 127.038211),
    ('수원과학대학교', 'ssc.ac.kr', '화성', 37.191937, 126.982974),
    ('수원여자대학교', 'swwu.ac.kr', '수원', 37.252372, 126.963650),
    ('신구대학교', 'shingu.ac.kr', '성남', 37.448536, 127.168329),
    ('신안산대학교', 'sau.ac.kr', '안산', 37.311614, 126.807365),
    ('안산대학교', 'ansan.ac.kr', '안산', 37.308866, 126.875798),
    ('여주대학교', 'yit.ac.kr', '여주', 37.270332, 127.634417),
    ('연성대학교', 'yeonsung.ac.kr', '안양', 37.397096, 126.908537),
    ('용인예술과학대학교', 'ysc.ac.kr', '용인', 37.228650, 127.218859),
    ('웅지세무대학교', 'watc.ac.kr', '파주', 37.818485, 126.735513),
    ('유한대학교', 'yuhan.ac.kr', '부천', 37.487058, 126.821178),
    ('장안대학교', 'jangan.ac.kr', '화성', 37.213111, 126.947300),
    ('청강문화산업대학교', 'ck.ac.kr', '이천', 37.205854, 127.356153),
    ('한국관광대학교', 'ktc.ac.kr', '이천', 37.282589, 127.376744),
    ('한국폴리텍대학(안성캠퍼스)', 'kopo.ac.kr', '안성', 37.0075, 127.1747),
    ('한국폴리텍대학(성남캠퍼스)', 'kopo.ac.kr', '성남', 37.4578, 127.1565),
    ('ICT폴리텍대학', 'ict.ac.kr', '광주', 37.396758, 127.248158);

-- 마이그레이션 1회 실행 보장용 마커. spring.sql.init.mode=always라서 schema.sql이 기동할 때마다
-- 재실행되는데, 아래 그랜드파더링 UPDATE에 가드 없이 두면 "가입 시 학교 미선택"으로 university_id가
-- NULL인 회원이 재기동마다 계속 오산대학교로 재배정돼서 이메일 도메인 검증을 영구히 우회하게 된다.
-- 그래서 최초 1회만 돌고 이후로는 스킵되도록 마커 테이블로 감싼다.
CREATE TABLE IF NOT EXISTS osan_schema_migration (
    migration_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (migration_key)
);

-- 회원 소속 대학교 — 가입 시 한 번 정해지면 이후 안 바뀐다. 이 기능 도입 시점의 기존 회원만
-- 전부 오산대학교로 그랜드파더링(지금까지의 실제 데이터가 전부 오산대 기준으로 쌓여있었으므로) —
-- 딱 한 번만 실행되고, 그 뒤로 학교 미선택 가입자는 university_id가 NULL로 남는다.
ALTER TABLE osan_member ADD COLUMN university_id BIGINT NULL;
ALTER TABLE osan_member ADD CONSTRAINT fk_osan_member_university FOREIGN KEY (university_id) REFERENCES osan_university (id) ON DELETE SET NULL;
UPDATE osan_member m
    JOIN (SELECT COUNT(*) AS done FROM osan_schema_migration WHERE migration_key = 'member_university_grandfather') g
    SET m.university_id = (SELECT id FROM osan_university WHERE name = '오산대학교')
    WHERE m.university_id IS NULL AND g.done = 0;
INSERT IGNORE INTO osan_schema_migration (migration_key) VALUES ('member_university_grandfather');

-- 맛집도 등록한 회원의 대학교를 그대로 물려받는다(지도/검색을 학교별로 분리하는 기준). 이 기능 도입
-- 시점의 기존 데이터만 전부 오산대학교로 그랜드파더링 — 위와 동일하게 1회만 실행.
ALTER TABLE osan_restaurant ADD COLUMN university_id BIGINT NULL;
ALTER TABLE osan_restaurant ADD CONSTRAINT fk_osan_restaurant_university FOREIGN KEY (university_id) REFERENCES osan_university (id) ON DELETE SET NULL;
UPDATE osan_restaurant r
    JOIN (SELECT COUNT(*) AS done FROM osan_schema_migration WHERE migration_key = 'restaurant_university_grandfather') g
    SET r.university_id = (SELECT id FROM osan_university WHERE name = '오산대학교')
    WHERE r.university_id IS NULL AND g.done = 0;
INSERT IGNORE INTO osan_schema_migration (migration_key) VALUES ('restaurant_university_grandfather');

-- 관리자 권한 — 기본 FALSE. 관리자로 지정할 계정만 DB에서 직접 TRUE로 바꿔준다.
ALTER TABLE osan_member ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- 회원 정지 — 관리자가 신고 처리 중 정지시킨 계정은 로그인이 막힌다.
ALTER TABLE osan_member ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT FALSE;

-- 연속 로그인 실패 횟수 — 일정 횟수 넘으면 이메일 재인증을 요구한다(계정 단위 무차별 대입 방어).
ALTER TABLE osan_member ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;

-- 신고 처리 감사 로그 — 누가(handled_by) 언제(resolved_at) 무슨 조치(action)를 취했는지 기록.
ALTER TABLE osan_report ADD COLUMN handled_by BIGINT NULL;
ALTER TABLE osan_report ADD COLUMN action VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE osan_report ADD CONSTRAINT fk_osan_report_handled_by FOREIGN KEY (handled_by) REFERENCES osan_member (id) ON DELETE SET NULL;

-- 신고 누적 자동 숨김 — 같은 대상에 처리 대기 신고가 3건 이상 쌓이면 관리자가 확인하기 전까지
-- 자동으로 숨겨진다(완전 삭제 아님, 관리자가 처리하면 다시 보이거나 진짜 삭제됨).
ALTER TABLE osan_post ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE osan_comment ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE osan_restaurant_review ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

-- 알림 — 내 글에 댓글/답글이 달리거나 좋아요를 받았을 때. 누가 그랬는지는 절대 안 담는다(익명 정책).
CREATE TABLE IF NOT EXISTS osan_notification (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT       NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    message      VARCHAR(200) NOT NULL,
    link         VARCHAR(300) NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_osan_notification_recipient (recipient_id, is_read),
    CONSTRAINT fk_osan_notification_recipient FOREIGN KEY (recipient_id) REFERENCES osan_member (id) ON DELETE CASCADE
);

-- 로그인 잠금 — 연속 실패 5회 시 이메일 재인증을 강제하던 방식(계정 탈취는 아니지만, 공격자가
-- 피해자 이메일만 알아도 틀린 비번 5번으로 피해자를 재인증 상태로 밀어넣을 수 있는 DoS)을
-- 파괴적이지 않은 일시 잠금(locked_until)으로 교체.
ALTER TABLE osan_member ADD COLUMN locked_until DATETIME(6) NULL;

-- 로그인 2차 인증(이메일 OTP) — 마이페이지에서 선택적으로 켜고 끈다. 켜져 있으면 비밀번호 확인 후
-- 메일로 받은 6자리 코드를 추가로 입력해야 로그인이 끝난다.
ALTER TABLE osan_member ADD COLUMN two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE osan_member ADD COLUMN otp_code VARCHAR(10) NULL;
ALTER TABLE osan_member ADD COLUMN otp_expiry DATETIME(6) NULL;
