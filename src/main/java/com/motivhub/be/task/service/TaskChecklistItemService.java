package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskChecklistItem;
import com.motivhub.be.task.dto.TaskChecklistItemResponse;
import com.motivhub.be.task.repository.TaskChecklistItemRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskChecklistItemService {

    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final TaskAccessPolicy taskAccessPolicy;

    public TaskChecklistItemService(TaskChecklistItemRepository taskChecklistItemRepository, TaskService taskService,
                                     WorkspaceService workspaceService, TaskAccessPolicy taskAccessPolicy) {
        this.taskChecklistItemRepository = taskChecklistItemRepository;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.taskAccessPolicy = taskAccessPolicy;
    }

    @Transactional
    public TaskChecklistItemResponse create(Long userId, Long taskId, String content) {
        Task task = taskService.getTask(taskId);
        taskAccessPolicy.requireEditPermission(task, userId);
        int orderIndex = (int) taskChecklistItemRepository.countByTaskId(taskId);
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
}
