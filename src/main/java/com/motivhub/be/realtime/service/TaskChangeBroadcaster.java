package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.dto.TaskChangedMessage;
import com.motivhub.be.task.event.TaskChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskChangeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public TaskChangeBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(TaskChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/tasks/" + event.taskId(), new TaskChangedMessage(event.taskId()));
    }
}
