package ac.kr.osan.unifeed.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SignupForm {

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 50, message = "이름은 50자 이내여야합니다.")
    private String name;

    @NotBlank(message = "이메일을 입력하세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100)
    @Pattern(regexp = "(?i)^[\\w.+-]+@([\\w-]+\\.)+ac\\.kr$", message = "대학교 공식 웹메일(@*.ac.kr)로만 가입할 수 있습니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력하세요")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력하세요.")
    private String passwordConfirm;

    // 필수 — university가 null이면 같은 학교 여부를 비교하는 모든 로직(글 조회/좋아요/댓글, 맛집 목록)이
    // 영구히 막혀버리므로(post-signup에 대학을 나중에 설정할 방법도 없음) 가입 시점에 반드시 선택하도록 강제한다.
    @NotNull(message = "소속 대학교를 선택해주세요.")
    private Long universityId;

    public void setUniversityId(Long universityId) {
        this.universityId = universityId;
    }

    public Long getUniversityId() {
        return universityId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }
}
