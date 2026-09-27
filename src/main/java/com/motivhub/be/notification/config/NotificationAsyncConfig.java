package com.motivhub.be.notification.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 알림 생성을 원본 HTTP 요청 스레드에서 분리하기 위한 전용 스레드풀. Spring 기본
// SimpleAsyncTaskExecutor(호출마다 새 스레드 생성, 풀 크기 제한 없음)는 쓰지 않는다 -
// "무제한 리소스 소모"가 원래 문제(요청 스레드가 알림 개수만큼 HikariCP 커넥션을 순차로 여는 것)였는데,
// 같은 유형의 문제를 스레드 쪽으로 옮기는 것일 뿐이기 때문이다.
@Configuration
@Profile("!test")
public class NotificationAsyncConfig {

    @Bean("notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        // 풀(10)과 큐(500)가 전부 찰 정도의 극단적 상황에서도 알림을 유실시키지 않고, 이벤트를
        // 발행한 스레드가 직접 실행하게 한다 - 원래(동기) 동작으로 자연스럽게 폴백하는 안전판.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 애플리케이션 컨텍스트 종료 시 executor를 즉시 shutdownNow()하지 않고, 큐에 남은/실행 중인
        // 알림 작업이 끝날 때까지(최대 20초) 대기한다 - graceful shutdown 중 Tomcat이 인플라이트
        // 요청을 드레인하는 동안에도 알림이 조용히 유실되지 않도록 하기 위함.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
