package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskChecklistItem;
import com.motivhub.be.task.dto.TaskChecklistItemResponse;
import com.motivhub.be.task.event.ChecklistCompletedEvent;
import com.motivhub.be.task.exception.TaskChecklistItemNotFoundException;
import com.motivhub.be.task.repository.TaskChecklistItemRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskChecklistItemService {

    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final TaskAccessPolicy taskAccessPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public TaskChecklistItemService(TaskChecklistItemRepository taskChecklistItemRepository, TaskService taskService,
                                     WorkspaceService workspaceService, TaskAccessPolicy taskAccessPolicy,
                                     ApplicationEventPublisher eventPublisher) {
        this.taskChecklistItemRepository = taskChecklistItemRepository;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.taskAccessPolicy = taskAccessPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TaskChecklistItemResponse create(Long userId, Long taskId, String content) {
        Task task = taskService.getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        int orderIndex = taskChecklistItemRepository.findMaxOrderIndexByTaskId(taskId) + 1;
        TaskChecklistItem item = taskChecklistItemRepository.save(TaskChecklistItem.create(task, content, orderIndex));
        return TaskChecklistItemResponse.from(item);
    }

    public List<TaskChecklistItemResponse> list(Long userId, Long taskId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return taskChecklistItemRepository.findByTaskIdOrderByOrderIndexAsc(taskId).stream()
                .map(TaskChecklistItemResponse::from)
                .toList();
    }

    @Transactional
    public TaskChecklistItemResponse update(Long userId, Long taskId, Long itemId, String content, Boolean isDone) {
        Task task = taskService.getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        TaskChecklistItem item = findItem(taskId, itemId);
        boolean wasAlreadyDone = item.isDone();
        if (content != null) {
            item.updateContent(content);
        }
        if (isDone != null) {
            item.markDone(isDone);
        }
        if (Boolean.TRUE.equals(isDone) && !wasAlreadyDone && isAllDone(taskId)) {
            eventPublisher.publishEvent(new ChecklistCompletedEvent(taskId));
        }
        return TaskChecklistItemResponse.from(item);
    }

    @Transactional
    public void delete(Long userId, Long taskId, Long itemId) {
        Task task = taskService.getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        taskChecklistItemRepository.delete(findItem(taskId, itemId));
    }

    private TaskChecklistItem findItem(Long taskId, Long itemId) {
        return taskChecklistItemRepository.findById(itemId)
                .filter(item -> item.getTask().getId().equals(taskId))
                .orElseThrow(() -> new TaskChecklistItemNotFoundException("체크리스트 항목을 찾을 수 없습니다."));
    }

    private boolean isAllDone(Long taskId) {
        long total = taskChecklistItemRepository.countByTaskId(taskId);
        long remaining = taskChecklistItemRepository.countByTaskIdAndDoneFalse(taskId);
        return total > 0 && remaining == 0;
    }
}
