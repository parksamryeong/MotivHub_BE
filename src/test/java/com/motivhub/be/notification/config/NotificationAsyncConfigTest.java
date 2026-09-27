package com.motivhub.be.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NotificationAsyncConfigTest {

    @Test
    void notificationTaskExecutorIsBoundedThreadPoolWithCallerRunsPolicy() {
        Executor executor = new NotificationAsyncConfig().notificationTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor poolExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(poolExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(poolExecutor.getMaxPoolSize()).isEqualTo(10);
        assertThat(poolExecutor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(500);
        assertThat(poolExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }
}
