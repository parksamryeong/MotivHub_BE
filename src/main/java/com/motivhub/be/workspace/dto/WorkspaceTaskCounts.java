package com.motivhub.be.workspace.dto;

public record WorkspaceTaskCounts(long waiting, long inProgress, long done, long expired) {

    public static WorkspaceTaskCounts empty() {
        return new WorkspaceTaskCounts(0, 0, 0, 0);
    }
}
