package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.Member;
import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.repository.MemberRepository;
import ac.kr.osan.unifeed.repository.UniversityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// ############################################################################
// MemberService 테스트 — 보안 핵심 로직(로그인 인증, 비밀번호 재설정, 회원탈퇴)만 검증한다.
// ----------------------------------------------------------------------------
// 순수 Mockito 단위 테스트: Spring 컨텍스트/실제 DB 없이 Member 엔티티는 실제 객체를 쓰고
// Repository/MailService 만 목(mock)으로 대체한다. 이 세션에서 실제 MySQL + 격리 서버로
// 수동 검증했던 시나리오(비밀번호 재설정 취약점 수정 등)를 회귀 테스트로 고정해두는 목적.
// ############################################################################
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    UniversityRepository universityRepository;

    @Mock
    MailService mailService;

    MemberService memberService;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    MemberServiceTest() {
        // MemberService 생성자가 MailService 를 요구하므로 매번 새로 만든다(테스트 간 상태 공유 방지).
    }

    private MemberService newService() {
        return new MemberService(memberRepository, universityRepository, mailService);
    }

    @Test
    void 올바른_비밀번호면_로그인에_성공한다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        member.verifyEmail(issueAndReturn(member)); // 이메일 인증 완료 상태로 만든다
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        Member result = memberService.authenticate("kim@test.ac.kr", "password123");

        assertThat(result).isSameAs(member);
    }

    @Test
    void 틀린_비밀번호면_로그인에_실패한다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        member.verifyEmail(issueAndReturn(member));
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        Member result = memberService.authenticate("kim@test.ac.kr", "wrong-password");

        assertThat(result).isNull();
    }

    @Test
    void 레거시_평문_비밀번호는_로그인_성공시_자동으로_해시로_전환된다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", "plaintext123"); // 해시 아님
        member.verifyEmail(issueAndReturn(member));
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        Member result = memberService.authenticate("kim@test.ac.kr", "plaintext123");

        assertThat(result).isSameAs(member);
        assertThat(member.getPassword()).startsWith("$2a$"); // 마이그레이션되어 bcrypt 해시로 바뀜
        assertThat(member.getPassword()).isNotEqualTo("plaintext123");
    }

    @Test
    void 이메일_미인증_계정은_비밀번호가_맞아도_로그인이_차단된다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        // 이메일 인증을 안 한 상태(기본값 false)로 둔다.
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.authenticate("kim@test.ac.kr", "password123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 가입시_선택한_대학교가_회원에_저장된다() {
        memberService = newService();
        University ajou = new University("아주대학교", "ajou.ac.kr", "수원", null, null);
        when(memberRepository.findByEmail("new@ajou.ac.kr")).thenReturn(Optional.empty());
        when(universityRepository.findById(12L)).thenReturn(Optional.of(ajou));
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = memberService.signup("김철수", "new@ajou.ac.kr", "password123", 12L);

        assertThat(saved.getUniversity()).isSameAs(ajou);
        verify(mailService).sendVerificationEmail(eq("new@ajou.ac.kr"), anyString());
    }

    @Test
    void 학교를_선택하지_않고도_가입할_수_있다() {
        memberService = newService();
        when(memberRepository.findByEmail("new2@test.ac.kr")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = memberService.signup("김철수", "new2@test.ac.kr", "password123", null);

        assertThat(saved.getUniversity()).isNull();
        verify(universityRepository, never()).findById(any());
    }

    @Test
    void 비밀번호를_5회_연속_틀리면_로그인이_일시적으로_잠긴다() {
        // [변경됨] 예전에는 5회 실패 시 이메일 재인증을 강제했지만, 공격자가 피해자 이메일만 알아도
        // 틀린 비번 5번으로 피해자를 "재인증부터 다시" 상태로 밀어넣을 수 있어서(DoS),
        // 파괴적이지 않은 일시 잠금(lockedUntil)으로 바뀌었다. 이 테스트도 그 기준으로 검증한다.
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        member.verifyEmail(issueAndReturn(member));
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        for (int i = 0; i < 4; i++) {
            assertThat(memberService.authenticate("kim@test.ac.kr", "wrong")).isNull();
        }
        assertThat(member.isLoginLocked()).isFalse(); // 4회까지는 아직 안 잠김

        assertThat(memberService.authenticate("kim@test.ac.kr", "wrong")).isNull(); // 5번째

        assertThat(member.isLoginLocked()).isTrue();
        assertThat(member.isEmailVerified()).isTrue();      // 인증 상태는 건드리지 않는다(비파괴적)
        assertThat(member.getFailedLoginAttempts()).isEqualTo(0); // 잠근 뒤엔 카운터도 리셋

        // 잠긴 동안은 올바른 비밀번호를 넣어도 막힌다
        assertThatThrownBy(() -> memberService.authenticate("kim@test.ac.kr", "password123"))
                .isInstanceOf(ac.kr.osan.unifeed.exception.MemberSuspendedException.class)
                .hasMessageContaining("로그인 시도가 너무 많습니다");
    }

    @Test
    void 로그인에_성공하면_연속실패_카운터가_리셋된다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        member.verifyEmail(issueAndReturn(member));
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        memberService.authenticate("kim@test.ac.kr", "wrong");
        memberService.authenticate("kim@test.ac.kr", "wrong");
        assertThat(member.getFailedLoginAttempts()).isEqualTo(2);

        Member result = memberService.authenticate("kim@test.ac.kr", "password123");

        assertThat(result).isSameAs(member);
        assertThat(member.getFailedLoginAttempts()).isEqualTo(0);
    }

    @Test
    void 비밀번호_재설정_요청시_존재하는_회원이면_토큰을_발급하고_메일을_보낸다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("oldpass123"));
        when(memberRepository.findByEmail("kim@test.ac.kr")).thenReturn(Optional.of(member));

        memberService.requestPasswordReset("kim@test.ac.kr");

        assertThat(member.getResetToken()).isNotNull();
        verify(mailService).sendPasswordResetEmail(eq("kim@test.ac.kr"), anyString());
    }

    @Test
    void 존재하지_않는_이메일로_재설정_요청해도_예외없이_조용히_끝나고_메일도_안보낸다() {
        memberService = newService();
        when(memberRepository.findByEmail("nobody@test.ac.kr")).thenReturn(Optional.empty());

        memberService.requestPasswordReset("nobody@test.ac.kr"); // 예외 없이 통과해야 함

        verify(mailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void 올바른_토큰이면_비밀번호가_재설정되고_토큰은_1회용으로_소멸한다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("oldpass123"));
        member.issuePasswordResetToken("valid-token");
        when(memberRepository.findByResetToken("valid-token")).thenReturn(Optional.of(member));

        boolean ok = memberService.resetPasswordWithToken("valid-token", "newpass456");

        assertThat(ok).isTrue();
        assertThat(encoder.matches("newpass456", member.getPassword())).isTrue();
        assertThat(member.getResetToken()).isNull(); // 재사용 방지
    }

    @Test
    void 틀린_토큰이면_비밀번호_재설정을_거부하고_비밀번호는_그대로_유지된다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("oldpass123"));
        member.issuePasswordResetToken("valid-token");
        when(memberRepository.findByResetToken("wrong-token")).thenReturn(Optional.empty());

        boolean ok = memberService.resetPasswordWithToken("wrong-token", "hijacked789");

        assertThat(ok).isFalse();
        assertThat(encoder.matches("oldpass123", member.getPassword())).isTrue(); // 안 바뀜
    }

    @Test
    void 만료된_토큰이면_비밀번호_재설정을_거부한다() throws Exception {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("oldpass123"));
        member.issuePasswordResetToken("expired-token");
        // 리플렉션으로 만료시각을 과거로 강제 조정(테스트 전용 우회 — 프로덕션 코드엔 없음)
        var field = Member.class.getDeclaredField("resetTokenExpiry");
        field.setAccessible(true);
        field.set(member, java.time.LocalDateTime.now().minusMinutes(1));
        when(memberRepository.findByResetToken("expired-token")).thenReturn(Optional.of(member));

        boolean ok = memberService.resetPasswordWithToken("expired-token", "newpass456");

        assertThat(ok).isFalse();
    }

    @Test
    void 회원탈퇴는_비밀번호가_맞아야만_삭제된다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        memberService.withdraw(1L, "password123");

        verify(memberRepository).delete(member);
    }

    @Test
    void 회원탈퇴는_비밀번호가_틀리면_삭제하지_않고_예외를_던진다() {
        memberService = newService();
        Member member = new Member("김철수", "kim@test.ac.kr", encoder.encode("password123"));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.withdraw(1L, "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(memberRepository, never()).delete(any());
    }

    // 이메일 인증 토큰을 발급하고 그 토큰 문자열을 바로 돌려주는 테스트 헬퍼.
    private String issueAndReturn(Member member) {
        member.issueVerificationToken("test-verify-token");
        return "test-verify-token";
    }
}
