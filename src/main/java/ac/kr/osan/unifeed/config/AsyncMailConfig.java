package ac.kr.osan.unifeed.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

// ############################################################################
// AsyncMailConfig — 메일 발송을 요청 스레드 밖으로 빼기 위한 설정
// ----------------------------------------------------------------------------
// [왜 필요한가]
// 예전에는 회원가입/2단계 인증 요청이 들어오면 그 요청 스레드가 Gmail SMTP 왕복
// (연결 → STARTTLS → 인증 → 전송)이 끝날 때까지 붙잡혀 있었다. 몇 초씩 걸리다 보니
//   - 사용자 화면은 아무 반응 없이 멈춰 있고
//   - 답답해서 버튼을 또 누르면 그만큼 요청이 또 들어가 메일이 여러 통 날아갔다
// 메일은 "보내지면 좋은 것"이지 응답을 막을 이유가 없어서 백그라운드로 뺀다.
//
// [풀 크기]
// 동시에 메일이 쏟아질 일이 없는 서비스라 작게 잡는다. 큐가 가득 차면 버리지 않고
// 호출한 스레드가 직접 보낸다(CallerRunsPolicy) — 느려질지언정 메일이 사라지진 않는다.
// ############################################################################
@Configuration
@EnableAsync
public class AsyncMailConfig implements AsyncConfigurer {

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 서버를 내릴 때 보내던 메일은 마저 보내고 종료한다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    // 비동기로 돌면 예외가 호출부로 돌아가지 않는다 — 그냥 사라지지 않게 로그로 남긴다.
    // (메일 실패는 사용자 흐름을 막지 않는다는 기존 정책 그대로)
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                System.err.println("메일 발송 실패(" + method.getName() + "): " + ex.getMessage());
    }
}
