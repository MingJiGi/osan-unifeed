package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// ############################################################################
// Member 엔티티 — 회원 한 명 = member 테이블 한 줄
// ----------------------------------------------------------------------------
// 로그인/인증은 이 예제의 범위가 아니다(Spring Security 없음). 회원은 그냥
// "게시글의 작성자" 로 쓰이는 데이터일 뿐이다.
// ############################################################################
@Entity
@Table(name = "osan_member") // 공유 toydb 에서 다른 프로젝트와 안 섞이도록 osan_ 프리픽스
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
	private Long id;

	// 이름. 화면 목록에서 작성자로 보여준다.
	@Column(nullable = false, length = 50)
	private String name;

	// 이메일. unique = true 로 중복 가입을 DB 레벨에서 막는다.
	@Column(nullable = false, length = 100, unique = true)
	private String email;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

    @Column(length = 500)
    private String profileImage;

    @Column(nullable = false, length = 100)
    private String password;


	// JPA 가 리플렉션으로 객체를 만들 때 쓰는 기본 생성자(필수).
	protected Member() {
	}

	public Member(String name, String email) {
		this(name, email, "1234");
	}

	public Member(String name, String email, String password) {
		this.name = name;
		this.email = email;
        this.password = password;
	}

	@jakarta.persistence.PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

    public String getProfileImage() {return profileImage;}

    public String getPassword() {return password;}

    public void setPassword(String password) {this.password = password;}

    public void updateProfile(String name, String profileImage) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (profileImage != null) {
            String trimmed = profileImage.trim();
            this.profileImage = trimmed.isEmpty() ? null : trimmed;
        }
    }
}
