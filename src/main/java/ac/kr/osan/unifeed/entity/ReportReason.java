package ac.kr.osan.unifeed.entity;

public enum ReportReason {
    SPAM("스팸/광고"),
    ABUSE("욕설/비방"),
    FALSE_INFO("허위 정보"),
    ADULT("음란물/불법 콘텐츠"),
    ETC("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
