package ac.kr.osan.unifeed.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "osan_post_like", uniqueConstraints = {@UniqueConstraint(name = "uq_osan_post_member", columnNames = {"post_id", "member_id"})})
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PostLike(){

    }
    public PostLike(Post post, Member member){
        this.post = post;
        this.member = member;
    }
    @PrePersist
    void onCreate(){
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Member getMember() {
        return member;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
