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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "osan_restaurant_bookmark", uniqueConstraints = {
        @UniqueConstraint(name = "uq_osan_rest_bookmark", columnNames = {"restaurant_id", "member_id"})
})
public class RestaurantBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RestaurantBookmark() {
    }

    public RestaurantBookmark(Restaurant restaurant, Member member) {
        this.restaurant = restaurant;
        this.member = member;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Restaurant getRestaurant() { return restaurant; }
    public Member getMember() { return member; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
