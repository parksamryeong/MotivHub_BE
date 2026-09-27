package com.motivhub.be.global.exception;

import com.motivhub.be.auth.exception.EmailAlreadyRegisteredException;
import com.motivhub.be.auth.exception.InvalidCodeException;
import com.motivhub.be.auth.exception.InvalidLoginException;
import com.motivhub.be.auth.exception.InvalidRefreshTokenException;
import com.motivhub.be.auth.exception.InvalidVerificationTokenException;
import com.motivhub.be.auth.exception.LogoutForbiddenException;
import com.motivhub.be.auth.exception.TooManyVerificationAttemptsException;
import com.motivhub.be.auth.exception.TooManyVerificationRequestsException;
import com.motivhub.be.auth.exception.VerificationCodeMismatchException;
import com.motivhub.be.auth.exception.VerificationTokenExpiredException;
import com.motivhub.be.file.exception.BlockedFileExtensionException;
import com.motivhub.be.file.exception.FileTooLargeException;
import com.motivhub.be.file.exception.FileUploadNotConfirmedException;
import com.motivhub.be.file.exception.WorkspaceFileForbiddenException;
import com.motivhub.be.file.exception.WorkspaceFileNotFoundException;
import com.motivhub.be.issue.exception.IssueCommentForbiddenException;
import com.motivhub.be.issue.exception.IssueCommentNotFoundException;
import com.motivhub.be.issue.exception.IssueForbiddenException;
import com.motivhub.be.issue.exception.IssueNotFoundException;
import com.motivhub.be.notification.exception.NotificationNotFoundException;
import com.motivhub.be.task.exception.InvalidTaskStatusTransitionException;
import com.motivhub.be.task.exception.TaskChecklistItemNotFoundException;
import com.motivhub.be.task.exception.TaskCommentForbiddenException;
import com.motivhub.be.task.exception.TaskCommentNotFoundException;
import com.motivhub.be.task.exception.TaskEditForbiddenException;
import com.motivhub.be.task.exception.TaskNotFoundException;
import com.motivhub.be.task.exception.TaskPeriodEditForbiddenException;
import com.motivhub.be.user.exception.InvalidNicknameException;
import com.motivhub.be.user.exception.NicknameDuplicateException;
import com.motivhub.be.user.exception.UserNotFoundException;
import com.motivhub.be.workspace.exception.InvalidInviteTokenException;
import com.motivhub.be.workspace.exception.InviteExpiredException;
import com.motivhub.be.workspace.exception.InviteRevokedException;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.exception.NotWorkspaceOwnerException;
import com.motivhub.be.workspace.exception.WorkspaceLeaveRequiresTransferException;
import com.motivhub.be.workspace.exception.WorkspaceMemberNotFoundException;
import com.motivhub.be.workspace.exception.WorkspaceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "요청 값이 올바르지 않습니다.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REQUEST", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DATA_CONFLICT", "요청이 다른 변경과 충돌했습니다. 다시 시도해주세요."));
    }

    @ExceptionHandler(InvalidCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCode(InvalidCodeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_CODE", e.getMessage()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_REFRESH_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(LogoutForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleLogoutForbidden(LogoutForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("LOGOUT_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWorkspaceNotFound(WorkspaceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("WORKSPACE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NotWorkspaceMemberException.class)
    public ResponseEntity<ErrorResponse> handleNotWorkspaceMember(NotWorkspaceMemberException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_WORKSPACE_MEMBER", e.getMessage()));
    }

    @ExceptionHandler(NotWorkspaceOwnerException.class)
    public ResponseEntity<ErrorResponse> handleNotWorkspaceOwner(NotWorkspaceOwnerException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("NOT_WORKSPACE_OWNER", e.getMessage()));
    }

    @ExceptionHandler(WorkspaceLeaveRequiresTransferException.class)
    public ResponseEntity<ErrorResponse> handleWorkspaceLeaveRequiresTransfer(WorkspaceLeaveRequiresTransferException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("WORKSPACE_LEAVE_REQUIRES_TRANSFER", e.getMessage()));
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWorkspaceMemberNotFound(WorkspaceMemberNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("WORKSPACE_MEMBER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidInviteTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInviteToken(InvalidInviteTokenException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("INVALID_INVITE_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(InviteExpiredException.class)
    public ResponseEntity<ErrorResponse> handleInviteExpired(InviteExpiredException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVITE_EXPIRED", e.getMessage()));
    }

    @ExceptionHandler(InviteRevokedException.class)
    public ResponseEntity<ErrorResponse> handleInviteRevoked(InviteRevokedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVITE_REVOKED", e.getMessage()));
    }

    @ExceptionHandler(NicknameDuplicateException.class)
    public ResponseEntity<ErrorResponse> handleNicknameDuplicate(NicknameDuplicateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("NICKNAME_DUPLICATE", e.getMessage()));
    }

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ErrorResponse> handleInvalidNickname(InvalidNicknameException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_NICKNAME", e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("USER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskNotFound(TaskNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TASK_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(TaskEditForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleTaskEditForbidden(TaskEditForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("TASK_EDIT_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(TaskPeriodEditForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleTaskPeriodEditForbidden(TaskPeriodEditForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("TASK_PERIOD_EDIT_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(InvalidTaskStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTaskStatusTransition(InvalidTaskStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_TASK_STATUS_TRANSITION", e.getMessage()));
    }

    @ExceptionHandler(TaskChecklistItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskChecklistItemNotFound(TaskChecklistItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TASK_CHECKLIST_ITEM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(TaskCommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaskCommentNotFound(TaskCommentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("TASK_COMMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(TaskCommentForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleTaskCommentForbidden(TaskCommentForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("TASK_COMMENT_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleFileTooLarge(FileTooLargeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("FILE_TOO_LARGE", e.getMessage()));
    }

    @ExceptionHandler(BlockedFileExtensionException.class)
    public ResponseEntity<ErrorResponse> handleBlockedFileExtension(BlockedFileExtensionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BLOCKED_FILE_EXTENSION", e.getMessage()));
    }

    @ExceptionHandler(FileUploadNotConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleFileUploadNotConfirmed(FileUploadNotConfirmedException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("FILE_UPLOAD_NOT_CONFIRMED", e.getMessage()));
    }

    @ExceptionHandler(WorkspaceFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWorkspaceFileNotFound(WorkspaceFileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("WORKSPACE_FILE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(WorkspaceFileForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleWorkspaceFileForbidden(WorkspaceFileForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("WORKSPACE_FILE_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(IssueForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleIssueForbidden(IssueForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ISSUE_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(IssueNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleIssueNotFound(IssueNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ISSUE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IssueCommentForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleIssueCommentForbidden(IssueCommentForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ISSUE_COMMENT_FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(IssueCommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleIssueCommentNotFound(IssueCommentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ISSUE_COMMENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOTIFICATION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("EMAIL_ALREADY_REGISTERED", e.getMessage()));
    }

    @ExceptionHandler(InvalidVerificationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVerificationToken(InvalidVerificationTokenException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("INVALID_VERIFICATION_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(VerificationTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleVerificationTokenExpired(VerificationTokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VERIFICATION_TOKEN_EXPIRED", e.getMessage()));
    }

    @ExceptionHandler(VerificationCodeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleVerificationCodeMismatch(VerificationCodeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VERIFICATION_CODE_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(TooManyVerificationAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyVerificationAttempts(TooManyVerificationAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("TOO_MANY_VERIFICATION_ATTEMPTS", e.getMessage()));
    }

    @ExceptionHandler(TooManyVerificationRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyVerificationRequests(TooManyVerificationRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of("TOO_MANY_VERIFICATION_REQUESTS", e.getMessage()));
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLogin(InvalidLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("INVALID_LOGIN", e.getMessage()));
    }

    // 메일 발송 실패(자격증명 오류, SMTP 장애 등)를 처리기 없이 그대로 흘려보내면, 인증이
    // 필요 없는 엔드포인트(회원가입 인증코드 발송 등)에서도 예외가 처리 안 된 채 컨테이너의
    // 기본 /error 디스패치로 넘어가는데, /error는 SecurityConfig의 공개 경로 목록에 없어서
    // 원래 500이어야 할 응답이 인증 필요(401)로 잘못 보이는 현상이 생긴다 - 여기서 직접 잡아서
    // 명확한 상태 코드로 응답하면 그 디스패치 자체가 필요 없어진다.
    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErrorResponse> handleMailException(MailException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("MAIL_SEND_FAILED", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요."));
    }
}
