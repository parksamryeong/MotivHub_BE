package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskPresenceMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.dto.UserSummary;
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
import java.util.concurrent.CompletableFuture;
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
class TaskPresenceEventListenerTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JwtProvider jwtProvider;

    private User newUser(String suffix) {
        User user = userRepository.save(User.create(
                SocialProvider.GITHUB, "presence-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        return user;
    }

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
    }

    private StompSession connectAsUser(User user) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtProvider.generateAccessToken(user.getId()));

        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<TaskPresenceMessage> subscribeToPresence(StompSession session, Long taskId) {
        BlockingQueue<TaskPresenceMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/tasks/" + taskId + "/presence", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskPresenceMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TaskPresenceMessage) payload);
            }
        });
        return messages;
    }

    @Test
    void singleViewerSubscribingReceivesSelfInViewerList() throws Exception {
        User owner = newUser("presence-single");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 단일 뷰어 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 단일 뷰어 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        // STOMP 구독 처리는 별도 스레드(clientInboundChannel)에서 이루어지므로, 테스트 트랜잭션 안에서만
        // 존재하는(아직 커밋되지 않은) 워크스페이스/태스크는 그 스레드에서 보이지 않는다. 커밋해서 넘겨준다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskPresenceMessage> messages = subscribeToPresence(session, task.id());

        TaskPresenceMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.taskId()).isEqualTo(task.id());
        assertThat(received.viewers()).extracting(UserSummary::id).containsExactly(owner.getId());

        session.disconnect();
    }

    @Test
    void secondViewerJoiningUpdatesViewerListToBoth() throws Exception {
        User owner = newUser("presence-second-owner");
        User teammate = newUser("presence-second-teammate");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 두 뷰어 워크스페이스");
        joinAsMember(workspace.id(), teammate);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 두 뷰어 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        // STOMP 구독 처리는 별도 스레드(clientInboundChannel)에서 이루어지므로, 테스트 트랜잭션 안에서만
        // 존재하는(아직 커밋되지 않은) 워크스페이스/태스크는 그 스레드에서 보이지 않는다. 커밋해서 넘겨준다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession ownerSession = connectAsUser(owner);
        BlockingQueue<TaskPresenceMessage> ownerMessages = subscribeToPresence(ownerSession, task.id());
        assertThat(ownerMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // 본인 입장으로 온 첫 메시지 소비

        StompSession teammateSession = connectAsUser(teammate);
        BlockingQueue<TaskPresenceMessage> teammateMessages = subscribeToPresence(teammateSession, task.id());

        TaskPresenceMessage teammateReceived = teammateMessages.poll(5, TimeUnit.SECONDS);
        assertThat(teammateReceived).isNotNull();
        assertThat(teammateReceived.viewers()).extracting(UserSummary::id).containsExactlyInAnyOrder(owner.getId(), teammate.getId());

        TaskPresenceMessage ownerReceivedUpdate = ownerMessages.poll(5, TimeUnit.SECONDS);
        assertThat(ownerReceivedUpdate).isNotNull();
        assertThat(ownerReceivedUpdate.viewers()).extracting(UserSummary::id).containsExactlyInAnyOrder(owner.getId(), teammate.getId());

        ownerSession.disconnect();
        teammateSession.disconnect();
    }

    @Test
    void disconnectingViewerIsRemovedFromRemainingViewersList() throws Exception {
        User owner = newUser("presence-disc-owner");
        User teammate = newUser("presence-disc-teammate");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 연결끊김 워크스페이스");
        joinAsMember(workspace.id(), teammate);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 연결끊김 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        // STOMP 구독 처리는 별도 스레드(clientInboundChannel)에서 이루어지므로, 테스트 트랜잭션 안에서만
        // 존재하는(아직 커밋되지 않은) 워크스페이스/태스크는 그 스레드에서 보이지 않는다. 커밋해서 넘겨준다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession ownerSession = connectAsUser(owner);
        BlockingQueue<TaskPresenceMessage> ownerMessages = subscribeToPresence(ownerSession, task.id());
        assertThat(ownerMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // 본인 입장으로 [owner] 수신, 소비

        StompSession teammateSession = connectAsUser(teammate);
        BlockingQueue<TaskPresenceMessage> teammateMessages = subscribeToPresence(teammateSession, task.id());
        assertThat(teammateMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // 자기 입장으로 [owner, teammate] 수신, 소비
        assertThat(ownerMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // teammate 입장 갱신을 owner도 수신, 소비

        teammateSession.disconnect();

        TaskPresenceMessage afterDisconnect = ownerMessages.poll(5, TimeUnit.SECONDS);
        assertThat(afterDisconnect).isNotNull();
        assertThat(afterDisconnect.viewers()).extracting(UserSummary::id).containsExactly(owner.getId());

        ownerSession.disconnect();
    }

    @Test
    void disconnectingFirstSubscribedViewerIsRemovedFromRemainingViewersList() throws Exception {
        User owner = newUser("presence-1st-disc-owner");
        User teammate = newUser("presence-1st-disc-mate");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 첫 구독자 연결끊김 워크스페이스");
        joinAsMember(workspace.id(), teammate);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 첫 구독자 연결끊김 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        // STOMP 구독 처리는 별도 스레드(clientInboundChannel)에서 이루어지므로, 테스트 트랜잭션 안에서만
        // 존재하는(아직 커밋되지 않은) 워크스페이스/태스크는 그 스레드에서 보이지 않는다. 커밋해서 넘겨준다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession ownerSession = connectAsUser(owner);
        BlockingQueue<TaskPresenceMessage> ownerMessages = subscribeToPresence(ownerSession, task.id());
        assertThat(ownerMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // 본인 입장으로 [owner] 수신, 소비

        StompSession teammateSession = connectAsUser(teammate);
        BlockingQueue<TaskPresenceMessage> teammateMessages = subscribeToPresence(teammateSession, task.id());
        assertThat(teammateMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // 자기 입장으로 [owner, teammate] 수신, 소비
        assertThat(ownerMessages.poll(5, TimeUnit.SECONDS)).isNotNull(); // teammate 입장 갱신을 owner도 수신, 소비

        // 두 세션 모두 각자의 커넥션에서 첫 구독이므로, 실제 STOMP 클라이언트(Spring의
        // DefaultStompSession)는 흔히 둘 다 구독 id "0"을 사용한다. "나중에" 구독한 세션이 아니라
        // "먼저" 구독한 세션(owner)을 끊어서, subscriptionId만으로 항목을 구분하던 예전 구현에서
        // owner의 로컬/Redis 항목이 teammate의 구독으로 덮어써지는 버그를 드러낸다.
        ownerSession.disconnect();

        TaskPresenceMessage afterDisconnect = teammateMessages.poll(5, TimeUnit.SECONDS);
        assertThat(afterDisconnect).isNotNull();
        assertThat(afterDisconnect.viewers()).extracting(UserSummary::id).containsExactly(teammate.getId());

        teammateSession.disconnect();
    }

    @Test
    void sameUserWithTwoSessionsAppearsOnceInViewerList() throws Exception {
        User owner = newUser("presence-multitab-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 멀티탭 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 멀티탭 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        // STOMP 구독 처리는 별도 스레드(clientInboundChannel)에서 이루어지므로, 테스트 트랜잭션 안에서만
        // 존재하는(아직 커밋되지 않은) 워크스페이스/태스크는 그 스레드에서 보이지 않는다. 커밋해서 넘겨준다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession firstTab = connectAsUser(owner);
        BlockingQueue<TaskPresenceMessage> firstTabMessages = subscribeToPresence(firstTab, task.id());
        assertThat(firstTabMessages.poll(5, TimeUnit.SECONDS)).isNotNull();

        StompSession secondTab = connectAsUser(owner);
        BlockingQueue<TaskPresenceMessage> secondTabMessages = subscribeToPresence(secondTab, task.id());

        TaskPresenceMessage received = secondTabMessages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.viewers()).extracting(UserSummary::id).containsExactly(owner.getId());

        firstTab.disconnect();
        secondTab.disconnect();
    }

    @Test
    void nonMemberCannotSubscribeToPresenceTopicOverRealStompConnection() throws Exception {
        User owner = newUser("presence-outsider-owner");
        User outsider = newUser("presence-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "프레즌스 비멤버 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("프레즌스 비멤버 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        // STOMP 구독 처리는 별도 스레드(clientInboundChannel)에서 이루어지므로, 테스트 트랜잭션 안에서만
        // 존재하는(아직 커밋되지 않은) 워크스페이스/태스크는 그 스레드에서 보이지 않는다. 커밋해서 넘겨준다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtProvider.generateAccessToken(outsider.getId()));

        CompletableFuture<Boolean> subscriptionRejected = new CompletableFuture<>();

        StompSession session = stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                subscriptionRejected.complete(true);
                            }

                            @Override
                            public void handleTransportError(StompSession session, Throwable exception) {
                                subscriptionRejected.complete(true);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        subscribeToPresence(session, task.id());

        assertThat(subscriptionRejected.get(5, TimeUnit.SECONDS)).isTrue();

        try {
            session.disconnect();
        } catch (Exception ignored) {
            // 서버가 이미 연결을 종료했을 수 있음 — 정리 목적의 호출이므로 예외는 무시
        }
    }
}
