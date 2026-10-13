package ac.kr.osan.unifeed.service;

import ac.kr.osan.unifeed.entity.University;
import ac.kr.osan.unifeed.entity.UniversityRequest;
import ac.kr.osan.unifeed.repository.UniversityRepository;
import ac.kr.osan.unifeed.repository.UniversityRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// ############################################################################
// UniversityRequestService — "내 학교가 목록에 없어요" 신청 접수 / 관리자 승인
// ----------------------------------------------------------------------------
// 승인하면 University 를 실제로 만들고, 신청자에게 "이제 가입할 수 있다"는 메일을 보낸다.
// 메일 발송이 실패해도 승인 자체는 롤백하지 않는다 — 가입 때와 같은 이유로, 외부 SMTP 사정 때문에
// 학교 등록이 취소되면 관리자가 같은 작업을 반복해야 한다(실제로 가입 500 에러로 겪었던 문제).
// ############################################################################
@Service
public class UniversityRequestService {

    private static final int NAME_MAX = 100;
    private static final int ADDRESS_MAX = 200;

    private final UniversityRequestRepository requestRepository;
    private final UniversityRepository universityRepository;
    private final MailService mailService;

    public UniversityRequestService(UniversityRequestRepository requestRepository,
                                    UniversityRepository universityRepository,
                                    MailService mailService) {
        this.requestRepository = requestRepository;
        this.universityRepository = universityRepository;
        this.mailService = mailService;
    }

    /** 가입 화면에서 들어오는 신청 접수(비로그인). */
    @Transactional
    public void create(String universityName, String address, String requesterEmail) {
        String name = trimOrThrow(universityName, "대학교 이름을 입력해주세요.", NAME_MAX, "대학교 이름");
        String addr = trimOrThrow(address, "학교 주소를 입력해주세요.", ADDRESS_MAX, "학교 주소");
        String email = trimOrThrow(requesterEmail, "이메일을 입력해주세요.", 100, "이메일");

        if (!email.matches("(?i)^[\\w.+-]+@([\\w-]+\\.)+ac\\.kr$")) {
            throw new IllegalArgumentException("대학교 공식 웹메일(@*.ac.kr)로만 신청할 수 있습니다.");
        }
        // 이미 등록된 학교면 신청받을 이유가 없다 — 검색으로 찾으라고 알려준다.
        if (!universityRepository.findByNameContainingOrderByNameAsc(name).isEmpty()) {
            throw new IllegalArgumentException("이미 등록된 학교입니다. 학교 검색에서 선택해주세요.");
        }
        if (requestRepository.existsByRequesterEmailAndUniversityNameAndStatus(
                email, name, UniversityRequest.Status.PENDING)) {
            throw new IllegalArgumentException("이미 신청하셨습니다. 승인되면 메일로 알려드립니다.");
        }

        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        requestRepository.save(new UniversityRequest(name, addr, email, domain));
    }

    public List<UniversityRequest> findPending() {
        return requestRepository.findByStatusOrderByCreatedAtDesc(UniversityRequest.Status.PENDING);
    }

    public List<UniversityRequest> findProcessed() {
        return requestRepository.findByStatusNotOrderByProcessedAtDesc(UniversityRequest.Status.PENDING);
    }

    public long pendingCount() {
        return requestRepository.countByStatus(UniversityRequest.Status.PENDING);
    }

    /**
     * 관리자 승인 — 실제 University 를 만든다.
     * 이름/도메인/지역/좌표는 승인 화면에서 관리자가 확인·수정한 값을 그대로 받는다
     * (신청자가 적은 학교명이 정식 명칭과 다를 수 있고, 좌표는 신청 폼에 아예 없다).
     * 좌표를 모르면 null 로 둔다 — 이 프로젝트 원칙대로 추측값을 넣지 않는다.
     */
    @Transactional
    public University approve(Long requestId, String name, String emailDomain, String regionName,
                              Double latitude, Double longitude) {
        UniversityRequest request = findById(requestId);
        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 신청입니다.");
        }

        String finalName = trimOrThrow(name, "대학교 이름을 입력해주세요.", NAME_MAX, "대학교 이름");
        if (universityRepository.findAllByOrderByNameAsc().stream()
                .anyMatch(u -> u.getName().equalsIgnoreCase(finalName))) {
            throw new IllegalArgumentException("같은 이름의 학교가 이미 등록되어 있습니다.");
        }
        // 좌표는 둘 다 있을 때만 저장한다 — 하나만 있으면 지도가 엉뚱한 곳을 가리킨다.
        boolean hasCoords = latitude != null && longitude != null;

        University university = universityRepository.save(new University(
                finalName,
                blankToNull(emailDomain),
                blankToNull(regionName),
                hasCoords ? latitude : null,
                hasCoords ? longitude : null));

        request.approve();

        try {
            mailService.sendUniversityApprovedEmail(request.getRequesterEmail(), finalName);
        } catch (RuntimeException e) {
            // 메일이 안 나가도 학교는 등록된 상태로 둔다(승인을 되돌리지 않는다).
            System.err.println("학교 등록 승인 메일 발송 실패(등록 자체는 완료): " + e.getMessage());
        }
        return university;
    }

    @Transactional
    public void reject(Long requestId) {
        UniversityRequest request = findById(requestId);
        if (!request.isPending()) {
            throw new IllegalStateException("이미 처리된 신청입니다.");
        }
        request.reject();
    }

    private UniversityRequest findById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("신청을 찾을 수 없습니다. id=" + id));
    }

    private static String trimOrThrow(String value, String blankMessage, int max, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + "은(는) " + max + "자 이내여야 합니다.");
        }
        return trimmed;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
