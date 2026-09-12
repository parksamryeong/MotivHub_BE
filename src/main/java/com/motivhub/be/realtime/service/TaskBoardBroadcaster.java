package com.motivhub.be.realtime.service;

import com.motivhub.be.realtime.config.RealtimeDestinations;
import com.motivhub.be.realtime.dto.TaskBoardChangeMessage;
import com.motivhub.be.task.dto.TaskResponse;
import com.motivhub.be.task.event.TaskChangeType;
import com.motivhub.be.task.event.TaskChangedEvent;
import com.motivhub.be.task.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TaskBoardBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TaskBoardBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskService taskService;

    public TaskBoardBroadcaster(SimpMessagingTemplate messagingTemplate, TaskService taskService) {
        this.messagingTemplate = messagingTemplate;
        this.taskService = taskService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(TaskChangedEvent event) {
        try {
            TaskResponse task = event.changeType() == TaskChangeType.DELETED
                    ? null
                    : taskService.getResponseForBoardBroadcast(event.taskId());
            messagingTemplate.convertAndSend(
                    RealtimeDestinations.workspaceBoard(event.workspaceId()),
                    new TaskBoardChangeMessage(event.changeType(), event.taskId(), task));
        } catch (Exception e) {
            log.warn("보드 브로드캐스트 실패 - taskId={}, workspaceId={}", event.taskId(), event.workspaceId(), e);
        }
    }
}
