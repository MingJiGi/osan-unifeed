package ac.kr.osan.unifeed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// ############################################################################
// PostForm — 글쓰기/수정 <form> 의 입력을 담는 DTO
// ----------------------------------------------------------------------------
// 엔티티(Post)를 폼에 직접 쓰지 않는 이유는 spring-data-jpas / 이 예제 주석 참고.
// authorId = 작성자를 드롭다운에서 고른 회원의 id.
// ############################################################################
public class PostForm {

	// 작성자(회원) 선택값. 새 글 작성 시 필수.
	private Long authorId;

	@NotBlank(message = "제목을 입력하세요.")
	@Size(max = 200, message = "제목은 200자 이내여야 합니다.")
	private String title;

	@NotBlank(message = "내용을 입력하세요.")
    @Size(max = 500, message = "내용은 500자 이내여야 합니다.")
	private String content;

    @Size(max = 300, message = "해시태그는 300자 이내여야 합니다")
    private String tags;

    @Size(max = 500, message = "이미지 URL은 500자 이내여야 합니다")
    private String imgUrl;

	public PostForm() {
	}

	public PostForm(Long authorId, String title, String content) {
		this.authorId = authorId;
		this.title = title;
		this.content = content;
	}

	public Long getAuthorId() {
		return authorId;
	}

	public void setAuthorId(Long authorId) {
		this.authorId = authorId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

    public String getTags() {return tags;}

    public void setTags(String tags) {this.tags = tags;}

    public String getImgUrl() {return imgUrl;}

    public void setImgUrl(String imgUrl) {this.imgUrl = imgUrl;}
}
