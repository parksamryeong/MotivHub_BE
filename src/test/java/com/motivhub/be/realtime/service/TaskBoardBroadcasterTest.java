package com.motivhub.be.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.dto.TaskBoardChangeMessage;
import com.motivhub.be.support.AbstractIntegrationTest;
import com.motivhub.be.task.domain.TaskStatus;
import com.motivhub.be.task.dto.TaskCreateRequest;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.event.TaskChangeType;
import com.motivhub.be.task.service.TaskExpirationScheduler;
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
class TaskBoardBroadcasterTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired private TaskService taskService;
    @Autowired private WorkspaceService workspaceService;
    @Autowired private UserRepository userRepository;
    @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TaskExpirationScheduler taskExpirationScheduler;

    private User newUser(String suffix) {
        User user = userRepository.save(User.create(
                SocialProvider.GITHUB, "board-bcast-" + suffix, suffix + "@test.com", "user_" + suffix, null));
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
    // 지원하지 않음 - MappingJackson2MessageConverter()의 기본 생성자는 findAndRegisterModules()를
    // 호출하지 않는 순수 new ObjectMapper()를 사용).
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
    void memberReceivesBroadcastWhenTaskIsCreated() throws Exception {
        User owner = newUser("create-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 브로드캐스트 생성 워크스페이스");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskBoardChangeMessage> messages = subscribeToBoard(session, workspace.id());
        Thread.sleep(500); // 구독 프레임이 서버에 실제로 등록될 시간 확보(구독은 비동기)

        TestTransaction.flagForCommit();
        TaskResponse created = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보드 브로드캐스트 생성 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.changeType()).isEqualTo(TaskChangeType.CREATED);
        assertThat(received.taskId()).isEqualTo(created.id());
        assertThat(received.task()).isNotNull();
        assertThat(received.task().name()).isEqualTo("보드 브로드캐스트 생성 태스크");

        session.disconnect();
    }

    @Test
    void memberReceivesBroadcastWhenTaskIsUpdated() throws Exception {
        User owner = newUser("update-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 브로드캐스트 수정 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보드 브로드캐스트 수정 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskBoardChangeMessage> messages = subscribeToBoard(session, workspace.id());
        Thread.sleep(500);

        TestTransaction.flagForCommit();
        taskService.updateContent(owner.getId(), task.id(), "바뀐 보드 태스크 이름", null);
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.changeType()).isEqualTo(TaskChangeType.UPDATED);
        assertThat(received.taskId()).isEqualTo(task.id());
        assertThat(received.task()).isNotNull();
        assertThat(received.task().name()).isEqualTo("바뀐 보드 태스크 이름");

        session.disconnect();
    }

    @Test
    void boardBroadcastIncludesAssigneesWhenTaskIsUpdated() throws Exception {
        User owner = newUser("board-assignee-owner");
        User teammate = newUser("board-assignee-teammate");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 브로드캐스트 담당자 워크스페이스");
        joinAsMember(workspace.id(), teammate);
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보드 브로드캐스트 담당자 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of(owner.getId())));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskBoardChangeMessage> messages = subscribeToBoard(session, workspace.id());
        Thread.sleep(500); // 구독 프레임이 서버에 실제로 등록될 시간 확보(구독은 비동기)

        TestTransaction.flagForCommit();
        taskService.addAssignee(owner.getId(), task.id(), teammate.getId());
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.changeType()).isEqualTo(TaskChangeType.UPDATED);
        assertThat(received.taskId()).isEqualTo(task.id());
        assertThat(received.task()).isNotNull();
        assertThat(received.task().assignees()).extracting(UserSummary::id).containsExactlyInAnyOrder(owner.getId(), teammate.getId());

        session.disconnect();
    }

    @Test
    void memberReceivesBroadcastWhenSchedulerExpiresOverdueTask() throws Exception {
        User owner = newUser("expire-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 브로드캐스트 만료 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보드 브로드캐스트 만료 태스크", null, LocalDate.now().minusDays(5), LocalDate.now().minusDays(1), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskBoardChangeMessage> messages = subscribeToBoard(session, workspace.id());
        Thread.sleep(500);

        TestTransaction.flagForCommit();
        taskExpirationScheduler.expireOverdueTasks();
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.changeType()).isEqualTo(TaskChangeType.UPDATED);
        assertThat(received.taskId()).isEqualTo(task.id());
        assertThat(received.task()).isNotNull();
        assertThat(received.task().status()).isEqualTo(TaskStatus.EXPIRED);

        session.disconnect();
    }

    @Test
    void memberReceivesBroadcastWithNullTaskWhenTaskIsDeleted() throws Exception {
        User owner = newUser("delete-owner");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 브로드캐스트 삭제 워크스페이스");
        TaskResponse task = taskService.create(owner.getId(), workspace.id(),
                new TaskCreateRequest("보드 브로드캐스트 삭제 태스크", null, LocalDate.now(), LocalDate.now().plusDays(5), List.of()));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        StompSession session = connectAsUser(owner);
        BlockingQueue<TaskBoardChangeMessage> messages = subscribeToBoard(session, workspace.id());
        Thread.sleep(500);

        TestTransaction.flagForCommit();
        taskService.delete(owner.getId(), task.id());
        TestTransaction.end();
        TestTransaction.start();

        TaskBoardChangeMessage received = messages.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.changeType()).isEqualTo(TaskChangeType.DELETED);
        assertThat(received.taskId()).isEqualTo(task.id());
        assertThat(received.task()).isNull();

        session.disconnect();
    }

    @Test
    void nonMemberCannotSubscribeToWorkspaceBoardTopicOverRealStompConnection() throws Exception {
        User owner = newUser("outsider-owner");
        User outsider = newUser("outsider");
        WorkspaceResponse workspace = workspaceService.create(owner.getId(), "보드 비멤버 워크스페이스");
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(jacksonMessageConverterWithJavaTimeSupport());

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

        subscribeToBoard(session, workspace.id());

        assertThat(subscriptionRejected.get(5, TimeUnit.SECONDS)).isTrue();

        try {
            session.disconnect();
        } catch (Exception ignored) {
            // 서버가 이미 연결을 종료했을 수 있음 — 정리 목적의 호출이므로 예외는 무시
        }
    }
}
