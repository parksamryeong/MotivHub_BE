package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskComment;
import com.motivhub.be.task.dto.TaskCommentResponse;
import com.motivhub.be.task.repository.TaskCommentRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskCommentService {

    private final TaskCommentRepository taskCommentRepository;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    public TaskCommentService(TaskCommentRepository taskCommentRepository, TaskService taskService,
                               WorkspaceService workspaceService, UserRepository userRepository) {
        this.taskCommentRepository = taskCommentRepository;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskCommentResponse create(Long userId, Long taskId, String content) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        TaskComment comment = taskCommentRepository.save(TaskComment.create(task, author, content));
        return TaskCommentResponse.from(comment);
    }

    public List<TaskCommentResponse> list(Long userId, Long taskId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(TaskCommentResponse::from)
                .toList();
    }
}
