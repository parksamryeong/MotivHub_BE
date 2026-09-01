package com.motivhub.be.workspace.service;

import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceInvite;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceInviteResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.InvalidInviteTokenException;
import com.motivhub.be.workspace.exception.InviteExpiredException;
import com.motivhub.be.workspace.exception.InviteRevokedException;
import com.motivhub.be.workspace.repository.WorkspaceInviteRepository;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkspaceInviteService {

    private static final long EXPIRE_DAYS = 7;

    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final WorkspaceInviteMailService workspaceInviteMailService;

    public WorkspaceInviteService(WorkspaceInviteRepository workspaceInviteRepository,
                                   WorkspaceMemberRepository workspaceMemberRepository,
                                   UserRepository userRepository,
                                   WorkspaceService workspaceService,
                                   WorkspaceInviteMailService workspaceInviteMailService) {
        this.workspaceInviteRepository = workspaceInviteRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.workspaceInviteMailService = workspaceInviteMailService;
    }

    @Transactional
    public WorkspaceInviteResponse createInvite(Long ownerUserId, Long workspaceId, String email) {
        workspaceService.requireOwner(workspaceId, ownerUserId);
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        String normalizedEmail = blankToNull(email);
        String token = UUID.randomUUID().toString();
        WorkspaceInvite invite = workspaceInviteRepository.save(
                WorkspaceInvite.create(workspace, token, normalizedEmail, LocalDateTime.now().plusDays(EXPIRE_DAYS)));
        if (normalizedEmail != null) {
            workspaceInviteMailService.sendInvite(normalizedEmail, workspace, token);
        }
        return WorkspaceInviteResponse.from(invite);
    }

    @Transactional
    public void revokeInvite(Long ownerUserId, Long workspaceId, Long inviteId) {
        workspaceService.requireOwner(workspaceId, ownerUserId);
        WorkspaceInvite invite = workspaceInviteRepository.findById(inviteId)
                .filter(i -> i.getWorkspace().getId().equals(workspaceId))
                .orElseThrow(() -> new InvalidInviteTokenException("존재하지 않는 초대입니다."));
        invite.revoke();
    }

    @Transactional
    public WorkspaceResponse accept(Long userId, String token) {
        WorkspaceInvite invite = workspaceInviteRepository.findByToken(token)
                .orElseThrow(() -> new InvalidInviteTokenException("유효하지 않은 초대 링크입니다."));
        if (invite.getRevokedAt() != null) {
            throw new InviteRevokedException("무효화된 초대입니다.");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InviteExpiredException("만료된 초대입니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        Workspace workspace = invite.getWorkspace();
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspace.getId(), userId)
                .orElseGet(() -> workspaceMemberRepository.save(
                        WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER)));
        return WorkspaceResponse.of(workspace, member.getRole());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
