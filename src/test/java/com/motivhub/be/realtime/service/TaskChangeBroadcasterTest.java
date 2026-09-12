package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskChangedMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.SocialProvider;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
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
class TaskChangeBroadcasterTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtProvider jwtProvider;

    private User newUser(String suffix) {
        User user = userRepository.save(User.create(
                SocialProvider.GITHUB, "broadcast-test-" + suffix, suffix + "@test.com", "user_" + suffix, null));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        return user;
    }

    @Test
    void memberReceivesBroadcastWhenTaskContentChanges() throws Exception {
        User owner = newUser("bcast-content");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "웹소켓 브로드캐스트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("웹소켓 브로드캐스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtProvider.generateAccessToken(owner.getId()));

        StompSession session = stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<TaskChangedMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/tasks/" + task.id(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskChangedMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TaskChangedMessage) payload);
            }
        });
        Thread.sleep(500); // 구독 프레임이 서버에 실제로 등록될 시간 확보(구독은 비동기)

        TestTransaction.flagForCommit();
        taskService.updateContent(owner.getId(), task.id(), "바뀐 이름", null);
        TestTransaction.end();
        TestTransaction.start();

        TaskChangedMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.taskId()).isEqualTo(task.id());

        session.disconnect();
    }

    @Test
    void noBroadcastWhenTransactionDoesNotCommit() throws Exception {
        User owner = newUser("bcast-rollback");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "롤백 브로드캐스트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("롤백 브로드캐스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtProvider.generateAccessToken(owner.getId()));

        StompSession session = stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<TaskChangedMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/tasks/" + task.id(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskChangedMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TaskChangedMessage) payload);
            }
        });
        Thread.sleep(500); // 구독 프레임이 서버에 실제로 등록될 시간 확보(구독은 비동기)

        // 의도적으로 flagForCommit()을 호출하지 않음 — 이 트랜잭션은 테스트 종료 시 롤백만 됨(AbstractIntegrationTest
        // 클래스 레벨 @Transactional 기본 동작), 즉 AFTER_COMMIT 리스너가 절대 실행되지 않는 상황을 재현
        taskService.updateContent(owner.getId(), task.id(), "커밋 안 된 이름", null);

        TaskChangedMessage received = messages.poll(2, TimeUnit.SECONDS);
        assertThat(received).isNull();

        session.disconnect();
    }

    @Test
    void memberReceivesBroadcastWhenTaskIsDeleted() throws Exception {
        User owner = newUser("bcast-delete");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "웹소켓 삭제 브로드캐스트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("웹소켓 삭제 브로드캐스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtProvider.generateAccessToken(owner.getId()));

        StompSession session = stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<TaskChangedMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/tasks/" + task.id(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskChangedMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TaskChangedMessage) payload);
            }
        });
        Thread.sleep(500); // 구독 프레임이 서버에 실제로 등록될 시간 확보(구독은 비동기)

        TestTransaction.flagForCommit();
        taskService.delete(owner.getId(), task.id());
        TestTransaction.end();
        TestTransaction.start();

        TaskChangedMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.taskId()).isEqualTo(task.id());

        session.disconnect();
    }

    @Test
    void nonMemberSubscribeIsRejectedOverRealStompConnection() throws Exception {
        User owner = newUser("bcast-outsider-owner");
        User outsider = newUser("bcast-outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "아웃사이더 구독 거부 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("아웃사이더 구독 거부 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
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
                                // 서버가 SUBSCRIBE를 거부하면 STOMP ERROR 프레임으로 이 콜백이 호출됨
                                subscriptionRejected.complete(true);
                            }

                            @Override
                            public void handleTransportError(StompSession session, Throwable exception) {
                                // ERROR 프레임 전송 후 연결이 종료되는 경우도 거부로 간주
                                subscriptionRejected.complete(true);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/tasks/" + task.id(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskChangedMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // 정상적으로 브로드캐스트를 수신했다면 구독이 거부되지 않은 것 — 실패해야 하는 경로
            }
        });

        assertThat(subscriptionRejected.get(5, TimeUnit.SECONDS)).isTrue();

        try {
            session.disconnect();
        } catch (Exception ignored) {
            // 서버가 이미 연결을 종료했을 수 있음 — 정리 목적의 호출이므로 예외는 무시
        }
    }
}
