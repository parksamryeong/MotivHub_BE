package com.motivhub.be.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceInviteResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.InvalidInviteTokenException;
import com.motivhub.be.workspace.exception.InviteRevokedException;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class WorkspaceInviteServiceTest extends AbstractIntegrationTest {

    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceInviteService workspaceInviteService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @MockitoBean private WorkspaceInviteMailService workspaceInviteMailService;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "invite-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void ownerCreatesLinkInvite() {
        User owner = newUser("link-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "링크초대 워크스페이스");

        WorkspaceInviteResponse invite = workspaceInviteService.createInvite(owner.getId(), workspace.id(), null);

        assertThat(invite.email()).isNull();
        assertThat(invite.token()).isNotBlank();
    }

    @Test
    void ownerCreatesEmailInviteAndTriggersMail() {
        User owner = newUser("email-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이메일초대 워크스페이스");
        doNothing().when(workspaceInviteMailService).sendInvite(anyString(), any(), anyString());

        WorkspaceInviteResponse invite = workspaceInviteService.createInvite(owner.getId(), workspace.id(), "target@test.com");

        assertThat(invite.email()).isEqualTo("target@test.com");
        verify(workspaceInviteMailService).sendInvite(anyString(), any(), anyString());
    }

    @Test
    void nonOwnerCannotCreateInvite() {
        User owner = newUser("noninvite-owner");
        User member = newUser("noninvite-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "워크스페이스");
        joinAsMember(workspace.id(), member);

        assertThatThrownBy(() -> workspaceInviteService.createInvite(member.getId(), workspace.id(), null))
                .isInstanceOf(NotWorkspaceOwnerException.class);
    }

    @Test
    void acceptWithLinkInviteAddsMember() {
        User owner = newUser("accept-owner");
        User joiner = newUser("accept-joiner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "참여할 워크스페이스");
        WorkspaceInviteResponse invite = workspaceInviteService.createInvite(owner.getId(), workspace.id(), null);

        WorkspaceResponse result = workspaceInviteService.accept(joiner.getId(), invite.token());

        assertThat(result.id()).isEqualTo(workspace.id());
        assertThat(workspaceService.getMembership(workspace.id(), joiner.getId()).getRole())
                .isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    void sameLinkInviteCanBeUsedByMultiplePeople() {
        User owner = newUser("multi-owner");
        User joinerA = newUser("multi-joinerA");
        User joinerB = newUser("multi-joinerB");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "여러명 참여 워크스페이스");
        WorkspaceInviteResponse invite = workspaceInviteService.createInvite(owner.getId(), workspace.id(), null);

        workspaceInviteService.accept(joinerA.getId(), invite.token());
        workspaceInviteService.accept(joinerB.getId(), invite.token());

        assertThat(workspaceService.getMembership(workspace.id(), joinerA.getId())).isNotNull();
        assertThat(workspaceService.getMembership(workspace.id(), joinerB.getId())).isNotNull();
    }

    @Test
    void acceptFailsForUnknownToken() {
        User joiner = newUser("unknown-token-joiner");

        assertThatThrownBy(() -> workspaceInviteService.accept(joiner.getId(), "no-such-token"))
                .isInstanceOf(InvalidInviteTokenException.class);
    }

    @Test
    void acceptFailsForRevokedInvite() {
        User owner = newUser("revoke-owner");
        User joiner = newUser("revoke-joiner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "무효화 워크스페이스");
        WorkspaceInviteResponse invite = workspaceInviteService.createInvite(owner.getId(), workspace.id(), null);

        workspaceInviteService.revokeInvite(owner.getId(), workspace.id(), invite.id());

        assertThatThrownBy(() -> workspaceInviteService.accept(joiner.getId(), invite.token()))
                .isInstanceOf(InviteRevokedException.class);
    }

    @Test
    void revokeInviteFailsForInviteFromDifferentWorkspace() {
        User ownerA = newUser("cross-ownerA");
        User ownerB = newUser("cross-ownerB");
        WorkspaceResponse workspaceA = workspaceService.create(ownerA.getId(), "워크스페이스A");
        WorkspaceResponse workspaceB = workspaceService.create(ownerB.getId(), "워크스페이스B");
        WorkspaceInviteResponse inviteB = workspaceInviteService.createInvite(ownerB.getId(), workspaceB.id(), null);

        assertThatThrownBy(() -> workspaceInviteService.revokeInvite(ownerA.getId(), workspaceA.id(), inviteB.id()))
                .isInstanceOf(InvalidInviteTokenException.class);
    }

    @Test
    void acceptFailsWhenWorkspaceWasDeletedAfterInviteCreated() {
        User owner = newUser("del-accept-owner");
        User joiner = newUser("del-accept-joiner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제될 초대 워크스페이스");
        WorkspaceInviteResponse invite = workspaceInviteService.createInvite(owner.getId(), workspace.id(), null);

        workspaceService.delete(owner.getId(), workspace.id());

        assertThatThrownBy(() -> workspaceInviteService.accept(joiner.getId(), invite.token()))
                .isInstanceOf(com.motivhub.be.workspace.exception.WorkspaceNotFoundException.class);
    }
}
