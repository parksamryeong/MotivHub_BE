package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.task.domain.TaskAssignee;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.service.WorkspaceService;
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
}
