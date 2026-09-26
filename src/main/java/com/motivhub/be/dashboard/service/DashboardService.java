package com.motivhub.be.dashboard.service;

import com.motivhub.be.dashboard.dto.DashboardStatsResponse;
import com.motivhub.be.dashboard.dto.DashboardStatsResponse.CompletionTrendItem;
import com.motivhub.be.dashboard.dto.DashboardStatsResponse.MemberWorkloadItem;
import com.motivhub.be.dashboard.dto.DashboardStatsResponse.PriorityCountItem;
import com.motivhub.be.dashboard.dto.DashboardStatsResponse.StatusCountItem;
import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.repository.MemberWorkloadCount;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskPriorityCount;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.task.repository.TaskStatusCount;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int COMPLETION_TREND_DAYS = 7;
    private static final int DUE_SOON_WINDOW_DAYS = 2;
    private static final List<TaskStatus> ACTIVE_STATUSES = List.of(TaskStatus.WAITING, TaskStatus.IN_PROGRESS);

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceService workspaceService;

    public DashboardService(TaskRepository taskRepository, TaskAssigneeRepository taskAssigneeRepository,
                             WorkspaceMemberRepository workspaceMemberRepository, WorkspaceService workspaceService) {
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceService = workspaceService;
    }

    public DashboardStatsResponse getStats(Long userId, Long workspaceId) {
        List<Long> workspaceIds;
        String scope;
        if (workspaceId != null) {
            workspaceService.getMembership(workspaceId, userId);
            workspaceIds = List.of(workspaceId);
            scope = "WORKSPACE";
        } else {
            workspaceIds = workspaceMemberRepository.findByUserIdFetchWorkspace(userId).stream()
                    .map(member -> member.getWorkspace().getId())
                    .toList();
            scope = "ALL";
        }

        if (workspaceIds.isEmpty()) {
            return new DashboardStatsResponse(scope, workspaceId, zeroStatusCounts(), zeroPriorityCounts(),
                    List.of(), zeroCompletionTrend(), 0L);
        }

        return new DashboardStatsResponse(scope, workspaceId, buildStatusCounts(workspaceIds),
                buildPriorityCounts(workspaceIds), buildMemberWorkload(workspaceIds),
                buildCompletionTrend(workspaceIds), buildDueSoonCount(workspaceIds));
    }

    private List<StatusCountItem> buildStatusCounts(List<Long> workspaceIds) {
        Map<TaskStatus, Long> counts = new HashMap<>();
        for (TaskStatusCount row : taskRepository.countByWorkspaceIdsGroupByStatus(workspaceIds)) {
            counts.merge(row.status(), row.count(), Long::sum);
        }
        return Arrays.stream(TaskStatus.values())
                .map(status -> new StatusCountItem(status, counts.getOrDefault(status, 0L)))
                .toList();
    }

    private List<PriorityCountItem> buildPriorityCounts(List<Long> workspaceIds) {
        Map<TaskPriority, Long> counts = new HashMap<>();
        for (TaskPriorityCount row : taskRepository.countByWorkspaceIdsGroupByPriority(workspaceIds)) {
            counts.merge(row.priority(), row.count(), Long::sum);
        }
        return Arrays.stream(TaskPriority.values())
                .map(priority -> new PriorityCountItem(priority, counts.getOrDefault(priority, 0L)))
                .toList();
    }

    private List<MemberWorkloadItem> buildMemberWorkload(List<Long> workspaceIds) {
        return taskAssigneeRepository.countByWorkspaceIdsAndTaskStatusInGroupByUser(workspaceIds, ACTIVE_STATUSES)
                .stream()
                .map(row -> new MemberWorkloadItem(row.userId(), row.nickname(), row.profileImageUrl(), row.count()))
                .sorted(Comparator.comparingLong(MemberWorkloadItem::count).reversed())
                .toList();
    }

    private List<CompletionTrendItem> buildCompletionTrend(List<Long> workspaceIds) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(COMPLETION_TREND_DAYS - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (LocalDateTime completedAt
                : taskRepository.findCompletedAtsByWorkspaceIdsSince(workspaceIds, start.atStartOfDay())) {
            counts.merge(completedAt.toLocalDate(), 1L, Long::sum);
        }
        return trendFromCounts(start, today, counts);
    }

    private long buildDueSoonCount(List<Long> workspaceIds) {
        LocalDate today = LocalDate.now();
        return taskRepository.countByWorkspaceIdsAndStatusInAndDueDateBetween(
                workspaceIds, ACTIVE_STATUSES, today, today.plusDays(DUE_SOON_WINDOW_DAYS));
    }

    private List<StatusCountItem> zeroStatusCounts() {
        return Arrays.stream(TaskStatus.values()).map(status -> new StatusCountItem(status, 0L)).toList();
    }

    private List<PriorityCountItem> zeroPriorityCounts() {
        return Arrays.stream(TaskPriority.values()).map(priority -> new PriorityCountItem(priority, 0L)).toList();
    }

    private List<CompletionTrendItem> zeroCompletionTrend() {
        LocalDate today = LocalDate.now();
        return trendFromCounts(today.minusDays(COMPLETION_TREND_DAYS - 1L), today, Map.of());
    }

    private List<CompletionTrendItem> trendFromCounts(LocalDate start, LocalDate today, Map<LocalDate, Long> counts) {
        List<CompletionTrendItem> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            trend.add(new CompletionTrendItem(date, counts.getOrDefault(date, 0L)));
        }
        return trend;
    }
}
