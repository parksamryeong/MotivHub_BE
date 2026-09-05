package com.motivhub.be.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.dto.UserSummary;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceDetailResponse;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.exception.WorkspaceLeaveRequiresTransferException;
import com.motivhub.be.workspace.exception.WorkspaceMemberNotFoundException;
import com.motivhub.be.workspace.exception.WorkspaceNotFoundException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceServiceTest extends AbstractIntegrationTest {

    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "ws-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    @Test
    void creatorBecomesOwner() {
        User creator = newUser("creator1");

        WorkspaceResponse response = workspaceService.create(creator.getId(), "내 프로젝트");

        assertThat(response.name()).isEqualTo("내 프로젝트");
        assertThat(response.myRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void listMineReturnsOnlyMyWorkspaces() {
        User creator = newUser("creator2");
        User stranger = newUser("stranger2");
        workspaceService.create(creator.getId(), "팀 프로젝트");
        workspaceService.create(stranger.getId(), "남의 프로젝트");

        List<WorkspaceResponse> mine = workspaceService.listMine(creator.getId());

        assertThat(mine).extracting(WorkspaceResponse::name).containsExactly("팀 프로젝트");
    }

    @Test
    void getDetailFailsForNonMember() {
        User owner = newUser("owner3");
        User stranger = newUser("stranger3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "비공개 프로젝트");

        assertThatThrownBy(() -> workspaceService.getDetail(stranger.getId(), workspace.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void getWorkspaceFailsForUnknownId() {
        assertThatThrownBy(() -> workspaceService.getWorkspace(999_999L))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void soloOwnerCanDeleteFreely() {
        User owner = newUser("solo-del");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "혼자 프로젝트");

        workspaceService.delete(owner.getId(), workspace.id());

        assertThatThrownBy(() -> workspaceService.getWorkspace(workspace.id()))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void nonOwnerCannotDelete() {
        User owner = newUser("del-owner");
        User member = newUser("del-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트");
        joinAsMember(workspace.id(), member);

        assertThatThrownBy(() -> workspaceService.delete(member.getId(), workspace.id()))
                .isInstanceOf(NotWorkspaceOwnerException.class);
    }

    @Test
    void ownerCanDeleteEvenWithOtherMembers() {
        User owner = newUser("del-owner2");
        User member = newUser("del-member2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트2");
        joinAsMember(workspace.id(), member);

        workspaceService.delete(owner.getId(), workspace.id());

        assertThatThrownBy(() -> workspaceService.getWorkspace(workspace.id()))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void memberCanLeaveFreely() {
        User owner = newUser("leave-owner");
        User member = newUser("leave-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트3");
        joinAsMember(workspace.id(), member);

        workspaceService.leave(member.getId(), workspace.id());

        assertThatThrownBy(() -> workspaceService.getMembership(workspace.id(), member.getId()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void ownerCannotLeaveWithoutTransferWhenOthersExist() {
        User owner = newUser("leave-owner2");
        User member = newUser("leave-member2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트4");
        joinAsMember(workspace.id(), member);

        assertThatThrownBy(() -> workspaceService.leave(owner.getId(), workspace.id()))
                .isInstanceOf(WorkspaceLeaveRequiresTransferException.class);
    }

    @Test
    void soloOwnerLeavingDeletesWorkspace() {
        User owner = newUser("leave-solo");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "혼자 프로젝트2");

        workspaceService.leave(owner.getId(), workspace.id());

        assertThatThrownBy(() -> workspaceService.getWorkspace(workspace.id()))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void ownerCanKickMember() {
        User owner = newUser("kick-owner");
        User member = newUser("kick-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트5");
        joinAsMember(workspace.id(), member);

        workspaceService.kick(owner.getId(), workspace.id(), member.getId());

        assertThatThrownBy(() -> workspaceService.getMembership(workspace.id(), member.getId()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void nonOwnerCannotKick() {
        User owner = newUser("kick-owner2");
        User memberA = newUser("kick-memberA");
        User memberB = newUser("kick-memberB");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트6");
        joinAsMember(workspace.id(), memberA);
        joinAsMember(workspace.id(), memberB);

        assertThatThrownBy(() -> workspaceService.kick(memberA.getId(), workspace.id(), memberB.getId()))
                .isInstanceOf(NotWorkspaceOwnerException.class);
    }

    @Test
    void transferOwnershipMovesRoles() {
        User owner = newUser("transfer-owner");
        User member = newUser("transfer-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트7");
        joinAsMember(workspace.id(), member);

        workspaceService.transferOwnership(owner.getId(), workspace.id(), member.getId());

        assertThat(workspaceService.getMembership(workspace.id(), member.getId()).isOwner()).isTrue();
        assertThat(workspaceService.getMembership(workspace.id(), owner.getId()).isOwner()).isFalse();
    }

    @Test
    void transferOwnershipFailsForNonMemberTarget() {
        User owner = newUser("transfer-owner2");
        User outsider = newUser("transfer-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "팀 프로젝트8");

        assertThatThrownBy(() -> workspaceService.transferOwnership(owner.getId(), workspace.id(), outsider.getId()))
                .isInstanceOf(WorkspaceMemberNotFoundException.class);
    }

    @Test
    void deletedWorkspaceIsNotFoundViaMembershipEither() {
        User owner = newUser("del-membership-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제될 워크스페이스3");
        workspaceService.delete(owner.getId(), workspace.id());

        assertThatThrownBy(() -> workspaceService.getMembership(workspace.id(), owner.getId()))
                .isInstanceOf(WorkspaceNotFoundException.class);
        assertThatThrownBy(() -> workspaceService.getDetail(owner.getId(), workspace.id()))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    void deletedWorkspaceIsExcludedFromListMine() {
        User owner = newUser("del-listmine-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제될 워크스페이스4");

        workspaceService.delete(owner.getId(), workspace.id());

        assertThat(workspaceService.listMine(owner.getId())).isEmpty();
    }

    @Test
    void ownerCanRenameWorkspace() {
        User owner = newUser("rename-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "원래 이름");

        WorkspaceResponse renamed = workspaceService.updateName(owner.getId(), workspace.id(), "바뀐 이름");

        assertThat(renamed.name()).isEqualTo("바뀐 이름");
    }

    @Test
    void nonOwnerCannotRenameWorkspace() {
        User owner = newUser("rename-owner2");
        User member = newUser("rename-member2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이름 워크스페이스");
        joinAsMember(workspace.id(), member);

        assertThatThrownBy(() -> workspaceService.updateName(member.getId(), workspace.id(), "몰래 변경"))
                .isInstanceOf(NotWorkspaceOwnerException.class);
    }

    @Test
    void getDetailIncludesMemberNicknames() {
        User owner = newUser("detail-owner");
        User member = newUser("detail-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "멤버목록 워크스페이스");
        joinAsMember(workspace.id(), member);

        WorkspaceDetailResponse detail = workspaceService.getDetail(owner.getId(), workspace.id());

        assertThat(detail.members()).extracting(m -> m.user().nickname())
                .containsExactlyInAnyOrder(owner.getNickname(), member.getNickname());
    }
}
