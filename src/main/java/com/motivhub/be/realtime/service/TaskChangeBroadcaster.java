package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.dto.TaskChangedMessage;
import com.motivhub.be.task.event.TaskChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskChangeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TaskChangeBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public TaskChangeBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(TaskChangedEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/tasks/" + event.taskId(), new TaskChangedMessage(event.taskId()));
        } catch (Exception e) {
            log.warn("태스크 변경 브로드캐스트 실패 - taskId={}", event.taskId(), e);
        }
    }
}
