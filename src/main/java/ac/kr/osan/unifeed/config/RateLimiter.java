package ac.kr.osan.unifeed.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

// 아주 단순한 메모리 기반 요청 횟수 제한기(단일 서버 인스턴스 기준).
// 무차별 대입 로그인, 이메일 폭탄(인증/재설정 메일 스팸 발송) 같은 남용을 막기 위한 최소한의 방어.
// ponytail: 별도 라이브러리(Bucket4j 등) 없이 표준 라이브러리(ConcurrentHashMap)만으로 충분한 수준.
// 여러 서버 인스턴스로 수평 확장하면 인스턴스별로 따로 카운트되므로, 그 시점엔 Redis 기반으로 바꿔야 함.
@Component
public class RateLimiter {

    // AuthController가 실존 여부 확인 전에 "resend:"+email, "pwreset:"+email 같은 키로
    // 이 맵을 채운다. 존재하지 않는 이메일을 계속 다른 값으로 넣으면 키 자체가 영원히 안 지워지는
    // 메모리 누수가 생길 수 있어서, 오래 방치된(=재요청이 없는) 키를 주기적으로 청소한다.
    private static final long SWEEP_EVERY_N_CALLS = 500;
    private static final long IDLE_EVICT_SECONDS = 7200; // 실제 사용 중인 windowSeconds 최댓값(3600)보다 넉넉히 큼

    private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final AtomicLong callCount = new AtomicLong();

    // key(보통 "용도:IP" 또는 "용도:이메일") 기준으로 windowSeconds 동안 maxAttempts 회를 넘기면 false.
    public boolean allow(String key, int maxAttempts, int windowSeconds) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        boolean allowed;
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                allowed = false;
            } else {
                timestamps.addLast(now);
                allowed = true;
            }
        }

        // 이번 호출이 거부된 게 아니라면(=allowed) 방금 now를 추가했으니 deque는 절대 비어있지 않다.
        // 그래도 compute로 정리해두면 (호출이 거부된 채로 끝나 deque가 비어버리는) 엣지 케이스와
        // 다른 스레드와의 레이스를 원자적으로 처리할 수 있다.
        attempts.compute(key, (k, v) -> (v == null || v.isEmpty()) ? null : v);

        // ponytail: "이 key만" 정리하는 위 compute는 방금 막 쓰인 key엔 사실상 무의미하다(항상 재사용되므로).
        // 진짜 누수는 "한 번 쓰이고 다시는 안 불리는" key들이라, N번 호출마다 맵 전체를 훑어
        // 오랫동안(IDLE_EVICT_SECONDS) 방치된 key를 정리한다. 트래픽이 아주 커지면 스케줄러 기반으로 바꿀 것.
        if (callCount.incrementAndGet() % SWEEP_EVERY_N_CALLS == 0) {
            sweepIdleKeys(now);
        }

        return allowed;
    }

    private void sweepIdleKeys(Instant now) {
        Instant idleCutoff = now.minusSeconds(IDLE_EVICT_SECONDS);
        for (String k : attempts.keySet()) {
            attempts.computeIfPresent(k, (kk, v) -> {
                Instant last = v.peekLast();
                return (last == null || last.isBefore(idleCutoff)) ? null : v;
            });
        }
    }
}
