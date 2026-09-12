package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PresenceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PresenceService presenceService;

    @Test
    void joiningAddsUserToCurrentViewerIds() {
        presenceService.join(5001L, "session-A", 1L);

        assertThat(presenceService.currentViewerIds(5001L)).containsExactly(1L);
    }

    @Test
    void currentViewerIdsIsEmptyWhenNoOneHasJoined() {
        assertThat(presenceService.currentViewerIds(5002L)).isEmpty();
    }

    @Test
    void leavingRemovesUserFromCurrentViewerIds() {
        presenceService.join(5003L, "session-A", 1L);

        presenceService.leave(5003L, "session-A");

        assertThat(presenceService.currentViewerIds(5003L)).isEmpty();
    }

    @Test
    void sameUserJoiningWithTwoDifferentSessionsAppearsOnceInViewerIds() {
        presenceService.join(5004L, "session-A", 1L);
        presenceService.join(5004L, "session-B", 1L);

        assertThat(presenceService.currentViewerIds(5004L)).containsExactly(1L);
    }

    @Test
    void leavingOneOfTwoSessionsForSameUserKeepsUserInViewerIds() {
        presenceService.join(5005L, "session-A", 1L);
        presenceService.join(5005L, "session-B", 1L);

        presenceService.leave(5005L, "session-A");

        assertThat(presenceService.currentViewerIds(5005L)).containsExactly(1L);
    }

    @Test
    void twoDifferentUsersOnSameTaskBothAppearInViewerIds() {
        presenceService.join(5006L, "session-A", 1L);
        presenceService.join(5006L, "session-B", 2L);

        assertThat(presenceService.currentViewerIds(5006L)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void leavingWithUnknownSessionIdDoesNotThrow() {
        presenceService.leave(5007L, "never-joined-session");

        assertThat(presenceService.currentViewerIds(5007L)).isEmpty();
    }
}
