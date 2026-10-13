package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.repository.MemberRepository;
import ac.kr.osan.unifeed.repository.UniversityRepository;
import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.University;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// ############################################################################
// MemberService — 회원 업무 로직
// ############################################################################
@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final UniversityRepository universityRepository;
	private final MailService mailService;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public MemberService(MemberRepository memberRepository, UniversityRepository universityRepository, MailService mailService) {
		this.memberRepository = memberRepository;
		this.universityRepository = universityRepository;
		this.mailService = mailService;
	}

	// 회원 목록(최신 가입순).
	public List<Member> findAll() {
		return memberRepository.findAllByOrderByCreatedAtDesc();
	}

	// 관리자 회원 검색(이름/이메일 + 대학).
	public List<Member> search(String q, Long universityId) {
		return memberRepository.search(q == null ? "" : q.trim(), universityId);
	}

	// 단건 조회. 없으면 예외.
	public Member findById(Long id) {
		return memberRepository.findById(id)
				.orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다. id=" + id));
	}

	// 회원 등록(이 예제 화면에는 폼을 넣지 않았지만, 시드/테스트에서 쓴다).
	@Transactional
	public Member register(String name, String email, String password) {return memberRepository.save(new Member(name, email, passwordEncoder.encode(password)));}

    public Member findByEmail(String email){
        return memberRepository.findByEmail(email).orElse(null);
    }

    // 회원가입 — 인증 토큰을 발급하고 대학교 웹메일로 인증 링크를 보낸다.
    // 이미 가입돼 있지만 아직 인증을 안 한 이메일이면(메일을 못 받았거나 놓친 경우)
    // 새 토큰을 발급해서 재발송한다(그래서 영원히 가입이 막히지 않는다).
    @Transactional
    public Member signup(String name, String email, String password, Long universityId){
        University university = universityId != null ? universityRepository.findById(universityId).orElse(null) : null;
        requireMatchingDomain(university, email);

        Member existing = memberRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            if (existing.isEmailVerified()) {
                return null; // 이미 가입 완료된 이메일
            }
            existing.setUniversity(university);
            existing.issueVerificationToken(java.util.UUID.randomUUID().toString());
            sendVerificationEmailSafely(email, existing.getVerificationToken());
            return existing;
        }

        Member member = new Member(name, email, passwordEncoder.encode(password));
        member.setUniversity(university);
        member.issueVerificationToken(java.util.UUID.randomUUID().toString());
        Member saved = memberRepository.save(member);
        sendVerificationEmailSafely(email, saved.getVerificationToken());
        return saved;
    }

    // 선택한 학교의 공식 도메인이 확인돼 있으면(emailDomain != null) 그 도메인 메일로만 가입을 허용한다 —
    // 그렇지 않으면 아주대 학생이 오산대학교를 고르고 가입하는 식으로 학교 분리가 뚫린다.
    // 도메인이 아직 확인 안 된 학교는 검증을 건너뛴다(가입 자체를 막을 순 없으므로).
    private void requireMatchingDomain(University university, String email) {
        if (university == null) return;
        String domain = university.getEmailDomain();
        if (domain == null || domain.isBlank()) return;
        String emailLower = email.toLowerCase();
        String domainLower = domain.toLowerCase();
        if (!emailLower.endsWith("@" + domainLower) && !emailLower.endsWith("." + domainLower)) {
            throw new IllegalArgumentException(
                    university.getName() + "은(는) @" + domain + " 이메일로만 가입할 수 있습니다.");
        }
    }

    // 이메일 인증 링크 클릭 처리
    @Transactional
    public boolean verifyEmailToken(String token) {
        if (token == null || token.isBlank()) return false;
        Member member = memberRepository.findByVerificationToken(token).orElse(null);
        if (member == null) return false;
        return member.verifyEmail(token);
    }

    // 비밀번호를 이 횟수 이상 연속으로 틀리면 일시적으로 로그인을 잠근다(계정 단위 무차별 대입 방어,
    // IP 기반 rate limiter와는 별개 — 여러 IP를 돌려써도 계정 자체가 잠긴다).
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    // 잠금 지속 시간(분) — 이 시간이 지나야 다시 로그인 시도를 받아준다.
    private static final long LOGIN_LOCK_MINUTES = 15;

    // 로그인 인증 — 저장된 값이 BCrypt 해시면 matches()로, 아직 평문인 레거시 계정이면
    // 평문 비교 후 로그인 성공 시 그 자리에서 해시로 마이그레이션한다(강제 재설정 없이 전환).
    // 비밀번호는 맞아도 이메일 인증이 안 됐으면 로그인을 막는다.
    @Transactional
    public Member authenticate(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) return null;

        // 잠긴 상태면 비밀번호가 맞아도 거부 — 그래야 잠금 시간 동안 확실히 브루트포스를 막는다.
        if (member.isLoginLocked()) {
            long minutesLeft = java.time.Duration.between(java.time.LocalDateTime.now(), member.getLockedUntil())
                    .plusSeconds(59).toMinutes(); // 올림 처리(0분 남았다고 나오는 걸 방지)
            // MemberSuspendedException을 재사용 — AuthController가 이미 이 타입을 잡아서
            // 부가 상태(emailNotVerified 등) 없이 메시지만 그대로 보여주는 경로라, 계정 파일만
            // 고치는 범위에서 새 예외 타입/컨트롤러 catch절을 추가하지 않고 그대로 활용했다.
            throw new ac.kr.osan.unifeed.exception.MemberSuspendedException(
                    "로그인 시도가 너무 많습니다. " + Math.max(minutesLeft, 1) + "분 후 다시 시도해주세요.");
        }

        if (!checkPassword(member, rawPassword)) {
            member.increaseFailedLoginAttempts();
            if (member.getFailedLoginAttempts() >= MAX_FAILED_LOGIN_ATTEMPTS) {
                member.resetFailedLoginAttempts();
                member.lockLoginFor(LOGIN_LOCK_MINUTES);
            }
            return null;
        }
        member.resetFailedLoginAttempts();
        member.clearLoginLock();

        if (member.isSuspended()) {
            throw new ac.kr.osan.unifeed.exception.MemberSuspendedException("정지된 계정입니다. 문의가 필요하면 관리자에게 연락해주세요.");
        }

        if (!member.isEmailVerified()) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다. 가입 시 받은 메일함을 확인해주세요.");
        }
        return member;
    }

    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    // 로그인 2차 인증(이메일 OTP) 코드 발급 + 발송 — authenticate()로 비밀번호까지 확인된 회원에게만 호출한다.
    // id로 받아서 이 메서드 자신의 트랜잭션 안에서 다시 조회한다 — authenticate()가 이미 커밋하고 끝난
    // 뒤라 그때 반환받은 Member 객체는 detached 상태라서, 그걸 그대로 고쳐봐야 (open-in-view=false라)
    // 영속성 컨텍스트가 없어 변경 감지가 안 돼 DB에 반영되지 않는다(실제로 이렇게 짰다가 otp_code가
    // 계속 null로 저장 안 되는 걸 테스트하다 발견함).
    // 메일 발송 실패는 회원가입과 같은 이유로 삼켜서(sendVerificationEmailSafely 참고) 500으로 안 죽게 한다.
    @Transactional
    public void issueLoginOtp(Long memberId) {
        Member member = findById(memberId);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        member.issueOtp(code);
        try {
            mailService.sendLoginOtpEmail(member.getEmail(), code);
        } catch (IllegalStateException e) {
            System.err.println("로그인 인증 코드 발송 실패(" + member.getEmail() + "): " + e.getCause());
        }
    }

    // 로그인 2차 인증 코드 검증 — 맞으면 로그인을 완료할 회원을, 틀리거나 만료면 null을 반환한다.
    // 시도 횟수 제한은 호출부(AuthController)에서 RateLimiter로 따로 건다.
    @Transactional
    public Member verifyLoginOtp(Long memberId, String code) {
        Member member = findById(memberId);
        return member.verifyOtp(code) ? member : null;
    }

    // 마이페이지에서 2차 인증 켜고 끄기
    @Transactional
    public void setTwoFactorEnabled(Long memberId, boolean enabled) {
        findById(memberId).setTwoFactorEnabled(enabled);
    }

    // 비밀번호 검증 (로그인/회원탈퇴 공용) — 레거시 평문 계정이면 맞는 경우 그 자리에서 해시로 마이그레이션한다.
    private boolean checkPassword(Member member, String rawPassword) {
        String stored = member.getPassword();
        if (isBcryptHash(stored)) {
            return passwordEncoder.matches(rawPassword, stored);
        }
        if (stored != null && stored.equals(rawPassword)) {
            member.setPassword(passwordEncoder.encode(rawPassword));
            return true;
        }
        return false;
    }

    private boolean isBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    // 인증 메일 재전송 — 가입은 했지만 메일을 못 받았거나 놓친 경우.
    // 존재하지 않는 이메일이거나 이미 인증된 계정이면 조용히 실패(false) 처리한다.
    @Transactional
    public boolean resendVerification(String email) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null || member.isEmailVerified()) return false;
        member.issueVerificationToken(java.util.UUID.randomUUID().toString());
        sendVerificationEmailSafely(email, member.getVerificationToken());
        return true;
    }

    // 인증 메일 발송 실패(네트워크 차단, 메일 서버 인증 실패, 타임아웃 등)가 가입/재전송 자체를
    // 막지 않게 한다 — 안 잡으면 여기서 던진 예외가 @Transactional 메서드 밖까지 그대로 나가
    // 트랜잭션이 롤백되고(계정 자체가 안 만들어짐) 컨트롤러까지 500으로 죽는다.
    // 토큰은 이미 발급돼 저장돼 있으니, 메일만 실패해도 "인증 메일 재전송" 버튼으로 나중에 복구 가능하다.
    private void sendVerificationEmailSafely(String email, String token) {
        try {
            mailService.sendVerificationEmail(email, token);
        } catch (IllegalStateException e) {
            System.err.println("인증 메일 발송 실패(" + email + "): " + e.getCause());
        }
    }

    // 비밀번호 확인만 (탈퇴 전 흔적 정리처럼, 지우기 전에 본인인지부터 확인해야 하는 경우용).
    public boolean verifyPassword(Long memberId, String rawPassword) {
        return checkPassword(findById(memberId), rawPassword);
    }

    // 회원 탈퇴 — 비밀번호 재확인 후 삭제. 게시글/댓글/리뷰/북마크/시간표는 FK ON DELETE CASCADE로 함께 삭제된다.
    // [주의] 이걸 직접 부르면 맛집 평점/게시글 댓글수 같은 집계 값이 안 맞게 된다 —
    //        화면에서 들어오는 탈퇴는 MemberWithdrawalService.withdraw 를 쓴다.
    @Transactional
    public void withdraw(Long memberId, String rawPassword) {
        Member member = findById(memberId);
        if (!checkPassword(member, rawPassword)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        memberRepository.delete(member);
    }

    // 프로필 수정
    @Transactional
    public Member updateProfile(Long id, String name, String profileImage) {
        Member member = findById(id);
        member.updateProfile(name, profileImage);
        return member;
    }

    // 로그인한 상태에서 비밀번호 변경 — 현재 비밀번호 재확인 후 교체(비밀번호 찾기 이메일 인증 흐름과 별개).
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = findById(memberId);
        if (!checkPassword(member, currentPassword)) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        member.setPassword(passwordEncoder.encode(newPassword));
    }

    // 비밀번호 재설정 요청 — 가입된 이메일이면 재설정 링크를 메일로 보낸다.
    // 존재하지 않는 이메일이어도 예외 없이 조용히 끝낸다(이메일 존재 여부가 응답으로 드러나지 않게).
    @Transactional
    public void requestPasswordReset(String email) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) return;
        member.issuePasswordResetToken(java.util.UUID.randomUUID().toString());
        try {
            mailService.sendPasswordResetEmail(email, member.getResetToken());
        } catch (IllegalStateException e) {
            System.err.println("비밀번호 재설정 메일 발송 실패(" + email + "): " + e.getCause());
        }
    }

    public boolean isResetTokenValid(String token) {
        if (token == null || token.isBlank()) return false;
        return memberRepository.findByResetToken(token)
                .map(m -> m.isResetTokenValid(token))
                .orElse(false);
    }

    // 비밀번호 재설정 — 메일로 받은 토큰을 검증한 뒤에만 실제로 비밀번호를 바꾼다.
    @Transactional
    public boolean resetPasswordWithToken(String token, String newPassword) {
        Member member = memberRepository.findByResetToken(token).orElse(null);
        if (member == null || !member.isResetTokenValid(token)) return false;
        member.setPassword(passwordEncoder.encode(newPassword));
        member.clearResetToken();
        return true;
    }

}
