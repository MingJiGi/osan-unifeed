package ac.kr.osan.unifeed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "osan_restaurant")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category; // 한식, 분식, 카페, 양식, 일식 등

    @Column(nullable = false, length = 200)
    private String address;

    @Column(length = 50)
    private String phone;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String imgUrl;

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Column(nullable = false)
    private Integer bookmarkCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Restaurant() {
    }

    public Restaurant(String name, String category, String address, String phone,
                      Double latitude, Double longitude, String description, String imgUrl) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.imgUrl = imgUrl;
        this.rating = 0.0;
        this.reviewCount = 0;
        this.bookmarkCount = 0;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getDescription() { return description; }
    public String getImgUrl() { return imgUrl; }
    public Double getRating() { return rating; }
    public Integer getReviewCount() { return reviewCount; }
    public Integer getBookmarkCount() { return bookmarkCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setRating(Double rating) {
        this.rating = Math.round(rating * 10.0) / 10.0;
    }

    public void increaseReviewCount() {
        this.reviewCount = (this.reviewCount == null ? 0 : this.reviewCount) + 1;
    }

    public void decreaseReviewCount() {
        if (this.reviewCount != null && this.reviewCount > 0) {
            this.reviewCount--;
        }
    }

    public void increaseBookmarkCount() {
        this.bookmarkCount = (this.bookmarkCount == null ? 0 : this.bookmarkCount) + 1;
    }

    public void decreaseBookmarkCount() {
        if (this.bookmarkCount != null && this.bookmarkCount > 0) {
            this.bookmarkCount--;
        }
    }
}
