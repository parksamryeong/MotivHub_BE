package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.service.NotificationService;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.TestTransaction;

class DueDateNotificationSchedulerTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private DueDateNotificationScheduler dueDateNotificationScheduler;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "duedate-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void taskDueInTwoDaysNotifiesAssigneeAndOwnerOnce() {
        User owner = newUser("d1-owner");
        User assignee = newUser("d1-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "마감일 스케줄러 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse dueSoon = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("D-2 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2),
                        List.of(assignee.getId())));
        TaskResponse dueLater = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("D-5 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));

        TestTransaction.flagForCommit();
        dueDateNotificationScheduler.notifyApproachingDueDates();
        TestTransaction.end();
        TestTransaction.start();

        List<NotificationResponse> ownerNotifications = notificationService
                .list(owner.getId(), PageRequest.of(0, 20)).getContent();
        List<NotificationResponse> assigneeNotifications = notificationService
                .list(assignee.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(ownerNotifications).hasSize(1);
        assertThat(ownerNotifications.get(0).message()).contains("D-2 태스크");
        assertThat(assigneeNotifications).hasSize(1);
        assertThat(assigneeNotifications.get(0).message()).contains("D-2 태스크");
    }

    @Test
    void doneTaskDueInTwoDaysIsNotNotified() {
        User owner = newUser("d2-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "마감일 완료태스크 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("완료된 D-2 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2), List.of()));
        taskService.changeStatus(owner.getId(), task.id(), TaskStatus.DONE);

        TestTransaction.flagForCommit();
        dueDateNotificationScheduler.notifyApproachingDueDates();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void runningSchedulerTwiceInSameDayDoesNotDuplicateNotification() {
        User owner = newUser("d3-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "마감일 중복방지 워크스페이스");
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("중복방지 D-2 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2), List.of()));

        TestTransaction.flagForCommit();
        dueDateNotificationScheduler.notifyApproachingDueDates();
        TestTransaction.end();
        TestTransaction.start();

        TestTransaction.flagForCommit();
        dueDateNotificationScheduler.notifyApproachingDueDates();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }
}
