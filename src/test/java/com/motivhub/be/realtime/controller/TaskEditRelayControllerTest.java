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
        // 브로커의 구독 등재가 끝날 시간 확보 - 등재 전에 브로드캐스트가 도착하면 브로커가 에러 없이
        // 조용히 버린다. 예전에는 릴레이가 Redis 버퍼 적재를 먼저 거치면서 그 왕복 지연이 우연히
        // 이 대기를 대신해줬는데, 지금은 브로드캐스트가 가장 먼저 나가므로(Redis 장애가 릴레이를
        // 막지 않도록 한 의도된 순서) 테스트가 직접 대기해야 한다.
        Thread.sleep(300);

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

    // 길이 제한 초과는 재시도해도 절대 성공할 수 없는 실패다(내용이 짧아질 리 없다) - 그래서 저장은
    // 건너뛰면서도 버퍼는 비워서, 자동저장 폴러가 retry-seconds마다 영원히 저장 요청을 재브로드캐스트
    // 하고 TTL 1시간 내내 죽은 엔트리를 들고 있는 상황을 막는다.
    @Test
    void snapshotExceedingDescriptionLengthLimitIsRejectedAndBufferIsCleared() throws Exception {
        User owner = newUser("snapshot-toolong-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "길이초과 스냅샷 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("길이초과 스냅샷 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribeToEdits(session, task.id(), "description");
        session.send("/app/tasks/" + task.id() + "/description/edits", new TaskEditUpdateMessage("u1"));
        Thread.sleep(300);

        session.send("/app/tasks/" + task.id() + "/description/snapshot",
                new TaskEditSnapshotMessage("가".repeat(2001)));
        Thread.sleep(500);

        assertThat(taskService.getTask(task.id()).getDescription()).isNull();
        assertThat(bufferService.isEmpty(task.id(), TaskEditableField.DESCRIPTION)).isTrue();

        session.disconnect();
    }

    // 저장 요청이 나간 뒤 도착한 타이핑 업데이트는 방금 영속화한 스냅샷에 반영되지 않았다 - 이때
    // 버퍼를 비우면 그 업데이트가 유실되므로, 다음 자동저장 주기가 최신 내용을 다시 저장할 수 있도록
    // 버퍼를 남겨둬야 한다.
    @Test
    void snapshotDoesNotClearBufferWhenNewerUpdateArrivedAfterSaveRequest() throws Exception {
        User owner = newUser("snapshot-stale-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "뒤늦은 업데이트 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("뒤늦은 업데이트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribeToEdits(session, task.id(), "note");
        session.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("u1"));
        Thread.sleep(300);

        // 폴러를 기다리지 않고 "저장 요청이 이미 나간 상태"를 직접 만든 뒤, 그 이후에 새 업데이트가
        // 도착하는 순서를 재현한다(테스트 프로파일의 idle-seconds=1보다 훨씬 짧게 끝내야 폴러가
        // 중간에 끼어들어 lastRequestedAt을 다시 갱신하지 않는다).
        bufferService.markRequested(task.id(), TaskEditableField.NOTE);
        Thread.sleep(50);
        session.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("u2"));
        Thread.sleep(300);

        session.send("/app/tasks/" + task.id() + "/note/snapshot",
                new TaskEditSnapshotMessage("저장 요청 시점의 노트"));
        Thread.sleep(500);

        // 저장 자체는 정상적으로 됐지만, 버퍼는 남아있어야 한다.
        assertThat(taskNoteService.get(owner.getId(), task.id()).content()).isEqualTo("저장 요청 시점의 노트");
        assertThat(bufferService.isEmpty(task.id(), TaskEditableField.NOTE)).isFalse();
        assertThat(bufferService.listUpdates(task.id(), TaskEditableField.NOTE)).contains("u2");

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
