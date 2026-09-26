package com.motivhub.be.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.dashboard.dto.DashboardStatsResponse;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DashboardServiceTest extends AbstractIntegrationTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "dash-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void newUserWithNoWorkspacesGetsZeroedStatsNotAnError() {
        User user = newUser("empty");

        DashboardStatsResponse response = dashboardService.getStats(user.getId(), null);

        assertThat(response.scope()).isEqualTo("ALL");
        assertThat(response.statusCounts()).hasSize(TaskStatus.values().length)
                .allMatch(item -> item.count() == 0);
        assertThat(response.priorityCounts()).hasSize(TaskPriority.values().length)
                .allMatch(item -> item.count() == 0);
        assertThat(response.memberWorkload()).isEmpty();
        assertThat(response.completionTrend()).hasSize(7).allMatch(item -> item.count() == 0);
        assertThat(response.dueSoonCount()).isZero();
    }

    @Test
    void statusCountsAlwaysReturnAllFourStatusesEvenWhenSomeAreZero() {
        User user = newUser("status");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "상태집계 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("대기 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TaskResponse inProgress = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("진행 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        taskService.changeStatus(user.getId(), inProgress.id(), TaskStatus.IN_PROGRESS);

        DashboardStatsResponse response = dashboardService.getStats(user.getId(), null);

        assertThat(response.statusCounts()).hasSize(4);
        assertThat(response.statusCounts()).filteredOn(item -> item.status() == TaskStatus.WAITING)
                .extracting(DashboardStatsResponse.StatusCountItem::count).containsExactly(1L);
        assertThat(response.statusCounts()).filteredOn(item -> item.status() == TaskStatus.IN_PROGRESS)
                .extracting(DashboardStatsResponse.StatusCountItem::count).containsExactly(1L);
        assertThat(response.statusCounts()).filteredOn(item -> item.status() == TaskStatus.DONE)
                .extracting(DashboardStatsResponse.StatusCountItem::count).containsExactly(0L);
    }

    @Test
    void priorityCountsGroupTasksByPriority() {
        User user = newUser("priority");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "우선순위집계 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("긴급 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(), TaskPriority.URGENT));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("보통 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(), TaskPriority.MEDIUM));

        DashboardStatsResponse response = dashboardService.getStats(user.getId(), null);

        assertThat(response.priorityCounts()).filteredOn(item -> item.priority() == TaskPriority.URGENT)
                .extracting(DashboardStatsResponse.PriorityCountItem::count).containsExactly(1L);
        assertThat(response.priorityCounts()).filteredOn(item -> item.priority() == TaskPriority.MEDIUM)
                .extracting(DashboardStatsResponse.PriorityCountItem::count).containsExactly(1L);
    }

    @Test
    void memberWorkloadOnlyCountsActiveTasksNotDoneOrExpired() {
        User owner = newUser("workload-owner");
        User assignee = newUser("workload-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "업무량집계 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("활성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));
        TaskResponse doneTask = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("완료 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));
        taskService.changeStatus(owner.getId(), doneTask.id(), TaskStatus.DONE);

        DashboardStatsResponse response = dashboardService.getStats(owner.getId(), null);

        assertThat(response.memberWorkload()).filteredOn(item -> item.userId().equals(assignee.getId()))
                .extracting(DashboardStatsResponse.MemberWorkloadItem::count).containsExactly(1L);
    }

    @Test
    void dueSoonCountIncludesTasksDueTodayThroughTwoDaysFromNowButNotBeyond() {
        User user = newUser("duesoon");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "마감임박집계 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("오늘 마감", null, LocalDate.now(), LocalDate.now(), List.of()));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("이틀 뒤 마감", null, LocalDate.now(), LocalDate.now().plusDays(2), List.of()));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("사흘 뒤 마감", null, LocalDate.now(), LocalDate.now().plusDays(3), List.of()));

        DashboardStatsResponse response = dashboardService.getStats(user.getId(), null);

        assertThat(response.dueSoonCount()).isEqualTo(2L);
    }

    @Test
    void completionTrendCoversExactlyLastSevenDaysZeroFilled() {
        User user = newUser("trend");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "완료추이집계 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("오늘 완료된 태스크", null, LocalDate.now(), LocalDate.now(), List.of()));
        taskService.changeStatus(user.getId(), task.id(), TaskStatus.DONE);

        DashboardStatsResponse response = dashboardService.getStats(user.getId(), null);

        assertThat(response.completionTrend()).hasSize(7);
        assertThat(response.completionTrend().get(6).date()).isEqualTo(LocalDate.now());
        assertThat(response.completionTrend().get(6).count()).isEqualTo(1L);
        assertThat(response.completionTrend().get(0).date()).isEqualTo(LocalDate.now().minusDays(6));
        assertThat(response.completionTrend().stream().mapToLong(DashboardStatsResponse.CompletionTrendItem::count).sum())
                .isEqualTo(1L);
    }

    @Test
    void scopedToOneWorkspaceOnlyCountsThatWorkspacesTasks() {
        User user = newUser("scoped");
        WorkspaceResponse workspaceA = workspaceService.create(user.getId(), "스코프A 워크스페이스");
        WorkspaceResponse workspaceB = workspaceService.create(user.getId(), "스코프B 워크스페이스");
        taskService.create(user.getId(), workspaceA.id(),
                new TaskCreateRequest("A 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        taskService.create(user.getId(), workspaceB.id(),
                new TaskCreateRequest("B 태스크1", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        taskService.create(user.getId(), workspaceB.id(),
                new TaskCreateRequest("B 태스크2", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        DashboardStatsResponse scopedToA = dashboardService.getStats(user.getId(), workspaceA.id());
        DashboardStatsResponse all = dashboardService.getStats(user.getId(), null);

        assertThat(scopedToA.scope()).isEqualTo("WORKSPACE");
        assertThat(scopedToA.workspaceId()).isEqualTo(workspaceA.id());
        assertThat(scopedToA.statusCounts()).filteredOn(item -> item.status() == TaskStatus.WAITING)
                .extracting(DashboardStatsResponse.StatusCountItem::count).containsExactly(1L);
        assertThat(all.statusCounts()).filteredOn(item -> item.status() == TaskStatus.WAITING)
                .extracting(DashboardStatsResponse.StatusCountItem::count).containsExactly(3L);
    }

    @Test
    void requestingStatsForWorkspaceYouAreNotAMemberOfThrows() {
        User owner = newUser("forbid-owner");
        User outsider = newUser("forbid-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "권한없음 워크스페이스");

        assertThatThrownBy(() -> dashboardService.getStats(outsider.getId(), workspace.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }
}
