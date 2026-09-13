package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskEditReplayMessage;
import com.motivhub.be.realtime.dto.TaskEditSaveRequestSignal;
import com.motivhub.be.realtime.dto.TaskEditSnapshotMessage;
import com.motivhub.be.realtime.dto.TaskEditUpdateMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.workspace.domain.Workspace;
import com.motivhub.be.workspace.domain.WorkspaceMember;
import com.motivhub.be.workspace.domain.WorkspaceRole;
import com.motivhub.be.workspace.dto.WorkspaceResponse;
import com.motivhub.be.workspace.repository.WorkspaceMemberRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.lang.reflect.Type;
import java.time.Duration;
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

/**
 * 실시간 공동편집 기능 전체(릴레이 -> 자동저장 요청 -> 스냅샷 영속화 -> 늦은 참여자 재생 ->
 * 구독 해제 시 즉시 플러시)를 실제 STOMP 커넥션 여러 개로 종단 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskLiveCoEditingEndToEndTest extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
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

    private void joinAsMember(Long workspaceId, User user) {
        Workspace workspace = workspaceService.getWorkspace(workspaceId);
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, WorkspaceRole.MEMBER));
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

    @Test
    void fullLifecycleFromTypingToSaveRequestToPersistedSnapshot() throws Exception {
        User owner = newUser("e2e-owner");
        User teammate = newUser("e2e-teammate");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "종단 테스트 워크스페이스");
        joinAsMember(workspace.id(), teammate);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        // teammate를 담당자로 지정한다 - 스냅샷 저장은 TaskService.updateContent를 그대로 타므로
        // TaskAccessPolicy.requireEditPermission(OWNER 또는 담당자)을 만족해야 한다. 단순 MEMBER로는
        // 편집 토픽을 구독하고 릴레이까지 할 수 있어도 저장은 조용히 거부된다(리포트의 우려사항 참고).
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("종단 테스트 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5),
                        List.of(teammate.getId())));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession ownerSession = connectAsUser(owner);
        subscribe(ownerSession, "/topic/tasks/" + task.id() + "/description/edits", TaskEditUpdateMessage.class);

        StompSession teammateSession = connectAsUser(teammate);
        BlockingQueue<TaskEditUpdateMessage> teammateEdits =
                subscribe(teammateSession, "/topic/tasks/" + task.id() + "/description/edits", TaskEditUpdateMessage.class);
        BlockingQueue<TaskEditSaveRequestSignal> teammateSaveRequests =
                subscribe(teammateSession, "/topic/tasks/" + task.id() + "/description/save-request",
                        TaskEditSaveRequestSignal.class);

        // clientInboundChannel은 스레드 풀이라 같은 세션의 SUBSCRIBE와 SEND가 서로 다른 스레드에서
        // 처리될 수 있고, 프레임 순서가 보장되지 않는다. 구독이 (a) 인터셉터의 SEND 인가 북키핑과
        // (b) 브로커의 구독 레지스트리에 모두 반영될 시간을 주고 나서 타이핑을 시작한다.
        Thread.sleep(300);

        // 1) A(owner)가 타이핑 -> B(teammate)에게 실시간으로 도착
        ownerSession.send("/app/tasks/" + task.id() + "/description/edits", new TaskEditUpdateMessage("u1"));
        assertThat(teammateEdits.poll(5, TimeUnit.SECONDS)).isNotNull();

        // 2) 자동저장 폴러가 유휴/최대대기 조건으로 저장 요청을 브로드캐스트함(테스트 프로파일은
        //    idle-seconds=1/max-wait-seconds=2)
        TaskEditSaveRequestSignal saveRequest = teammateSaveRequests.poll(5, TimeUnit.SECONDS);
        assertThat(saveRequest).isNotNull();

        // 3) 접속 중인 아무 클라이언트(teammate)나 스냅샷으로 응답 -> DB 반영 + 버퍼 비워짐
        teammateSession.send("/app/tasks/" + task.id() + "/description/snapshot",
                new TaskEditSnapshotMessage("최종 합쳐진 설명"));
        // 저장은 별도 스레드(clientInboundChannel)의 별도 트랜잭션에서 커밋된다. 아래 검증은 이 테스트
        // 트랜잭션의 첫 읽기이므로(= 아직 스냅샷이 잡히지 않았다) 커밋된 값을 보게 된다. 단, 같은
        // 트랜잭션에서 재조회해도 JPA 1차 캐시 탓에 갱신이 안 보이므로 폴링하지 않고 한 번만 읽는다.
        Thread.sleep(1500);
        assertThat(taskService.getTask(task.id()).getDescription()).isEqualTo("최종 합쳐진 설명");
        assertThat(bufferService.isEmpty(task.id(), TaskEditableField.DESCRIPTION)).isTrue();

        ownerSession.disconnect();
        teammateSession.disconnect();
    }

    @Test
    void lateJoinerReceivesReplayOfUnsavedUpdatesBeforeSaveHappens() throws Exception {
        User owner = newUser("e2e-late-owner");
        User lateJoiner = newUser("e2e-late-joiner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "늦은 참여 워크스페이스");
        joinAsMember(workspace.id(), lateJoiner);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("늦은 참여 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession ownerSession = connectAsUser(owner);
        subscribe(ownerSession, "/topic/tasks/" + task.id() + "/note/edits", TaskEditUpdateMessage.class);
        // 구독이 인터셉터의 SEND 인가 북키핑에 반영될 시간을 준다(프레임 순서 미보장).
        Thread.sleep(300);
        ownerSession.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("still-unsaved"));
        Thread.sleep(300);

        StompSession lateSession = connectAsUser(lateJoiner);
        subscribe(lateSession, "/topic/tasks/" + task.id() + "/note/edits", TaskEditUpdateMessage.class);
        BlockingQueue<TaskEditReplayMessage> replay =
                subscribe(lateSession, "/user/queue/tasks/" + task.id() + "/note/edits", TaskEditReplayMessage.class);

        TaskEditReplayMessage received = replay.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.updates()).contains("still-unsaved");

        ownerSession.disconnect();
        lateSession.disconnect();
    }

    @Test
    void unsubscribingFromEditsTopicWithNonEmptyBufferTriggersImmediateSaveRequest() throws Exception {
        User owner = newUser("e2e-flush-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "즉시플러시 워크스페이스");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("즉시플러시 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // 저장요청 신호를 관찰하는 세션을 "타이핑 전에" 미리 붙여둔다. 그래야 아래의 관찰 창이
        // 커넥션 수립 시간에 영향받지 않는다.
        StompSession observerSession = connectAsUser(owner);
        BlockingQueue<TaskEditSaveRequestSignal> saveRequests = subscribe(observerSession,
                "/topic/tasks/" + task.id() + "/description/save-request", TaskEditSaveRequestSignal.class);
        Thread.sleep(300);

        StompSession session = connectAsUser(owner);
        var subscription = session.subscribe(
                "/topic/tasks/" + task.id() + "/description/edits", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return TaskEditUpdateMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                    }
                });
        // 구독이 인터셉터의 SEND 인가 북키핑에 반영될 시간을 준다(프레임 순서 미보장).
        Thread.sleep(300);
        session.send("/app/tasks/" + task.id() + "/description/edits", new TaskEditUpdateMessage("u1"));
        // 버퍼 적재(SEND 처리)가 끝나기를 기다린다. 아래 관찰 창을 자동저장 폴러보다 확실히 앞서게
        // 하려면 이 대기는 짧아야 한다(테스트 프로파일 idle-seconds=1).
        Thread.sleep(200);

        subscription.unsubscribe();

        // 이 테스트가 "리스너 덕분에" 통과하는지 확실히 하려면, 자동저장 폴러가 끼어들 수 없는 창
        // 안에서 신호가 와야 한다. 폴러가 가장 빨리 이 버퍼를 저장 대상으로 판정하는 시점은
        // lastUpdateAt + idle-seconds(1초) = 타이핑 후 1초이고, 위에서 200ms만 대기했으므로
        // 800ms 안에 도착한 신호는 구독 해제가 유발한 것일 수밖에 없다(최대대기 2초도 아직 멀다).
        assertThat(saveRequests.poll(800, TimeUnit.MILLISECONDS)).isNotNull();

        session.disconnect();
        observerSession.disconnect();
    }

    @Test
    void unsubscribingFromEditsTopicWithEmptyBufferTriggersNoSaveRequest() throws Exception {
        User owner = newUser("e2e-noflush-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "즉시플러시 없음 워크스페이스");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("즉시플러시 없음 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession observerSession = connectAsUser(owner);
        BlockingQueue<TaskEditSaveRequestSignal> saveRequests = subscribe(observerSession,
                "/topic/tasks/" + task.id() + "/description/save-request", TaskEditSaveRequestSignal.class);

        StompSession session = connectAsUser(owner);
        var subscription = session.subscribe(
                "/topic/tasks/" + task.id() + "/description/edits", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return TaskEditUpdateMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                    }
                });
        Thread.sleep(300);

        // 한 번도 타이핑하지 않았으므로 버퍼가 비어 있다 -> 저장 요청이 나가면 안 된다.
        subscription.unsubscribe();

        assertThat(saveRequests.poll(2, TimeUnit.SECONDS)).isNull();

        session.disconnect();
        observerSession.disconnect();
    }

    @Test
    void unsubscribingFromNoteEditsTopicAlsoTriggersImmediateSaveRequest() throws Exception {
        User owner = newUser("e2e-flush-note-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "노트 즉시플러시 워크스페이스");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("노트 즉시플러시 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession observerSession = connectAsUser(owner);
        BlockingQueue<TaskEditSaveRequestSignal> saveRequests = subscribe(observerSession,
                "/topic/tasks/" + task.id() + "/note/save-request", TaskEditSaveRequestSignal.class);
        Thread.sleep(300);

        StompSession session = connectAsUser(owner);
        var subscription = session.subscribe(
                "/topic/tasks/" + task.id() + "/note/edits", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return TaskEditUpdateMessage.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                    }
                });
        // 구독이 인터셉터의 SEND 인가 북키핑에 반영될 시간을 준다(프레임 순서 미보장).
        Thread.sleep(300);
        session.send("/app/tasks/" + task.id() + "/note/edits", new TaskEditUpdateMessage("note-u1"));
        Thread.sleep(200);

        long unsubscribedAt = System.nanoTime();
        subscription.unsubscribe();

        assertThat(saveRequests.poll(800, TimeUnit.MILLISECONDS)).isNotNull();
        // 폴러가 아니라 구독 해제가 원인이라는 점을 한 번 더 못 박는다(타이핑 후 1초 이전 도착).
        assertThat(Duration.ofNanos(System.nanoTime() - unsubscribedAt))
                .isLessThan(Duration.ofMillis(800));

        session.disconnect();
        observerSession.disconnect();
    }
}
