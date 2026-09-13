package com.motivhub.be.realtime.config;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.exception.StompAuthenticationException;
import com.motivhub.be.realtime.service.TaskEditChannelRegistry;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.service.TaskService;
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
    private static final Pattern EDIT_SAVE_REQUEST_TOPIC_PATTERN =
            Pattern.compile("^/topic/tasks/(\\d+)/(description|note)/save-request$");
    private static final Pattern EDIT_USER_QUEUE_PATTERN =
            Pattern.compile("^/user/queue/tasks/(\\d+)/(description|note)/edits$");
    private static final Pattern EDIT_SEND_PATTERN =
            Pattern.compile("^/app/tasks/(\\d+)/(description|note)/(edits|snapshot)$");

    private final JwtProvider jwtProvider;
    private final WorkspaceService workspaceService;
    private final TaskService taskService;
    private final TaskEditChannelRegistry editChannelRegistry;

    public TaskTopicChannelInterceptor(JwtProvider jwtProvider, WorkspaceService workspaceService,
                                        TaskService taskService, TaskEditChannelRegistry editChannelRegistry) {
        this.jwtProvider = jwtProvider;
        this.workspaceService = workspaceService;
        this.taskService = taskService;
        this.editChannelRegistry = editChannelRegistry;
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
        accessor.setUser(new StompPrincipal(userId));
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
            requireTaskMembership(Long.valueOf(editBroadcastMatcher.group(1)), userId);
            // 이 구독 하나가 곧 "이 세션이 이 필드를 편집 중이다"라는 인가 근거가 된다 - SEND는
            // DB를 다시 조회하지 않고 이 사실만 확인한다(핸들send 참고).
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
        throw new StompAuthenticationException("구독할 수 없는 목적지입니다.");
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Matcher editSendMatcher = destination == null ? null : EDIT_SEND_PATTERN.matcher(destination);
        if (editSendMatcher == null || !editSendMatcher.matches()) {
            throw new StompAuthenticationException("클라이언트의 SEND는 허용되지 않습니다.");
        }
        String taskId = editSendMatcher.group(1);
        String field = editSendMatcher.group(2);
        String canonicalTopic = "/topic/tasks/" + taskId + "/" + field + "/edits";
        String sessionId = accessor.getSessionId();
        if (sessionId == null || !editChannelRegistry.isAuthorized(sessionId, canonicalTopic)) {
            throw new StompAuthenticationException("이 목적지로 SEND할 권한이 없습니다.");
        }
    }

    private void requireTaskMembership(Long taskId, Long userId) {
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
    }
}
