package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.domain.TaskNote;
import com.motivhub.be.task.dto.TaskNoteResponse;
import com.motivhub.be.task.repository.TaskNoteRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskNoteService {

    private final TaskNoteRepository taskNoteRepository;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final UserRepository userRepository;

    public TaskNoteService(TaskNoteRepository taskNoteRepository, TaskService taskService,
                            WorkspaceService workspaceService, UserRepository userRepository) {
        this.taskNoteRepository = taskNoteRepository;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.userRepository = userRepository;
    }

    public TaskNoteResponse get(Long userId, Long taskId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return taskNoteRepository.findByTaskId(taskId)
                .map(TaskNoteResponse::from)
                .orElseGet(() -> TaskNoteResponse.empty(taskId));
    }

    public String getYjsStateBase64(Long userId, Long taskId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        return taskNoteRepository.findByTaskId(taskId)
                .map(TaskNote::getContentYjsState)
                .filter(state -> state != null)
                .map(state -> Base64.getEncoder().encodeToString(state))
                .orElse(null);
    }

    @Transactional
    public void updateYjsState(Long taskId, byte[] state) {
        taskNoteRepository.findByTaskId(taskId)
                .ifPresent(note -> note.updateYjsState(state));
    }

    @Transactional
    public TaskNoteResponse upsert(Long userId, Long taskId, String content) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
        User editor = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        TaskNote note = taskNoteRepository.findByTaskId(taskId).orElse(null);
        if (note == null) {
            note = taskNoteRepository.save(TaskNote.create(task, content, editor));
        } else {
            note.updateContent(content, editor);
        }
        return TaskNoteResponse.from(note);
    }
}
