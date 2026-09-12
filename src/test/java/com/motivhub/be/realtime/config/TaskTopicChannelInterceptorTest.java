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

    @Autowired private TaskTopicChannelInterceptor interceptor;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;

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
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (userId != null) {
            accessor.setUser(new StompPrincipal(userId));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
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
    void sendCommandIsRejectedEvenForAuthenticatedMember() {
        User owner = newUser("send-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "SEND 거부 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("SEND 거부 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));

        Message<byte[]> message = sendMessage("/topic/tasks/" + task.id(), owner.getId());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(StompAuthenticationException.class);
    }
}
