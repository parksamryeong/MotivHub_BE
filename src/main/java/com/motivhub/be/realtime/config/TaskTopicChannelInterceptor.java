package com.motivhub.be.realtime.config;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.exception.StompAuthenticationException;
import com.motivhub.be.realtime.service.TaskEditChannelRegistry;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.service.TaskAccessPolicy;
import com.motivhub.be.task.service.TaskService;
import com.motivhub.be.user.domain.User;
import com.motivhub.be.user.repository.UserRepository;
import com.motivhub.be.workspace.service.WorkspaceService;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class TaskTopicChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final Pattern TASK_TOPIC_PATTERN = Pattern.compile("^/topic/tasks/(\\d+)(?:/presence)?$");
    private static final Pattern WORKSPACE_BOARD_TOPIC_PATTERN = Pattern.compile("^/topic/workspaces/(\\d+)/tasks$");
    private static final Pattern EDIT_BROADCAST_TOPIC_PATTERN =
            Pattern.compile("^/topic/tasks/(\\d+)/(description|note)/edits$");
    private static final Pattern AWARENESS_TOPIC_PATTERN =
            Pattern.compile("^/topic/tasks/(\\d+)/(description|note)/awareness$");
    private static final Pattern EDIT_SAVE_REQUEST_TOPIC_PATTERN =
            Pattern.compile("^/topic/tasks/(\\d+)/(description|note)/save-request$");
    private static final Pattern EDIT_USER_QUEUE_PATTERN =
            Pattern.compile("^/user/queue/tasks/(\\d+)/(description|note)/edits$");
    private static final Pattern EDIT_SEND_PATTERN =
            Pattern.compile("^/app/tasks/(\\d+)/(description|note)/(edits|snapshot|awareness)$");
    private static final Pattern WORKSPACE_BOARD_CURSOR_TOPIC_PATTERN =
            Pattern.compile("^/topic/workspaces/(\\d+)/board/cursors$");
    private static final Pattern TASK_CURSOR_TOPIC_PATTERN = Pattern.compile("^/topic/tasks/(\\d+)/cursors$");
    private static final Pattern WORKSPACE_BOARD_CURSOR_SEND_PATTERN =
            Pattern.compile("^/app/workspaces/(\\d+)/board/cursor$");
    private static final Pattern TASK_CURSOR_SEND_PATTERN = Pattern.compile("^/app/tasks/(\\d+)/cursor$");

    private final JwtProvider jwtProvider;
    private final WorkspaceService workspaceService;
    private final TaskService taskService;
    private final TaskEditChannelRegistry editChannelRegistry;
    private final TaskAccessPolicy taskAccessPolicy;
    private final UserRepository userRepository;

    public TaskTopicChannelInterceptor(JwtProvider jwtProvider, WorkspaceService workspaceService,
                                        TaskService taskService, TaskEditChannelRegistry editChannelRegistry,
                                        TaskAccessPolicy taskAccessPolicy, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.workspaceService = workspaceService;
        this.taskService = taskService;
        this.editChannelRegistry = editChannelRegistry;
        this.taskAccessPolicy = taskAccessPolicy;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            handleSend(accessor);
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new StompAuthenticationException("인증 토큰이 필요합니다.");
        }
        String token = header.substring(BEARER_PREFIX.length());
        if (!jwtProvider.isValid(token) || !TOKEN_TYPE_ACCESS.equals(jwtProvider.getTokenType(token))) {
            throw new StompAuthenticationException("유효하지 않은 토큰입니다.");
        }
        Long userId = jwtProvider.getUserId(token);
        // 커서 브로드캐스트에 쓸 닉네임/프로필 이미지를 CONNECT 시점에 한 번만 조회해 세션
        // Principal에 담아둔다 - 탈퇴 등으로 유저를 찾지 못해도 연결 자체는 막지 않는다(기존
        // CONNECT 동작은 JWT 유효성만 확인했을 뿐 유저 존재 여부는 확인하지 않았다).
        User user = userRepository.findById(userId).orElse(null);
        String nickname = user == null ? null : user.getNickname();
        String profileImageUrl = user == null ? null : user.getProfileImageUrl();
        accessor.setUser(new StompPrincipal(userId, nickname, profileImageUrl));
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new StompAuthenticationException("구독할 수 없는 목적지입니다.");
        }
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new StompAuthenticationException("인증되지 않았습니다.");
        }
        Long userId = Long.valueOf(principal.getName());

        Matcher taskMatcher = TASK_TOPIC_PATTERN.matcher(destination);
        if (taskMatcher.matches()) {
            requireTaskMembership(Long.valueOf(taskMatcher.group(1)), userId);
            return;
        }
        Matcher workspaceMatcher = WORKSPACE_BOARD_TOPIC_PATTERN.matcher(destination);
        if (workspaceMatcher.matches()) {
            workspaceService.getMembership(Long.valueOf(workspaceMatcher.group(1)), userId);
            return;
        }
        Matcher editBroadcastMatcher = EDIT_BROADCAST_TOPIC_PATTERN.matcher(destination);
        if (editBroadcastMatcher.matches()) {
            Long editTaskId = Long.valueOf(editBroadcastMatcher.group(1));
            String field = editBroadcastMatcher.group(2);
            Task task = taskService.getTask(editTaskId);
            if ("description".equals(field)) {
                // description은 REST PATCH(TaskService.updateContent)와 동일하게 OWNER 또는
                // 담당자만 편집 가능 - 실시간 편집도 이 기준과 어긋나면 안 된다(구독은 허용해놓고
                // 저장은 항상 거부되는 상황을 SUBSCRIBE 시점에 막는다).
                taskAccessPolicy.requireEditPermission(task, userId);
            } else {
                // note는 TaskNoteService.upsert와 동일하게 워크스페이스 멤버십만 요구.
                workspaceService.getMembership(task.getWorkspace().getId(), userId);
            }
            // 이 구독 하나가 곧 "이 세션이 이 필드를 편집 중이다"라는 인가 근거가 된다 - SEND는
            // DB를 다시 조회하지 않고 이 사실만 확인한다(핸들send 참고).
            editChannelRegistry.authorize(accessor.getSessionId(), destination);
            return;
        }
        Matcher awarenessMatcher = AWARENESS_TOPIC_PATTERN.matcher(destination);
        if (awarenessMatcher.matches()) {
            Long awarenessTaskId = Long.valueOf(awarenessMatcher.group(1));
            String field = awarenessMatcher.group(2);
            Task task = taskService.getTask(awarenessTaskId);
            // /edits 구독과 완전히 같은 권한 기준 - 이 필드를 볼/편집할 권한이 없으면 그 필드의
            // 커서 정보도 볼 이유가 없다.
            if ("description".equals(field)) {
                taskAccessPolicy.requireEditPermission(task, userId);
            } else {
                workspaceService.getMembership(task.getWorkspace().getId(), userId);
            }
            // /edits와는 별개의 인가 항목이다(토픽 문자열이 다르므로 키가 자동으로 분리된다) -
            // /edits만 구독한 세션은 이 토픽으로 SEND할 수 없다.
            editChannelRegistry.authorize(accessor.getSessionId(), destination);
            return;
        }
        Matcher saveRequestMatcher = EDIT_SAVE_REQUEST_TOPIC_PATTERN.matcher(destination);
        if (saveRequestMatcher.matches()) {
            requireTaskMembership(Long.valueOf(saveRequestMatcher.group(1)), userId);
            return;
        }
        Matcher userQueueMatcher = EDIT_USER_QUEUE_PATTERN.matcher(destination);
        if (userQueueMatcher.matches()) {
            requireTaskMembership(Long.valueOf(userQueueMatcher.group(1)), userId);
            return;
        }
        Matcher boardCursorTopicMatcher = WORKSPACE_BOARD_CURSOR_TOPIC_PATTERN.matcher(destination);
        if (boardCursorTopicMatcher.matches()) {
            workspaceService.getMembership(Long.valueOf(boardCursorTopicMatcher.group(1)), userId);
            return;
        }
        Matcher taskCursorTopicMatcher = TASK_CURSOR_TOPIC_PATTERN.matcher(destination);
        if (taskCursorTopicMatcher.matches()) {
            requireTaskMembership(Long.valueOf(taskCursorTopicMatcher.group(1)), userId);
            return;
        }
        throw new StompAuthenticationException("구독할 수 없는 목적지입니다.");
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new StompAuthenticationException("클라이언트의 SEND는 허용되지 않습니다.");
        }

        // 커서 전송은 /edits·/awareness와 달리 "먼저 구독해야 보낼 수 있다"는 순서 보장이
        // 필요 없다(설계상 relay-only이고 세션 간 순서 꼬임을 신경 쓸 상태가 없음) - 그래서
        // editChannelRegistry를 거치지 않고 매 SEND마다 바로 멤버십을 확인한다.
        Matcher boardCursorSendMatcher = WORKSPACE_BOARD_CURSOR_SEND_PATTERN.matcher(destination);
        if (boardCursorSendMatcher.matches()) {
            workspaceService.getMembership(
                    Long.valueOf(boardCursorSendMatcher.group(1)), requireAuthenticatedUserId(accessor));
            return;
        }
        Matcher taskCursorSendMatcher = TASK_CURSOR_SEND_PATTERN.matcher(destination);
        if (taskCursorSendMatcher.matches()) {
            requireTaskMembership(Long.valueOf(taskCursorSendMatcher.group(1)), requireAuthenticatedUserId(accessor));
            return;
        }

        Matcher editSendMatcher = EDIT_SEND_PATTERN.matcher(destination);
        if (!editSendMatcher.matches()) {
            throw new StompAuthenticationException("클라이언트의 SEND는 허용되지 않습니다.");
        }
        String taskId = editSendMatcher.group(1);
        String field = editSendMatcher.group(2);
        String action = editSendMatcher.group(3);
        // edits/snapshot은 /edits 구독 인가를 공유한다(기존 동작, 변경 없음) - awareness는
        // /awareness 구독 인가가 별도로 필요하다.
        String requiredSubscription = "awareness".equals(action) ? "awareness" : "edits";
        String canonicalTopic = "/topic/tasks/" + taskId + "/" + field + "/" + requiredSubscription;
        String sessionId = accessor.getSessionId();
        if (sessionId == null || !editChannelRegistry.isAuthorized(sessionId, canonicalTopic)) {
            throw new StompAuthenticationException("이 목적지로 SEND할 권한이 없습니다.");
        }
    }

    private void requireTaskMembership(Long taskId, Long userId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
    }

    private Long requireAuthenticatedUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new StompAuthenticationException("인증되지 않았습니다.");
        }
        return Long.valueOf(principal.getName());
    }
}
