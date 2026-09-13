package com.motivhub.be.realtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.exception.StompAuthenticationException;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.exception.NotWorkspaceMemberException;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class TaskTopicChannelInterceptorTest extends AbstractIntegrationTest {

    private static final String DEFAULT_SESSION_ID = "test-session";

    @Autowired private TaskTopicChannelInterceptor interceptor;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;

    private User newUser(String suffix) {
        return userRepository.save(User.create(
                SocialProvider.GITHUB, "stomp-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
    }

    private Message<byte[]> connectMessage(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, Long userId) {
        return subscribeMessage(destination, userId, DEFAULT_SESSION_ID);
    }

    private Message<byte[]> subscribeMessage(String destination, Long userId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId(sessionId);
        if (userId != null) {
            accessor.setUser(new StompPrincipal(userId));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(String destination, Long userId) {
        return sendMessage(destination, userId, DEFAULT_SESSION_ID);
    }

    private Message<byte[]> sendMessage(String destination, Long userId, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setSessionId(sessionId);
        if (userId != null) {
            accessor.setUser(new StompPrincipal(userId));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithValidTokenSetsUserOnAccessor() {
        User user = newUser("connect-ok");
        String token = jwtProvider.generateAccessToken(user.getId());
        Message<byte[]> message = connectMessage("Bearer " + token);

        interceptor.preSend(message, null);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(message);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(String.valueOf(user.getId()));
    }

    @Test
    void connectWithMissingAuthorizationHeaderThrows() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void connectWithInvalidTokenThrows() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer garbage-token"), null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void memberCanSubscribeToTaskTopic() {
        User owner = newUser("sub-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "구독 테스트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("구독 테스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/" + task.id(), owner.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToTaskTopic() {
        User owner = newUser("sub-owner2");
        User outsider = newUser("sub-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "구독 권한 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("구독 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/" + task.id(), outsider.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void unauthenticatedSubscribeToTaskTopicThrows() {
        User owner = newUser("sub-owner3");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "미인증 구독 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("미인증 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/" + task.id(), null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void subscribeToWildcardTaskTopicThrowsEvenForLegitimateMember() {
        User owner = newUser("sub-wildcard-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "와일드카드 구독 워크스페이스");
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("와일드카드 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/*", owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void subscribeToDoubleWildcardTopicThrowsEvenForLegitimateMember() {
        User owner = newUser("sub-doublewildcard-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "이중 와일드카드 구독 워크스페이스");
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("이중 와일드카드 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/**", owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void subscribeToPatternSuffixedTaskTopicThrowsEvenForLegitimateMember() {
        User owner = newUser("sub-suffixwildcard-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "접미사 와일드카드 구독 워크스페이스");
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("접미사 와일드카드 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/1*", owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void subscribeToNonTaskTopicThrowsEvenForLegitimateMember() {
        User owner = newUser("sub-other-owner");
        workspaceService.create(owner.getId(), "다른 목적지 구독 워크스페이스");

        Message<byte[]> message = subscribeMessage("/topic/other", owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void memberCanSubscribeToTaskPresenceTopic() {
        User owner = newUser("presence-sub-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 구독 테스트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 구독 테스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/" + task.id() + "/presence", owner.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToTaskPresenceTopic() {
        User owner = newUser("presence-sub-owner2");
        User outsider = newUser("presence-sub-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 구독 권한 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 구독 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage("/topic/tasks/" + task.id() + "/presence", outsider.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void memberCanSubscribeToWorkspaceBoardTopic() {
        User owner = newUser("board-sub-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 구독 테스트 워크스페이스");

        Message<byte[]> message = subscribeMessage("/topic/workspaces/" + workspace.id() + "/tasks", owner.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToWorkspaceBoardTopic() {
        User owner = newUser("board-sub-owner2");
        User outsider = newUser("board-sub-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 구독 권한 워크스페이스");

        Message<byte[]> message = subscribeMessage("/topic/workspaces/" + workspace.id() + "/tasks", outsider.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void subscribeToWorkspaceBoardWildcardThrowsEvenForLegitimateMember() {
        User owner = newUser("board-sub-wildcard-owner");
        workspaceService.create(owner.getId(), "보드 와일드카드 구독 워크스페이스");

        Message<byte[]> message = subscribeMessage("/topic/workspaces/*/tasks", owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void memberCanSubscribeToTaskEditBroadcastTopic() {
        User owner = newUser("edit-sub-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "편집 구독 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("편집 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/description/edits", owner.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToTaskEditBroadcastTopic() {
        User owner = newUser("edit-sub-owner2");
        User outsider = newUser("edit-sub-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "편집 구독 권한 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("편집 구독 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/description/edits", outsider.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void memberCanSubscribeToTaskEditSaveRequestTopic() {
        User owner = newUser("save-req-sub-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "저장요청 구독 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("저장요청 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/note/save-request", owner.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToTaskEditSaveRequestTopic() {
        User owner = newUser("save-req-sub-owner2");
        User outsider = newUser("save-req-sub-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "저장요청 구독 권한 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("저장요청 구독 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/note/save-request", outsider.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void memberCanSubscribeToTaskEditUserQueue() {
        User owner = newUser("user-queue-sub-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "유저큐 구독 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("유저큐 구독 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/user/queue/tasks/" + task.id() + "/description/edits", owner.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void nonMemberCannotSubscribeToTaskEditUserQueue() {
        User owner = newUser("user-queue-sub-owner2");
        User outsider = newUser("user-queue-sub-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "유저큐 구독 권한 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("유저큐 구독 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/user/queue/tasks/" + task.id() + "/description/edits", outsider.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(NotWorkspaceMemberException.class);
    }

    @Test
    void sendCommandIsRejectedEvenForAuthenticatedMember() {
        User owner = newUser("send-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "SEND 거부 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("SEND 거부 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = sendMessage("/topic/tasks/" + task.id(), owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void sendToEditDestinationWithoutPriorSubscribeIsRejected() {
        User owner = newUser("edit-send-no-sub");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "미구독 SEND 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("미구독 SEND 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = sendMessage(
                "/app/tasks/" + task.id() + "/description/edits", owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void sendToEditDestinationAfterSubscribingIsAllowed() {
        User owner = newUser("edit-send-with-sub");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "구독후 SEND 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("구독후 SEND 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        String sessionId = "edit-send-session";
        interceptor.preSend(subscribeMessage(
                "/topic/tasks/" + task.id() + "/description/edits", owner.getId(), sessionId), null);

        Message<byte[]> message = sendMessage(
                "/app/tasks/" + task.id() + "/description/edits", owner.getId(), sessionId);

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void sendToSnapshotDestinationAfterSubscribingToEditsIsAllowed() {
        User owner = newUser("snapshot-send-with-sub");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "스냅샷 SEND 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("스냅샷 SEND 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        String sessionId = "snapshot-send-session";
        interceptor.preSend(subscribeMessage(
                "/topic/tasks/" + task.id() + "/note/edits", owner.getId(), sessionId), null);

        Message<byte[]> message = sendMessage(
                "/app/tasks/" + task.id() + "/note/snapshot", owner.getId(), sessionId);

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void sendToEditDestinationFromDifferentSessionIsRejected() {
        User owner = newUser("edit-send-other-session");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "다른세션 SEND 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("다른세션 SEND 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        interceptor.preSend(subscribeMessage(
                "/topic/tasks/" + task.id() + "/description/edits", owner.getId(), "session-A"), null);

        Message<byte[]> message = sendMessage(
                "/app/tasks/" + task.id() + "/description/edits", owner.getId(), "session-B");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    void memberWithoutAssigneeCannotSubscribeToDescriptionEditTopic() {
        User owner = newUser("desc-edit-perm-owner");
        User plainMember = newUser("desc-edit-perm-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "설명 편집 권한 워크스페이스");
        workspaceMemberRepository.save(com.motivhub.be.workspace.domain.WorkspaceMember.create(
                workspaceService.getWorkspace(workspace.id()), plainMember,
                com.motivhub.be.workspace.domain.WorkspaceRole.MEMBER));
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("설명 편집 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/description/edits", plainMember.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(com.motivhub.be.task.exception.TaskEditForbiddenException.class);
    }

    @Test
    void assigneeCanSubscribeToDescriptionEditTopicWithoutBeingOwner() {
        User owner = newUser("desc-edit-assignee-owner");
        User assignee = newUser("desc-edit-assignee-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "설명 편집 담당자 워크스페이스");
        workspaceMemberRepository.save(com.motivhub.be.workspace.domain.WorkspaceMember.create(
                workspaceService.getWorkspace(workspace.id()), assignee,
                com.motivhub.be.workspace.domain.WorkspaceRole.MEMBER));
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("설명 편집 담당자 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        taskService.addAssignee(owner.getId(), task.id(), assignee.getId());

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/description/edits", assignee.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    void plainMemberCanSubscribeToNoteEditTopicWithoutEditPermission() {
        User owner = newUser("note-edit-perm-owner");
        User plainMember = newUser("note-edit-perm-member");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 편집 권한 워크스페이스");
        workspaceMemberRepository.save(com.motivhub.be.workspace.domain.WorkspaceMember.create(
                workspaceService.getWorkspace(workspace.id()), plainMember,
                com.motivhub.be.workspace.domain.WorkspaceRole.MEMBER));
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 편집 권한 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = subscribeMessage(
                "/topic/tasks/" + task.id() + "/note/edits", plainMember.getId());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }
}
