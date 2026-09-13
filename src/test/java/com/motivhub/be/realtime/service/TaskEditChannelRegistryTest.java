package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskEditChannelRegistryTest {

    @Test
    void unauthorizedSessionTopicPairIsNotAuthorized() {
        TaskEditChannelRegistry registry = new TaskEditChannelRegistry();

        assertThat(registry.isAuthorized("session-1", "/topic/tasks/1/description/edits")).isFalse();
    }

    @Test
    void authorizingMakesSessionTopicPairAuthorized() {
        TaskEditChannelRegistry registry = new TaskEditChannelRegistry();

        registry.authorize("session-1", "/topic/tasks/1/description/edits");

        assertThat(registry.isAuthorized("session-1", "/topic/tasks/1/description/edits")).isTrue();
    }

    @Test
    void authorizationIsScopedToExactSessionAndTopic() {
        TaskEditChannelRegistry registry = new TaskEditChannelRegistry();

        registry.authorize("session-1", "/topic/tasks/1/description/edits");

        assertThat(registry.isAuthorized("session-2", "/topic/tasks/1/description/edits")).isFalse();
        assertThat(registry.isAuthorized("session-1", "/topic/tasks/1/note/edits")).isFalse();
    }
}
