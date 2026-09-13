package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.config.RealtimeDestinations;
import com.motivhub.be.realtime.dto.TaskEditSaveRequestSignal;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskEditAutosaveScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskEditAutosaveScheduler.class);
    private static final Pattern ACTIVE_KEY_PATTERN = Pattern.compile("^(\\d+):(description|note)$");

    private final TaskEditBufferService bufferService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Duration idleThreshold;
    private final Duration maxWaitThreshold;
    private final Duration retryThreshold;

    public TaskEditAutosaveScheduler(
            TaskEditBufferService bufferService,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.task-edit.autosave.idle-seconds}") long idleSeconds,
            @Value("${app.task-edit.autosave.max-wait-seconds}") long maxWaitSeconds,
            @Value("${app.task-edit.autosave.retry-seconds}") long retrySeconds) {
        this.bufferService = bufferService;
        this.messagingTemplate = messagingTemplate;
        this.idleThreshold = Duration.ofSeconds(idleSeconds);
        this.maxWaitThreshold = Duration.ofSeconds(maxWaitSeconds);
        this.retryThreshold = Duration.ofSeconds(retrySeconds);
    }

    @Scheduled(fixedDelay = 1000)
    public void triggerDueSaves() {
        try {
            for (String activeKey : bufferService.activeBufferKeys()) {
                Matcher matcher = ACTIVE_KEY_PATTERN.matcher(activeKey);
                if (!matcher.matches()) {
                    continue;
                }
                Long taskId = Long.valueOf(matcher.group(1));
                TaskEditableField field = TaskEditableField.fromPathSegment(matcher.group(2));
                bufferService.metadata(taskId, field).ifPresent(metadata -> {
                    if (isDue(metadata)) {
                        requestSave(taskId, field);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("자동저장 폴러 실행 실패", e);
        }
    }

    /**
     * 유휴/최대 대기 조건과 무관하게, "지금 저장을 요청할 만한 이유가 생겼다"(예: 마지막 구독자 이탈)는
     * 외부 신호를 받아 즉시 저장 요청을 시도한다. 최근에 이미 요청했다면(retryThreshold 이내) 중복
     * 브로드캐스트를 피하기 위해 건너뛴다 - triggerDueSaves()와 동일한 재요청 억제 로직을 공유한다.
     */
    public void requestSaveIfDue(Long taskId, TaskEditableField field) {
        bufferService.metadata(taskId, field).ifPresent(metadata -> {
            if (notRecentlyRequested(metadata)) {
                requestSave(taskId, field);
            }
        });
    }

    private boolean isDue(TaskEditBufferService.BufferMetadata metadata) {
        Instant now = Instant.now();
        boolean idleElapsed = Duration.between(metadata.lastUpdateAt(), now).compareTo(idleThreshold) >= 0;
        boolean maxWaitElapsed = Duration.between(metadata.firstUpdateAt(), now).compareTo(maxWaitThreshold) >= 0;
        return (idleElapsed || maxWaitElapsed) && notRecentlyRequested(metadata);
    }

    private boolean notRecentlyRequested(TaskEditBufferService.BufferMetadata metadata) {
        if (metadata.lastRequestedAt() == null) {
            return true;
        }
        return Duration.between(metadata.lastRequestedAt(), Instant.now()).compareTo(retryThreshold) >= 0;
    }

    private void requestSave(Long taskId, TaskEditableField field) {
        bufferService.markRequested(taskId, field);
        messagingTemplate.convertAndSend(
                RealtimeDestinations.taskEditSaveRequest(taskId, field), new TaskEditSaveRequestSignal());
    }
}
