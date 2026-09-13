package com.motivhub.be.realtime.config;

import com.motivhub.be.realtime.service.TaskEditableField;

public final class RealtimeDestinations {

    private RealtimeDestinations() {
    }

    public static String workspaceBoard(Long workspaceId) {
        return "/topic/workspaces/" + workspaceId + "/tasks";
    }

    public static String taskEditBroadcast(Long taskId, TaskEditableField field) {
        return "/topic/tasks/" + taskId + "/" + field.pathSegment() + "/edits";
    }

    public static String taskEditSaveRequest(Long taskId, TaskEditableField field) {
        return "/topic/tasks/" + taskId + "/" + field.pathSegment() + "/save-request";
    }

    /**
     * convertAndSendToUser()의 destination 인자로 그대로 넘길 값 - "/user" 프리픽스는 뺀 형태다.
     */
    public static String taskEditUserQueue(Long taskId, TaskEditableField field) {
        return "/queue/tasks/" + taskId + "/" + field.pathSegment() + "/edits";
    }
}
