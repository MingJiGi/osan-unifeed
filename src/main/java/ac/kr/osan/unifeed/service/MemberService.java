package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.repository.MemberRepository;
import ac.kr.osan.unifeed.entity.Member;

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

	public MemberService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
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
	public Member register(String name, String email) {
		return memberRepository.save(new Member(name, email));
	}
}
