package com.motivhub.be.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

class NotificationSyncTestConfigTest {

    @Test
    void notificationTaskExecutorIsSynchronous() {
        Executor executor = new NotificationSyncTestConfig().notificationTaskExecutor();

        assertThat(executor).isInstanceOf(SyncTaskExecutor.class);
    }
}
