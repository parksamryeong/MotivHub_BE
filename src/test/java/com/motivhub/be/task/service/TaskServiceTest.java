package com.motivhub.be.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.TaskActivityAction;
import com.motivhub.be.task.domain.TaskActivityLog;
import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.dto.TaskActivityLogResponse;
import com.motivhub.be.task.service.TaskActivityLogService;
import com.motivhub.be.task.dto.MyTaskResponse;
import com.motivhub.be.task.dto.TaskChecklistItemResponse;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.exception.InvalidTaskStatusTransitionException;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.exception.TaskPeriodEditForbiddenException;
import com.motivhub.be.task.domain.TaskComment;
import com.motivhub.be.task.service.TaskCommentService;
import com.motivhub.be.task.repository.TaskActivityLogRepository;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskChecklistItemRepository;
import com.motivhub.be.task.repository.TaskCommentRepository;
import com.motivhub.be.notification.dto.NotificationResponse;
import com.motivhub.be.notification.service.NotificationService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.transaction.TestTransaction;

class TaskServiceTest extends AbstractIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskChecklistItemService taskChecklistItemService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private TaskAssigneeRepository taskAssigneeRepository;
    @Autowired private TaskChecklistItemRepository taskChecklistItemRepository;
    @Autowired private TaskCommentRepository taskCommentRepository;
    @Autowired private TaskActivityLogRepository taskActivityLogRepository;
    @Autowired private TaskExpirationScheduler taskExpirationScheduler;
    @Autowired private TaskCommentService taskCommentService;
    @Autowired private NotificationService notificationService;
    @Autowired private TaskActivityLogService taskActivityLogService;
    @Autowired private EntityManager entityManager;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "task-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void memberCanCreateTask() {
        User creator = newUser("creator1");
        WorkspaceResponse workspace = workspaceService.create(creator.getId(), "태스크 워크스페이스");

        TaskResponse task = taskService.create(creator.getId(), workspace.id(),
                new TaskCreateRequest("첫 태스크", "설명", LocalDate.now(), LocalDate.now().plusDays(3), List.of()));

        assertThat(task.name()).isEqualTo("첫 태스크");
        assertThat(task.status().name()).isEqualTo("WAITING");
    }

    @Test
    void createdTaskAppearsInWorkspaceList() {
        User creator = newUser("creator2");
        WorkspaceResponse workspace = workspaceService.create(creator.getId(), "목록 워크스페이스");
        taskService.create(creator.getId(), workspace.id(),
                new TaskCreateRequest("태스크A", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        List<TaskResponse> tasks = taskService.listByWorkspace(creator.getId(), workspace.id());

        assertThat(tasks).extracting(TaskResponse::name).containsExactly("태스크A");
    }

    @Test
    void nonMemberCannotCreateTask() {
        User owner = newUser("owner3");
        User outsider = newUser("outsider3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "비공개 워크스페이스");

        assertThatThrownBy(() -> taskService.create(outsider.getId(), workspace.id(),
                new TaskCreateRequest("몰래 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of())))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void getDetailFailsForUnknownTask() {
        User user = newUser("detail4");

        assertThatThrownBy(() -> taskService.getDetail(user.getId(), 999_999L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void assigneeCanUpdateContent() {
        User owner = newUser("content-owner");
        User assignee = newUser("content-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내용수정 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("원래 이름", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        TaskResponse updated = taskService.updateContent(assignee.getId(), task.id(), "바뀐 이름", "바뀐 설명");

        assertThat(updated.name()).isEqualTo("바뀐 이름");
    }

    @Test
    void nonAssigneeCannotUpdateContent() {
        User owner = newUser("content-owner2");
        User assignee = newUser("content-assignee2");
        User bystander = newUser("content-bystander2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내용수정 워크스페이스2");
        joinAsMember(workspace.id(), assignee);
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("원래 이름2", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        assertThatThrownBy(() -> taskService.updateContent(bystander.getId(), task.id(), "바뀐 이름2", null))
                .isInstanceOf(TaskEditForbiddenException.class);
    }

    @Test
    void onlyOwnerCanUpdatePeriod() {
        User owner = newUser("period-owner");
        User assignee = newUser("period-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "기간수정 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("기간 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        assertThatThrownBy(() -> taskService.updatePeriod(assignee.getId(), task.id(), LocalDate.now(), LocalDate.now().plusDays(10)))
                .isInstanceOf(TaskPeriodEditForbiddenException.class);

        TaskResponse updated = taskService.updatePeriod(owner.getId(), task.id(), LocalDate.now(), LocalDate.now().plusDays(10));
        assertThat(updated.dueDate()).isEqualTo(LocalDate.now().plusDays(10));
    }

    @Test
    void creatorCanDeleteOwnTask() {
        User owner = newUser("del-owner3");
        User member = newUser("del-member3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스");
        joinAsMember(workspace.id(), member);
        TaskResponse task = taskService.create(member.getId(), workspace.id(),
                new TaskCreateRequest("내가 만든 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.delete(member.getId(), task.id());

        assertThatThrownBy(() -> taskService.getDetail(owner.getId(), task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void nonCreatorNonOwnerCannotDelete() {
        User owner = newUser("del-owner4");
        User creator = newUser("del-creator4");
        User bystander = newUser("del-bystander4");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스2");
        joinAsMember(workspace.id(), creator);
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(creator.getId(), workspace.id(),
                new TaskCreateRequest("남의 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskService.delete(bystander.getId(), task.id()))
                .isInstanceOf(TaskEditForbiddenException.class);
    }

    @Test
    void assigneeCanChangeStatus() {
        User owner = newUser("status-owner");
        User assignee = newUser("status-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "상태변경 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("상태 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        TaskResponse updated = taskService.changeStatus(assignee.getId(), task.id(), TaskStatus.IN_PROGRESS);

        assertThat(updated.status()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void cannotChangeStatusToExpiredDirectly() {
        User owner = newUser("status-owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "상태변경 워크스페이스2");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("상태 태스크2", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskService.changeStatus(owner.getId(), task.id(), TaskStatus.EXPIRED))
                .isInstanceOf(InvalidTaskStatusTransitionException.class);
    }

    @Test
    void ownerCanAddAndRemoveAssignee() {
        User owner = newUser("assignee-owner");
        User newAssignee = newUser("assignee-new");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "담당자 워크스페이스");
        joinAsMember(workspace.id(), newAssignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        TaskResponse added = taskService.addAssignee(owner.getId(), task.id(), newAssignee.getId());
        assertThat(added.assignees()).extracting(UserSummary::id).containsExactly(newAssignee.getId());

        TaskResponse removed = taskService.removeAssignee(owner.getId(), task.id(), newAssignee.getId());
        assertThat(removed.assignees()).isEmpty();
    }

    @Test
    void existingAssigneeCanAddAnotherAssignee() {
        User owner = newUser("assignee-owner2");
        User assigneeA = newUser("assignee-a2");
        User assigneeB = newUser("assignee-b2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "담당자 워크스페이스2");
        joinAsMember(workspace.id(), assigneeA);
        joinAsMember(workspace.id(), assigneeB);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 태스크2", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assigneeA.getId())));

        TaskResponse updated = taskService.addAssignee(assigneeA.getId(), task.id(), assigneeB.getId());

        assertThat(updated.assignees()).extracting(UserSummary::id)
                .containsExactlyInAnyOrder(assigneeA.getId(), assigneeB.getId());
    }

    @Test
    void deletingTaskWithAssigneeAndCommentDoesNotThrow() {
        User owner = newUser("del-cascade-owner");
        User assignee = newUser("del-cascade-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 캐스케이드 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자·댓글 있는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(assignee.getId())));
        taskCommentRepository.save(TaskComment.create(
                taskService.getTask(task.id()), owner, "댓글입니다"));

        taskService.delete(owner.getId(), task.id());

        assertThatThrownBy(() -> taskService.getDetail(owner.getId(), task.id()))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(taskAssigneeRepository.findByTaskId(task.id())).isEmpty();
        assertThat(taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(task.id())).isEmpty();
    }

    @Test
    void extendingExpiredTaskToExactlyTodayRevivesIt() {
        User owner = newUser("expire-boundary-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "만료 경계 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("경계 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        taskExpirationScheduler.expireOverdueTasks();
        assertThat(taskService.getDetail(owner.getId(), task.id()).status()).isEqualTo(TaskStatus.EXPIRED);

        TaskResponse revived = taskService.updatePeriod(owner.getId(), task.id(), LocalDate.now(), LocalDate.now());

        assertThat(revived.status()).isEqualTo(TaskStatus.WAITING);
    }

    @Test
    void getDetailIncludesAssigneeAndCreatorNicknames() {
        User owner = newUser("nickname-owner");
        User assignee = newUser("nickname-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "닉네임 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("닉네임 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        TaskResponse detail = taskService.getDetail(owner.getId(), task.id());

        assertThat(detail.createdBy().nickname()).isEqualTo(owner.getNickname());
        assertThat(detail.assignees()).extracting(UserSummary::nickname).containsExactly(assignee.getNickname());
    }

    @Test
    void listByWorkspaceIncludesAssigneeAndCreatorNicknames() {
        User owner = newUser("nickname-list-owner");
        User assignee = newUser("nickname-list-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "닉네임 목록 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("닉네임 목록 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));

        List<TaskResponse> tasks = taskService.listByWorkspace(owner.getId(), workspace.id());

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).createdBy().nickname()).isEqualTo(owner.getNickname());
        assertThat(tasks.get(0).assignees()).extracting(UserSummary::nickname).containsExactly(assignee.getNickname());
    }

    @Test
    void withdrawnAssigneeStillShowsMaskedNickname() {
        User owner = newUser("nickname-wd-owner");
        User assignee = newUser("nickname-wd-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "탈퇴 닉네임 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("탈퇴 닉네임 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(assignee.getId())));
        assignee.withdraw();
        userRepository.save(assignee);

        TaskResponse detail = taskService.getDetail(owner.getId(), task.id());

        assertThat(detail.assignees()).extracting(UserSummary::nickname).containsExactly(assignee.getNickname());
        assertThat(detail.assignees().get(0).nickname()).startsWith("탈퇴한 사용자_");
    }

    @Test
    void creatingTaskRecordsCreateActivityLog() {
        User owner = newUser("activity-create-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 생성 워크스페이스");

        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("생성 활동 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(TaskActivityAction.CREATE);
        assertThat(logs.get(0).getActor().getId()).isEqualTo(owner.getId());
    }

    @Test
    void updatingNameRecordsActivityLog() {
        User owner = newUser("activity-content-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 내용 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("원래 이름", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.updateContent(owner.getId(), task.id(), "바뀐 이름", null);

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs).extracting(TaskActivityLog::getAction)
                .containsExactly(TaskActivityAction.UPDATE_CONTENT, TaskActivityAction.CREATE);
        assertThat(logs.get(0).getField()).isEqualTo("name");
        assertThat(logs.get(0).getOldValue()).isEqualTo("원래 이름");
        assertThat(logs.get(0).getNewValue()).isEqualTo("바뀐 이름");
    }

    @Test
    void updatingContentWithSameValuesRecordsNoAdditionalActivityLog() {
        User owner = newUser("activity-noop-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 무변경 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("동일 이름", "동일 설명", LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.updateContent(owner.getId(), task.id(), "동일 이름", "동일 설명");

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs).extracting(TaskActivityLog::getAction).containsExactly(TaskActivityAction.CREATE);
    }

    @Test
    void changingStatusRecordsActivityLog() {
        User owner = newUser("activity-status-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 상태 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("상태 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.changeStatus(owner.getId(), task.id(), TaskStatus.IN_PROGRESS);

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs.get(0).getAction()).isEqualTo(TaskActivityAction.CHANGE_STATUS);
        assertThat(logs.get(0).getOldValue()).isEqualTo("WAITING");
        assertThat(logs.get(0).getNewValue()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void addingAndRemovingAssigneeRecordsActivityLog() {
        User owner = newUser("activity-assignee-owner");
        User newAssignee = newUser("activity-assignee-new");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 담당자 워크스페이스");
        joinAsMember(workspace.id(), newAssignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 활동 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.addAssignee(owner.getId(), task.id(), newAssignee.getId());
        taskService.removeAssignee(owner.getId(), task.id(), newAssignee.getId());

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs).extracting(TaskActivityLog::getAction).containsExactly(
                TaskActivityAction.REMOVE_ASSIGNEE, TaskActivityAction.ADD_ASSIGNEE, TaskActivityAction.CREATE);
        assertThat(logs.get(1).getNewValue()).isEqualTo(newAssignee.getNickname());
        assertThat(logs.get(0).getOldValue()).isEqualTo(newAssignee.getNickname());
    }

    @Test
    void deletingTaskAlsoDeletesActivityLogs() {
        User owner = newUser("activity-delete-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 삭제 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("삭제될 활동 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.delete(owner.getId(), task.id());

        assertThat(taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id())).isEmpty();
    }

    @Test
    void updatingDescriptionBeyondActivityLogColumnWidthDoesNotThrow() {
        User owner = newUser("activity-overflow-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 초과 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("초과 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        String longDescription = "x".repeat(600);

        assertThatCode(() -> taskService.updateContent(owner.getId(), task.id(), "초과 태스크", longDescription))
                .doesNotThrowAnyException();

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs.get(0).getAction()).isEqualTo(TaskActivityAction.UPDATE_CONTENT);
        assertThat(logs.get(0).getField()).isEqualTo("description");
        assertThat(logs.get(0).getNewValue()).hasSize(500);
    }

    @Test
    void updatingOnlyDescriptionRecordsSingleActivityLog() {
        User owner = newUser("activity-desc-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 설명 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("설명 태스크", "원래 설명", LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.updateContent(owner.getId(), task.id(), "설명 태스크", "바뀐 설명");

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs).extracting(TaskActivityLog::getAction)
                .containsExactly(TaskActivityAction.UPDATE_CONTENT, TaskActivityAction.CREATE);
        assertThat(logs.get(0).getField()).isEqualTo("description");
        assertThat(logs.get(0).getOldValue()).isEqualTo("원래 설명");
        assertThat(logs.get(0).getNewValue()).isEqualTo("바뀐 설명");
    }

    @Test
    void updatingPeriodRecordsActivityLog() {
        User owner = newUser("activity-period-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 기간 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("기간 활동 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        String oldPeriod = LocalDate.now() + "~" + LocalDate.now().plusDays(1);
        String newPeriod = LocalDate.now() + "~" + LocalDate.now().plusDays(10);

        taskService.updatePeriod(owner.getId(), task.id(), LocalDate.now(), LocalDate.now().plusDays(10));

        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs.get(0).getAction()).isEqualTo(TaskActivityAction.UPDATE_PERIOD);
        assertThat(logs.get(0).getField()).isEqualTo("period");
        assertThat(logs.get(0).getOldValue()).isEqualTo(oldPeriod);
        assertThat(logs.get(0).getNewValue()).isEqualTo(newPeriod);
    }

    @Test
    void revivingExpiredTaskViaUpdatePeriodRecordsPeriodAndStatusActivityLogs() {
        User owner = newUser("activity-revive-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "활동로그 부활 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("부활 활동 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        taskExpirationScheduler.expireOverdueTasks();
        assertThat(taskService.getDetail(owner.getId(), task.id()).status()).isEqualTo(TaskStatus.EXPIRED);

        TaskResponse revived = taskService.updatePeriod(owner.getId(), task.id(), LocalDate.now(), LocalDate.now());

        assertThat(revived.status()).isEqualTo(TaskStatus.WAITING);
        List<TaskActivityLog> logs = taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(task.id());
        assertThat(logs).extracting(TaskActivityLog::getAction)
                .contains(TaskActivityAction.UPDATE_PERIOD, TaskActivityAction.CHANGE_STATUS);
        TaskActivityLog statusLog = logs.stream()
                .filter(log -> log.getAction() == TaskActivityAction.CHANGE_STATUS)
                .findFirst().orElseThrow();
        assertThat(statusLog.getOldValue()).isEqualTo("EXPIRED");
        assertThat(statusLog.getNewValue()).isEqualTo("WAITING");
    }

    @Test
    void deletingTaskAlsoDeletesChecklistItems() {
        User owner = newUser("checklist-delete-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "체크리스트 삭제 캐스케이드 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("삭제될 체크리스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskChecklistItemService.create(owner.getId(), task.id(), "삭제될 항목");

        taskService.delete(owner.getId(), task.id());

        assertThat(taskChecklistItemRepository.findByTaskIdOrderByOrderIndexAsc(task.id())).isEmpty();
    }

    @Test
    void addingAssigneeNotifiesOnlyTheNewAssignee() {
        User owner = newUser("assignee-notify-owner");
        User assignee = newUser("assignee-notify-target");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "담당자 알림 워크스페이스");
        workspaceMemberRepository.save(WorkspaceMember.create(
                workspaceService.getWorkspace(workspace.id()), assignee, WorkspaceRole.MEMBER));
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 알림 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        TestTransaction.flagForCommit();
        taskService.addAssignee(owner.getId(), task.id(), assignee.getId());
        TestTransaction.end();
        TestTransaction.start();

        List<NotificationResponse> notifications = notificationService
                .list(assignee.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).message()).contains("담당자 알림 태스크");
    }

    @Test
    void reAddingSameAssigneeDoesNotDuplicateNotification() {
        User owner = newUser("assignee-dup-owner");
        User assignee = newUser("assignee-dup-target");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "담당자 중복 알림 워크스페이스");
        workspaceMemberRepository.save(WorkspaceMember.create(
                workspaceService.getWorkspace(workspace.id()), assignee, WorkspaceRole.MEMBER));
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 중복 알림 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        TestTransaction.flagForCommit();
        taskService.addAssignee(owner.getId(), task.id(), assignee.getId());
        taskService.addAssignee(owner.getId(), task.id(), assignee.getId());
        TestTransaction.end();
        TestTransaction.start();

        assertThat(notificationService.list(assignee.getId(), PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void descriptionYjsStateIsNullByDefault() {
        User owner = createUniqueUser("yjs-state-default");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "yjs 기본값 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("yjs 기본값 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        assertThat(taskService.getDescriptionYjsStateBase64(owner.getId(), task.id())).isNull();
    }

    @Test
    void updateDescriptionYjsStateThenGetReturnsBase64EncodedValue() {
        User owner = createUniqueUser("yjs-state-roundtrip");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "yjs 저장 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("yjs 저장 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        byte[] state = new byte[] {1, 2, 3, 4};

        taskService.updateDescriptionYjsState(task.id(), state);

        String base64 = taskService.getDescriptionYjsStateBase64(owner.getId(), task.id());
        assertThat(base64).isEqualTo(java.util.Base64.getEncoder().encodeToString(state));
    }

    @Test
    void nonEditorCannotGetDescriptionYjsState() {
        User owner = createUniqueUser("yjs-state-perm-owner");
        User plainMember = createUniqueUser("yjs-state-perm-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "yjs 권한 워크스페이스");
        workspaceMemberRepository.save(com.motivhub.be.workspace.domain.WorkspaceMember.create(
                workspaceService.getWorkspace(workspace.id()), plainMember,
                com.motivhub.be.workspace.domain.WorkspaceRole.MEMBER));
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("yjs 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        assertThatThrownBy(() -> taskService.getDescriptionYjsStateBase64(plainMember.getId(), task.id()))
                .isInstanceOf(com.motivhub.be.task.exception.TaskEditForbiddenException.class);
    }

    @Test
    void listMineReturnsTasksAssignedToUserAcrossWorkspaces() {
        User user = newUser("mine-cross-1");
        WorkspaceResponse workspaceA = workspaceService.create(user.getId(), "내할일 워크스페이스A");
        WorkspaceResponse workspaceB = workspaceService.create(user.getId(), "내할일 워크스페이스B");
        taskService.create(user.getId(), workspaceA.id(),
                new TaskCreateRequest("A 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));
        taskService.create(user.getId(), workspaceB.id(),
                new TaskCreateRequest("B 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2), List.of(user.getId())));

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).extracting(MyTaskResponse::name).containsExactlyInAnyOrder("A 태스크", "B 태스크");
    }

    @Test
    void listMineExcludesTasksNotAssignedToUser() {
        User owner = newUser("mine-excl-owner");
        User bystander = newUser("mine-excl-bystander");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내할일 비담당 워크스페이스");
        joinAsMember(workspace.id(), bystander);
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 없는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        List<MyTaskResponse> result = taskService.listMine(bystander.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void listMineExcludesDoneTasks() {
        User user = newUser("mine-done");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 완료제외 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("완료될 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));
        taskService.changeStatus(user.getId(), task.id(), TaskStatus.DONE);

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void listMineIncludesExpiredTasks() {
        User user = newUser("mine-expired");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 지연 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("지연될 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1),
                        List.of(user.getId())));
        taskExpirationScheduler.expireOverdueTasks();

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).extracting(MyTaskResponse::status).containsExactly(TaskStatus.EXPIRED);
    }

    @Test
    void listMineIncludesChecklistProgress() {
        User user = newUser("mine-checklist");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 체크리스트 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("체크리스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));
        var item1 = taskChecklistItemService.create(user.getId(), task.id(), "항목1");
        taskChecklistItemService.create(user.getId(), task.id(), "항목2");
        taskChecklistItemService.update(user.getId(), task.id(), item1.id(), null, true);

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).checklistTotal()).isEqualTo(2L);
        assertThat(result.get(0).checklistCompleted()).isEqualTo(1L);
    }

    @Test
    void listMineReturnsZeroChecklistProgressWhenNoChecklistItems() {
        User user = newUser("mine-no-checklist");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 체크리스트없음 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("체크리스트 없는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result.get(0).checklistTotal()).isZero();
        assertThat(result.get(0).checklistCompleted()).isZero();
    }

    @Test
    void listMineIndicatesHasCommentsCorrectly() {
        User user = newUser("mine-comments");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 댓글 워크스페이스");
        TaskResponse withComment = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("댓글 있는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));
        TaskResponse withoutComment = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("댓글 없는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(2), List.of(user.getId())));
        taskCommentService.create(user.getId(), withComment.id(), "댓글 내용");

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).filteredOn(r -> r.taskId().equals(withComment.id()))
                .extracting(MyTaskResponse::hasComments).containsExactly(true);
        assertThat(result).filteredOn(r -> r.taskId().equals(withoutComment.id()))
                .extracting(MyTaskResponse::hasComments).containsExactly(false);
    }

    @Test
    void listMineSortsByDueDateAscending() {
        User user = newUser("mine-sort");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "내할일 정렬 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("나중 마감", null, LocalDate.now(), LocalDate.now().plusDays(10), List.of(user.getId())));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("먼저 마감", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).extracting(MyTaskResponse::name).containsExactly("먼저 마감", "나중 마감");
    }

    @Test
    void listMineReturnsEmptyListForUserWithNoAssignments() {
        User user = newUser("mine-empty");

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void listMineExcludesTasksInWorkspacesTheUserLeft() {
        User owner = createUniqueUser("mine-kicked-owner");
        User assignee = createUniqueUser("mine-kicked-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내할일 강퇴 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("강퇴 전 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(assignee.getId())));

        workspaceService.kick(owner.getId(), workspace.id(), assignee.getId());

        List<MyTaskResponse> result = taskService.listMine(assignee.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void listMineExcludesTasksFromDeletedWorkspaces() {
        User owner = createUniqueUser("mine-deleted-ws-owner");
        User assignee = createUniqueUser("mine-deleted-ws-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "내할일 삭제됨 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("삭제된 워크스페이스 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(assignee.getId())));

        workspaceService.delete(owner.getId(), workspace.id());

        List<MyTaskResponse> result = taskService.listMine(assignee.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void creatingTaskWithExplicitPrioritySetsIt() {
        User owner = createUniqueUser("priority-explicit-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 명시 워크스페이스");

        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("긴급 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(), TaskPriority.URGENT));

        assertThat(task.priority()).isEqualTo(TaskPriority.URGENT);
    }

    @Test
    void creatingTaskWithoutPriorityDefaultsToMedium() {
        User owner = createUniqueUser("priority-default-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 기본값 워크스페이스");

        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보통 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThat(task.priority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    void assigneeCanUpdatePriority() {
        User owner = createUniqueUser("priority-update-owner");
        User assignee = createUniqueUser("priority-update-assignee");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 수정 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("우선순위 바뀔 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(assignee.getId())));

        TaskResponse updated = taskService.updatePriority(assignee.getId(), task.id(), TaskPriority.HIGH);

        assertThat(updated.priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void nonAssigneeCannotUpdatePriority() {
        User owner = createUniqueUser("priority-forbidden-owner");
        User bystander = createUniqueUser("priority-forbidden-bystander");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 권한 워크스페이스");
        joinAsMember(workspace.id(), bystander);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("권한 없는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThatThrownBy(() -> taskService.updatePriority(bystander.getId(), task.id(), TaskPriority.URGENT))
                .isInstanceOf(TaskEditForbiddenException.class);
    }

    @Test
    void updatingPriorityRecordsActivityLog() {
        User owner = createUniqueUser("priority-log-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "우선순위 로그 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("로그 남을 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.updatePriority(owner.getId(), task.id(), TaskPriority.LOW);

        List<TaskActivityLogResponse> logs = taskActivityLogService.list(owner.getId(), task.id());
        assertThat(logs).extracting(TaskActivityLogResponse::action)
                .contains(TaskActivityAction.CHANGE_PRIORITY);
    }

    @Test
    void listMineSortsByPriorityWithinSameDueDate() {
        User user = createUniqueUser("priority-sort-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "우선순위 정렬 워크스페이스");
        LocalDate sameDueDate = LocalDate.now().plusDays(3);
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("낮음 태스크", null, LocalDate.now(), sameDueDate,
                        List.of(user.getId()), TaskPriority.LOW));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("긴급 태스크", null, LocalDate.now(), sameDueDate,
                        List.of(user.getId()), TaskPriority.URGENT));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("보통 태스크", null, LocalDate.now(), sameDueDate,
                        List.of(user.getId()), TaskPriority.MEDIUM));

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).extracting(MyTaskResponse::name)
                .containsExactly("긴급 태스크", "보통 태스크", "낮음 태스크");
    }

    @Test
    void changedPrioritySurfacesInListMineAfterUpdate() {
        User user = createUniqueUser("priority-roundtrip-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "우선순위 왕복 워크스페이스");
        TaskResponse lowTask = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("낮음으로 시작한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(3),
                        List.of(user.getId()), TaskPriority.LOW));
        TaskResponse mediumTask = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("보통 태스크", null, LocalDate.now(), LocalDate.now().plusDays(3),
                        List.of(user.getId()), TaskPriority.MEDIUM));

        taskService.updatePriority(user.getId(), lowTask.id(), TaskPriority.URGENT);

        List<MyTaskResponse> result = taskService.listMine(user.getId());

        assertThat(result).extracting(MyTaskResponse::name).containsExactly("낮음으로 시작한 태스크", "보통 태스크");
    }

    @Test
    void duplicateCopiesNameDescriptionDatesAndPriority() {
        User owner = createUniqueUser("dup-basic-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 기본 워크스페이스");
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("원본 태스크", "원본 설명", LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(), TaskPriority.HIGH));

        TaskResponse duplicated = taskService.duplicate(owner.getId(), original.id());

        assertThat(duplicated.id()).isNotEqualTo(original.id());
        assertThat(duplicated.name()).isEqualTo("원본 태스크");
        assertThat(duplicated.description()).isEqualTo("원본 설명");
        assertThat(duplicated.startDate()).isEqualTo(original.startDate());
        assertThat(duplicated.dueDate()).isEqualTo(original.dueDate());
        assertThat(duplicated.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(duplicated.status().name()).isEqualTo("WAITING");
    }

    @Test
    void duplicateDoesNotMutateOriginal() {
        User owner = createUniqueUser("dup-immutable-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 원본보존 워크스페이스");
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보존될 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        taskService.duplicate(owner.getId(), original.id());

        TaskResponse stillOriginal = taskService.getDetail(owner.getId(), original.id());
        assertThat(stillOriginal.name()).isEqualTo("보존될 태스크");
    }

    @Test
    void duplicateCopiesChecklistItemsResetToNotDone() {
        User owner = createUniqueUser("dup-checklist-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 체크리스트 워크스페이스");
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("체크리스트 있는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TaskChecklistItemResponse item1 = taskChecklistItemService.create(owner.getId(), original.id(), "항목1");
        taskChecklistItemService.create(owner.getId(), original.id(), "항목2");
        taskChecklistItemService.update(owner.getId(), original.id(), item1.id(), null, true);

        TaskResponse duplicated = taskService.duplicate(owner.getId(), original.id());

        List<TaskChecklistItemResponse> duplicatedItems = taskChecklistItemService.list(owner.getId(), duplicated.id());
        assertThat(duplicatedItems).hasSize(2);
        assertThat(duplicatedItems).extracting(TaskChecklistItemResponse::content)
                .containsExactly("항목1", "항목2");
        assertThat(duplicatedItems).extracting(TaskChecklistItemResponse::isDone)
                .containsExactly(false, false);
    }

    @Test
    void duplicateCopiesAssignees() {
        User owner = createUniqueUser("dup-assignee-owner");
        User assignee = createUniqueUser("dup-assignee-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 담당자 워크스페이스");
        joinAsMember(workspace.id(), assignee);
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("담당자 있는 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(assignee.getId())));

        TaskResponse duplicated = taskService.duplicate(owner.getId(), original.id());

        assertThat(duplicated.assignees()).extracting(UserSummary::id).containsExactly(assignee.getId());
    }

    @Test
    void duplicateExcludesAssigneeWhoHasLeftWorkspace() {
        User owner = createUniqueUser("dup-exmember-owner");
        User stayingAssignee = createUniqueUser("dup-exmember-staying");
        User leavingAssignee = createUniqueUser("dup-exmember-leaving");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 탈퇴담당자 워크스페이스");
        joinAsMember(workspace.id(), stayingAssignee);
        joinAsMember(workspace.id(), leavingAssignee);
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("두 담당자 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(stayingAssignee.getId(), leavingAssignee.getId())));

        workspaceService.kick(owner.getId(), workspace.id(), leavingAssignee.getId());

        TaskResponse duplicated = taskService.duplicate(owner.getId(), original.id());

        assertThat(duplicated.assignees()).extracting(UserSummary::id)
                .containsExactly(stayingAssignee.getId());
    }

    @Test
    void anyWorkspaceMemberCanDuplicateTask() {
        User owner = createUniqueUser("dup-anymember-owner");
        User plainMember = createUniqueUser("dup-anymember-plain");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 아무나 워크스페이스");
        joinAsMember(workspace.id(), plainMember);
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("아무나 복제 가능 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        TaskResponse duplicated = taskService.duplicate(plainMember.getId(), original.id());

        assertThat(duplicated.name()).isEqualTo("아무나 복제 가능 태스크");
    }

    @Test
    void nonMemberCannotDuplicateTask() {
        User owner = createUniqueUser("dup-outsider-owner");
        User outsider = createUniqueUser("dup-outsider-user");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "복제 비멤버 워크스페이스");
        TaskResponse original = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("비멤버 접근 불가 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        assertThatThrownBy(() -> taskService.duplicate(outsider.getId(), original.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void completingTaskSetsCompletedAt() {
        User user = createUniqueUser("completedat-set-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "완료시각 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("완료될 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));
        assertThat(task.completedAt()).isNull();

        TaskResponse updated = taskService.changeStatus(user.getId(), task.id(), TaskStatus.DONE);

        assertThat(updated.completedAt()).isNotNull();
        List<MyTaskResponse> result = taskService.listMine(user.getId(), TaskStatus.DONE);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).completedAt()).isNotNull();
    }

    @Test
    void revertingDoneClearsCompletedAt() {
        User user = createUniqueUser("completedat-clear-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "완료취소 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("완료취소될 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of(user.getId())));
        taskService.changeStatus(user.getId(), task.id(), TaskStatus.DONE);

        taskService.changeStatus(user.getId(), task.id(), TaskStatus.IN_PROGRESS);

        List<MyTaskResponse> result = taskService.listMine(user.getId(), TaskStatus.IN_PROGRESS);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).completedAt()).isNull();
    }

    @Test
    void listMineWithNoStatusBehavesExactlyAsBefore() {
        User user = createUniqueUser("status-default-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "기본동작 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("대기 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        TaskResponse done = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("완료 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.changeStatus(user.getId(), done.id(), TaskStatus.DONE);

        List<MyTaskResponse> withoutStatus = taskService.listMine(user.getId());
        List<MyTaskResponse> withNullStatus = taskService.listMine(user.getId(), null);

        assertThat(withoutStatus).extracting(MyTaskResponse::name).containsExactly("대기 태스크");
        assertThat(withNullStatus).extracting(MyTaskResponse::name).containsExactly("대기 태스크");
    }

    @Test
    void listMineWithSpecificNonDoneStatusFiltersToThatStatusOnly() {
        User user = createUniqueUser("status-filter-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "상태필터 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("대기중 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        TaskResponse inProgress = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("진행중 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.changeStatus(user.getId(), inProgress.id(), TaskStatus.IN_PROGRESS);

        List<MyTaskResponse> result = taskService.listMine(user.getId(), TaskStatus.IN_PROGRESS);

        assertThat(result).extracting(MyTaskResponse::name).containsExactly("진행중 태스크");
    }

    @Test
    void listMineWithDoneStatusSortsByCompletedAtDescending() {
        User user = createUniqueUser("done-sort-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "완료정렬 워크스페이스");
        TaskResponse firstDone = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("먼저 완료된 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        TaskResponse secondDone = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("나중에 완료된 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.changeStatus(user.getId(), firstDone.id(), TaskStatus.DONE);
        taskService.changeStatus(user.getId(), secondDone.id(), TaskStatus.DONE);

        List<MyTaskResponse> result = taskService.listMine(user.getId(), TaskStatus.DONE);

        assertThat(result).extracting(MyTaskResponse::name)
                .containsExactly("나중에 완료된 태스크", "먼저 완료된 태스크");
    }

    @Test
    void listMineWithDoneStatusExcludesTasksFromDeletedWorkspacesAndExMembers() {
        User owner = createUniqueUser("done-filter-owner");
        User leavingAssignee = createUniqueUser("done-filter-leaving");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "완료필터 워크스페이스");
        joinAsMember(workspace.id(), leavingAssignee);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("완료필터 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(leavingAssignee.getId())));
        taskService.changeStatus(owner.getId(), task.id(), TaskStatus.DONE);

        workspaceService.kick(owner.getId(), workspace.id(), leavingAssignee.getId());

        List<MyTaskResponse> result = taskService.listMine(leavingAssignee.getId(), TaskStatus.DONE);

        assertThat(result).isEmpty();
    }

    @Test
    void listMineWithKeywordFiltersByNameCaseInsensitively() {
        User user = createUniqueUser("keyword-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "키워드 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("Design Review", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("백엔드 작업", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));

        List<MyTaskResponse> result = taskService.listMine(user.getId(), null, "design");

        assertThat(result).extracting(MyTaskResponse::name).containsExactly("Design Review");
    }

    @Test
    void listMineWithKeywordCombinesWithStatusFilter() {
        User user = createUniqueUser("keyword-status-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "키워드상태 워크스페이스");
        TaskResponse matchingInProgress = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("결제 모듈 작업", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.changeStatus(user.getId(), matchingInProgress.id(), TaskStatus.IN_PROGRESS);
        TaskResponse matchingWaiting = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("결제 문서화", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("알림 작업", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));

        List<MyTaskResponse> result = taskService.listMine(user.getId(), TaskStatus.IN_PROGRESS, "결제");

        assertThat(result).extracting(MyTaskResponse::name).containsExactly("결제 모듈 작업");
        assertThat(matchingWaiting).isNotNull();
    }

    @Test
    void listMineWithBlankKeywordBehavesAsNoFilter() {
        User user = createUniqueUser("keyword-blank-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "빈키워드 워크스페이스");
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("아무 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));

        List<MyTaskResponse> withNullKeyword = taskService.listMine(user.getId(), null, null);
        List<MyTaskResponse> withEmptyKeyword = taskService.listMine(user.getId(), null, "");
        List<MyTaskResponse> withWhitespaceKeyword = taskService.listMine(user.getId(), null, "   ");

        assertThat(withNullKeyword).extracting(MyTaskResponse::name).containsExactly("아무 태스크");
        assertThat(withEmptyKeyword).extracting(MyTaskResponse::name).containsExactly("아무 태스크");
        assertThat(withWhitespaceKeyword).extracting(MyTaskResponse::name).containsExactly("아무 태스크");
    }

    @Test
    void listMineWithKeywordAndDoneStatusStillSortsByCompletedAtDescending() {
        User user = createUniqueUser("keyword-done-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "키워드완료 워크스페이스");
        TaskResponse firstDone = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("리포트 먼저 완료", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        TaskResponse secondDone = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("리포트 나중 완료", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("무관한 완료 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1),
                        List.of(user.getId())));
        taskService.changeStatus(user.getId(), firstDone.id(), TaskStatus.DONE);
        taskService.changeStatus(user.getId(), secondDone.id(), TaskStatus.DONE);

        List<MyTaskResponse> result = taskService.listMine(user.getId(), TaskStatus.DONE, "리포트");

        assertThat(result).extracting(MyTaskResponse::name)
                .containsExactly("리포트 나중 완료", "리포트 먼저 완료");
    }

    @Test
    void watchingTaskMakesIsWatchingTrue() {
        User user = createUniqueUser("watch-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "구독 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        assertThat(taskService.isWatching(user.getId(), task.id())).isFalse();

        taskService.watch(user.getId(), task.id());

        assertThat(taskService.isWatching(user.getId(), task.id())).isTrue();
    }

    @Test
    void unwatchingTaskMakesIsWatchingFalse() {
        User user = createUniqueUser("unwatch-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "구독취소 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("구독취소 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskService.watch(user.getId(), task.id());

        taskService.unwatch(user.getId(), task.id());

        assertThat(taskService.isWatching(user.getId(), task.id())).isFalse();
    }

    @Test
    void watchingSameTaskTwiceIsIdempotent() {
        User user = createUniqueUser("watch-twice-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "중복구독 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("중복구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.watch(user.getId(), task.id());
        taskService.watch(user.getId(), task.id());

        assertThat(taskService.isWatching(user.getId(), task.id())).isTrue();
    }

    @Test
    void unwatchingTaskNeverWatchedDoesNotThrow() {
        User user = createUniqueUser("unwatch-nothing-user");
        WorkspaceResponse workspace = workspaceService.create(user.getId(), "구독안한 워크스페이스");
        TaskResponse task = taskService.create(user.getId(), workspace.id(),
                new TaskCreateRequest("구독안한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.unwatch(user.getId(), task.id());

        assertThat(taskService.isWatching(user.getId(), task.id())).isFalse();
    }

    @Test
    void anyWorkspaceMemberCanWatchNotJustAssigneeOrOwner() {
        User owner = createUniqueUser("watch-owner");
        User plainMember = createUniqueUser("watch-plain-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "구독권한 워크스페이스");
        joinAsMember(workspace.id(), plainMember);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("구독권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));

        taskService.watch(plainMember.getId(), task.id());

        assertThat(taskService.isWatching(plainMember.getId(), task.id())).isTrue();
    }

    @Test
    void deletingWatchedTaskDoesNotThrow() {
        User owner = createUniqueUser("watch-delete-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "구독삭제 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("구독삭제 태스크", null, LocalDate.now(), LocalDate.now().plusDays(1), List.of()));
        taskService.watch(owner.getId(), task.id());

        taskService.delete(owner.getId(), task.id());
        entityManager.flush();

        assertThat(taskService.isWatching(owner.getId(), task.id())).isFalse();
    }
}
