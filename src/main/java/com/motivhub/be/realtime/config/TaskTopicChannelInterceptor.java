package com.motivhub.be.realtime.config;

import com.motivhub.be.auth.jwt.JwtProvider;
import com.motivhub.be.realtime.exception.StompAuthenticationException;
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
    private static final Pattern TASK_TOPIC_PATTERN = Pattern.compile("^/topic/tasks/(\\d+)$");

    private final JwtProvider jwtProvider;
    private final WorkspaceService workspaceService;
    private final TaskService taskService;

    public TaskTopicChannelInterceptor(JwtProvider jwtProvider, WorkspaceService workspaceService,
                                        TaskService taskService) {
        this.jwtProvider = jwtProvider;
        this.workspaceService = workspaceService;
        this.taskService = taskService;
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
            throw new StompAuthenticationException("클라이언트의 SEND는 허용되지 않습니다.");
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
        Matcher matcher = destination == null ? null : TASK_TOPIC_PATTERN.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new StompAuthenticationException("구독할 수 없는 목적지입니다.");
        }
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new StompAuthenticationException("인증되지 않았습니다.");
        }
        Long userId = Long.valueOf(principal.getName());
        Long taskId = Long.valueOf(matcher.group(1));
        Task task = taskService.getTask(taskId);
        workspaceService.getMembership(task.getWorkspace().getId(), userId);
    }
}
