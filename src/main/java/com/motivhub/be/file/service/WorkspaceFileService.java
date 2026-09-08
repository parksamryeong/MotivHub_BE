package com.motivhub.be.file.service;

import com.motivhub.be.file.domain.WorkspaceFile;
import com.motivhub.be.file.dto.FileDownloadResponse;
import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.dto.WorkspaceFileResponse;
import com.motivhub.be.file.exception.BlockedFileExtensionException;
import com.motivhub.be.file.exception.FileTooLargeException;
import com.motivhub.be.file.exception.FileUploadNotConfirmedException;
import com.motivhub.be.file.exception.WorkspaceFileForbiddenException;
import com.motivhub.be.file.exception.WorkspaceFileNotFoundException;
import com.motivhub.be.file.repository.WorkspaceFileRepository;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.service.WorkspaceService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Transactional(readOnly = true)
public class WorkspaceFileService {

    private static final long MAX_FILE_SIZE = 52_428_800L;
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".sh", ".msi", ".dll", ".scr", ".com", ".jar");

    private final WorkspaceService workspaceService;
    private final WorkspaceFileRepository workspaceFileRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public WorkspaceFileService(WorkspaceService workspaceService, WorkspaceFileRepository workspaceFileRepository,
                                 UserRepository userRepository, S3Client s3Client, S3Presigner s3Presigner) {
        this.workspaceService = workspaceService;
        this.workspaceFileRepository = workspaceFileRepository;
        this.userRepository = userRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @PostConstruct
    void validateBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("aws.s3.bucket 설정이 필요합니다(환경변수 AWS_S3_BUCKET을 설정하세요).");
        }
    }

    public FilePresignResponse presign(Long userId, Long workspaceId, String fileName, String contentType,
                                        long fileSize) {
        workspaceService.getMembership(workspaceId, userId);
        validateFile(fileName, fileSize);

        String fileKey = "workspaces/" + workspaceId + "/files/" + UUID.randomUUID() + "-" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new FilePresignResponse(presigned.url().toString(), fileKey);
    }

    @Transactional
    public WorkspaceFileResponse confirm(Long userId, Long workspaceId, String fileKey, String fileName,
                                          long fileSize, String contentType) {
        Workspace workspace = workspaceService.getMembership(workspaceId, userId).getWorkspace();
        String expectedPrefix = "workspaces/" + workspaceId + "/files/";
        if (!fileKey.startsWith(expectedPrefix)) {
            throw new FileUploadNotConfirmedException("이 워크스페이스에 속하지 않는 파일입니다.");
        }
        HeadObjectResponse headResponse;
        try {
            headResponse = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(fileKey).build());
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new FileUploadNotConfirmedException("업로드가 완료되지 않았습니다.");
            }
            throw e;
        }
        long actualFileSize = headResponse.contentLength();
        validateFile(fileName, actualFileSize);
        String actualContentType = headResponse.contentType() != null ? headResponse.contentType() : contentType;
        if (workspaceFileRepository.existsByFileKey(fileKey)) {
            throw new FileUploadNotConfirmedException("이미 등록된 파일입니다.");
        }
        User uploadedBy = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저를 찾을 수 없습니다."));
        WorkspaceFile file = workspaceFileRepository.save(
                WorkspaceFile.create(workspace, fileKey, fileName, actualFileSize, actualContentType, uploadedBy));
        return WorkspaceFileResponse.from(file);
    }

    public List<WorkspaceFileResponse> list(Long userId, Long workspaceId) {
        workspaceService.getMembership(workspaceId, userId);
        return workspaceFileRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(WorkspaceFileResponse::from)
                .toList();
    }

    private void validateFile(String fileName, long fileSize) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new FileTooLargeException("파일 크기는 50MB를 초과할 수 없습니다.");
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String extension = fileName.substring(dotIndex).toLowerCase();
            if (BLOCKED_EXTENSIONS.contains(extension)) {
                throw new BlockedFileExtensionException("허용되지 않는 파일 형식입니다: " + extension);
            }
        }
    }

    public FileDownloadResponse getDownloadUrl(Long userId, Long workspaceId, Long fileId) {
        workspaceService.getMembership(workspaceId, userId);
        WorkspaceFile file = findFile(workspaceId, fileId);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(file.getFileKey())
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);

        return new FileDownloadResponse(presigned.url().toString());
    }

    @Transactional
    public void delete(Long userId, Long workspaceId, Long fileId) {
        WorkspaceMember member = workspaceService.getMembership(workspaceId, userId);
        WorkspaceFile file = findFile(workspaceId, fileId);
        boolean allowed = member.isOwner() || file.isUploadedBy(userId);
        if (!allowed) {
            throw new WorkspaceFileForbiddenException("업로더 본인이거나 워크스페이스 OWNER만 삭제할 수 있습니다.");
        }
        String fileKey = file.getFileKey();
        workspaceFileRepository.delete(file);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileKey).build());
    }

    private WorkspaceFile findFile(Long workspaceId, Long fileId) {
        return workspaceFileRepository.findById(fileId)
                .filter(file -> file.getWorkspace().getId().equals(workspaceId))
                .orElseThrow(() -> new WorkspaceFileNotFoundException("파일을 찾을 수 없습니다."));
    }
}
