package com.motivhub.be.support;

import java.util.concurrent.Executor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

// AbstractIntegrationTest가 @ActiveProfiles("test")이며, 이 top-level @TestConfiguration 클래스는
// 컴포넌트 스캔으로 자동 감지되지 않으므로(Spring Boot는 중첩/내부 @TestConfiguration만 자동 인식한다)
// AbstractIntegrationTest에 명시적으로 선언된 @Import(NotificationSyncTestConfig.class)를 통해서만
// 등록된다 - 이 @Import를 지워도 안전할 것이라 오해하지 말 것. NotificationAsyncConfig(@Profile("!test"))와 같은 빈 이름
// "notificationTaskExecutor"를 등록하지만 프로파일이 정확히 반대라 항상 둘 중 하나만 활성화된다 -
// 기존 통합 테스트(NotificationEventListenerTest 등)가 "커밋 직후 알림이 이미 존재한다"고 가정하고
// 바로 assert하는 타이밍을 그대로 유지하기 위해, 여기서는 호출 스레드에서 즉시 동기 실행한다.
@TestConfiguration
@Profile("test")
public class NotificationSyncTestConfig {

    @Bean("notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
