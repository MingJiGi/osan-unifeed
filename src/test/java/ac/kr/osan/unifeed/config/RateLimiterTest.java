package ac.kr.osan.unifeed.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

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

    @Test
    void 존재하지_않는_이메일처럼_한_번만_쓰이고_방치된_키는_스윕으로_정리된다() throws Exception {
        RateLimiter limiter = new RateLimiter();

        Field attemptsField = RateLimiter.class.getDeclaredField("attempts");
        attemptsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Deque<Instant>> attempts =
                (ConcurrentHashMap<String, Deque<Instant>>) attemptsField.get(limiter);

        // 공격자가 실존하지 않는 이메일 3개로 각각 딱 한 번씩만 재전송/재설정을 시도했다고 가정.
        for (int i = 0; i < 3; i++) {
            limiter.allow("resend:no-such-user-" + i + "@osan.ac.kr", 3, 600);
        }
        assertThat(attempts.size()).isEqualTo(3);

        // 스윕 대상이 되도록, 리플렉션으로 각 키의 마지막 타임스탬프를 아주 오래 전으로 조작한다.
        Instant longAgo = Instant.now().minusSeconds(10_000);
        for (int i = 0; i < 3; i++) {
            Deque<Instant> deque = attempts.get("resend:no-such-user-" + i + "@osan.ac.kr");
            deque.clear();
            deque.addLast(longAgo);
        }

        // allow()가 500번째 호출될 때마다 맵 전체를 훑어 오래 방치된 키를 정리한다(RateLimiter.SWEEP_EVERY_N_CALLS).
        for (int i = 0; i < 500; i++) {
            limiter.allow("other-active-key", 1_000_000, 600);
        }

        assertThat(attempts.containsKey("resend:no-such-user-0@osan.ac.kr")).isFalse();
        assertThat(attempts.containsKey("resend:no-such-user-1@osan.ac.kr")).isFalse();
        assertThat(attempts.containsKey("resend:no-such-user-2@osan.ac.kr")).isFalse();
    }
}
