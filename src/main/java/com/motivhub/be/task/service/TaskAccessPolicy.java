package com.motivhub.be.task.service;

import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.repository.TaskAssigneeRepository;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.service.WorkspaceService;
import org.springframework.stereotype.Component;

@Component
public class TaskAccessPolicy {

    private final TaskAssigneeRepository taskAssigneeRepository;
    private final WorkspaceService workspaceService;

    public TaskAccessPolicy(TaskAssigneeRepository taskAssigneeRepository, WorkspaceService workspaceService) {
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.workspaceService = workspaceService;
    }

    public void requireEditPermission(Task task, Long userId) {
        WorkspaceMember member = workspaceService.getMembership(task.getWorkspace().getId(), userId);
        boolean isAssignee = taskAssigneeRepository.existsByTaskIdAndUserId(task.getId(), userId);
        if (!member.isOwner() && !isAssignee) {
            throw new TaskEditForbiddenException("태스크 수정 권한이 없습니다(담당자 또는 OWNER만 가능).");
        }
    }
}
