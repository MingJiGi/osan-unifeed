package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "osan_restaurant_review")
public class RestaurantReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private Integer rating = 5; // 1 ~ 5 점

    @Column(length = 500)
    private String imgUrl; // 리뷰 사진 (선택)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RestaurantReview() {
    }

    public RestaurantReview(String content, Integer rating, Restaurant restaurant, Member author) {
        this.content = content;
        this.rating = (rating == null || rating < 1) ? 1 : Math.min(rating, 5);
        this.restaurant = restaurant;
        this.author = author;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getContent() { return content; }
    public Integer getRating() { return rating; }
    public Restaurant getRestaurant() { return restaurant; }
    public Member getAuthor() { return author; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getImgUrl() { return imgUrl; }
    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }

    // 리뷰 수정 (본인 리뷰만 — 소유 확인은 서비스 계층에서 한다)
    public void update(String content, Integer rating, String imgUrl) {
        this.content = content;
        this.rating = (rating == null || rating < 1) ? 1 : Math.min(rating, 5);
        this.imgUrl = (imgUrl == null || imgUrl.isBlank()) ? null : imgUrl;
    }
}
