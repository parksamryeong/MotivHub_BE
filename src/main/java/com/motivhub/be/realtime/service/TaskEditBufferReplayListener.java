package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.config.RealtimeDestinations;
import com.motivhub.be.realtime.dto.TaskEditReplayMessage;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
public class TaskEditBufferReplayListener {

    private static final Logger log = LoggerFactory.getLogger(TaskEditBufferReplayListener.class);
    private static final Pattern USER_QUEUE_PATTERN =
            Pattern.compile("^/user/queue/tasks/(\\d+)/(description|note)/edits$");

    private final TaskEditBufferService bufferService;
    private final SimpMessagingTemplate messagingTemplate;

    public TaskEditBufferReplayListener(TaskEditBufferService bufferService,
                                         SimpMessagingTemplate messagingTemplate) {
        this.bufferService = bufferService;
        this.messagingTemplate = messagingTemplate;
    }

    // 클라이언트가 이 개인 큐를 구독하는 시점 = "이 문서를 지금 막 열었다"는 신호. 그 순간 Redis에
    // 아직 저장되지 않은 업데이트가 쌓여 있으면, 이 세션(딱 이 세션에게만)에게 한 번 재생해준다.
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String destination = accessor.getDestination();
            Matcher matcher = destination == null ? null : USER_QUEUE_PATTERN.matcher(destination);
            if (matcher == null || !matcher.matches()) {
                return;
            }
            Principal principal = event.getUser();
            if (principal == null) {
                return;
            }
            Long taskId = Long.valueOf(matcher.group(1));
            TaskEditableField field = TaskEditableField.fromPathSegment(matcher.group(2));
            List<String> updates = bufferService.listUpdates(taskId, field);
            if (updates.isEmpty()) {
                return;
            }
            messagingTemplate.convertAndSendToUser(principal.getName(),
                    RealtimeDestinations.taskEditUserQueue(taskId, field),
                    new TaskEditReplayMessage(updates));
        } catch (Exception e) {
            log.warn("편집 버퍼 재생 실패", e);
        }
    }
}
