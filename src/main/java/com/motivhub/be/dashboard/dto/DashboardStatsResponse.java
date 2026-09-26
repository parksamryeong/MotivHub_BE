package com.motivhub.be.dashboard.dto;

import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.domain.TaskStatus;
import java.time.LocalDate;
import java.util.List;

public record DashboardStatsResponse(
        String scope,
        Long workspaceId,
        List<StatusCountItem> statusCounts,
        List<PriorityCountItem> priorityCounts,
        List<MemberWorkloadItem> memberWorkload,
        List<CompletionTrendItem> completionTrend,
        long dueSoonCount) {

    public record StatusCountItem(TaskStatus status, long count) {
    }

    public record PriorityCountItem(TaskPriority priority, long count) {
    }

    public record MemberWorkloadItem(Long userId, String nickname, String profileImageUrl, long count) {
    }

    public record CompletionTrendItem(LocalDate date, long count) {
    }
}
