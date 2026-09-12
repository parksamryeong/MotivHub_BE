package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskBoardChangeMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkspaceMemberKickedSessionCleanerTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JwtProvider jwtProvider;

    private User newUser(String suffix) {
        User user = userRepository.save(User.create(
                SocialProvider.GITHUB, "kick-cleanup-" + suffix, suffix + "@test.com", "user_" + suffix, null));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        return user;
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    // TaskBoardChangeMessage는 LocalDate/LocalDateTime을 담은 TaskResponse를 포함하므로, 클라이언트
    // 쪽 STOMP 메시지 컨버터에도 JavaTimeModule을 등록해야 역직렬화가 가능하다(기본 ObjectMapper는
    // 지원하지 않음).
    private static MappingJackson2MessageConverter jacksonMessageConverterWithJavaTimeSupport() {
        return new MappingJackson2MessageConverter(new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private StompSession connectAsUser(User user) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(jacksonMessageConverterWithJavaTimeSupport());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtProvider.generateAccessToken(user.getId()));

        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<TaskBoardChangeMessage> subscribeToBoard(StompSession session, Long workspaceId) {
        BlockingQueue<TaskBoardChangeMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/workspaces/" + workspaceId + "/tasks", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskBoardChangeMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TaskBoardChangeMessage) payload);
            }
        });
        return messages;
    }

    @Test
    void kickedMemberStopsReceivingBoardBroadcastsWhileOtherMembersStillDo() throws Exception {
        User owner = newUser("owner");
        User kicked = newUser("kicked");
        User staying = newUser("staying");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "추방 구독해제 워크스페이스");
        joinAsMember(workspace.id(), kicked);
        joinAsMember(workspace.id(), staying);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession kickedSession = connectAsUser(kicked);
        BlockingQueue<TaskBoardChangeMessage> kickedMessages = subscribeToBoard(kickedSession, workspace.id());
        StompSession stayingSession = connectAsUser(staying);
        BlockingQueue<TaskBoardChangeMessage> stayingMessages = subscribeToBoard(stayingSession, workspace.id());
        Thread.sleep(500); // 구독 프레임이 서버에 실제로 등록될 시간 확보(구독은 비동기)

        TestTransaction.flagForCommit();
        workspaceService.kick(owner.getId(), workspace.id(), kicked.getId());
        TestTransaction.end();
        TestTransaction.start();
        Thread.sleep(500); // 구독 해제 메시지가 브로커에 실제로 반영될 시간 확보

        TestTransaction.flagForCommit();
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("추방 후 생성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage stayingReceived = stayingMessages.poll(5, TimeUnit.SECONDS);
        assertThat(stayingReceived).isNotNull();

        TaskBoardChangeMessage kickedReceived = kickedMessages.poll(2, TimeUnit.SECONDS);
        assertThat(kickedReceived).isNull();

        kickedSession.disconnect();
        stayingSession.disconnect();
    }
}
