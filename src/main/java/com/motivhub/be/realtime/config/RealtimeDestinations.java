package com.motivhub.be.realtime.config;

public final class RealtimeDestinations {

    private RealtimeDestinations() {
    }

    public static String workspaceBoard(Long workspaceId) {
        return "/topic/workspaces/" + workspaceId + "/tasks";
    }
}
