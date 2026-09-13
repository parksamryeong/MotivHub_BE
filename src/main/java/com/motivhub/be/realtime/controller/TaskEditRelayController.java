package com.motivhub.be.realtime.controller;

import com.motivhub.be.realtime.config.RealtimeDestinations;
import com.motivhub.be.realtime.dto.TaskEditSnapshotMessage;
import com.motivhub.be.realtime.dto.TaskEditUpdateMessage;
import com.motivhub.be.realtime.service.TaskEditBufferService;
import com.motivhub.be.realtime.service.TaskEditableField;
import com.motivhub.be.task.domain.Task;
import com.motivhub.be.task.service.TaskNoteService;
import com.motivhub.be.task.service.TaskService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class TaskEditRelayController {

    private static final Logger log = LoggerFactory.getLogger(TaskEditRelayController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskEditBufferService bufferService;
    private final TaskService taskService;
    private final TaskNoteService taskNoteService;

    public TaskEditRelayController(SimpMessagingTemplate messagingTemplate, TaskEditBufferService bufferService,
                                    TaskService taskService, TaskNoteService taskNoteService) {
        this.messagingTemplate = messagingTemplate;
        this.bufferService = bufferService;
        this.taskService = taskService;
        this.taskNoteService = taskNoteService;
    }

    // 이 메서드가 처리하는 SEND는 TaskTopicChannelInterceptor가 이미 "이 세션이 이 필드의 편집 토픽을
    // 구독했다"는 사실을 확인한 뒤에만 여기까지 도달한다. 내용(Base64 Yjs 업데이트)은 파싱하지 않고
    // 그대로 버퍼링 + 릴레이만 한다 - 백엔드는 CRDT를 이해하지 않는다(relay-only).
    @MessageMapping("/tasks/{taskId}/{field}/edits")
    public void relayEdit(@DestinationVariable Long taskId, @DestinationVariable String field,
                           TaskEditUpdateMessage message) {
        try {
            TaskEditableField editableField = TaskEditableField.fromPathSegment(field);
            bufferService.appendUpdate(taskId, editableField, message.update());
            messagingTemplate.convertAndSend(
                    RealtimeDestinations.taskEditBroadcast(taskId, editableField), message);
        } catch (Exception e) {
            log.warn("편집 업데이트 릴레이 실패 - taskId={}, field={}", taskId, field, e);
        }
    }

    // 서버의 저장 요청(save-request)에 응답해서(또는 자발적으로) 클라이언트가 보낸 평문 스냅샷을
    // 기존 REST 저장 경로(TaskService.updateContent / TaskNoteService.upsert)로 그대로 영속화한다.
    // 누가 보내든(CRDT 특성상 모두 같은 최종 텍스트로 수렴) 결과는 동일하므로 발신자를 구분하지 않는다.
    @MessageMapping("/tasks/{taskId}/{field}/snapshot")
    public void receiveSnapshot(@DestinationVariable Long taskId, @DestinationVariable String field,
                                 TaskEditSnapshotMessage message, Principal principal) {
        try {
            TaskEditableField editableField = TaskEditableField.fromPathSegment(field);
            Long userId = Long.valueOf(principal.getName());
            if (editableField == TaskEditableField.DESCRIPTION) {
                Task task = taskService.getTask(taskId);
                taskService.updateContent(userId, taskId, task.getName(), message.content());
            } else {
                taskNoteService.upsert(userId, taskId, message.content());
            }
            bufferService.clear(taskId, editableField);
        } catch (Exception e) {
            log.warn("편집 스냅샷 저장 실패 - taskId={}, field={}", taskId, field, e);
        }
    }
}
