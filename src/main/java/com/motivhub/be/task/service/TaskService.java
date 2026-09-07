package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskActivityAction;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.exception.TaskPeriodEditForbiddenException;
import com.motivhub.be.task.repository.TaskActivityLogRepository;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskChecklistItemRepository;
import com.motivhub.be.task.repository.TaskCommentRepository;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.task.domain.TaskAssignee;
import com.motivhub.be.task.exception.InvalidTaskStatusTransitionException;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskActivityLogRepository taskActivityLogRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final TaskActivityLogService taskActivityLogService;
    private final TaskAccessPolicy taskAccessPolicy;

    public TaskService(TaskRepository taskRepository, TaskAssigneeRepository taskAssigneeRepository,
                        TaskChecklistItemRepository taskChecklistItemRepository,
                        TaskCommentRepository taskCommentRepository, TaskActivityLogRepository taskActivityLogRepository,
                        UserRepository userRepository, WorkspaceService workspaceService,
                        TaskActivityLogService taskActivityLogService, TaskAccessPolicy taskAccessPolicy) {
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskChecklistItemRepository = taskChecklistItemRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskActivityLogRepository = taskActivityLogRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.taskActivityLogService = taskActivityLogService;
        this.taskAccessPolicy = taskAccessPolicy;
    }

    @Transactional
    public TaskResponse create(Long userId, Long workspaceId, TaskCreateRequest request) {
        WorkspaceMember membership = workspaceService.getMembership(workspaceId, userId);
        Workspace workspace = membership.getWorkspace();
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        Task task = taskRepository.save(Task.create(
                workspace, request.name(), request.description(), request.startDate(), request.dueDate(), creator));
        taskActivityLogService.record(task, creator, TaskActivityAction.CREATE, null, null, null);

        List<Long> assigneeIds = request.assigneeIds() == null ? List.of() : request.assigneeIds();
        for (Long assigneeId : assigneeIds) {
            workspaceService.getMembership(workspaceId, assigneeId);
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
            taskAssigneeRepository.save(TaskAssignee.create(task, assignee));
        }
        return TaskResponse.of(task, getAssigneeSummaries(task.getId()));
    }

    public List<TaskResponse> listByWorkspace(Long userId, Long workspaceId) {
        workspaceService.getMembership(workspaceId, userId);
        List<Task> tasks = taskRepository.findByWorkspaceId(workspaceId);
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Long, List<UserSummary>> assigneesByTaskId = taskAssigneeRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(
                        assignee -> assignee.getTask().getId(),
                        Collectors.mapping(assignee -> UserSummary.from(assignee.getUser()), Collectors.toList())));
        return tasks.stream()
                .map(task -> TaskResponse.of(task, assigneesByTaskId.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    public TaskResponse getDetail(Long userId, Long taskId) {
        Task task = getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    public Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("태스크를 찾을 수 없습니다."));
    }

    List<UserSummary> getAssigneeSummaries(Long taskId) {
        return taskAssigneeRepository.findByTaskId(taskId).stream()
                .map(assignee -> UserSummary.from(assignee.getUser()))
                .toList();
    }

    @Transactional
    public TaskResponse updateContent(Long userId, Long taskId, String name, String description) {
        Task task = getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        String oldName = task.getName();
        String oldDescription = task.getDescription();
        task.updateContent(name, description);
        if (!Objects.equals(oldName, task.getName())) {
            taskActivityLogService.record(task, actor, TaskActivityAction.UPDATE_CONTENT, "name", oldName, task.getName());
        }
        if (!Objects.equals(oldDescription, task.getDescription())) {
            taskActivityLogService.record(task, actor, TaskActivityAction.UPDATE_CONTENT,
                    "description", oldDescription, task.getDescription());
        }
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    @Transactional
    public TaskResponse updatePeriod(Long userId, Long taskId, LocalDate startDate, LocalDate dueDate) {
        Task task = getTask(taskId);
        try {
            workspaceService.requireOwner(task.getWorkspace().getId(), userId);
        } catch (NotWorkspaceOwnerException e) {
            throw new TaskPeriodEditForbiddenException("태스크 기간 수정은 워크스페이스 OWNER만 가능합니다.");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        String oldPeriod = task.getStartDate() + "~" + task.getDueDate();
        task.updatePeriod(startDate, dueDate);
        String newPeriod = task.getStartDate() + "~" + task.getDueDate();
        if (!oldPeriod.equals(newPeriod)) {
            taskActivityLogService.record(task, actor, TaskActivityAction.UPDATE_PERIOD, "period", oldPeriod, newPeriod);
        }
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        Task task = getTask(taskId);
        WorkspaceMember member = workspaceService.getMembership(task.getWorkspace().getId(), userId);
        boolean allowed = member.isOwner()
                || (task.getStatus() != TaskStatus.EXPIRED && task.isCreatedBy(userId));
        if (!allowed) {
            throw new TaskEditForbiddenException("태스크 삭제 권한이 없습니다.");
        }
        taskAssigneeRepository.deleteByTaskId(taskId);
        taskCommentRepository.deleteByTaskId(taskId);
        taskActivityLogRepository.deleteByTaskId(taskId);
        taskChecklistItemRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponse changeStatus(Long userId, Long taskId, TaskStatus newStatus) {
        Task task = getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        if (newStatus == TaskStatus.EXPIRED || task.getStatus() == TaskStatus.EXPIRED) {
            throw new InvalidTaskStatusTransitionException("만료 상태는 시스템(자동) 또는 기간 연장을 통해서만 변경됩니다.");
        }
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        TaskStatus oldStatus = task.getStatus();
        task.changeStatus(newStatus);
        if (oldStatus != newStatus) {
            taskActivityLogService.record(task, actor, TaskActivityAction.CHANGE_STATUS,
                    "status", oldStatus.name(), newStatus.name());
        }
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    @Transactional
    public TaskResponse addAssignee(Long userId, Long taskId, Long targetUserId) {
        Task task = getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        workspaceService.getMembership(task.getWorkspace().getId(), targetUserId);
        if (!taskAssigneeRepository.existsByTaskIdAndUserId(taskId, targetUserId)) {
            User target = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
            taskAssigneeRepository.save(TaskAssignee.create(task, target));
            User actor = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
            taskActivityLogService.record(task, actor, TaskActivityAction.ADD_ASSIGNEE, "assignee", null, target.getNickname());
        }
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    @Transactional
    public TaskResponse removeAssignee(Long userId, Long taskId, Long targetUserId) {
        Task task = getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        taskAssigneeRepository.findByTaskIdAndUserId(taskId, targetUserId)
                .ifPresent(assignee -> {
                    taskAssigneeRepository.delete(assignee);
                    User actor = userRepository.findById(userId)
                            .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
                    taskActivityLogService.record(task, actor, TaskActivityAction.REMOVE_ASSIGNEE,
                            "assignee", assignee.getUser().getNickname(), null);
                });
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

}
