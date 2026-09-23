package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(length = 100)
    private String verificationToken;

    private LocalDateTime tokenExpiry;

    @Column(length = 100)
    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    // 신고 관리 페이지(/admin/reports) 접근 권한. 가입 흐름에 노출되지 않고,
    // 관리자로 지정할 계정만 DB에서 직접 TRUE로 바꿔준다(기본값 FALSE, 안전한 기본값).
    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    // 관리자가 신고 처리 중 정지시킨 계정. 정지되면 로그인 자체가 막힌다(MemberService.authenticate).
    @Column(nullable = false)
    private boolean suspended = false;

    // 연속 로그인 실패 횟수 — 비밀번호가 맞으면 0으로 리셋, 일정 횟수 넘으면 이메일 재인증을 요구한다(무차별 대입 방어).
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    // 소속 대학교 — 가입할 때 한 번 정해지면 이후로 안 바뀐다(멀티캠퍼스 지도/커뮤니티 분리 기준).
    // EAGER인 이유: loginMember가 매 요청마다 새로 DB 조회되는 게 아니라 로그인 시점에
    // HttpSession에 그대로 저장돼서 나중 요청에서 재사용되는 구조라(이 앱 전체의 기존 패턴),
    // LAZY로 두면 세션이 끊긴 뒤 university에 접근할 때 LazyInitializationException이 난다.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "university_id")
    private University university;

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

    public boolean isEmailVerified() {return emailVerified;}

    public boolean isAdmin() {return admin;}

    public boolean isSuspended() {return suspended;}

    public void suspend() {this.suspended = true;}

    public void unsuspend() {this.suspended = false;}

    public int getFailedLoginAttempts() {return failedLoginAttempts;}

    public void increaseFailedLoginAttempts() {this.failedLoginAttempts++;}

    public void resetFailedLoginAttempts() {this.failedLoginAttempts = 0;}

    // 로그인 실패 누적으로 잠글 때 — 인증 완료 상태를 되돌려서 재인증 전까지 로그인을 막는다.
    public void requireReverification() {this.emailVerified = false;}

    public University getUniversity() {return university;}

    public void setUniversity(University university) {this.university = university;}

    public String getVerificationToken() {return verificationToken;}

    // 가입 시 발급하는 인증 토큰 + 유효기간(10분) 설정
    public void issueVerificationToken(String token) {
        this.verificationToken = token;
        this.tokenExpiry = LocalDateTime.now().plusMinutes(10);
    }

    // 토큰이 유효한지(만료 전인지) 확인 후 인증 완료 처리
    public boolean verifyEmail(String token) {
        if (verificationToken == null || !verificationToken.equals(token)) return false;
        if (tokenExpiry == null || tokenExpiry.isBefore(LocalDateTime.now())) return false;
        this.emailVerified = true;
        this.verificationToken = null;
        this.tokenExpiry = null;
        return true;
    }

    public String getResetToken() {return resetToken;}

    // 비밀번호 재설정 토큰 발급 + 유효기간(10분) 설정
    public void issuePasswordResetToken(String token) {
        this.resetToken = token;
        this.resetTokenExpiry = LocalDateTime.now().plusMinutes(10);
    }

    public boolean isResetTokenValid(String token) {
        return resetToken != null && resetToken.equals(token)
                && resetTokenExpiry != null && resetTokenExpiry.isAfter(LocalDateTime.now());
    }

    public void clearResetToken() {
        this.resetToken = null;
        this.resetTokenExpiry = null;
    }

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
