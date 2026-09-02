package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.exception.TaskPeriodEditForbiddenException;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.task.domain.TaskAssignee;
import com.motivhub.be.task.exception.InvalidTaskStatusTransitionException;
import com.motivhub.be.user.domain.User;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;

    public TaskService(TaskRepository taskRepository, TaskAssigneeRepository taskAssigneeRepository,
                        UserRepository userRepository, WorkspaceService workspaceService) {
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional
    public TaskResponse create(Long userId, Long workspaceId, TaskCreateRequest request) {
        WorkspaceMember membership = workspaceService.getMembership(workspaceId, userId);
        Workspace workspace = membership.getWorkspace();
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        Task task = taskRepository.save(Task.create(
                workspace, request.name(), request.description(), request.startDate(), request.dueDate(), creator));

        List<Long> assigneeIds = request.assigneeIds() == null ? List.of() : request.assigneeIds();
        for (Long assigneeId : assigneeIds) {
            workspaceService.getMembership(workspaceId, assigneeId);
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
            taskAssigneeRepository.save(TaskAssignee.create(task, assignee));
        }
        return TaskResponse.of(task, getAssigneeIds(task.getId()));
    }

    public List<TaskResponse> listByWorkspace(Long userId, Long workspaceId) {
        workspaceService.getMembership(workspaceId, userId);
        List<Task> tasks = taskRepository.findByWorkspaceId(workspaceId);
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Long, List<Long>> assigneeIdsByTaskId = taskAssigneeRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(
                        assignee -> assignee.getTask().getId(),
                        Collectors.mapping(assignee -> assignee.getUser().getId(), Collectors.toList())));
        return tasks.stream()
                .map(task -> TaskResponse.of(task, assigneeIdsByTaskId.getOrDefault(task.getId(), List.of())))
                .toList();
    }

    public TaskResponse getDetail(Long userId, Long taskId) {
        Task task = getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return TaskResponse.of(task, getAssigneeIds(taskId));
    }

    public Task getTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("태스크를 찾을 수 없습니다."));
    }

    List<Long> getAssigneeIds(Long taskId) {
        return taskAssigneeRepository.findByTaskId(taskId).stream()
                .map(assignee -> assignee.getUser().getId())
                .toList();
    }

    @Transactional
    public TaskResponse updateContent(Long userId, Long taskId, String name, String description) {
        Task task = getTask(taskId);
        requireAssigneeOrOwner(task, userId);
        task.updateContent(name, description);
        return TaskResponse.of(task, getAssigneeIds(taskId));
    }

    @Transactional
    public TaskResponse updatePeriod(Long userId, Long taskId, LocalDate startDate, LocalDate dueDate) {
        Task task = getTask(taskId);
        try {
            workspaceService.requireOwner(task.getWorkspace().getId(), userId);
        } catch (NotWorkspaceOwnerException e) {
            throw new TaskPeriodEditForbiddenException("태스크 기간 수정은 워크스페이스 OWNER만 가능합니다.");
        }
        task.updatePeriod(startDate, dueDate);
        return TaskResponse.of(task, getAssigneeIds(taskId));
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
        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponse changeStatus(Long userId, Long taskId, TaskStatus newStatus) {
        Task task = getTask(taskId);
        requireAssigneeOrOwner(task, userId);
        if (newStatus == TaskStatus.EXPIRED || task.getStatus() == TaskStatus.EXPIRED) {
            throw new InvalidTaskStatusTransitionException("만료 상태는 시스템(자동) 또는 기간 연장을 통해서만 변경됩니다.");
        }
        task.changeStatus(newStatus);
        return TaskResponse.of(task, getAssigneeIds(taskId));
    }

    @Transactional
    public TaskResponse addAssignee(Long userId, Long taskId, Long targetUserId) {
        Task task = getTask(taskId);
        requireAssigneeOrOwner(task, userId);
        workspaceService.getMembership(task.getWorkspace().getId(), targetUserId);
        if (!taskAssigneeRepository.existsByTaskIdAndUserId(taskId, targetUserId)) {
            User target = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
            taskAssigneeRepository.save(TaskAssignee.create(task, target));
        }
        return TaskResponse.of(task, getAssigneeIds(taskId));
    }

    @Transactional
    public TaskResponse removeAssignee(Long userId, Long taskId, Long targetUserId) {
        Task task = getTask(taskId);
        requireAssigneeOrOwner(task, userId);
        taskAssigneeRepository.findByTaskIdAndUserId(taskId, targetUserId)
                .ifPresent(taskAssigneeRepository::delete);
        return TaskResponse.of(task, getAssigneeIds(taskId));
    }

    private void requireAssigneeOrOwner(Task task, Long userId) {
        WorkspaceMember member = workspaceService.getMembership(task.getWorkspace().getId(), userId);
        boolean isAssignee = taskAssigneeRepository.existsByTaskIdAndUserId(task.getId(), userId);
        if (!member.isOwner() && !isAssignee) {
            throw new TaskEditForbiddenException("태스크 수정 권한이 없습니다(담당자 또는 OWNER만 가능).");
        }
    }
}
