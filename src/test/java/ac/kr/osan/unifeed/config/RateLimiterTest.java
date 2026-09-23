package ac.kr.osan.unifeed.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void 제한_횟수_이내면_허용한다() {
        RateLimiter limiter = new RateLimiter();

        assertThat(limiter.allow("key", 3, 60)).isTrue();
        assertThat(limiter.allow("key", 3, 60)).isTrue();
        assertThat(limiter.allow("key", 3, 60)).isTrue();
    }

    @Test
    void 제한_횟수를_넘으면_거부한다() {
        RateLimiter limiter = new RateLimiter();

        limiter.allow("key", 3, 60);
        limiter.allow("key", 3, 60);
        limiter.allow("key", 3, 60);

        assertThat(limiter.allow("key", 3, 60)).isFalse();
    }

    @Test
    void 서로_다른_키는_독립적으로_카운트된다() {
        RateLimiter limiter = new RateLimiter();

        limiter.allow("key-a", 1, 60);

        assertThat(limiter.allow("key-b", 1, 60)).isTrue();
    }

    @Test
    void 윈도우가_지나면_다시_허용된다() throws Exception {
        RateLimiter limiter = new RateLimiter();

        // windowSeconds=0 이면 바로 만료된 것으로 취급되어 매번 허용되어야 한다.
        assertThat(limiter.allow("key", 1, 0)).isTrue();
        assertThat(limiter.allow("key", 1, 0)).isTrue();
    }
}
