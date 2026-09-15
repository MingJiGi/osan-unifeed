package ac.kr.osan.unifeed.entity;

import ac.kr.osan.unifeed.entity.Member;
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
// Post 엔티티 — 게시글 한 개 = post 테이블 한 줄
// ----------------------------------------------------------------------------
// 회원(Member)과의 관계: 게시글 여러 개가 회원 한 명에게 속한다 → N : 1.
// 그래서 Post 쪽에 @ManyToOne 을 둔다(외래키 author_id 가 post 테이블에 생긴다).
// ############################################################################
@Entity
@Table(name = "osan_post") // 공유 toydb 에서 다른 프로젝트와 안 섞이도록 osan_ 프리픽스
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	// ------------------------------------------------------------------------
	// 작성자 — N:1. 여러 Post 가 한 Member 를 가리킨다.
	// ------------------------------------------------------------------------
	// fetch = LAZY : author 를 실제로 쓸 때까지 DB 에서 안 읽어온다(성능 기본기).
	//   [주의] LAZY 라서, 트랜잭션이 끝난 뒤(=화면 렌더링 시점)에 author 를 처음 건드리면
	//          LazyInitializationException 이 난다(open-in-view=false 로 뒀기 때문).
	//          그래서 목록/상세 조회 쿼리에서 "join fetch" 로 author 를 미리 함께 읽어온다.
	//          (PostRepository 의 @Query 참고. 이게 N+1 문제를 피하는 방법이기도 하다)
	// @JoinColumn : post 테이블에 만들어질 외래키 컬럼 이름.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "author_id", nullable = false)
	private Member author;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

    @Column(length = 500)
    private String imgUrl;

    @Column(length = 300)
    private String tags;

    @Column(nullable = false)
    private int commentCount = 0;

    @Column(nullable = false)
    private int likeCount = 0;



    protected Post() {
	}

	public Post(String title, String content, Member author) {
		this.title = title;
		this.content = content;
		this.author = author;
	}

	@jakarta.persistence.PrePersist
	void onCreate() {
		this.createdAt = LocalDateTime.now();
	}

	// 제목/내용만 수정한다. 작성자는 바꾸지 않는다(현실의 게시판처럼).
	public void edit(String title, String content) {
		this.title = title;
		this.content = content;
	}
    // 좋아요 메서드
    public void increaseLikeCount(){
        this.likeCount++;
    }
    // 좋아요 감소 메서드
    public void decreaseLikeCount(){
        if (this.likeCount > 0){
            this.likeCount--;
        }
    }

    // 댓글 수 증감 메서드
    public void increaseCommentCount() {
        this.commentCount++;
    }

    public void decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public Member getAuthor() {
		return author;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

    public String getImgUrl() {return imgUrl;}

    public void setImgUrl(String imgUrl) {this.imgUrl = imgUrl;}

    public String getTags() {return tags;}

    public int getCommentCount() {return commentCount;}

    public int getLikeCount() {return likeCount;}

    public void setTags(String tags) {this.tags = tags;}
}
