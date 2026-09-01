package com.motivhub.be.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.WorkspaceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceServiceTest extends AbstractIntegrationTest {

    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "ws-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
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
}
