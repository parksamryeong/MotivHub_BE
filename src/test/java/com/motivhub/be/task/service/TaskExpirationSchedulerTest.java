package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.service.NotificationService;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
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

class TaskExpirationSchedulerTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskExpirationScheduler taskExpirationScheduler;
    @Autowired private NotificationService notificationService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void overdueIncompleteTaskBecomesExpired() {
        User owner = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-owner", "expire@test.com", "user_expire", null));
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 워크스페이스");
        TaskResponse overdueTask = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("지난 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        TaskResponse futureTask = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("미래 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        taskExpirationScheduler.expireOverdueTasks();

        assertThat(taskService.getDetail(owner.getId(), overdueTask.id()).status()).isEqualTo(TaskStatus.EXPIRED);
        assertThat(taskService.getDetail(owner.getId(), futureTask.id()).status()).isEqualTo(TaskStatus.WAITING);
    }

    @Test
    void completedOverdueTaskStaysUntouched() {
        User owner = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-owner2", "expire2@test.com", "user_expire2", null));
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 워크스페이스2");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("완료된 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        taskService.changeStatus(owner.getId(), task.id(), TaskStatus.DONE);

        taskExpirationScheduler.expireOverdueTasks();

        assertThat(taskService.getDetail(owner.getId(), task.id()).status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void overdueTaskNotifiesAssigneeAndOwner() {
        User owner = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-owner3", "expire3@test.com", "user_expire3", null));
        User assignee = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-assignee3", "expire3b@test.com", "user_expireA3", null));
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 알림 워크스페이스");
        Workspace workspaceEntity = workspaceService.getWorkspace(workspace.id());
        workspaceMemberRepository.save(WorkspaceMember.create(workspaceEntity, assignee, WorkspaceRole.MEMBER));
        TaskResponse overdueTask = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("만료 알림 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1),
                        List.of(assignee.getId())));

        TestTransaction.flagForCommit();
        taskExpirationScheduler.expireOverdueTasks();
        TestTransaction.end();
        TestTransaction.start();

        List<NotificationResponse> ownerNotifications = notificationService
                .list(owner.getId(), PageRequest.of(0, 20)).getContent();
        List<NotificationResponse> assigneeNotifications = notificationService
                .list(assignee.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(ownerNotifications).hasSize(1);
        assertThat(ownerNotifications.get(0).type()).isEqualTo(NotificationType.TASK_OVERDUE);
        assertThat(ownerNotifications.get(0).message()).contains("만료 알림 태스크");
        assertThat(assigneeNotifications).hasSize(1);
        assertThat(assigneeNotifications.get(0).type()).isEqualTo(NotificationType.TASK_OVERDUE);
    }

    @Test
    void runningExpirationSchedulerTwiceDoesNotDoubleNotifyBecauseTaskOnlyExpiresOnce() {
        User owner = userRepository.save(User.create(
                SocialProvider.GITHUB, "expire-owner4", "expire4@test.com", "user_expire4", null));
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 알림 중복방지 워크스페이스");
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("만료 알림 중복방지 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));

        TestTransaction.flagForCommit();
        taskExpirationScheduler.expireOverdueTasks();
        TestTransaction.end();
        TestTransaction.start();

        TestTransaction.flagForCommit();
        taskExpirationScheduler.expireOverdueTasks();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }
}
