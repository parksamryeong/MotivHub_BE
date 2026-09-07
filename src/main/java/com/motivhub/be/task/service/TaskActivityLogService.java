package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskActivityAction;
import com.motivhub.be.task.domain.TaskActivityLog;
import com.motivhub.be.task.dto.TaskActivityLogResponse;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.repository.TaskActivityLogRepository;
import com.motivhub.be.task.repository.TaskRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskActivityLogService {

    private static final int FIELD_MAX_LENGTH = 30;
    private static final int VALUE_MAX_LENGTH = 500;

    private final TaskActivityLogRepository taskActivityLogRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceService workspaceService;

    public TaskActivityLogService(TaskActivityLogRepository taskActivityLogRepository,
                                   TaskRepository taskRepository, WorkspaceService workspaceService) {
        this.taskActivityLogRepository = taskActivityLogRepository;
        this.taskRepository = taskRepository;
        this.workspaceService = workspaceService;
    }

    @Transactional
    public void record(Task task, User actor, TaskActivityAction action,
                        String field, String oldValue, String newValue) {
        taskActivityLogRepository.save(TaskActivityLog.create(task, actor, action,
                truncate(field, FIELD_MAX_LENGTH), truncate(oldValue, VALUE_MAX_LENGTH), truncate(newValue, VALUE_MAX_LENGTH)));
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public List<TaskActivityLogResponse> list(Long userId, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("태스크를 찾을 수 없습니다."));
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return taskActivityLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(TaskActivityLogResponse::from)
                .toList();
    }
}
