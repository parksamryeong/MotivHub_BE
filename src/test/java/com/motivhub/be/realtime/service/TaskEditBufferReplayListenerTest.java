package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskEditReplayMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
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
class TaskEditBufferReplayListenerTest extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TaskEditBufferService bufferService;

    private User newUser(String label) {
        User user = createUniqueUser(label);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        return user;
    }

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

    @Test
    void lateJoinerSubscribingToUserQueueReceivesBufferedUpdates() throws Exception {
        User owner = newUser("replay-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "재생 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("재생 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        bufferService.appendUpdate(task.id(), TaskEditableField.DESCRIPTION, "buffered-1");
        bufferService.appendUpdate(task.id(), TaskEditableField.DESCRIPTION, "buffered-2");

        StompSession session = connectAsUser(owner);
        // 편집 브로드캐스트 토픽 구독도 실제 플로우처럼 같이 해준다 (재생 자체는 유저큐 구독으로 트리거됨)
        session.subscribe("/topic/tasks/" + task.id() + "/description/edits", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        BlockingQueue<TaskEditReplayMessage> replayMessages = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/tasks/" + task.id() + "/description/edits", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskEditReplayMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                replayMessages.add((TaskEditReplayMessage) payload);
            }
        });

        TaskEditReplayMessage replay = replayMessages.poll(5, TimeUnit.SECONDS);
        assertThat(replay).isNotNull();
        assertThat(replay.updates()).containsExactly("buffered-1", "buffered-2");

        session.disconnect();
    }

    @Test
    void subscribingToUserQueueWithEmptyBufferReceivesNoReplayMessage() throws Exception {
        User owner = newUser("replay-empty-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "재생 없음 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("재생 없음 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskEditReplayMessage> replayMessages = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/tasks/" + task.id() + "/note/edits", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskEditReplayMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                replayMessages.add((TaskEditReplayMessage) payload);
            }
        });

        assertThat(replayMessages.poll(2, TimeUnit.SECONDS)).isNull();

        session.disconnect();
    }
}
