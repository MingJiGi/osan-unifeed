package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.repository.MemberRepository;
import ac.kr.osan.unifeed.entity.Member;

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
	private final MailService mailService;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public MemberService(MemberRepository memberRepository, MailService mailService) {
		this.memberRepository = memberRepository;
		this.mailService = mailService;
	}

	// 회원 목록(최신 가입순).
	public List<Member> findAll() {
		return memberRepository.findAllByOrderByCreatedAtDesc();
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
    public Member signup(String name, String email, String password){
        Member existing = memberRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            if (existing.isEmailVerified()) {
                return null; // 이미 가입 완료된 이메일
            }
            existing.issueVerificationToken(java.util.UUID.randomUUID().toString());
            mailService.sendVerificationEmail(email, existing.getVerificationToken());
            return existing;
        }

        Member member = new Member(name, email, passwordEncoder.encode(password));
        member.issueVerificationToken(java.util.UUID.randomUUID().toString());
        Member saved = memberRepository.save(member);
        mailService.sendVerificationEmail(email, saved.getVerificationToken());
        return saved;
    }

    // 이메일 인증 링크 클릭 처리
    @Transactional
    public boolean verifyEmailToken(String token) {
        if (token == null || token.isBlank()) return false;
        Member member = memberRepository.findByVerificationToken(token).orElse(null);
        if (member == null) return false;
        return member.verifyEmail(token);
    }

    // 로그인 인증 — 저장된 값이 BCrypt 해시면 matches()로, 아직 평문인 레거시 계정이면
    // 평문 비교 후 로그인 성공 시 그 자리에서 해시로 마이그레이션한다(강제 재설정 없이 전환).
    // 비밀번호는 맞아도 이메일 인증이 안 됐으면 로그인을 막는다.
    @Transactional
    public Member authenticate(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) return null;

        String stored = member.getPassword();
        boolean passwordOk;
        if (isBcryptHash(stored)) {
            passwordOk = passwordEncoder.matches(rawPassword, stored);
        } else if (stored != null && stored.equals(rawPassword)) {
            member.setPassword(passwordEncoder.encode(rawPassword));
            passwordOk = true;
        } else {
            passwordOk = false;
        }
        if (!passwordOk) return null;

        if (!member.isEmailVerified()) {
            throw new IllegalStateException("이메일 인증이 완료되지 않았습니다. 가입 시 받은 메일함을 확인해주세요.");
        }
        return member;
    }

    private boolean isBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    // 프로필 수정
    @Transactional
    public Member updateProfile(Long id, String name, String profileImage) {
        Member member = findById(id);
        member.updateProfile(name, profileImage);
        return member;
    }

    // 비밀번호 재설정 — 이 앱엔 메일 발송 기능이 없어서, 가입 이메일 확인만으로 새 비밀번호를 받는다.
    @Transactional
    public boolean resetPassword(String email, String newPassword) {
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) return false;
        member.setPassword(passwordEncoder.encode(newPassword));
        return true;
    }

}
