package com.motivhub.be.task.service;

import com.motivhub.be.issue.dto.IssueResponse;
import com.motivhub.be.issue.service.IssueService;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskComment;
import com.motivhub.be.task.dto.TaskCommentResponse;
import com.motivhub.be.task.exception.TaskCommentForbiddenException;
import com.motivhub.be.task.exception.TaskCommentNotFoundException;
import com.motivhub.be.task.event.TaskCommentCreatedEvent;
import com.motivhub.be.task.repository.TaskCommentRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskCommentService {

    private final TaskCommentRepository taskCommentRepository;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IssueService issueService;

    public TaskCommentService(TaskCommentRepository taskCommentRepository, TaskService taskService,
                               WorkspaceService workspaceService, UserRepository userRepository,
                               ApplicationEventPublisher eventPublisher, IssueService issueService) {
        this.taskCommentRepository = taskCommentRepository;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.issueService = issueService;
    }

    @Transactional
    public TaskCommentResponse create(Long userId, Long taskId, String content) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        TaskComment comment = taskCommentRepository.save(TaskComment.create(task, author, content));
        eventPublisher.publishEvent(new TaskCommentCreatedEvent(taskId, userId, author.getNickname()));
        return TaskCommentResponse.from(comment);
    }

    public List<TaskCommentResponse> list(Long userId, Long taskId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return taskCommentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(TaskCommentResponse::from)
                .toList();
    }

    @Transactional
    public TaskCommentResponse update(Long userId, Long taskId, Long commentId, String content) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        TaskComment comment = findComment(taskId, commentId);
        if (!comment.isAuthoredBy(userId)) {
            throw new TaskCommentForbiddenException("본인이 작성한 댓글만 수정할 수 있습니다.");
        }
        comment.updateContent(content);
        return TaskCommentResponse.from(comment);
    }

    @Transactional
    public void delete(Long userId, Long taskId, Long commentId) {
        Task task = taskService.getTask(taskId);
        WorkspaceMember member = workspaceService.getMembership(task.getWorkspace().getId(), userId);
        TaskComment comment = findComment(taskId, commentId);
        boolean allowed = member.isOwner() || comment.isAuthoredBy(userId);
        if (!allowed) {
            throw new TaskCommentForbiddenException("본인이 작성한 댓글이거나 워크스페이스 OWNER만 삭제할 수 있습니다.");
        }
        taskCommentRepository.delete(comment);
    }

    @Transactional
    public IssueResponse promoteToIssue(Long userId, Long taskId, Long commentId, String title) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        TaskComment comment = findComment(taskId, commentId);
        return issueService.create(userId, task.getWorkspace().getId(), title, comment.getContent(), null);
    }

    private TaskComment findComment(Long taskId, Long commentId) {
        return taskCommentRepository.findById(commentId)
                .filter(comment -> comment.getTask().getId().equals(taskId))
                .orElseThrow(() -> new TaskCommentNotFoundException("댓글을 찾을 수 없습니다."));
    }
}
