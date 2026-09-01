package com.motivhub.be.workspace.service;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.WorkspaceNotFoundException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.repository.WorkspaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                             WorkspaceMemberRepository workspaceMemberRepository,
                             UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public WorkspaceResponse create(Long userId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        Workspace workspace = workspaceRepository.save(Workspace.create(name));
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.OWNER));
        return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER);
    }

    public List<WorkspaceResponse> listMine(Long userId) {
        return workspaceMemberRepository.findByUserIdFetchWorkspace(userId).stream()
                .map(member -> WorkspaceResponse.of(member.getWorkspace(), member.getRole()))
                .toList();
    }

    public WorkspaceResponse getDetail(Long userId, Long workspaceId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        return WorkspaceResponse.of(member.getWorkspace(), member.getRole());
    }

    public Workspace getWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new WorkspaceNotFoundException("워크스페이스를 찾을 수 없습니다."));
    }

    public WorkspaceMember getMembership(Long workspaceId, Long userId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotWorkspaceMemberException("워크스페이스 멤버가 아닙니다."));
    }
}
