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
        WorkspaceResponse otherWorkspace = workspaceService.create(owner.getId(), "추방과 무관한 워크스페이스");
        joinAsMember(workspace.id(), kicked);
        joinAsMember(workspace.id(), staying);
        joinAsMember(otherWorkspace.id(), kicked);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession kickedSession = connectAsUser(kicked);
        BlockingQueue<TaskBoardChangeMessage> kickedMessages = subscribeToBoard(kickedSession, workspace.id());
        BlockingQueue<TaskBoardChangeMessage> kickedOtherWorkspaceMessages =
                subscribeToBoard(kickedSession, otherWorkspace.id());
        StompSession stayingSession = connectAsUser(staying);
        BlockingQueue<TaskBoardChangeMessage> stayingMessages = subscribeToBoard(stayingSession, workspace.id());

        TestTransaction.flagForCommit();
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("추방 전 생성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.end();
        TestTransaction.start();

        // 추방 전: 두 구독 모두 살아있다는 것부터 확인 - 이후 "추방된 사람은 못 받는다" 검증이 의미
        // 있으려면(구독이 애초에 등록조차 안 된 채로 우연히 통과하는 게 아니라) 먼저 정상 수신을
        // 증명해야 한다.
        assertThat(stayingMessages.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(kickedMessages.poll(5, TimeUnit.SECONDS)).isNotNull();

        TestTransaction.flagForCommit();
        workspaceService.kick(owner.getId(), workspace.id(), kicked.getId());
        TestTransaction.end();
        TestTransaction.start();
        Thread.sleep(500); // 구독 해제 메시지가 브로커에 실제로 반영될 시간 확보

        TestTransaction.flagForCommit();
        taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("추방 후 생성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        taskService.create(owner.getId(), otherWorkspace.id(),
                new TaskCreateRequest("무관한 워크스페이스 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage stayingReceived = stayingMessages.poll(5, TimeUnit.SECONDS);
        assertThat(stayingReceived).isNotNull();

        TaskBoardChangeMessage kickedReceived = kickedMessages.poll(2, TimeUnit.SECONDS);
        assertThat(kickedReceived).isNull();

        // 추방은 딱 그 워크스페이스의 보드 구독만 해제해야 한다 - 같은 세션의 다른 워크스페이스 보드
        // 구독은 그대로 살아있어야 한다.
        TaskBoardChangeMessage kickedOtherWorkspaceReceived = kickedOtherWorkspaceMessages.poll(5, TimeUnit.SECONDS);
        assertThat(kickedOtherWorkspaceReceived).isNotNull();

        kickedSession.disconnect();
        stayingSession.disconnect();
    }
}
