package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskEditSnapshotMessage;
import com.motivhub.be.realtime.dto.TaskEditUpdateMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.dto.TaskYjsStateResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * 스냅샷 저장 시 함께 보낸 Yjs 바이너리 상태가 그대로 영속화되어, 늦은 참여자 부트스트랩용 REST 조회
 * 엔드포인트(GET /api/tasks/{id}/{field}/yjs-state)로 동일하게 되돌아오는지 종단 검증한다. STOMP 헬퍼
 * 구성은 {@link TaskLiveCoEditingEndToEndTest}의 관례를 그대로 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TaskLiveCoEditingYjsStateEndToEndTest extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private JwtProvider jwtProvider;

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

    @SuppressWarnings("unchecked")
    private <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> payloadType) {
        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((T) payload);
            }
        });
        return messages;
    }

    private String fetchYjsStateViaRest(User user, Long taskId, String field) throws Exception {
        String responseBody = mockMvc.perform(get("/api/tasks/{id}/{field}/yjs-state", taskId, field)
                        .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(user.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(responseBody, TaskYjsStateResponse.class).state();
    }

    @Test
    void snapshotWithYjsStateIsRetrievableFromDescriptionYjsStateEndpoint() throws Exception {
        User owner = newUser("yjs-e2e-desc-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "Yjs 상태 종단 워크스페이스(설명)");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("Yjs 상태 종단 태스크(설명)", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribe(session, "/topic/tasks/" + task.id() + "/description/edits", TaskEditUpdateMessage.class);
        // 구독이 인터셉터의 SEND 인가 북키핑(editChannelRegistry)에 반영될 시간을 준다(프레임 순서 미보장).
        Thread.sleep(300);

        // 1) A가 타이핑
        session.send("/app/tasks/" + task.id() + "/description/edits", new TaskEditUpdateMessage("u1"));
        Thread.sleep(200);

        // 2) A가 스냅샷을 저장 - 평문 content와 함께 Yjs 바이너리 상태(Base64)도 실어 보낸다.
        String yjsStateBase64 = Base64.getEncoder().encodeToString("fake-yjs-description-state".getBytes());
        session.send("/app/tasks/" + task.id() + "/description/snapshot",
                new TaskEditSnapshotMessage("설명 최종본", yjsStateBase64));
        // 저장은 별도 스레드(clientInboundChannel)의 별도 트랜잭션에서 커밋된다. 아래 REST 조회는 이
        // 테스트 트랜잭션과 무관한 새 커넥션(MockMvc)이므로, 커밋이 끝날 시간만 확보하면 된다.
        Thread.sleep(1500);

        // 3) 늦은 참여자를 흉내내는 REST 조회로, 방금 저장한 값과 정확히 같은 바이너리 상태가
        //    돌아오는지 확인한다.
        assertThat(fetchYjsStateViaRest(owner, task.id(), "description")).isEqualTo(yjsStateBase64);

        session.disconnect();
    }

    @Test
    void snapshotWithYjsStateIsRetrievableFromNoteYjsStateEndpoint() throws Exception {
        User owner = newUser("yjs-e2e-note-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "Yjs 상태 종단 워크스페이스(노트)");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("Yjs 상태 종단 태스크(노트)", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        subscribe(session, "/topic/tasks/" + task.id() + "/note/edits", TaskEditUpdateMessage.class);
        Thread.sleep(300);

        session.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("u1"));
        Thread.sleep(200);

        String yjsStateBase64 = Base64.getEncoder().encodeToString("fake-yjs-note-state".getBytes());
        session.send("/app/tasks/" + task.id() + "/note/snapshot",
                new TaskEditSnapshotMessage("노트 최종본", yjsStateBase64));
        Thread.sleep(1500);

        assertThat(fetchYjsStateViaRest(owner, task.id(), "note")).isEqualTo(yjsStateBase64);

        session.disconnect();
    }
}
