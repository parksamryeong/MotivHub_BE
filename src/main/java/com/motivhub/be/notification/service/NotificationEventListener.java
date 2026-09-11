package com.motivhub.be.notification.service;

import com.motivhub.be.notification.domain.NotificationTargetType;
import com.motivhub.be.notification.domain.NotificationType;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.event.AssigneeAddedEvent;
import com.motivhub.be.task.event.ChecklistCompletedEvent;
import com.motivhub.be.task.event.DueDateApproachingEvent;
import com.motivhub.be.task.event.TaskCommentCreatedEvent;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;
    private final TaskService taskService;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public NotificationEventListener(NotificationService notificationService, TaskService taskService,
                                      TaskAssigneeRepository taskAssigneeRepository,
                                      WorkspaceMemberRepository workspaceMemberRepository) {
        this.notificationService = notificationService;
        this.taskService = taskService;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssigneeAdded(AssigneeAddedEvent event) {
        Task task = taskService.getTask(event.taskId());
        String message = "'" + task.getName() + "'의 담당자로 지정되었습니다.";
        notifySafely(event.newAssigneeUserId(), NotificationType.ASSIGNEE_ADDED,
                NotificationTargetType.TASK, task.getId(), message);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCommentCreated(TaskCommentCreatedEvent event) {
        Task task = taskService.getTask(event.taskId());
        Set<Long> recipientIds = assigneeIds(task.getId());
        recipientIds.add(task.getCreatedBy().getId());
        recipientIds.remove(event.authorId());
        String message = "'" + event.authorNickname() + "'님이 '" + task.getName() + "'에 댓글을 남겼습니다.";
        for (Long recipientId : recipientIds) {
            notifySafely(recipientId, NotificationType.TASK_COMMENT_ADDED,
                    NotificationTargetType.TASK, task.getId(), message);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChecklistCompleted(ChecklistCompletedEvent event) {
        Task task = taskService.getTask(event.taskId());
        String message = "'" + task.getName() + "'의 체크리스트를 모두 완료했습니다.";
        for (Long recipientId : assigneeIds(task.getId())) {
            notifySafely(recipientId, NotificationType.CHECKLIST_COMPLETED,
                    NotificationTargetType.TASK, task.getId(), message);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDueDateApproaching(DueDateApproachingEvent event) {
        Task task = taskService.getTask(event.taskId());
        Set<Long> recipientIds = assigneeIds(task.getId());
        workspaceMemberRepository.findByWorkspaceIdAndRole(task.getWorkspace().getId(), WorkspaceRole.OWNER)
                .ifPresent(owner -> recipientIds.add(owner.getUser().getId()));
        String message = "'" + task.getName() + "' 마감일이 이틀 남았습니다.";
        for (Long recipientId : recipientIds) {
            if (!notificationService.alreadyNotifiedToday(recipientId, NotificationType.DUE_DATE_APPROACHING, task.getId())) {
                notifySafely(recipientId, NotificationType.DUE_DATE_APPROACHING,
                        NotificationTargetType.TASK, task.getId(), message);
            }
        }
    }

    private void notifySafely(Long recipientId, NotificationType type, NotificationTargetType targetType,
                               Long targetId, String message) {
        try {
            notificationService.notify(recipientId, type, targetType, targetId, message);
        } catch (Exception e) {
            log.warn("알림 생성 실패 - recipientId={}, type={}, targetId={}", recipientId, type, targetId, e);
        }
    }

    private Set<Long> assigneeIds(Long taskId) {
        Set<Long> ids = new HashSet<>();
        taskAssigneeRepository.findByTaskId(taskId).forEach(assignee -> ids.add(assignee.getUser().getId()));
        return ids;
    }
}
