package com.motivhub.be.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.realtime.service.TaskEditableField;
import org.junit.jupiter.api.Test;

class RealtimeDestinationsTest {

    @Test
    void workspaceBoardBuildsExpectedTopic() {
        assertThat(RealtimeDestinations.workspaceBoard(7L)).isEqualTo("/topic/workspaces/7/tasks");
    }

    @Test
    void taskEditBroadcastBuildsExpectedTopic() {
        assertThat(RealtimeDestinations.taskEditBroadcast(42L, TaskEditableField.DESCRIPTION))
                .isEqualTo("/topic/tasks/42/description/edits");
    }

    @Test
    void taskEditSaveRequestBuildsExpectedTopic() {
        assertThat(RealtimeDestinations.taskEditSaveRequest(42L, TaskEditableField.NOTE))
                .isEqualTo("/topic/tasks/42/note/save-request");
    }

    @Test
    void taskEditUserQueueBuildsDestinationWithoutUserPrefix() {
        // convertAndSendToUser()에 그대로 넘길 값이라 "/user" 프리픽스가 없어야 한다 -
        // Spring이 그 프리픽스를 내부적으로 붙인다.
        assertThat(RealtimeDestinations.taskEditUserQueue(42L, TaskEditableField.DESCRIPTION))
                .isEqualTo("/queue/tasks/42/description/edits");
    }
}
