package ac.kr.osan.unifeed.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

// 아주 단순한 메모리 기반 요청 횟수 제한기(단일 서버 인스턴스 기준).
// 무차별 대입 로그인, 이메일 폭탄(인증/재설정 메일 스팸 발송) 같은 남용을 막기 위한 최소한의 방어.
// ponytail: 별도 라이브러리(Bucket4j 등) 없이 표준 라이브러리(ConcurrentHashMap)만으로 충분한 수준.
// 여러 서버 인스턴스로 수평 확장하면 인스턴스별로 따로 카운트되므로, 그 시점엔 Redis 기반으로 바꿔야 함.
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    // key(보통 "용도:IP" 또는 "용도:이메일") 기준으로 windowSeconds 동안 maxAttempts 회를 넘기면 false.
    public boolean allow(String key, int maxAttempts, int windowSeconds) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
