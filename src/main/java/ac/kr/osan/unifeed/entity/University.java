package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 소속 대학교 — 멀티캠퍼스(전국 대학 지원)의 기준 단위. 가입 시 회원 한 명당 하나씩 연결되고,
// 지도 중심좌표/맛집/커뮤니티 게시글은 전부 이 University 단위로 나뉜다.
// emailDomain이 확인된 학교는 MemberService.signup()에서 그 도메인 메일로만 가입을 강제한다
// (타 학교 학생이 다른 학교 커뮤니티에 들어오는 걸 막기 위함 — 멀티캠퍼스 분리의 핵심 전제).
// 아직 도메인을 확인 못 한 학교는 emailDomain이 NULL이고, 이 경우엔 강제 검증 없이 가입을 허용한다.
@Entity
@Table(name = "osan_university")
public class University {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "email_domain", length = 100)
    private String emailDomain;

    // 카카오 장소검색 반경 밖일 때 재검색 접두어로 쓰는 짧은 지역명(예: "오산", "평택").
    @Column(name = "region_name", length = 30)
    private String regionName;

    // 카카오 API로 실제 조회해서 채운다 — 직접 추측해서 넣지 않는다. 채워지기 전까지는 null.
    private Double latitude;
    private Double longitude;

    protected University() {
    }

    public University(String name, String emailDomain, String regionName, Double latitude, Double longitude) {
        this.name = name;
        this.emailDomain = emailDomain;
        this.regionName = regionName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmailDomain() { return emailDomain; }
    public String getRegionName() { return regionName; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }

    public void setCoordinates(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
