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

    // REST DTO(TaskContentUpdateRequest / TaskNoteUpdateRequest)의 @Size(max=...)와 반드시 같은 값.
    // 스냅샷 저장은 Bean Validation을 타지 않는 STOMP 경로이므로, 같은 제약을 여기서 직접 확인한다.
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final int NOTE_MAX_LENGTH = 50000;

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
        TaskEditableField editableField;
        try {
            editableField = TaskEditableField.fromPathSegment(field);
        } catch (Exception e) {
            log.warn("편집 업데이트 릴레이 실패 - 알 수 없는 필드: taskId={}, field={}", taskId, field, e);
            return;
        }

        // 브로드캐스트를 먼저, 그리고 버퍼 적재와 완전히 분리된 try/catch로 처리한다. Redis 장애가
        // 실시간 릴레이 자체를 막으면 안 된다 - 설계 문서의 "Redis 장애 시 자동저장만 멈추고 릴레이는
        // 계속 동작한다"는 약속을 코드 순서로 보장하는 부분.
        try {
            messagingTemplate.convertAndSend(
                    RealtimeDestinations.taskEditBroadcast(taskId, editableField), message);
        } catch (Exception e) {
            log.warn("편집 업데이트 브로드캐스트 실패 - taskId={}, field={}", taskId, field, e);
        }

        try {
            bufferService.appendUpdate(taskId, editableField, message.update());
        } catch (Exception e) {
            log.warn("편집 업데이트 버퍼 적재 실패(자동저장만 영향, 실시간 릴레이는 정상) - taskId={}, field={}",
                    taskId, field, e);
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

            // STOMP 경로에는 @Valid가 걸리지 않으므로 REST DTO와 같은 길이 제약을 직접 확인한다.
            // 길이 초과는 "재시도하면 성공할 수 있는 실패"가 아니다(내용이 짧아질 리 없다) - 그래서
            // 저장을 건너뛰면서도 버퍼는 비워서, 자동저장 폴러가 retry-seconds마다 영원히 저장 요청을
            // 재브로드캐스트하고 TTL 1시간 내내 죽은 엔트리를 들고 있는 상황을 막는다.
            int length = message.content() == null ? 0 : message.content().length();
            int maxLength = maxLength(editableField);
            if (length > maxLength) {
                log.warn("편집 스냅샷 저장 거부 - 길이 제한 초과로 저장하지 않고 재시도도 하지 않음: "
                                + "taskId={}, field={}, length={}, maxLength={}",
                        taskId, field, length, maxLength);
                bufferService.clear(taskId, editableField);
                return;
            }

            if (editableField == TaskEditableField.DESCRIPTION) {
                Task task = taskService.getTask(taskId);
                taskService.updateContent(userId, taskId, task.getName(), message.content());
            } else {
                taskNoteService.upsert(userId, taskId, message.content());
            }

            // 저장 요청이 나간 뒤에 새 타이핑 업데이트가 도착했다면, 방금 영속화한 스냅샷은 이미 그
            // 업데이트를 반영하지 못한 옛 내용이다. 이때 버퍼를 비우면 그 업데이트가 유실되므로
            // 버퍼를 남겨둬서 다음 자동저장 주기(또는 즉시 플러시)가 최신 내용을 다시 저장하게 한다.
            if (hasNewerUpdateSinceSaveRequest(taskId, editableField)) {
                log.debug("편집 스냅샷 저장 후 버퍼 유지 - 저장 요청 이후 새 업데이트 도착: taskId={}, field={}",
                        taskId, field);
                return;
            }
            bufferService.clear(taskId, editableField);
        } catch (Exception e) {
            log.warn("편집 스냅샷 저장 실패 - taskId={}, field={}", taskId, field, e);
        }
    }

    private int maxLength(TaskEditableField field) {
        return field == TaskEditableField.DESCRIPTION ? DESCRIPTION_MAX_LENGTH : NOTE_MAX_LENGTH;
    }

    private boolean hasNewerUpdateSinceSaveRequest(Long taskId, TaskEditableField field) {
        return bufferService.metadata(taskId, field)
                .filter(metadata -> metadata.lastRequestedAt() != null)
                .map(metadata -> metadata.lastUpdateAt().isAfter(metadata.lastRequestedAt()))
                .orElse(false);
    }
}
