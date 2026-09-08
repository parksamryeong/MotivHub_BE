package com.motivhub.be.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.file.dto.FileDownloadResponse;
import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.dto.WorkspaceFileResponse;
import com.motivhub.be.file.exception.BlockedFileExtensionException;
import com.motivhub.be.file.exception.FileTooLargeException;
import com.motivhub.be.file.exception.FileUploadNotConfirmedException;
import com.motivhub.be.file.exception.WorkspaceFileForbiddenException;
import com.motivhub.be.file.exception.WorkspaceFileNotFoundException;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class WorkspaceFileServiceTest extends AbstractIntegrationTest {

    @Autowired private WorkspaceFileService workspaceFileService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "file-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    private WorkspaceFileResponse confirmUploadedFile(User uploader, Long workspaceId, String fileName)
            throws Exception {
        FilePresignResponse presign = workspaceFileService.presign(
                uploader.getId(), workspaceId, fileName, "text/plain", 5L);
        uploadToPresignedUrl(presign.uploadUrl(), "hello");
        return workspaceFileService.confirm(
                uploader.getId(), workspaceId, presign.fileKey(), fileName, 5L, "text/plain", null);
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
        uploadToPresignedUrl(presign.uploadUrl(), "hello world!!");

        WorkspaceFileResponse confirmed = workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "report.pdf", 13L, "application/pdf", null);

        assertThat(confirmed.fileName()).isEqualTo("report.pdf");
        assertThat(confirmed.fileSize()).isEqualTo(13L);
        assertThat(confirmed.uploadedBy().id()).isEqualTo(owner.getId());
    }

    @Test
    void confirmingWithCategorySavesAndReturnsIt() throws Exception {
        User owner = newUser("category-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "카테고리 워크스페이스1");
        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "invoice.pdf", "application/pdf", 5L);
        uploadToPresignedUrl(presign.uploadUrl(), "hello");

        WorkspaceFileResponse confirmed = workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "invoice.pdf", 5L, "application/pdf", "영수증");

        assertThat(confirmed.category()).isEqualTo("영수증");
    }

    @Test
    void confirmingWithoutCategorySavesNull() throws Exception {
        User owner = newUser("category-owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "카테고리 워크스페이스2");
        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "no-category.pdf", "application/pdf", 5L);
        uploadToPresignedUrl(presign.uploadUrl(), "hello");

        WorkspaceFileResponse confirmed = workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "no-category.pdf", 5L, "application/pdf", null);

        assertThat(confirmed.category()).isNull();
    }

    @Test
    void confirmingWithoutActualUploadThrows() {
        User owner = newUser("confirm-owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 확정 워크스페이스2");
        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "ghost.pdf", "application/pdf", 100L);

        assertThatThrownBy(() -> workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "ghost.pdf", 100L, "application/pdf", null))
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
                owner.getId(), workspaceB.id(), presignForA.fileKey(), "shared.pdf", 5L, "application/pdf", null))
                .isInstanceOf(FileUploadNotConfirmedException.class);
    }

    @Test
    void listReturnsFilesNewestFirst() throws Exception {
        User owner = newUser("list-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "파일함 목록 워크스페이스1");
        FilePresignResponse first = workspaceFileService.presign(
                owner.getId(), workspace.id(), "first.txt", "text/plain", 1L);
        uploadToPresignedUrl(first.uploadUrl(), "a");
        workspaceFileService.confirm(
                owner.getId(), workspace.id(), first.fileKey(), "first.txt", 1L, "text/plain", null);
        FilePresignResponse second = workspaceFileService.presign(
                owner.getId(), workspace.id(), "second.txt", "text/plain", 1L);
        uploadToPresignedUrl(second.uploadUrl(), "b");
        workspaceFileService.confirm(
                owner.getId(), workspace.id(), second.fileKey(), "second.txt", 1L, "text/plain", null);

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

    @Test
    void downloadingReturnsWorkingPresignedUrl() throws Exception {
        User owner = newUser("dl-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "다운로드 워크스페이스1");
        WorkspaceFileResponse file = confirmUploadedFile(owner, workspace.id(), "dl.txt");

        FileDownloadResponse download = workspaceFileService.getDownloadUrl(owner.getId(), workspace.id(), file.id());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest getRequest = HttpRequest.newBuilder().uri(URI.create(download.downloadUrl())).GET().build();
        HttpResponse<String> response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello");
    }

    @Test
    void downloadingUnknownFileThrows() {
        User owner = newUser("dl-owner2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "다운로드 워크스페이스2");

        assertThatThrownBy(() -> workspaceFileService.getDownloadUrl(owner.getId(), workspace.id(), 999_999L))
                .isInstanceOf(WorkspaceFileNotFoundException.class);
    }

    @Test
    void uploaderCanDeleteOwnFile() throws Exception {
        User owner = newUser("del-owner1");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스1");
        WorkspaceFileResponse file = confirmUploadedFile(owner, workspace.id(), "mine.txt");

        workspaceFileService.delete(owner.getId(), workspace.id(), file.id());

        assertThat(workspaceFileService.list(owner.getId(), workspace.id())).isEmpty();
    }

    @Test
    void ownerCanDeleteOthersFile() throws Exception {
        User owner = newUser("del-owner2");
        User uploader = newUser("del-uploader2");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스2");
        joinAsMember(workspace.id(), uploader);
        WorkspaceFileResponse file = confirmUploadedFile(uploader, workspace.id(), "theirs.txt");

        workspaceFileService.delete(owner.getId(), workspace.id(), file.id());

        assertThat(workspaceFileService.list(owner.getId(), workspace.id())).isEmpty();
    }

    @Test
    void nonUploaderNonOwnerCannotDelete() throws Exception {
        User owner = newUser("del-owner3");
        User uploader = newUser("del-uploader3");
        User bystander = newUser("del-bystander3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "삭제 워크스페이스3");
        joinAsMember(workspace.id(), uploader);
        joinAsMember(workspace.id(), bystander);
        WorkspaceFileResponse file = confirmUploadedFile(uploader, workspace.id(), "protected.txt");

        assertThatThrownBy(() -> workspaceFileService.delete(bystander.getId(), workspace.id(), file.id()))
                .isInstanceOf(WorkspaceFileForbiddenException.class);
    }

    @Test
    void deletingFromDifferentWorkspaceThrows() throws Exception {
        User owner = newUser("del-owner4");
        WorkspaceResponse workspaceA = workspaceService.create(owner.getId(), "삭제 워크스페이스A");
        WorkspaceResponse workspaceB = workspaceService.create(owner.getId(), "삭제 워크스페이스B");
        WorkspaceFileResponse file = confirmUploadedFile(owner, workspaceA.id(), "cross.txt");

        assertThatThrownBy(() -> workspaceFileService.delete(owner.getId(), workspaceB.id(), file.id()))
                .isInstanceOf(WorkspaceFileNotFoundException.class);
    }

    @Test
    void deletingFileActuallyRemovesS3Object() throws Exception {
        User owner = newUser("del-s3-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "S3 삭제 확인 워크스페이스");
        FilePresignResponse presign = workspaceFileService.presign(
                owner.getId(), workspace.id(), "gone.txt", "text/plain", 5L);
        uploadToPresignedUrl(presign.uploadUrl(), "hello");
        WorkspaceFileResponse file = workspaceFileService.confirm(
                owner.getId(), workspace.id(), presign.fileKey(), "gone.txt", 5L, "text/plain", null);

        workspaceFileService.delete(owner.getId(), workspace.id(), file.id());

        assertThatThrownBy(() -> s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(presign.fileKey()).build()))
                .isInstanceOf(S3Exception.class);
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
