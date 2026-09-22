package com.motivhub.be.task.service;

import com.motivhub.be.file.repository.WorkspaceFileRepository;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskActivityAction;
import com.motivhub.be.task.domain.TaskChecklistItem;
import com.motivhub.be.task.domain.TaskPriority;
import com.motivhub.be.task.dto.MyTaskResponse;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.repository.TaskChecklistProgress;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.exception.TaskPeriodEditForbiddenException;
import com.motivhub.be.task.repository.TaskActivityLogRepository;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.task.repository.TaskChecklistItemRepository;
import com.motivhub.be.task.repository.TaskCommentRepository;
import com.motivhub.be.task.repository.TaskNoteRepository;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.task.domain.TaskAssignee;
import com.motivhub.be.task.event.AssigneeAddedEvent;
import com.motivhub.be.task.event.TaskChangedEvent;
import com.motivhub.be.task.event.TaskChangeType;
import com.motivhub.be.task.exception.InvalidTaskStatusTransitionException;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
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
    private final TaskNoteRepository taskNoteRepository;
    private final WorkspaceFileRepository workspaceFileRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final TaskActivityLogService taskActivityLogService;
    private final TaskAccessPolicy taskAccessPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public TaskService(TaskRepository taskRepository, TaskAssigneeRepository taskAssigneeRepository,
                        TaskChecklistItemRepository taskChecklistItemRepository,
                        TaskCommentRepository taskCommentRepository, TaskActivityLogRepository taskActivityLogRepository,
                        TaskNoteRepository taskNoteRepository, WorkspaceFileRepository workspaceFileRepository,
                        UserRepository userRepository, WorkspaceService workspaceService,
                        TaskActivityLogService taskActivityLogService, TaskAccessPolicy taskAccessPolicy,
                        ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.taskChecklistItemRepository = taskChecklistItemRepository;
        this.taskCommentRepository = taskCommentRepository;
        this.taskActivityLogRepository = taskActivityLogRepository;
        this.taskNoteRepository = taskNoteRepository;
        this.workspaceFileRepository = workspaceFileRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.taskActivityLogService = taskActivityLogService;
        this.taskAccessPolicy = taskAccessPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TaskResponse create(Long userId, Long workspaceId, TaskCreateRequest request) {
        WorkspaceMember membership = workspaceService.getMembership(workspaceId, userId);
        Workspace workspace = membership.getWorkspace();
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        TaskPriority priority = request.priority() == null ? TaskPriority.MEDIUM : request.priority();
        Task task = taskRepository.save(Task.create(
                workspace, request.name(), request.description(), request.startDate(), request.dueDate(),
                creator, priority));
        taskActivityLogService.record(task, creator, TaskActivityAction.CREATE, null, null, null);

        List<Long> assigneeIds = request.assigneeIds() == null ? List.of() : request.assigneeIds();
        for (Long assigneeId : assigneeIds) {
            workspaceService.getMembership(workspaceId, assigneeId);
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
            taskAssigneeRepository.save(TaskAssignee.create(task, assignee));
        }
        eventPublisher.publishEvent(new TaskChangedEvent(task.getId(), workspaceId, TaskChangeType.CREATED));
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

    public String getDescriptionYjsStateBase64(Long userId, Long taskId) {
        Task task = getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        byte[] state = task.getDescriptionYjsState();
        return state == null ? null : Base64.getEncoder().encodeToString(state);
    }

    @Transactional
    public void updateDescriptionYjsState(Long taskId, byte[] state) {
        Task task = getTask(taskId);
        task.updateYjsDescriptionState(state);
    }

    public TaskResponse getResponseForBoardBroadcast(Long taskId) {
        Task task = getTask(taskId);
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
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
        eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.UPDATED));
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
        TaskStatus oldStatus = task.getStatus();
        task.updatePeriod(startDate, dueDate);
        String newPeriod = task.getStartDate() + "~" + task.getDueDate();
        if (!oldPeriod.equals(newPeriod)) {
            taskActivityLogService.record(task, actor, TaskActivityAction.UPDATE_PERIOD, "period", oldPeriod, newPeriod);
        }
        if (task.getStatus() != oldStatus) {
            taskActivityLogService.record(task, actor, TaskActivityAction.CHANGE_STATUS,
                    "status", oldStatus.name(), task.getStatus().name());
        }
        eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.UPDATED));
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
        taskNoteRepository.deleteByTaskId(taskId);
        workspaceFileRepository.clearTaskId(taskId);
        taskRepository.delete(task);
        eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.DELETED));
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
        eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.UPDATED));
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    @Transactional
    public TaskResponse updatePriority(Long userId, Long taskId, TaskPriority newPriority) {
        Task task = getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        TaskPriority oldPriority = task.getPriority();
        task.changePriority(newPriority);
        if (oldPriority != newPriority) {
            taskActivityLogService.record(task, actor, TaskActivityAction.CHANGE_PRIORITY,
                    "priority", oldPriority.name(), newPriority.name());
        }
        eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.UPDATED));
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    @Transactional
    public TaskResponse duplicate(Long userId, Long taskId) {
        Task original = getTask(taskId);
        Long workspaceId = original.getWorkspace().getId();
        workspaceService.getMembership(workspaceId, userId);

        List<Long> validAssigneeIds = taskAssigneeRepository.findByTaskId(taskId).stream()
                .map(assignee -> assignee.getUser().getId())
                .filter(assigneeId -> isStillWorkspaceMember(workspaceId, assigneeId))
                .toList();

        TaskCreateRequest duplicateRequest = new TaskCreateRequest(
                original.getName(), original.getDescription(), original.getStartDate(), original.getDueDate(),
                validAssigneeIds, original.getPriority());
        // 같은 빈 내부 호출이라 프록시를 타지 않는다 - create()의 @Transactional은 무시되고
        // duplicate()가 연 트랜잭션에서 그대로 실행된다(REQUIRED라 결과는 동일). create()의
        // propagation/rollback 규칙을 바꾸더라도 이 경로에는 적용되지 않으니 주의.
        TaskResponse duplicated = create(userId, workspaceId, duplicateRequest);

        Task duplicatedTask = getTask(duplicated.id());
        for (TaskChecklistItem item : taskChecklistItemRepository.findByTaskIdOrderByOrderIndexAsc(taskId)) {
            taskChecklistItemRepository.save(
                    TaskChecklistItem.create(duplicatedTask, item.getContent(), item.getOrderIndex()));
        }
        return duplicated;
    }

    // 원본 담당자 중 이미 워크스페이스를 나갔거나 추방된 사람은 조용히 제외한다 - leave()/kick()이
    // TaskAssignee 행을 정리하지 않으므로 원본 담당자 목록에 비멤버가 남아있을 수 있다.
    private boolean isStillWorkspaceMember(Long workspaceId, Long userId) {
        try {
            workspaceService.getMembership(workspaceId, userId);
            return true;
        } catch (NotWorkspaceMemberException e) {
            return false;
        }
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
            eventPublisher.publishEvent(new AssigneeAddedEvent(taskId, targetUserId));
            eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.UPDATED));
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
                    eventPublisher.publishEvent(new TaskChangedEvent(taskId, task.getWorkspace().getId(), TaskChangeType.UPDATED));
                });
        return TaskResponse.of(task, getAssigneeSummaries(taskId));
    }

    public List<MyTaskResponse> listMine(Long userId) {
        return listMine(userId, null, null);
    }

    public List<MyTaskResponse> listMine(Long userId, TaskStatus status) {
        return listMine(userId, status, null);
    }

    public List<MyTaskResponse> listMine(Long userId, TaskStatus status, String q) {
        String normalizedQ = normalizeKeyword(q);
        List<Task> tasks = fetchTasksForMine(userId, status, normalizedQ);
        return toMyTaskResponses(tasks);
    }

    private String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim();
    }

    private List<Task> fetchTasksForMine(Long userId, TaskStatus status, String q) {
        if (status == null) {
            return taskRepository.findAssignedToUserExcludingStatus(userId, TaskStatus.DONE, q);
        }
        if (status == TaskStatus.DONE) {
            return taskRepository.findCompletedTasksForUser(userId, q, PageRequest.of(0, 50));
        }
        return taskRepository.findAssignedToUserByStatus(userId, status, q);
    }

    private List<MyTaskResponse> toMyTaskResponses(List<Task> tasks) {
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        Map<Long, TaskChecklistProgress> progressByTaskId = checklistProgressByTaskId(taskIds);
        Set<Long> taskIdsWithComments = taskIds.isEmpty()
                ? Set.of()
                : new HashSet<>(taskCommentRepository.findTaskIdsWithCommentsByTaskIdIn(taskIds));
        return tasks.stream()
                .map(task -> {
                    TaskChecklistProgress progress = progressByTaskId.get(task.getId());
                    long total = progress == null ? 0L : progress.total();
                    long completed = progress == null ? 0L : progress.completed();
                    return MyTaskResponse.of(task, total, completed, taskIdsWithComments.contains(task.getId()));
                })
                .toList();
    }

    // 태스크마다 개별 쿼리를 날리지 않고, 한 번의 집계 쿼리로 전부 가져온 뒤 메모리에서 조합한다(N+1 방지).
    private Map<Long, TaskChecklistProgress> checklistProgressByTaskId(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TaskChecklistProgress> result = new HashMap<>();
        for (TaskChecklistProgress row : taskChecklistItemRepository.countProgressByTaskIdIn(taskIds)) {
            result.put(row.taskId(), row);
        }
        return result;
    }

}
