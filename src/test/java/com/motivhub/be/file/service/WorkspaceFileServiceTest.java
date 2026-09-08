package com.motivhub.be.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.exception.BlockedFileExtensionException;
import com.motivhub.be.file.exception.FileTooLargeException;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceFileServiceTest extends AbstractIntegrationTest {

    @Autowired private WorkspaceFileService workspaceFileService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "file-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    @Test
    void memberCanPresignUpload() {
        User owner = newUser("presign-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 워크스페이스1");

        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "report.pdf", "application/pdf", 1_000L);

        assertThat(presign.uploadUrl()).isNotBlank();
        assertThat(presign.fileKey()).startsWith("workspaces/" + workspace.id() + "/files/");
        assertThat(presign.fileKey()).endsWith("-report.pdf");
    }

    @Test
    void nonMemberCannotPresign() {
        User owner = newUser("presign-owner2");
        User outsider = newUser("presign-outsider2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 워크스페이스2");

        assertThatThrownBy(() -> workspaceFileService.presign(
                outsider.getId(), workspace.id(), "report.pdf", "application/pdf", 1_000L))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void fileLargerThan50MbIsRejected() {
        User owner = newUser("presign-owner3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 워크스페이스3");

        assertThatThrownBy(() -> workspaceFileService.presign(
                owner.getId(), workspace.id(), "big.zip", "application/zip", 52_428_801L))
                .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    void blockedExtensionIsRejected() {
        User owner = newUser("presign-owner4");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 워크스페이스4");

        assertThatThrownBy(() -> workspaceFileService.presign(
                owner.getId(), workspace.id(), "virus.EXE", "application/octet-stream", 1_000L))
                .isInstanceOf(BlockedFileExtensionException.class);
    }
}
