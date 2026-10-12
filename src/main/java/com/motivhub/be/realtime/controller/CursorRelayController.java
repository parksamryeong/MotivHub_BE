package com.motivhub.be.realtime.controller;

import com.motivhub.be.realtime.config.RealtimeDestinations;
import com.motivhub.be.realtime.config.StompPrincipal;
import com.motivhub.be.realtime.dto.CursorBroadcastMessage;
import com.motivhub.be.realtime.dto.CursorUpdateMessage;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

// 마우스 커서 위치는 순수 중계(relay-only)다 - 저장하지 않고, 서버는 좌표 값을 해석하지 않는다.
// 권한 확인은 TaskTopicChannelInterceptor가 SUBSCRIBE/SEND 시점마다 직접 멤버십을 확인하므로
// (편집 채널처럼 선구독 인가를 요구하지 않음) 이 컨트롤러에서는 다시 확인하지 않는다.
@Controller
public class CursorRelayController {

    private static final Logger log = LoggerFactory.getLogger(CursorRelayController.class);

    private final SimpMessagingTemplate messagingTemplate;

    public CursorRelayController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/workspaces/{workspaceId}/board/cursor")
    public void relayBoardCursor(@DestinationVariable Long workspaceId, CursorUpdateMessage message,
                                  Principal principal) {
        try {
            messagingTemplate.convertAndSend(
                    RealtimeDestinations.workspaceBoardCursorBroadcast(workspaceId),
                    toBroadcastMessage(principal, message));
        } catch (Exception e) {
            log.warn("보드 커서 브로드캐스트 실패 - workspaceId={}", workspaceId, e);
        }
    }

    @MessageMapping("/tasks/{taskId}/cursor")
    public void relayTaskCursor(@DestinationVariable Long taskId, CursorUpdateMessage message,
                                 Principal principal) {
        try {
            messagingTemplate.convertAndSend(
                    RealtimeDestinations.taskCursorBroadcast(taskId), toBroadcastMessage(principal, message));
        } catch (Exception e) {
            log.warn("태스크 커서 브로드캐스트 실패 - taskId={}", taskId, e);
        }
    }

    private CursorBroadcastMessage toBroadcastMessage(Principal principal, CursorUpdateMessage message) {
        StompPrincipal stompPrincipal = (StompPrincipal) principal;
        Long userId = Long.valueOf(stompPrincipal.getName());
        return CursorBroadcastMessage.of(
                userId, stompPrincipal.getNickname(), stompPrincipal.getProfileImageUrl(), message);
    }
}
