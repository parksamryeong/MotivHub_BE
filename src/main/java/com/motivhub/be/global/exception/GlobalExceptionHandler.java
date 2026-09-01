package com.motivhub.be.global.exception;

import com.motivhub.be.auth.exception.InvalidCodeException;
import com.motivhub.be.auth.exception.InvalidRefreshTokenException;
import com.motivhub.be.auth.exception.LogoutForbiddenException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
