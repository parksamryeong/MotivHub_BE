package com.motivhub.be.realtime.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // STOMP 프로토콜 레벨 하트비트 주기(양방향 각 10초) - 죽은 커넥션을 전송 계층의 느린 타임아웃보다
    // 훨씬 빠르게 감지하기 위함. 흔히 쓰이는 기본값이라 10초로 설정.
    private static final long HEARTBEAT_INTERVAL_MS = 10_000;

    private final String frontendUrl;
    private final TaskTopicChannelInterceptor taskTopicChannelInterceptor;
    // Spring Boot가 @EnableScheduling이 있을 때 자동 구성하는 "taskScheduler" 빈(TaskSchedulingAutoConfiguration).
    // 생성자에서 바로 꺼내 쓰지 않고 ObjectProvider로만 들고 있다가 configureMessageBroker(...) 호출
    // 시점(이 빈이 완전히 만들어진 뒤)에 조회한다. 생성자에서 즉시 조회하면 Spring의
    // DelegatingWebSocketMessageBrokerConfiguration이 모든 WebSocketMessageBrokerConfigurer(이 빈 포함)를
    // 필요로 하는 것과 서로 맞물려 순환 참조(BeanCurrentlyInCreationException)가 발생한다.
    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;

    public WebSocketConfig(@Value("${app.frontend-url}") String frontendUrl,
                            TaskTopicChannelInterceptor taskTopicChannelInterceptor,
                            ObjectProvider<TaskScheduler> taskSchedulerProvider) {
        this.frontendUrl = frontendUrl;
        this.taskTopicChannelInterceptor = taskTopicChannelInterceptor;
        this.taskSchedulerProvider = taskSchedulerProvider;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(frontendUrl).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setTaskScheduler(taskSchedulerProvider.getObject())
                .setHeartbeatValue(new long[] {HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS});
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(taskTopicChannelInterceptor);
    }
}
