package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// ############################################################################
// UniversityRequest — "내 학교가 목록에 없어요" 등록 신청
// ----------------------------------------------------------------------------
// [왜 필요한가]
// 가입 화면은 등록된 학교 중에서 고르게 되어 있고 university는 필수값이라,
// 목록에 없는 학교 학생은 가입 자체가 불가능했다(검색 결과 안내문은 "비워두고 가입하라"고
// 적혀 있었지만 실제로는 폼 검증에 걸려 막힌다). 그 사람들이 남길 수 있는 창구가 이 신청이다.
//
// [흐름]
// 가입 화면에서 학교 검색 -> 결과 없음 -> 신청 폼 -> 여기 저장(PENDING)
//   -> 관리자가 /admin/university-requests 에서 확인 -> 승인하면 University 생성 + 신청자에게 메일
//   -> 신청자가 다시 와서 정상 가입
//
// 신청자는 비로그인 상태라 Member와 연결하지 않고 이메일만 남긴다(승인 메일을 보낼 주소).
// ############################################################################
@Entity
@Table(name = "osan_university_request")
public class UniversityRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "university_name", nullable = false, length = 100)
    private String universityName;

    @Column(nullable = false, length = 200)
    private String address;

    // 신청자 이메일 — 승인됐을 때 "이제 가입하실 수 있습니다" 안내를 보낼 주소.
    @Column(name = "requester_email", nullable = false, length = 100)
    private String requesterEmail;

    // 신청자 이메일에서 뽑아둔 도메인(예: kmu.ac.kr). 관리자가 승인할 때 기본값으로 쓴다 —
    // 이 도메인이 곧 "그 학교 메일로만 가입 가능"의 기준이 되므로 승인 화면에서 수정할 수 있다.
    @Column(name = "email_domain", length = 100)
    private String emailDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected UniversityRequest() {
    }

    public UniversityRequest(String universityName, String address, String requesterEmail, String emailDomain) {
        this.universityName = universityName;
        this.address = address;
        this.requesterEmail = requesterEmail;
        this.emailDomain = emailDomain;
        this.status = Status.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUniversityName() { return universityName; }
    public String getAddress() { return address; }
    public String getRequesterEmail() { return requesterEmail; }
    public String getEmailDomain() { return emailDomain; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void approve() {
        this.status = Status.APPROVED;
        this.processedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = Status.REJECTED;
        this.processedAt = LocalDateTime.now();
    }
}
