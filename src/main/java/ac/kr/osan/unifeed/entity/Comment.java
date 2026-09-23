package ac.kr.osan.unifeed.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "osan_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Transient
    private java.util.List<Comment> replies = new java.util.ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 신고 누적 자동 숨김 — 자리는 남기고(답글 트리 구조 유지) 내용만 안내문구로 대체된다.
    @Column(nullable = false)
    private boolean hidden = false;

    protected Comment() {
    }

    public Comment(String content, Post post, Member author) {
        this(content, post, author, null);
    }

    public Comment(String content, Post post, Member author, Comment parent) {
        this.content = content;
        this.post = post;
        this.author = author;
        this.parent = parent;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public Post getPost() { return post; }
    public Member getAuthor() { return author; }
    public Comment getParent() { return parent; }
    public java.util.List<Comment> getReplies() { return replies; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void edit(String newContent) {
        this.content = newContent;
    }

    public boolean isHidden() {return hidden;}

    public void hide() {this.hidden = true;}

    public void unhide() {this.hidden = false;}
}