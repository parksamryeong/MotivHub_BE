package com.motivhub.be.workspace.service;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.exception.WorkspaceLeaveRequiresTransferException;
import com.motivhub.be.workspace.exception.WorkspaceMemberNotFoundException;
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
        getWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotWorkspaceMemberException("워크스페이스 멤버가 아닙니다."));
    }

    @Transactional
    public WorkspaceResponse updateName(Long userId, Long workspaceId, String name) {
        requireOwner(workspaceId, userId);
        Workspace workspace = getWorkspace(workspaceId);
        workspace.rename(name);
        return WorkspaceResponse.of(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public void delete(Long userId, Long workspaceId) {
        requireOwner(workspaceId, userId);
        getWorkspace(workspaceId).delete();
    }

    @Transactional
    public void leave(Long userId, Long workspaceId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        if (member.isOwner()) {
            long memberCount = workspaceMemberRepository.countByWorkspaceId(workspaceId);
            if (memberCount > 1) {
                throw new WorkspaceLeaveRequiresTransferException(
                        "다른 멤버가 있는 워크스페이스는 오너십을 이전한 후에만 나갈 수 있습니다.");
            }
            member.getWorkspace().delete();
            return;
        }
        workspaceMemberRepository.delete(member);
    }

    @Transactional
    public void kick(Long ownerUserId, Long workspaceId, Long targetUserId) {
        requireOwner(workspaceId, ownerUserId);
        WorkspaceMember target = getMembership(workspaceId, targetUserId);
        workspaceMemberRepository.delete(target);
    }

    @Transactional
    public void transferOwnership(Long ownerUserId, Long workspaceId, Long newOwnerUserId) {
        WorkspaceMember currentOwner = getMembership(workspaceId, ownerUserId);
        if (!currentOwner.isOwner()) {
            throw new NotWorkspaceOwnerException("워크스페이스 OWNER만 가능한 작업입니다.");
        }
        WorkspaceMember newOwner = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, newOwnerUserId)
                .orElseThrow(() -> new WorkspaceMemberNotFoundException("대상이 워크스페이스 멤버가 아닙니다."));
        currentOwner.changeRole(WorkspaceRole.MEMBER);
        newOwner.changeRole(WorkspaceRole.OWNER);
    }

    public void requireOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = getMembership(workspaceId, userId);
        if (!member.isOwner()) {
            throw new NotWorkspaceOwnerException("워크스페이스 OWNER만 가능한 작업입니다.");
        }
    }
}
