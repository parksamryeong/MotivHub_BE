package com.motivhub.be.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.dto.WorkspaceFileResponse;
import com.motivhub.be.file.exception.BlockedFileExtensionException;
import com.motivhub.be.file.exception.FileTooLargeException;
import com.motivhub.be.file.exception.FileUploadNotConfirmedException;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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

    @Test
    void confirmingAfterRealUploadSavesMetadata() throws Exception {
        User owner = newUser("confirm-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 확정 워크스페이스1");
        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "report.pdf", "application/pdf", 13L);
        uploadToPresignedUrl(presign.uploadUrl(), "hello world!!!");

        WorkspaceFileResponse confirmed = workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "report.pdf", 13L, "application/pdf");

        assertThat(confirmed.fileName()).isEqualTo("report.pdf");
        assertThat(confirmed.fileSize()).isEqualTo(13L);
        assertThat(confirmed.uploadedBy().id()).isEqualTo(owner.getId());
    }

    @Test
    void confirmingWithoutActualUploadThrows() {
        User owner = newUser("confirm-owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 확정 워크스페이스2");
        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "ghost.pdf", "application/pdf", 100L);

        assertThatThrownBy(() -> workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "ghost.pdf", 100L, "application/pdf"))
                .isInstanceOf(FileUploadNotConfirmedException.class);
    }

    @Test
    void confirmingWithFileKeyFromAnotherWorkspaceThrows() throws Exception {
        User owner = newUser("confirm-owner3");
        WorkspaceResponse workspaceA = workspaceService.create(owner.getId(), "파일함 확정 워크스페이스A");
        WorkspaceResponse workspaceB = workspaceService.create(owner.getId(), "파일함 확정 워크스페이스B");
        FilePresignResponse presignForA = workspaceFileService.presign(
                owner.getId(), workspaceA.id(), "shared.pdf", "application/pdf", 5L);
        uploadToPresignedUrl(presignForA.uploadUrl(), "hello");

        assertThatThrownBy(() -> workspaceFileService.confirm(
                owner.getId(), workspaceB.id(), presignForA.fileKey(), "shared.pdf", 5L, "application/pdf"))
                .isInstanceOf(FileUploadNotConfirmedException.class);
    }

    @Test
    void listReturnsFilesNewestFirst() throws Exception {
        User owner = newUser("list-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 목록 워크스페이스1");
        FilePresignResponse first = workspaceFileService.presign(
                owner.getId(), workspace.id(), "first.txt", "text/plain", 1L);
        uploadToPresignedUrl(first.uploadUrl(), "a");
        workspaceFileService.confirm(owner.getId(), workspace.id(), first.fileKey(), "first.txt", 1L, "text/plain");
        FilePresignResponse second = workspaceFileService.presign(
                owner.getId(), workspace.id(), "second.txt", "text/plain", 1L);
        uploadToPresignedUrl(second.uploadUrl(), "b");
        workspaceFileService.confirm(owner.getId(), workspace.id(), second.fileKey(), "second.txt", 1L, "text/plain");

        List<WorkspaceFileResponse> files = workspaceFileService.list(owner.getId(), workspace.id());

        assertThat(files).extracting(WorkspaceFileResponse::fileName).containsExactly("second.txt", "first.txt");
    }

    @Test
    void nonMemberCannotListFiles() {
        User owner = newUser("list-owner2");
        User outsider = newUser("list-outsider2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 목록 워크스페이스2");

        assertThatThrownBy(() -> workspaceFileService.list(outsider.getId(), workspace.id()))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    private void uploadToPresignedUrl(String uploadUrl, String content) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .PUT(HttpRequest.BodyPublishers.ofString(content))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
