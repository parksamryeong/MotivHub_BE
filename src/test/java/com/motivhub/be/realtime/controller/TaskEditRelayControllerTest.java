package com.motivhub.be.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskEditSnapshotMessage;
import com.motivhub.be.realtime.dto.TaskEditUpdateMessage;
import com.motivhub.be.realtime.service.TaskEditBufferService;
import com.motivhub.be.realtime.service.TaskEditableField;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskNoteResponse;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskNoteService;
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
class TaskEditRelayControllerTest extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private TaskService taskService;
    @Autowired private TaskNoteService taskNoteService;
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

    private BlockingQueue<TaskEditUpdateMessage> subscribeToEdits(StompSession session, Long taskId, String field) {
        BlockingQueue<TaskEditUpdateMessage> messages = new LinkedBlockingQueue<>();
        session.subscribe("/topic/tasks/" + taskId + "/" + field + "/edits", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskEditUpdateMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((TaskEditUpdateMessage) payload);
            }
        });
        return messages;
    }

    @Test
    void editUpdateSentByOneSessionIsRelayedToOtherSubscriber() throws Exception {
        User owner = newUser("relay-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "릴레이 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("릴레이 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession senderSession = connectAsUser(owner);
        senderSession.subscribe("/topic/tasks/" + task.id() + "/description/edits", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TaskEditUpdateMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        StompSession receiverSession = connectAsUser(owner);
        BlockingQueue<TaskEditUpdateMessage> received = subscribeToEdits(receiverSession, task.id(), "description");

        senderSession.send("/app/tasks/" + task.id() + "/description/edits",
                new TaskEditUpdateMessage("base64-update-1"));

        TaskEditUpdateMessage message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.update()).isEqualTo("base64-update-1");

        senderSession.disconnect();
        receiverSession.disconnect();
    }

    @Test
    void editUpdateIsAppendedToRedisBuffer() throws Exception {
        User owner = newUser("relay-buffer-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "릴레이 버퍼 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("릴레이 버퍼 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribeToEdits(session, task.id(), "note");

        session.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("buffered-update"));

        Thread.sleep(500);
        assertThat(bufferService.listUpdates(task.id(), TaskEditableField.NOTE)).contains("buffered-update");

        session.disconnect();
    }

    @Test
    void snapshotForDescriptionPersistsToTaskAndClearsBuffer() throws Exception {
        User owner = newUser("snapshot-desc-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "스냅샷 설명 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("스냅샷 설명 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribeToEdits(session, task.id(), "description");
        session.send("/app/tasks/" + task.id() + "/description/edits", new TaskEditUpdateMessage("u1"));
        Thread.sleep(300);

        session.send("/app/tasks/" + task.id() + "/description/snapshot",
                new TaskEditSnapshotMessage("실시간으로 합쳐진 최종 설명"));
        Thread.sleep(500);

        assertThat(taskService.getTask(task.id()).getDescription()).isEqualTo("실시간으로 합쳐진 최종 설명");
        assertThat(bufferService.isEmpty(task.id(), TaskEditableField.DESCRIPTION)).isTrue();

        session.disconnect();
    }

    @Test
    void snapshotForNotePersistsToTaskNote() throws Exception {
        User owner = newUser("snapshot-note-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "스냅샷 노트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("스냅샷 노트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribeToEdits(session, task.id(), "note");
        session.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("u1"));
        Thread.sleep(300);

        session.send("/app/tasks/" + task.id() + "/note/snapshot",
                new TaskEditSnapshotMessage("실시간으로 합쳐진 최종 노트"));
        Thread.sleep(500);

        TaskNoteResponse note = taskNoteService.get(owner.getId(), task.id());
        assertThat(note.content()).isEqualTo("실시간으로 합쳐진 최종 노트");
        assertThat(bufferService.isEmpty(task.id(), TaskEditableField.NOTE)).isTrue();

        session.disconnect();
    }
}
