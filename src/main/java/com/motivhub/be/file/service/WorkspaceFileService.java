package com.motivhub.be.file.service;

import com.motivhub.be.file.dto.FilePresignResponse;
import com.motivhub.be.file.exception.BlockedFileExtensionException;
import com.motivhub.be.file.exception.FileTooLargeException;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Transactional(readOnly = true)
public class WorkspaceFileService {

    private static final long MAX_FILE_SIZE = 52_428_800L;
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".sh", ".msi", ".dll", ".scr", ".com", ".jar");

    private final WorkspaceService workspaceService;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public WorkspaceFileService(WorkspaceService workspaceService, S3Presigner s3Presigner) {
        this.workspaceService = workspaceService;
        this.s3Presigner = s3Presigner;
    }

    public FilePresignResponse presign(Long userId, Long workspaceId, String fileName, String contentType,
                                        long fileSize) {
        workspaceService.getMembership(workspaceId, userId);
        validateFile(fileName, fileSize);

        String fileKey = "workspaces/" + workspaceId + "/files/" + UUID.randomUUID() + "-" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        return new FilePresignResponse(presigned.url().toString(), fileKey);
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
}
