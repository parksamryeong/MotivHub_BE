package com.motivhub.be.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.event.AssigneeAddedEvent;
import com.motivhub.be.task.event.ChecklistCompletedEvent;
import com.motivhub.be.task.event.DueDateApproachingEvent;
import com.motivhub.be.task.event.TaskCommentCreatedEvent;
import com.motivhub.be.task.service.TaskService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.TestTransaction;

class NotificationEventListenerTest extends AbstractIntegrationTest {

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "listener-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void assigneeAddedEventCreatesNotificationForNewAssignee() {
        User owner = newUser("l1-owner");
        User assignee = newUser("l1-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "리스너 담당자 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("리스너 담당자 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        TestTransaction.flagForCommit();
        eventPublisher.publishEvent(new AssigneeAddedEvent(task.id(), assignee.getId()));
        TestTransaction.end();
        TestTransaction.start();

        List<NotificationResponse> notifications = notificationService
                .list(assignee.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).type()).isEqualTo(NotificationType.ASSIGNEE_ADDED);
        assertThat(notifications.get(0).targetType()).isEqualTo(NotificationTargetType.TASK);
        assertThat(notifications.get(0).targetId()).isEqualTo(task.id());
        assertThat(notifications.get(0).message()).contains("리스너 담당자 태스크");
    }

    @Test
    void taskCommentCreatedEventNotifiesAssigneesAndCreatorExceptAuthor() {
        User owner = newUser("l2-owner");
        User assignee = newUser("l2-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "리스너 댓글 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("리스너 댓글 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));

        TestTransaction.flagForCommit();
        eventPublisher.publishEvent(new TaskCommentCreatedEvent(task.id(), assignee.getId(), assignee.getNickname()));
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(notificationService.list(assignee.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void checklistCompletedEventNotifiesAllAssignees() {
        User owner = newUser("l3-owner");
        User assignee = newUser("l3-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "리스너 체크리스트 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("리스너 체크리스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));

        TestTransaction.flagForCommit();
        eventPublisher.publishEvent(new ChecklistCompletedEvent(task.id()));
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(assignee.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void dueDateApproachingEventNotifiesAssigneesAndOwner() {
        User owner = newUser("l4-owner");
        User assignee = newUser("l4-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "리스너 마감일 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("리스너 마감일 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2),
                        List.of(assignee.getId())));

        TestTransaction.flagForCommit();
        eventPublisher.publishEvent(new DueDateApproachingEvent(task.id()));
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
        assertThat(notificationService.list(assignee.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void eventPublishedWithoutCommitNeverCreatesNotification() {
        User owner = newUser("l5-owner");
        User assignee = newUser("l5-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "리스너 롤백 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("리스너 롤백 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        // 일부러 TestTransaction.flagForCommit()/end()를 호출하지 않는다 — 이 테스트 메서드는 결국
        // AbstractIntegrationTest의 기본 동작대로 끝에서 롤백된다. AFTER_COMMIT 리스너는 실제 커밋이 있어야만
        // 실행되므로, 커밋이 한 번도 없었다면 이 시점에 알림이 생성되어 있으면 안 된다 — 이게 롤백 시
        // 알림이 생기지 않는다는 걸 보여주는 시나리오다(실제 예외로 인한 롤백과 "커밋 전" 상태는 트랜잭션
        // 매니저 입장에서 동일하게 취급된다).
        eventPublisher.publishEvent(new AssigneeAddedEvent(task.id(), assignee.getId()));

        assertThat(notificationService.list(assignee.getId(), PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void dueDateApproachingEventDoesNotDuplicateNotificationWhenPublishedTwiceInOneDay() {
        User owner = newUser("l6-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "리스너 마감일 중복방지 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("리스너 마감일 중복방지 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2), List.of()));

        TestTransaction.flagForCommit();
        eventPublisher.publishEvent(new DueDateApproachingEvent(task.id()));
        TestTransaction.end();
        TestTransaction.start();

        TestTransaction.flagForCommit();
        eventPublisher.publishEvent(new DueDateApproachingEvent(task.id()));
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(owner.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }
}
