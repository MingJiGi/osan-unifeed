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

    @Column(length = 300)
    private String tags; // 등록자가 입력한 쉼표 구분 태그 (예: "가성비,단체모임,혼밥")

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(nullable = false)
    private Integer reviewCount = 0;

    @Column(nullable = false)
    private Integer bookmarkCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 소속 대학교 — 등록한 회원의 university를 그대로 물려받는다(멀티캠퍼스 지도 분리 기준).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id")
    private University university;

    protected Restaurant() {
    }

    public Restaurant(String name, String category, String address, String phone,
                      Double latitude, Double longitude, String description, String imgUrl) {
        this(name, category, address, phone, latitude, longitude, description, imgUrl, null);
    }

    public Restaurant(String name, String category, String address, String phone,
                      Double latitude, Double longitude, String description, String imgUrl, String tags) {
        this.name = name;
        this.category = category;
        this.address = address;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.imgUrl = imgUrl;
        this.tags = tags;
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
    public String getTags() { return tags; }
    public Double getRating() { return rating; }
    public Integer getReviewCount() { return reviewCount; }
    public Integer getBookmarkCount() { return bookmarkCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public University getUniversity() { return university; }

    public void setUniversity(University university) {
        this.university = university;
    }

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

    public void setDescription(String description) {
        this.description = description;
    }

    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }

    /**
     * AI가 만든 소개/태그 채우기 — 사람이 적어둔 값은 절대 덮지 않는다.
     * 비어 있는 칸만 채우므로, 등록자가 직접 쓴 소개글이 AI 문장으로 바뀌는 일은 없다.
     * 컬럼 길이(description 500, tags 300)보다 길게 오면 저장 시 DB에서 터지므로 여기서 자른다.
     */
    public void fillEmptyAiHints(String aiDescription, String aiTags) {
        if ((description == null || description.isBlank()) && aiDescription != null && !aiDescription.isBlank()) {
            this.description = cut(aiDescription.trim(), 500);
        }
        if ((tags == null || tags.isBlank()) && aiTags != null && !aiTags.isBlank()) {
            this.tags = cut(aiTags.trim(), 300);
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
