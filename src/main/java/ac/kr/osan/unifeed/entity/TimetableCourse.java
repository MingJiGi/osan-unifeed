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
@Table(name = "osan_timetable_course")
public class TimetableCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 100)
    private String semester;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String info;

    @Column(name = "day_col", nullable = false)
    private Integer dayCol; // 2=월 ~ 6=금

    @Column(nullable = false, length = 30)
    private String color; // course-pink 등

    @Column(name = "start_hour", nullable = false)
    private Integer startHour;

    @Column(name = "start_min", nullable = false)
    private Integer startMin;

    @Column(name = "end_hour", nullable = false)
    private Integer endHour;

    @Column(name = "end_min", nullable = false)
    private Integer endMin;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TimetableCourse() {
    }

    public TimetableCourse(Member member, String semester, String name, String info, Integer dayCol, String color,
                            Integer startHour, Integer startMin, Integer endHour, Integer endMin) {
        this.member = member;
        this.semester = semester;
        this.name = name;
        this.info = info;
        this.dayCol = dayCol;
        this.color = color;
        this.startHour = startHour;
        this.startMin = startMin;
        this.endHour = endHour;
        this.endMin = endMin;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 수업 수정 (시간표 카드 클릭 -> 수정 모달 저장)
    public void update(String semester, String name, String info, Integer dayCol, String color,
                        Integer startHour, Integer startMin, Integer endHour, Integer endMin) {
        this.semester = semester;
        this.name = name;
        this.info = info;
        this.dayCol = dayCol;
        this.color = color;
        this.startHour = startHour;
        this.startMin = startMin;
        this.endHour = endHour;
        this.endMin = endMin;
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public String getSemester() { return semester; }
    public String getName() { return name; }
    public String getInfo() { return info; }
    public Integer getDayCol() { return dayCol; }
    public String getColor() { return color; }
    public Integer getStartHour() { return startHour; }
    public Integer getStartMin() { return startMin; }
    public Integer getEndHour() { return endHour; }
    public Integer getEndMin() { return endMin; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
